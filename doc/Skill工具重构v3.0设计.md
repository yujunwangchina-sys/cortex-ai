# Skill工具重构 v3.0 设计文档

## 问题分析

**当前问题：**
1. AI无法按目录层级探索技能包文件
2. skill_view 返回 SKILL.md 后，AI不知道还有其他文件可以读取
3. relatedFiles 列表缺少使用指引

## 新设计方案

### 工具设计（共3个核心工具）

#### 1. **skill_list** - 技能包索引
**功能：** 列出所有可用的技能包（顶层视图）

**返回内容：**
```json
{
  "packages": [
    {
      "packageId": 123,
      "packageName": "HAP系统维护",
      "description": "DESCRIPTION.md的完整内容",
      "skillCount": 5
    }
  ],
  "personalSkills": [
    {
      "skillId": 456,
      "name": "我的个人技能",
      "description": "..."
    }
  ]
}
```

**使用场景：** AI 首次调用，获取技能包概览

---

#### 2. **skill_tree** - 目录树查看
**功能：** 查看某个技能包的完整文件结构

**输入参数：**
```json
{
  "skillId": 123  // 技能包ID（从skill_list获取）
}
```

**返回内容：**
```json
{
  "packageId": 123,
  "packageName": "HAP系统维护",
  "skillInfo": {
    "description": "从SKILL.md YAML front matter提取的描述",
    "keywords": ["hap", "维护", "故障"],
    "version": "1.0.0",
    "author": "运维团队"
  },
  "files": [
    {
      "fileId": 124,
      "name": "SKILL.md",
      "type": "markdown",
      "isDirectory": false
    },
    {
      "fileId": 125,
      "name": "故障排查流程.md",
      "type": "markdown",
      "isDirectory": false
    },
    {
      "fileId": 126,
      "name": "配置示例.json",
      "type": "json",
      "isDirectory": false
    }
  ],
  "fileCount": 3,
  "usage": "Use skill_read with fileId to read any file content"
}
```

**关键点：**
- **skillInfo** 包含 SKILL.md 的元信息（不含完整内容）
- **files** 列出所有文件，每个文件有 fileId
- **usage** 提示如何读取文件

**使用场景：** AI 想了解技能包结构，查看有哪些文件可用

---

#### 3. **skill_read** - 文件内容读取
**功能：** 读取技能包中任意文件的完整内容

**输入参数：**
```json
{
  "fileId": 124  // 文件ID（从skill_tree获取）
}
```

**返回内容：**
```json
{
  "fileId": 124,
  "fileName": "SKILL.md",
  "filePath": "/HAP系统维护/SKILL.md",
  "content": "# HAP系统维护\n\n完整的SKILL.md内容...",
  "contentLength": 1500
}
```

**使用场景：** AI 需要查看具体文件内容时调用

---

## 工作流示例

### 典型使用流程

```
1. AI: 调用 skill_list
   → 获取所有技能包列表
   → 看到"HAP系统维护"技能包，packageId=123

2. AI: 调用 skill_tree(skillId=123)
   → 看到目录结构：
     - SKILL.md (fileId=124)
     - 故障排查流程.md (fileId=125)
     - 配置示例.json (fileId=126)
   → 看到 skillInfo 中的description和keywords

3. AI: 调用 skill_read(fileId=124)
   → 读取 SKILL.md 完整内容

4. AI: （如需要）调用 skill_read(fileId=125)
   → 读取 故障排查流程.md 完整内容
```

---

## 实现要点

### skill_list 实现
```java
1. 加载授权的技能节点
2. 找出所有技能包（skill_package或directory类型）
3. 对每个技能包：
   - 查找 DESCRIPTION.md
   - 提取description内容
   - 统计子技能数量
4. 返回技能包列表 + 个人技能列表
```

### skill_tree 实现
```java
1. 加载技能包节点，权限检查
2. 加载所有子节点（files）
3. 查找 SKILL.md 文件：
   - 解析 YAML front matter
   - 提取 description, keywords, version 等元信息
4. 构建文件列表（包含fileId、name、type）
5. 返回 skillInfo + files + usage提示
```

### skill_read 实现
```java
1. 根据 fileId 加载文件节点
2. 权限检查
3. 返回完整文件内容
```

---

## YAML Front Matter 解析

### 标准格式
```markdown
---
description: "HAP系统日常维护和故障排查技能"
keywords: ["hap", "维护", "故障排查", "日志"]
version: "1.0.0"
author: "运维团队"
---

# HAP系统维护

...正文内容...
```

### 解析逻辑
1. 检查文件是否以 `---` 开头
2. 提取两个 `---` 之间的YAML块
3. 简单解析key-value对
4. 处理列表（如keywords）

---

## 删除的工具

- **skill_view**：功能被 skill_tree + skill_read 替代
- **skill_search**：暂时保留或移除（可选）

---

## 优势

### 1. 清晰的层级关系
```
skill_list (顶层索引)
    ↓
skill_tree (目录结构)
    ↓
skill_read (文件内容)
```

### 2. 渐进式披露
- 第一层：只看技能包名称和描述
- 第二层：看目录结构和元信息
- 第三层：读取完整文件内容

### 3. 明确的使用指引
- 每个返回都包含 `usage` 提示
- AI 清楚知道下一步如何操作

### 4. 灵活的文件访问
- 可以读取技能包中的任意文件
- 支持多种文件类型（md, json, yaml, txt）

---

## 更新日期

2026-07-03
