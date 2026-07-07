# SKILL.md 标准格式说明

**文档日期**: 2026-07-03  
**版本**: v2.0

---

## 📋 标准格式

### 完整示例

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
    tags: [python, code-quality, linting, analysis]
prerequisites:
  commands: [pylint, flake8, black]
  packages: [pylint>=2.0.0, flake8>=5.0.0]
---

# Python代码分析器

这是一个综合性的Python代码质量分析工具，集成了多个流行的代码检查工具。

## 功能特性

- 静态代码分析（pylint）
- 代码风格检查（flake8）
- 代码格式化（black）
- 复杂度分析
- 自动修复常见问题

## 使用方法

### 分析单个文件

\`\`\`bash
pylint myfile.py
\`\`\`

### 分析整个项目

\`\`\`bash
pylint src/
\`\`\`

## 支持的平台

- Linux
- macOS  
- Windows

## 相关引用

参考其他技能: @file[python-formatter](../python-formatter/SKILL.md)
使用插件: @plugin[code-quality-plugin](code-quality-plugin)
```

---

## 🔑 YAML Frontmatter 字段说明

### 必填字段 ✅

| 字段 | 类型 | 说明 | 示例 |
|------|------|------|------|
| `name` | string | 技能唯一标识（kebab-case） | `python-code-analyzer` |
| `description` | string | 技能简短描述（用于技能索引） | `"Python代码质量分析工具"` |
| `version` | string | 版本号（语义化版本） | `1.0.0` |

### 可选字段 ⭐

| 字段 | 类型 | 说明 | 示例 |
|------|------|------|------|
| `author` | string | 作者 | `Hermes Agent` |
| `license` | string | 许可证 | `MIT` |
| `platforms` | array | 支持的平台 | `[linux, macos, windows]` |
| `metadata` | object | 元数据（tags等） | 见下方 |
| `prerequisites` | object | 前置依赖 | 见下方 |

### metadata 结构

```yaml
metadata:
  hermes:
    tags: [python, code-quality, linting]  # 标签
    category: software-development         # 分类
    difficulty: beginner                   # 难度
```

### prerequisites 结构

```yaml
prerequisites:
  commands: [pylint, flake8]              # 需要的命令行工具
  packages: [pylint>=2.0.0]               # 需要的包及版本
  environment: [PYTHON_PATH]              # 需要的环境变量
```

---

## 📊 系统如何使用这些字段

### 1️⃣ 技能索引（第一次渐进式披露）

**使用字段**：`description`

**显示效果**：
```markdown
## 技能索引

### Python开发工具包
*Python开发常用工具集，包括代码检查、性能分析和测试工具。*

- **python-code-analyzer** — Python代码质量分析工具，支持静态检查、复杂度分析和代码风格检查  [id=201]
- **python-profiler** — Python性能分析工具，找出代码瓶颈  [id=202]
- **python-test-runner** — Python测试执行工具，支持unittest和pytest  [id=203]
```

**提取逻辑**：
1. 优先从 YAML frontmatter 读取 `description` 字段
2. 如果没有，从 `skillMetadata.description` 读取
3. 如果还没有，提取内容的第一段文字

### 2️⃣ 技能详情（skill_view 工具调用时）

**使用字段**：全部字段

AI调用 `skill_view(201)` 时，会获得完整的 SKILL.md 内容，包括：
- YAML frontmatter 的所有字段
- 完整的技能文档内容

---

## 🎯 前端创建模板

### 新建技能时自动生成

前端在用户创建新技能时，会自动生成标准格式的SKILL.md：

```javascript
// SkillTree.vue
const skillMdData = {
  name: 'SKILL.md',
  parentId: response.data.id,
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

## 功能特性

- 功能1
- 功能2

## 使用方法

\`\`\`bash
# 示例代码
\`\`\`
`
}
```

---

## ✅ 最佳实践

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

### 2. name 字段使用 kebab-case

❌ **不好的命名**：
```yaml
name: Python Code Analyzer
name: python_code_analyzer
name: PythonCodeAnalyzer
```

✅ **好的命名**：
```yaml
name: python-code-analyzer
name: git-repo-manager
name: docker-compose-helper
```

### 3. 版本号遵循语义化版本

```yaml
version: 1.0.0      # 主版本.次版本.修订号
version: 2.1.5      # 向下兼容的功能新增
version: 3.0.0      # 不兼容的API变更
```

### 4. tags 要具体

❌ **不好的标签**：
```yaml
tags: [tool, utility, helper]
```

✅ **好的标签**：
```yaml
tags: [python, code-quality, linting, static-analysis]
tags: [git, version-control, repository-management]
```

---

## 🔍 YAML Frontmatter 解析优先级

### 技能描述提取优先级

```
1. YAML frontmatter 的 description 字段（最高优先级）
   ↓
2. skillMetadata JSON 的 description 字段
   ↓
3. 内容的第一段文字（跳过标题和frontmatter）
   ↓
4. 空字符串（兜底）
```

### 示例对比

#### 有 YAML description

```markdown
---
name: my-skill
description: "这是技能的简短描述"
---

# My Skill

这是技能的详细说明，内容很长很长...
```

**提取结果**：`"这是技能的简短描述"`  
**来源**：YAML frontmatter

#### 无 YAML description

```markdown
---
name: my-skill
version: 1.0.0
---

# My Skill

这是技能的详细说明，会被提取作为描述。
```

**提取结果**：`"这是技能的详细说明，会被提取作为描述。"`  
**来源**：内容第一段

---

## 📝 字段校验规则

### 必填字段校验

- `name`: 不能为空，只能包含小写字母、数字、连字符
- `description`: 不能为空，建议20-100字符
- `version`: 必须符合语义化版本格式（x.y.z）

### 可选字段建议

- `author`: 建议填写，便于追踪技能来源
- `platforms`: 如果技能平台无关，可以留空
- `prerequisites`: 如果有依赖，务必填写完整

---

## 🚀 迁移指南

### 旧格式（无YAML）

```markdown
# Python代码分析器

这是一个Python代码质量分析工具...
```

### 新格式（有YAML）

```markdown
---
name: python-code-analyzer
description: "Python代码质量分析工具，支持静态检查和复杂度分析"
version: 1.0.0
author: Hermes Agent
---

# Python代码分析器

这是一个Python代码质量分析工具...
```

### 迁移步骤

1. 在文件开头添加 `---` 分隔符
2. 添加必填字段：name, description, version
3. 根据需要添加可选字段
4. 在最后添加 `---` 结束分隔符
5. 保持原有内容不变

---

## 📚 相关文档

- [Skill渐进式披露机制详解](./Skill渐进式披露机制详解.md)
- [Skill两层权限改造完成说明](./Skill两层权限改造完成说明.md)
- [Skill管理系统完整升级指南](./Skill管理系统完整升级指南.md)

---

**文档状态**: ✅ 完成  
**版本**: v2.0  
**最后更新**: 2026-07-03

