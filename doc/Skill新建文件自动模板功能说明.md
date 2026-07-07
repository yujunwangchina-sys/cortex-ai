# Skill新建文件自动模板功能说明

**功能日期**: 2026-07-03  
**版本**: v2.0  
**功能**: 新建Markdown文件时自动填充YAML frontmatter模板

---

## 📋 功能概述

用户在Skill管理界面新建Markdown文件时，系统会自动填充标准的YAML frontmatter模板，无需手动编写。

---

## 🎯 新建文件类型及模板

### 1️⃣ SKILL.md（技能文件）

**触发时机**: 右键技能包 → 新建技能

**自动生成模板**:
```markdown
---
name: my-skill
description: "my-skill技能描述"
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

# my-skill Skill

技能说明文档

## 功能特性

- 功能1
- 功能2

## 使用方法

\`\`\`bash
# 示例代码
\`\`\`

## 支持的平台

- 平台1

## 相关引用

参考其他技能: @file[文件名](路径)
使用插件: @plugin[插件名](插件名)
```

---

### 2️⃣ DESCRIPTION.md（技能包描述）

**触发时机**: 右键技能包 → 新建DESCRIPTION.md

**自动生成模板**:
```markdown
---
name: 技能包名称
description: "技能包描述"
version: 1.0.0
author: 
---

# 技能包名称 Skills Package

技能包整体说明

## 包含的技能

- skill1: 功能说明
- skill2: 功能说明

## 适用场景

- 场景1
- 场景2

## 前置要求

- 要求1
- 要求2
```

---

### 3️⃣ 普通Markdown文件（新增功能 ⭐）

**触发时机**: 右键文件夹/技能 → 新建文件 → 选择Markdown类型

**自动生成模板**:
```markdown
---
name: 文件名
description: "文件名文档说明"
version: 1.0.0
author: 当前用户名
date: 2026-07-03
---

# 文件名

文档内容

## 简介

在此填写文档内容...

## 详细说明

### 小节1

内容...

### 小节2

内容...

## 参考资料

- 参考链接1
- 参考链接2
```

**特性**:
- ✅ 自动提取文件名（不含.md扩展名）
- ✅ 自动填充当前用户名
- ✅ 自动填充当前日期
- ✅ 提供完整的文档结构模板

---

## 🔧 实现逻辑

### 前端代码（SkillTree.vue）

```javascript
/** 执行创建文件 */
function doCreateFile() {
  let fileName = dialogForm.name
  
  // 自动添加扩展名
  if (dialogForm.fileType && !fileName.includes('.')) {
    fileName += dialogForm.fileType
  }
  
  // 根据文件类型自动填充内容模板
  let defaultContent = ''
  
  // ✅ 如果是Markdown文件，自动添加YAML frontmatter
  if (fileName.toLowerCase().endsWith('.md')) {
    const fileNameWithoutExt = fileName.replace(/\.md$/i, '')
    defaultContent = `---
name: ${fileNameWithoutExt}
description: "${fileNameWithoutExt}文档说明"
version: 1.0.0
author: ${userStore.user.nickName || userStore.user.userName}
date: ${new Date().toISOString().split('T')[0]}
---

# ${fileNameWithoutExt}

文档内容

## 简介

在此填写文档内容...

## 详细说明

### 小节1

内容...

### 小节2

内容...

## 参考资料

- 参考链接1
- 参考链接2
`
  }
  
  const data = {
    name: fileName,
    parentId: currentParentNode.value?.id || null,
    content: defaultContent
  }
  
  createFile(data).then(() => {
    ElMessage.success('创建成功')
    dialogVisible.value = false
    loadTree()
  })
}
```

---

## 🎯 使用场景

### 场景1：创建技能文档

1. 右键技能包 → 新建技能
2. 输入技能名称：`python-analyzer`
3. 点击确定
4. 系统自动创建 `SKILL.md`，内容已包含YAML frontmatter

**结果**: 打开文件即可编辑，无需手动添加YAML

---

### 场景2：创建技能包说明

1. 右键技能包 → 新建DESCRIPTION.md
2. 点击确定
3. 系统自动创建 `DESCRIPTION.md`，内容已包含YAML frontmatter

**结果**: 打开文件即可编辑技能包描述

---

### 场景3：创建普通说明文档

1. 右键文件夹 → 新建文件
2. 输入文件名：`使用指南`
3. 选择文件类型：`Markdown文件 (.md)`
4. 点击确定
5. 系统自动创建 `使用指南.md`，内容已包含YAML frontmatter

**结果**: 标准化的文档结构，便于维护

---

## ✅ 优势

| 优势 | 说明 |
|------|------|
| **节省时间** | 无需手动编写YAML frontmatter |
| **标准化** | 所有文档格式统一 |
| **减少错误** | 避免YAML语法错误 |
| **提高效率** | 开箱即用，直接编辑内容 |
| **便于解析** | 后端可直接提取元数据 |

---

## 📊 模板字段说明

### 必填字段

| 字段 | 说明 | 自动填充 |
|------|------|---------|
| `name` | 文件标识（不含扩展名） | ✅ 自动 |
| `description` | 文件描述 | ✅ 自动（可修改） |
| `version` | 版本号 | ✅ 默认1.0.0 |
| `author` | 作者 | ✅ 当前用户 |
| `date` | 创建日期 | ✅ 当前日期 |

### 可选字段

用户可根据需要添加：
- `license`: 许可证
- `tags`: 标签
- `category`: 分类
- `status`: 状态

---

## 🔍 后端如何使用

### 提取description字段

```java
// PromptBuilder.java
private String extractDescriptionFromYamlFrontmatter(String content) {
    // 1. 找到 --- 分隔符
    // 2. 解析 description: 字段
    // 3. 移除引号
    // 4. 返回描述文本
}
```

### 技能索引显示

```markdown
## 技能索引

### Python开发工具包

- **python-analyzer** — Python代码质量分析工具，支持静态检查和复杂度分析  [id=201]
                         ↑ 来自YAML frontmatter的description字段
```

---

## ⚙️ 自定义模板

### 修改默认模板

如需修改默认模板，编辑 `SkillTree.vue` 中的 `doCreateFile` 方法：

```javascript
// 修改默认模板内容
defaultContent = `---
name: ${fileNameWithoutExt}
description: "${fileNameWithoutExt}文档说明"
version: 1.0.0
author: ${userStore.user.nickName || userStore.user.userName}
date: ${new Date().toISOString().split('T')[0]}
custom_field: "自定义字段"  // ✅ 添加自定义字段
---

# ${fileNameWithoutExt}

自定义内容...
`
```

---

## 🐛 常见问题

### Q1: 创建非Markdown文件会有模板吗？

A: ❌ 不会。只有Markdown文件（.md）会自动填充YAML frontmatter模板，其他文件类型（.py, .js, .json等）创建为空文件。

### Q2: 可以修改模板吗？

A: ✅ 可以。创建后立即可以编辑YAML frontmatter的任何字段。

### Q3: 旧文件会自动添加YAML吗？

A: ❌ 不会。此功能只对新建文件生效，旧文件需要手动添加。

### Q4: YAML frontmatter写错会报错吗？

A: ⚠️ 后端解析时会忽略格式错误的YAML，降级到提取第一段文字作为描述。

---

## 📝 最佳实践

### 1. 及时修改description

创建文件后，立即修改description字段为准确的描述：

```yaml
---
name: python-analyzer
description: "Python代码质量分析工具，支持静态检查、复杂度分析和代码风格检查"  # ✅ 修改为准确描述
---
```

### 2. 添加有用的元数据

根据文件用途添加额外字段：

```yaml
---
name: api-reference
description: "API接口参考文档"
version: 2.1.0
author: 张三
date: 2026-07-03
status: draft        # ✅ 添加状态
category: reference  # ✅ 添加分类
tags: [api, rest, http]  # ✅ 添加标签
---
```

### 3. 保持格式规范

- ✅ 使用双引号包裹含特殊字符的值
- ✅ 数组使用 `[]` 格式
- ✅ 日期使用 ISO 格式（YYYY-MM-DD）
- ✅ 字段名使用小写和下划线

---

## 📚 相关文档

- [SKILL.md标准格式说明](./SKILL.md标准格式说明.md)
- [Skill渐进式披露机制详解](./Skill渐进式披露机制详解.md)
- [Skill渐进式披露v2.0更新说明](./Skill渐进式披露v2.0更新说明.md)

---

**功能状态**: ✅ 已实现  
**版本**: v2.0  
**最后更新**: 2026-07-03

