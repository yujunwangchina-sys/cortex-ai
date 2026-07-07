# Skill插件工具简化说明

## 修改时间
2026-07-06

## 问题描述
SkillManagerPlugin 同时提供了旧的独立工具和新的统一工具，导致功能重复和Agent使用混淆：

### 旧工具（已删除）
- `skill_create` - 创建个人技能
- `skill_edit` - 编辑个人技能

### 新工具（保留）
- `skill_manage` - 统一的技能管理工具
  - action='create' - 创建新技能
  - action='patch' - 精确更新部分内容（推荐用于修复）
  - action='edit' - 全量重写（仅用于大改）
  - action='delete' - 删除技能

## 解决方案

### 删除了旧工具定义
从 `getTools()` 方法中删除：
- `skill_create` 工具定义
- `skill_edit` 工具定义

### 删除了工具路由
从 `executeTool()` 方法中删除：
```java
case "skill_create": return createSkill(arguments);
case "skill_edit": return editSkill(arguments);
```

### 保留了私有方法
`createSkill()` 和 `editSkill()` 作为私有方法保留，因为 `skill_manage` 内部需要调用：
```java
private String skillManage(Map<String, Object> args) {
    switch (action) {
        case "create": return createSkill(args);  // 内部调用
        case "edit": return editSkill(args);      // 内部调用
        case "patch": return patchSkill(args);
        case "delete": return deleteSkill(args);
    }
}
```

## 最终工具列表

SkillManagerPlugin v5.0.0 现在提供**5个工具**：

1. **skills_list** - 列出所有技能包（全局+个人）
2. **skill_view** - 查看技能的完整SKILL.md内容
3. **skill_tree** - 查看技能包的文件树结构
4. **skill_read_file** - 读取技能包内的任意文件
5. **skill_manage** - 统一的技能管理工具 ⭐

## 优势

✅ **简化选择**：Agent不再困惑该用哪个工具创建/编辑技能  
✅ **功能统一**：所有管理操作通过一个工具完成  
✅ **易于维护**：减少代码重复，Bug只需修一次  
✅ **功能更强**：`skill_manage` 提供了 `patch` 功能，旧工具没有  

## 使用示例

### 创建技能
```json
{
  "tool": "skill_manage",
  "arguments": {
    "action": "create",
    "name": "数据库查询技巧",
    "content": "---\ntitle: 数据库查询技巧\n---\n\n## 步骤\n..."
  }
}
```

### 更新技能（推荐patch）
```json
{
  "tool": "skill_manage",
  "arguments": {
    "action": "patch",
    "name": "数据库查询技巧",
    "old_string": "SELECT * FROM",
    "new_string": "SELECT id, name FROM"
  }
}
```

### 删除技能
```json
{
  "tool": "skill_manage",
  "arguments": {
    "action": "delete",
    "name": "数据库查询技巧"
  }
}
```

## 迁移说明

如果有旧代码或文档引用了 `skill_create` 或 `skill_edit`，请替换为：
- `skill_create(...)` → `skill_manage(action='create', ...)`
- `skill_edit(...)` → `skill_manage(action='edit', ...)`
