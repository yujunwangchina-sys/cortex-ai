# Skill渐进式披露 v2.0 更新说明

**更新日期**: 2026-07-03  
**版本**: v2.0.0  
**影响范围**: SKILL.md 格式规范、DESCRIPTION.md 使用、技能索引显示

---

## 📋 更新概述

本次更新优化了Skill渐进式披露机制，主要改进包括：

1. ✅ **DESCRIPTION.md 正式启用** - 技能包描述第一次就展示给AI
2. ✅ **SKILL.md YAML Frontmatter 解析** - 从元数据提取描述字段
3. ✅ **技能索引按包分组** - 结构化展示，层次清晰
4. ✅ **智能描述提取优先级** - 多级降级策略

---

## 🎯 核心改进

### 1. DESCRIPTION.md 启用

#### ❌ v1.0（旧版）

```markdown
## 技能索引

- **apple-notes** — 管理Apple Notes笔记  [id=123]
- **apple-reminders** — 管理Apple Reminders任务  [id=124]
- **python-lint** — Python代码质量检查  [id=201]
```

**问题**：
- AI看不到技能之间的关系
- 不知道哪些技能属于同一个技能包
- DESCRIPTION.md 文件被浪费

#### ✅ v2.0（新版）

```markdown
## 技能索引

### Apple技能包
*Apple生产力工具集成，包括Notes笔记管理、Reminders任务管理和Calendar日历管理。*

- **apple-notes** — 管理Apple Notes笔记  [id=123]
- **apple-reminders** — 管理Apple Reminders任务  [id=124]

### Python开发工具包
*Python开发常用工具集，包括代码检查、性能分析和测试覆盖率工具。*

- **python-lint** — Python代码质量检查  [id=201]
```

**优势**：
- ✅ AI第一眼就知道技能包用途
- ✅ 技能按包分组，结构清晰
- ✅ DESCRIPTION.md 文件发挥作用

---

### 2. SKILL.md YAML Frontmatter 解析

#### ✅ 标准格式（前端自动生成）

```markdown
---
name: python-code-analyzer
description: "Python代码质量分析工具，支持静态检查、复杂度分析和代码风格检查"
version: 1.0.0
author: Hermes Agent
license: MIT
platforms: [linux, macos, windows]
metadata:
  hermes:
    tags: [python, code-quality, linting]
prerequisites:
  commands: [pylint, flake8]
---

# Python代码分析器

完整的技能文档内容...
```

#### ✅ 解析逻辑

```java
// PromptBuilder.java
private String extractDescriptionFromYamlFrontmatter(String content) {
    // 1. 找到 --- 分隔符
    // 2. 解析 description: 字段
    // 3. 移除引号
    // 4. 返回描述文本
}
```

**优势**：
- ✅ 描述直接写在SKILL.md中，集中管理
- ✅ 支持结构化元数据（版本、作者、依赖等）
- ✅ 前端自动生成标准模板

---

### 3. 智能描述提取优先级

```
描述来源优先级（从高到低）：

1. YAML frontmatter 的 description 字段
   ↓ 如果没有
2. skillMetadata JSON 的 description 字段
   ↓ 如果没有
3. 内容的第一段文字（跳过标题和frontmatter）
   ↓ 如果没有
4. 空字符串
```

**优势**：
- ✅ 灵活降级，兼容旧格式
- ✅ 优先使用结构化数据
- ✅ 兜底方案保证不出错

---

## 🔧 技术实现

### 1. AgentConfigLoader.java

#### 新增方法：extractPackageDescription

```java
private String extractPackageDescription(List<SkillNode> children) {
    // 1. 查找 DESCRIPTION.md 文件（不区分大小写）
    // 2. 懒加载其 content 字段
    // 3. 提取第一段有效内容（跳过YAML frontmatter和标题）
    // 4. 限制长度为200字符
    // 5. 保存到技能包节点的 skillMetadata.packageDescription
}
```

#### 新增方法：extractFirstMeaningfulContent

```java
private String extractFirstMeaningfulContent(String content) {
    // 1. 跳过 YAML frontmatter (--- ... ---)
    // 2. 跳过空行
    // 3. 跳过标题行 (# ...)
    // 4. 收集第一段有效内容
    // 5. 限制200字符
}
```

### 2. PromptBuilder.java

#### 新增方法：extractDescriptionFromYamlFrontmatter

```java
private String extractDescriptionFromYamlFrontmatter(String content) {
    // 1. 找到 --- 分隔符
    // 2. 在frontmatter内查找 description: 字段
    // 3. 提取值并移除引号
    // 4. 返回描述文本
}
```

#### 新增方法：groupSkillsByPackage

```java
private Map<String, List<SkillNode>> groupSkillsByPackage(List<SkillNode> skills) {
    // 1. 第一遍：识别所有技能包节点
    // 2. 第二遍：按父节点分组技能
    // 3. 返回分组Map
}
```

#### 新增方法：getPackageDescription

```java
private String getPackageDescription(List<SkillNode> skills, String packageName) {
    // 从技能包节点的 skillMetadata.packageDescription 提取描述
}
```

### 3. SkillTree.vue

#### 自动生成YAML Frontmatter

```javascript
const skillMdData = {
  name: 'SKILL.md',
  content: `---
name: ${skillName}
description: "${skillName}技能描述"
version: 1.0.0
author: 
license: MIT
platforms: []
metadata:
  hermes:
    tags: []
prerequisites:
  commands: []
---

# ${skillName} Skill

技能说明文档
...
`
}
```

---

## 📊 Token消耗对比

### v1.0（旧版）

```markdown
## 技能索引

- **apple-notes** — 管理Apple Notes笔记  [id=123]
- **apple-reminders** — 管理Apple Reminders任务  [id=124]
- **python-lint** — Python代码质量检查  [id=201]
```

**Token消耗**：约 150 tokens

### v2.0（新版）

```markdown
## 技能索引

### Apple技能包
*Apple生产力工具集成，包括Notes笔记管理、Reminders任务管理和Calendar日历管理。*

- **apple-notes** — 管理Apple Notes笔记  [id=123]
- **apple-reminders** — 管理Apple Reminders任务  [id=124]

### Python开发工具包
*Python开发常用工具集，包括代码检查、性能分析和测试覆盖率工具。*

- **python-lint** — Python代码质量检查  [id=201]
```

**Token消耗**：约 250 tokens（仅增加100 tokens）

### 对比SKILL.md全量注入

- SKILL.md全文：5000+ tokens per skill
- 5个技能 = 25000+ tokens
- v2.0方案：~250 tokens
- **节省 99% token！**

---

## 🚀 升级指南

### 对于新技能

✅ **无需操作** - 前端自动生成标准格式

### 对于旧技能

#### 方案1：添加YAML Frontmatter（推荐）

```markdown
<!-- 在SKILL.md开头添加 -->
---
name: my-skill
description: "技能的简短描述"
version: 1.0.0
---

# 原有标题

原有内容...
```

#### 方案2：不修改

✅ **仍然兼容** - 系统会自动降级到提取第一段文字

---

## 📋 最佳实践

### 1. description 字段要清晰简洁

❌ **不好的描述**：
```yaml
description: "工具"
description: "这是一个很好用的工具"
```

✅ **好的描述**：
```yaml
description: "Python代码质量分析工具，支持静态检查、复杂度分析和代码风格检查"
description: "Git仓库管理工具，提供分支操作、提交历史查询和冲突解决功能"
```

### 2. DESCRIPTION.md 要写技能包整体介绍

❌ **不好的描述**：
```markdown
# Apple技能包

这是一个技能包。
```

✅ **好的描述**：
```markdown
# Apple技能包

Apple生产力工具集成，包括Notes笔记管理、Reminders任务管理和Calendar日历管理。
通过这些技能，你可以轻松地管理个人事务和工作任务，提高工作效率。
```

### 3. 技能包分组要合理

✅ **合理的技能包**：
- Apple技能包：apple-notes, apple-reminders, apple-calendar
- Python开发工具包：python-lint, python-profiler, python-test
- Git工具包：git-commit, git-branch, git-merge

❌ **不合理的技能包**：
- 工具包：python-lint, git-commit, docker-run（太杂乱）

---

## 🐛 常见问题

### Q1: 旧技能会不会报错？

A: ✅ **不会**。系统有完整的降级策略，旧格式的SKILL.md仍然可以正常工作。

### Q2: 必须写YAML Frontmatter吗？

A: ⭐ **建议写**。虽然不写也能工作，但写了可以让描述更精确，AI理解更准确。

### Q3: DESCRIPTION.md 没写会怎样？

A: ✅ **优雅降级**。技能包不会显示描述，只显示技能列表，不影响功能。

### Q4: 如何迁移旧技能？

A: 有两种方式：
1. 手动在SKILL.md开头添加YAML frontmatter
2. 什么都不做，系统自动兼容

---

## 📚 相关文档

- [Skill渐进式披露机制详解](./Skill渐进式披露机制详解.md)
- [SKILL.md标准格式说明](./SKILL.md标准格式说明.md)
- [Skill两层权限改造完成说明](./Skill两层权限改造完成说明.md)

---

**更新状态**: ✅ 完成  
**版本**: v2.0.0  
**最后更新**: 2026-07-03

