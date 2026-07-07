# Skill个人技能创建问题诊断

## 问题描述

用户反馈：创建个人技能时，"老是就创建个描述文件拉倒了"

## 问题分析

### 1. 代码逻辑检查

查看 `SkillManagerPlugin.java` 的 `createSkill` 方法，发现创建流程是：

```java
// 1. 创建技能目录
SkillNode skillDir = new SkillNode();
skillDir.setParentId(personalPackage.getId());
skillDir.setName(name);
skillDir.setPath(personalPackage.getPath() + "/" + name);
skillDir.setIsDirectory(true);
skillDir.setNodeType("directory");
// ... 其他属性
skillNodeMapper.insertNode(skillDir);

// 2. 创建 SKILL.md 文件
SkillNode skillMd = new SkillNode();
skillMd.setParentId(skillDir.getId());
skillMd.setName("SKILL.md");
skillMd.setPath(skillDir.getPath() + "/SKILL.md");
skillMd.setIsDirectory(false);
skillMd.setNodeType("file");
skillMd.setContent(content);
// ... 其他属性
skillNodeMapper.insertNode(skillMd);
```

**结论**：代码逻辑上创建了两个节点：
1. 技能目录节点
2. SKILL.md 文件节点

### 2. 可能的原因

#### 原因1：前端树组件没有展示子文件

前端skill树组件可能只展示了目录级别，没有自动展开显示子文件。

**排查方法**：
1. 在前端技能树中，手动点击展开新创建的技能目录
2. 查看是否能看到 SKILL.md 文件

#### 原因2：数据库插入失败

第二次 `insertNode` 可能失败了（比如外键约束、字段校验等）

**排查方法**：
1. 查看后端日志，搜索 "Skill created" 日志
2. 检查数据库 `skill_node` 表，查看是否有两条记录：
   ```sql
   SELECT * FROM skill_node 
   WHERE owner_user = 'your_username' 
   AND name LIKE '%技能名称%'
   ORDER BY create_time DESC;
   ```

#### 原因3：Agent content参数问题

Agent调用 skill_create 时，传入的 `content` 参数可能不完整或格式不对。

**排查方法**：
1. 查看 Agent 执行日志中的 tool_call 参数
2. 确认 content 参数是否包含完整的 SKILL.md 内容

#### 原因4：期望创建多个文件

如果你期望创建的技能不只有 SKILL.md，还包括其他文件（如 Python脚本、JavaScript文件等），那么当前的 skill_create 工具确实只会创建 SKILL.md。

**解决方法**：
- 使用 skill_create 创建技能后，再使用其他工具（如文件上传）添加更多文件
- 或者修改 SkillManagerPlugin，支持批量创建多个文件

## 排查步骤

### 步骤1：查看数据库

```sql
-- 查看最近创建的个人技能
SELECT id, name, path, is_directory, node_type, parent_id, create_time 
FROM skill_node 
WHERE skill_scope = 'personal' 
AND owner_user = 'your_username'
ORDER BY create_time DESC 
LIMIT 20;

-- 查看某个技能的所有子节点
SELECT id, name, path, is_directory, node_type, parent_id 
FROM skill_node 
WHERE parent_id = (
  SELECT id FROM skill_node 
  WHERE name = '技能名称' AND is_directory = 1 
  LIMIT 1
);
```

### 步骤2：查看后端日志

搜索以下关键字：
- `📝 skill_create called`
- `✨ Skill created`
- `ERROR` 或 `Exception`

### 步骤3：测试完整流程

1. 在 Agent 聊天界面发送：
   ```
   帮我创建一个测试技能，名称叫"测试技能001"，内容是：
   ---
   name: 测试技能001
   description: 这是一个测试技能
   ---
   
   # 测试技能
   
   这是测试内容。
   ```

2. 观察返回结果

3. 去前端技能管理页面，查看是否出现新的技能

4. 点击展开技能目录，查看是否有 SKILL.md 文件

### 步骤4：前端树组件排查

查看 `SkillTree.vue` 组件：

**检查点1：是否支持懒加载子节点**
```javascript
// 查找 lazy 或 load 相关的代码
// 确认是否设置了 :load 方法
```

**检查点2：树节点展开事件**
```javascript
// 查找 node-expand 或类似事件
// 确认展开时是否加载子节点
```

**检查点3：数据加载逻辑**
```javascript
// 查找 getSkillTree 或 getAvailablePackages 调用
// 确认返回的数据是否包含子节点
```

## 临时解决方案

如果确认是前端展示问题，可以手动刷新技能树：

1. 切换到其他页面
2. 再切换回技能管理页面
3. 手动点击展开新创建的技能目录

## 长期解决方案

### 方案1：优化前端树组件

确保技能树组件在创建新节点后：
1. 自动刷新树数据
2. 自动展开新创建的节点
3. 显示创建成功的提示

### 方案2：增强 skill_create 工具

如果需要支持创建多文件技能：

```java
// 在 SkillManagerPlugin.java 中添加新参数
private String createSkill(Map<String, Object> args) {
    String name = (String) args.get("name");
    String content = (String) args.get("content");
    List<Map<String, String>> additionalFiles = (List) args.get("additionalFiles"); // 新增
    
    // ... 创建目录和 SKILL.md
    
    // 创建额外的文件
    if (additionalFiles != null) {
        for (Map<String, String> file : additionalFiles) {
            SkillNode fileNode = new SkillNode();
            fileNode.setParentId(skillDir.getId());
            fileNode.setName(file.get("name"));
            fileNode.setContent(file.get("content"));
            // ... 其他属性
            skillNodeMapper.insertNode(fileNode);
        }
    }
    
    // ...
}
```

### 方案3：Agent提示优化

在 Agent 的 prompt 中明确说明：

```
skill_create 工具会创建：
1. 一个技能目录
2. 目录下的 SKILL.md 描述文件

如果需要添加更多文件（如Python脚本、配置文件等），请在创建技能后：
- 使用文件上传功能
- 或使用 skill_edit 工具添加更多文件
```

## 需要提供的信息

为了更准确地诊断问题，请提供：

1. **具体的操作步骤**：
   - 你是如何让 Agent 创建技能的？（完整的对话内容）
   - 创建后看到了什么？（截图或详细描述）
   - 期望看到什么？

2. **后端日志**：
   - 创建技能时的完整日志
   - 是否有 ERROR 或 WARN 日志

3. **数据库查询结果**：
   ```sql
   SELECT * FROM skill_node 
   WHERE owner_user = 'your_username' 
   ORDER BY create_time DESC 
   LIMIT 10;
   ```

4. **前端表现**：
   - 技能树中是否能看到新创建的技能目录？
   - 点击展开后是否能看到 SKILL.md？
   - 控制台是否有错误？

## 快速测试

执行以下SQL，手动创建一个测试技能，看看前端能否正常显示：

```sql
-- 1. 找到你的个人技能包ID
SELECT id, name, path FROM skill_node 
WHERE name LIKE '%个人技能%' 
AND owner_user = 'your_username' 
AND is_directory = 1;

-- 假设返回的ID是 123

-- 2. 创建测试技能目录
INSERT INTO skill_node (
  parent_id, name, path, is_directory, node_type, 
  skill_scope, business_system, owner_user, 
  sort_order, create_by, create_time
) VALUES (
  123, '手动测试技能', 'your_username的个人技能/手动测试技能', 
  1, 'directory', 'personal', 'cortex', 'your_username', 
  0, 'test', NOW()
);

-- 3. 获取刚创建的目录ID
SELECT id FROM skill_node WHERE name = '手动测试技能' ORDER BY id DESC LIMIT 1;

-- 假设返回的ID是 456

-- 4. 创建 SKILL.md 文件
INSERT INTO skill_node (
  parent_id, name, path, is_directory, node_type, 
  skill_scope, business_system, owner_user, 
  content, file_size, mime_type,
  sort_order, create_by, create_time
) VALUES (
  456, 'SKILL.md', 'your_username的个人技能/手动测试技能/SKILL.md', 
  0, 'file', 'personal', 'cortex', 'your_username', 
  '---\nname: 手动测试技能\n---\n\n# 测试', 
  50, 'text/markdown',
  0, 'test', NOW()
);
```

然后刷新前端页面，查看是否能正常显示这两个节点。

## 总结

根据代码分析，`skill_create` 工具确实会创建目录和 SKILL.md 文件。如果你只看到"描述文件"，最可能的原因是：

1. **前端树组件没有展开子节点** - 需要手动点击展开
2. **你期望的不只是 SKILL.md** - 需要额外添加其他文件
3. **数据库插入部分失败** - 需要查看后端日志确认

建议按照上面的排查步骤逐一检查，找出具体原因。
