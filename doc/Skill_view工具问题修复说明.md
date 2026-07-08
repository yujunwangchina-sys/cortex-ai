# skill_view 工具返回空内容问题 - 修复说明

## 问题背景

用户报告使用 `skill_view` 工具查询技能 ID=11 时，返回的 `content` 字段为空，`isEmpty` 为 true，无法获取技能内容。

## 问题分析

### 根本原因

ID=11 是一个**技能包目录节点**，而不是 SKILL.md 文件本身。原有代码逻辑存在以下潜在问题：

1. **node_type 判断不完整**：
   - 原代码只检查 `node_type` 是否为 "skill_package" 或 "directory"
   - 但有些技能包导入时，`node_type` 可能被设置为 "skill"，同时 `is_directory=true`
   - 这导致目录节点被误判为普通技能文件，直接返回了空的 content

2. **日志不够详细**：
   - 原代码日志简单，无法诊断子节点查询是否成功
   - 无法看到每个子节点的名称和内容状态

3. **错误处理不够明确**：
   - 当找到 SKILL.md 但内容为空时，没有单独的错误提示
   - 无法区分"未找到 SKILL.md"和"SKILL.md 内容为空"

### 数据库结构分析

技能包在数据库中的典型结构：

```
skill_node 表
├─ ID=11: human-genetic-declaration-skill-1.0 (技能包目录)
│   ├─ node_type: "skill" 或 "skill_package" 或 "directory"
│   ├─ is_directory: true
│   ├─ content: NULL 或 ""（目录节点没有内容）
│   └─ children (parent_id=11):
│       ├─ ID=12: SKILL.md (实际技能文件)
│       │   ├─ node_type: "file"
│       │   ├─ is_directory: false
│       │   └─ content: "实际技能内容..."
│       ├─ ID=13: DESCRIPTION.md
│       └─ ...
```

## 修复方案

### 1. 增强目录检测逻辑

**修改文件**：`SkillManagerPlugin.java` 的 `viewSkill()` 方法

**原代码**：
```java
String nodeType = node.getNodeType() != null ? node.getNodeType() : "skill";

if ("skill_package".equals(nodeType) || "directory".equals(nodeType))
{
    // 处理技能包...
}
```

**修复后**：
```java
String nodeType = node.getNodeType() != null ? node.getNodeType() : "skill";
Boolean isDirectory = node.getIsDirectory() != null ? node.getIsDirectory() : false;

// ✅ 增加 isDirectory 检查，覆盖所有目录类型
if ("skill_package".equals(nodeType) || "directory".equals(nodeType) || isDirectory)
{
    // 处理技能包...
}
```

**效果**：无论 `node_type` 如何设置，只要 `is_directory=true`，都会进入技能包处理逻辑。

### 2. 增强调试日志

**新增日志**：
```java
log.info("📦 Detected directory/package [id={}, nodeType={}, isDirectory={}], searching for SKILL.md", 
        skillId, nodeType, isDirectory);

log.info("📂 Found {} children for package [packageId={}]", 
        children != null ? children.size() : 0, skillId);

// 列出所有子节点
for (SkillNode child : children)
{
    log.debug("  - Child: id={}, name={}, nodeType={}, hasContent={}", 
            child.getId(), child.getName(), child.getNodeType(),
            child.getContent() != null && !child.getContent().isEmpty());
}

log.info("🔍 Found SKILL.md child [childId={}], loading content...", child.getId());
log.info("✅ Loaded SKILL.md [skillMdId={}, contentLength={}]", skillMdNode.getId(), contentLength);
```

**效果**：
- 清楚显示节点类型判断过程
- 列出所有子节点，便于诊断文件缺失问题
- 显示 SKILL.md 加载过程和内容长度

### 3. 细化错误提示

**新增判断**：
```java
if (contentLength > 0)
{
    return buildSkillViewResponse(skillMdNode, node.getPath(), skillId, children);
}
else
{
    log.warn("⚠️ SKILL.md found but content is empty [skillMdId={}]", skillMdNode.getId());
    return ToolResult.error("SKILL.md 文件内容为空，请检查导入过程是否完整").toJson();
}
```

**错误消息分类**：
- "技能包中未找到 SKILL.md 文件，请检查技能包结构" → 文件缺失
- "SKILL.md 文件内容为空，请检查导入过程是否完整" → 文件存在但内容空
- "Package has no children [packageId={}]" → 技能包为空

### 4. 添加优化查询方法

**新增接口方法**：`SkillNodeMapper.selectSkillMdByPackageId()`

**Mapper 接口**：
```java
/**
 * Find SKILL.md file within a skill package by package ID (for viewSkill optimization).
 */
SkillNode selectSkillMdByPackageId(@org.apache.ibatis.annotations.Param("packageId") Long packageId);
```

**Mapper XML**：
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

**效果**：
- 一次 SQL 查询直接获取 SKILL.md，减少数据库往返
- 不区分大小写匹配（SKILL.md, skill.md 都能匹配）
- 未来可以替换当前的循环查找逻辑

## 诊断工具

### 1. SQL 诊断脚本

**文件**：`diagnose-skill-11.sql`

包含 4 个查询：
1. 查看 ID=11 节点的详细信息（node_type, is_directory, content 状态）
2. 查看所有子节点及其内容状态
3. 专门查找 SKILL.md 文件（不区分大小写）
4. 列出所有 .md 文件（如果 SKILL.md 不存在）

**使用方法**：
```bash
mysql -u root -p cortex-vue < diagnose-skill-11.sql
```

### 2. 诊断文档

**文件**：`Skill_view工具空内容问题诊断.md`

包含：
- 问题描述和根本原因分析
- 5 种可能的问题点及验证方法
- 完整的诊断步骤
- 修复方案（数据修复 / 代码增强 / 查询优化）
- 代码参考位置

## 验证步骤

### 1. 重启应用

修改代码后，重启 Spring Boot 应用使更改生效。

### 2. 查看启动日志

确认没有 Mapper XML 解析错误。

### 3. 重新调用 skill_view

使用 Agent 重新调用：
```
请查看技能 ID=11 的内容
```

### 4. 检查应用日志

查找新增的日志：
```
📖 skill_view called [skillId=11, name=..., nodeType=...]
📦 Detected directory/package [id=11, nodeType=..., isDirectory=true], searching for SKILL.md
📂 Found 3 children for package [packageId=11]
  - Child: id=12, name=SKILL.md, nodeType=file, hasContent=true
🔍 Found SKILL.md child [childId=12], loading content...
✅ Loaded SKILL.md [skillMdId=12, contentLength=5432]
```

### 5. 如果仍然失败

执行 `diagnose-skill-11.sql` 查看数据库实际情况：
- 确认 ID=11 的 `is_directory` 值
- 确认子节点是否存在
- 确认 SKILL.md 的 content 是否有值

如果 content 确实为空，需要重新导入技能包。

## 影响范围

### 修改的文件

1. **SkillManagerPlugin.java**
   - 方法：`viewSkill(Map<String, Object> args)`
   - 修改：增强目录判断逻辑、增加调试日志、细化错误提示

2. **SkillNodeMapper.java**
   - 新增方法：`selectSkillMdByPackageId(Long packageId)`

3. **SkillNodeMapper.xml**
   - 新增查询：`<select id="selectSkillMdByPackageId">`

### 兼容性

✅ **向后兼容**：
- 原有的 `node_type` 判断逻辑保留
- 只是新增了 `isDirectory` 检查，扩大了覆盖范围
- 不影响现有正常工作的技能包

✅ **不影响其他功能**：
- 只修改了 `viewSkill()` 方法
- 其他方法（`listSkills`, `searchSkills` 等）未改动

## 未来优化建议

### 1. 使用优化查询

将当前的循环查找 SKILL.md 替换为直接查询：

```java
// 替换原有的循环查找
SkillNode skillMdNode = skillNodeMapper.selectSkillMdByPackageId(skillId);
if (skillMdNode != null && skillMdNode.getContent() != null && !skillMdNode.getContent().isEmpty())
{
    return buildSkillViewResponse(skillMdNode, node.getPath(), skillId, null);
}
```

**优点**：
- 减少一次 `selectChildrenByParentId()` 查询
- 减少内存中的列表遍历
- 性能更好

### 2. 标准化 node_type 值

在技能包导入时，统一规范：
- 技能包根目录：`node_type="skill_package"`
- 子目录（包含 SKILL.md）：`node_type="skill"`
- 普通子目录：`node_type="directory"`
- 文件节点：`node_type="file"`

这样可以减少判断逻辑的复杂度。

### 3. 添加单元测试

为 `viewSkill()` 方法添加单元测试，覆盖：
- 技能包目录（不同 node_type 组合）
- 单个技能文件
- SKILL.md 不存在的情况
- SKILL.md 内容为空的情况
- 权限检查失败的情况

## 总结

本次修复解决了 `skill_view` 工具在查询技能包目录时返回空内容的问题，主要通过：

1. **增强判断逻辑**：不仅依赖 `node_type`，还检查 `is_directory` 字段
2. **增强日志**：详细输出查询过程，便于问题诊断
3. **细化错误**：区分"文件不存在"和"文件内容为空"两种情况
4. **提供诊断工具**：SQL 脚本和文档帮助快速定位数据问题

修改完全向后兼容，不影响现有功能。
