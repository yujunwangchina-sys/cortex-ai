# skill_view 工具返回空内容问题诊断

## 问题描述

用户调用 `skill_view` 工具查询技能 ID=11 时，返回结果中 `content` 字段为空，`isEmpty` 为 true：

```json
{
  "success": true,
  "skill": {
    "id": 11,
    "name": "human-genetic-declaration-skill-1.0",
    "path": "/BIMS/human-genetic-declaration-skill-1.0",
    "content": "",
    "isEmpty": true,
    ...
  }
}
```

## 根本原因分析

从代码分析来看，ID=11 是一个**技能包目录**（`node_type` 可能是 "skill_package"、"directory" 或 "skill"），不是 SKILL.md 文件本身。

`viewSkill()` 方法的正常流程：

1. 当 `skillId=11` 传入时，调用 `selectNodeWithContent(11)` 返回目录节点本身
2. 检测到是目录类型（`skill_package` 或 `directory`）
3. 调用 `selectChildrenByParentId(11)` 查找子节点
4. 在子节点中查找名为 "SKILL.md" 的文件（不区分大小写）
5. 如果找到，返回 SKILL.md 的完整内容
6. 如果未找到，返回错误："技能包中未找到 SKILL.md 文件"

## 可能的问题点

### 1. node_type 字段值不正确

如果 ID=11 的 `node_type` 不是 "skill_package"、"directory" 或 "skill"，代码会走到错误的分支。

**验证方法**：
```sql
SELECT id, name, node_type, is_directory 
FROM skill_node 
WHERE id = 11;
```

### 2. 子节点不存在或 parent_id 未正确设置

如果没有子节点或 parent_id 未正确关联到 11，`selectChildrenByParentId(11)` 会返回空列表。

**验证方法**：
```sql
SELECT id, name, node_type, parent_id, LENGTH(content) as content_length
FROM skill_node 
WHERE parent_id = 11;
```

### 3. SKILL.md 文件不存在或命名不匹配

如果子节点中没有名为 "SKILL.md" 的文件（注意大小写），代码无法找到。

**验证方法**：
```sql
SELECT id, name, UPPER(name) as name_upper, LENGTH(content) as content_length
FROM skill_node 
WHERE parent_id = 11 
  AND UPPER(name) = 'SKILL.MD';
```

### 4. SKILL.md 文件的 content 字段为空

如果文件存在但导入时 content 未保存（可能是导入过程出错）。

**验证方法**：
```sql
SELECT id, name, 
       CASE 
           WHEN content IS NULL THEN 'NULL'
           WHEN content = '' THEN 'EMPTY'
           ELSE CONCAT('LENGTH=', LENGTH(content))
       END as content_status
FROM skill_node 
WHERE parent_id = 11 AND UPPER(name) = 'SKILL.MD';
```

### 5. 数据库查询问题

`selectNodeWithContent` 方法虽然名字叫 "WithContent"，但实际上总是查询 content 字段。如果数据库连接有问题或字段映射错误，可能导致内容丢失。

**验证方法**：检查 SkillNodeMapper.xml 中的 resultMap 映射。

## 诊断步骤

### 步骤 1：执行完整诊断 SQL

运行 `diagnose-skill-11.sql` 中的查询，获取完整的技能包结构信息。

```sql
-- 查看 ID=11 节点信息
SELECT id, name, node_type, is_directory, skill_scope, parent_id, path,
       CASE 
           WHEN content IS NULL THEN 'NULL'
           WHEN content = '' THEN 'EMPTY'
           ELSE CONCAT('LENGTH=', LENGTH(content))
       END as content_status
FROM skill_node WHERE id = 11;

-- 查看子节点
SELECT id, name, node_type, parent_id,
       CASE 
           WHEN content IS NULL THEN 'NULL'
           WHEN content = '' THEN 'EMPTY'
           ELSE CONCAT('LENGTH=', LENGTH(content))
       END as content_status
FROM skill_node WHERE parent_id = 11 ORDER BY name;

-- 专门查找 SKILL.md
SELECT id, name, LENGTH(content) as content_length, 
       LEFT(content, 200) as content_preview
FROM skill_node 
WHERE parent_id = 11 AND UPPER(name) = 'SKILL.MD';
```

### 步骤 2：检查日志输出

在 `SkillManagerPlugin.viewSkill()` 方法中添加了详细的日志：

```java
log.info("📖 skill_view called [skillId={}, name={}, nodeType={}]", ...);
log.info("📦 Detected skill package, searching for SKILL.md [packageId={}]", ...);
log.info("✅ Found SKILL.md [skillMdId={}, contentLength={}]", ...);
log.warn("⚠️ No SKILL.md found in package [packageId={}, packageName={}]", ...);
```

检查应用日志，查看实际执行了哪个分支。

### 步骤 3：验证导入过程

如果是新导入的技能包，检查导入日志：

```
创建文件夹节点 [name=human-genetic-declaration-skill-1.0, id=11, nodeType=skill_package]
创建文件节点 [name=SKILL.md, id=12, size=1234]
```

确认：
- 文件夹节点是否创建成功
- SKILL.md 文件节点是否创建成功
- 文件大小 (size) 是否大于 0

### 步骤 4：直接查询 SKILL.md 内容

如果找到了 SKILL.md 文件的 ID（假设是 ID=12），直接查询其内容：

```sql
SELECT id, name, LENGTH(content) as content_length,
       LEFT(content, 500) as content_preview
FROM skill_node WHERE id = 12;
```

## 修复方案

### 方案 A：数据修复（如果是数据问题）

如果发现 SKILL.md 的 content 确实为空，需要重新导入：

1. 删除现有技能包（包括所有子节点）
2. 重新上传技能包 ZIP 文件
3. 确认导入成功并检查 content 字段

### 方案 B：代码增强（如果是逻辑问题）

如果代码逻辑有问题，可以增强错误提示：

```java
// 在 viewSkill() 方法中添加更详细的日志
if ("skill_package".equals(nodeType) || "directory".equals(nodeType))
{
    List<SkillNode> children = skillNodeMapper.selectChildrenByParentId(skillId);
    log.info("📂 Package has {} children", children != null ? children.size() : 0);
    
    if (children != null && !children.isEmpty()) {
        for (SkillNode child : children) {
            log.debug("  - Child: id={}, name={}, hasContent={}", 
                child.getId(), child.getName(), 
                child.getContent() != null && !child.getContent().isEmpty());
        }
    }
    
    // ... 原有的查找 SKILL.md 逻辑
}
```

### 方案 C：查询优化

考虑在 `viewSkill()` 中添加一个专门的查询方法：

```java
/**
 * 根据技能包ID直接查找SKILL.md文件（一次查询）
 */
public SkillNode findSkillMdInPackage(Long packageId) {
    return skillNodeMapper.selectSkillMdByPackageId(packageId);
}
```

在 SkillNodeMapper.xml 中添加：

```xml
<select id="selectSkillMdByPackageId" parameterType="Long" resultMap="SkillNodeResult">
    SELECT id, name, file_extension, path, is_directory, node_type,
           skill_scope, skill_type, parent_id, content, skill_metadata,
           file_size, mime_type, create_by, create_time
    FROM skill_node
    WHERE parent_id = #{packageId}
      AND UPPER(name) = 'SKILL.MD'
      AND is_directory = 0
    LIMIT 1
</select>
```

## 下一步行动

1. **立即执行**：运行 `diagnose-skill-11.sql` 获取详细信息
2. **根据结果**：
   - 如果子节点不存在 → 检查导入过程，可能需要重新导入
   - 如果 SKILL.md 存在但 content 为空 → 数据损坏，需要重新导入
   - 如果 SKILL.md 存在且有内容 → 代码逻辑问题，检查日志和分支判断
3. **提供反馈**：将诊断结果反馈给开发团队，确定最终修复方案

## 参考代码位置

- **插件类**：`com.ruoyi.agent.runtime.builtin.SkillManagerPlugin`
  - `viewSkill()` 方法（约 400 行）
  - `buildSkillViewResponse()` 方法（约 450 行）

- **Mapper 接口**：`com.ruoyi.skill.mapper.SkillNodeMapper`
  - `selectNodeWithContent(Long id)`
  - `selectChildrenByParentId(Long parentId)`

- **Mapper XML**：`mapper/skill/SkillNodeMapper.xml`
  - `<select id="selectNodeWithContent">` 
  - `<select id="selectChildrenByParentId">`

- **导入逻辑**：`com.ruoyi.skill.service.impl.SkillNodeServiceImpl`
  - `importSkillPackageToDb()` 方法（约 680 行）
