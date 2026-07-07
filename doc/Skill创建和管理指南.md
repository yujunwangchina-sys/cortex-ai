---
description: Skill工具使用完整指南，包含查看、创建和更新个人技能的详细流程
keywords: [skill, 技能管理, 个人技能, skill_create, skill_update]
version: 3.0.0
---

# Skill创建和管理指南（v3.0）

本指南详细介绍如何使用Skill工具系统查看、创建和管理个人技能。

## 一、查看技能的标准流程（Pull Model - 渐进式披露）

Skill系统采用Pull Model设计，避免一次性加载所有内容造成上下文过载。

### 1.1 skills_list - 列出所有技能包

**用途**：获取技能包概览，查看技能包描述和子技能列表

**返回结构**：
```json
{
  "globalPackages": [
    {
      "id": 1,
      "name": "数据库操作",
      "description": "包含MySQL、PostgreSQL等数据库操作技能",
      "skills": [
        {
          "id": 10,
          "name": "MySQL查询优化.md",
          "description": "MySQL查询性能优化技巧"
        }
      ]
    }
  ],
  "personalPackages": [
    {
      "id": 100,
      "name": "admin的个人技能",
      "description": "您的个人技能集合",
      "skills": [
        {
          "id": 101,
          "name": "Python爬虫技巧",
          "description": "爬虫常用技巧汇总"
        }
      ]
    }
  ]
}
```

**调用示例**：
```json
{
  "tool": "skills_list"
}
```

### 1.2 skill_tree - 查看技能包的目录结构

**用途**：查看技能包内的完整文件列表和SKILL.md元信息

**参数**：
- `skillId`: 技能包ID（从 skills_list 获取）

**返回结构**：
```json
{
  "packageId": 1,
  "packageName": "数据库操作",
  "packagePath": "/Skills/Database",
  "skillInfo": {
    "description": "数据库操作技能包",
    "keywords": ["database", "sql"],
    "version": "1.0.0"
  },
  "files": [
    {
      "fileId": 10,
      "name": "SKILL.md",
      "isDirectory": false,
      "type": "markdown"
    },
    {
      "fileId": 11,
      "name": "MySQL示例.sql",
      "isDirectory": false,
      "type": "other"
    }
  ],
  "usage": "Use skill_read with fileId to read any file content"
}
```

**调用示例**：
```json
{
  "tool": "skill_tree",
  "skillId": 1
}
```

### 1.3 skill_read - 读取具体文件内容

**用途**：读取技能包中某个文件的完整内容

**参数**：
- `fileId`: 文件ID（从 skill_tree 获取）

**返回结构**：
```json
{
  "fileId": 10,
  "fileName": "SKILL.md",
  "filePath": "/Skills/Database/SKILL.md",
  "content": "# 数据库操作技能\n\n...",
  "contentLength": 1234
}
```

**调用示例**：
```json
{
  "tool": "skill_read",
  "fileId": 10
}
```

### 1.4 完整工作流示例

```
User: 帮我查看数据库相关的技能

Agent工作流：
1. 调用 skills_list 查看所有技能包
   → 返回：发现有"数据库操作"技能包（id=1）

2. 调用 skill_tree(skillId=1) 查看目录
   → 返回：
     - SKILL.md (fileId=10)
     - MySQL示例.sql (fileId=11)
     - PostgreSQL示例.sql (fileId=12)

3. 调用 skill_read(fileId=10) 读取 SKILL.md 内容
   → 返回：完整的技能文档

4. 根据需要，继续读取示例文件：
   skill_read(fileId=11) 读取 MySQL示例.sql
```

---

## 二、管理个人技能

每个用户有一个独立的个人技能包，包含多个技能目录。每个技能目录包含SKILL.md和其他可选文件。

### 2.1 skill_get_personal - 查看个人技能树

**用途**：查看当前用户的所有个人技能，用于更新前了解现有结构

**调用示例**：
```json
{
  "tool": "skill_get_personal"
}
```

**返回结构**：
```json
{
  "packageId": 100,
  "packageName": "admin的个人技能",
  "skills": [
    {
      "skillName": "Python爬虫技巧",
      "files": [
        {
          "fileId": 101,
          "fileName": "SKILL.md"
        },
        {
          "fileId": 102,
          "fileName": "示例代码.py"
        }
      ]
    }
  ],
  "skillCount": 1
}
```

### 2.2 skill_create - 创建个人技能

**用途**：创建新的个人技能，支持文件树结构（必须包含SKILL.md，可选其他文件）

**参数**：
- `skillName`: 技能名称
- `files`: 文件列表数组，每个文件包含 `name` 和 `content`

**完整调用示例**：
```json
{
  "tool": "skill_create",
  "skillName": "Python爬虫技巧",
  "files": [
    {
      "name": "SKILL.md",
      "content": "---\ndescription: Python爬虫常用技巧汇总\nkeywords: [python, 爬虫, requests, beautifulsoup]\nversion: 1.0.0\n---\n\n# Python爬虫技巧\n\n## 1. 使用requests发送请求\n\n```python\nimport requests\nresponse = requests.get('https://example.com')\nprint(response.text)\n```\n\n## 2. 使用BeautifulSoup解析HTML\n\n```python\nfrom bs4 import BeautifulSoup\nsoup = BeautifulSoup(response.text, 'html.parser')\ntitles = soup.find_all('h1')\n```"
    },
    {
      "name": "示例代码.py",
      "content": "import requests\nfrom bs4 import BeautifulSoup\n\nurl = 'https://example.com'\nresponse = requests.get(url)\nsoup = BeautifulSoup(response.text, 'html.parser')\nprint(soup.title.string)"
    },
    {
      "name": "requirements.txt",
      "content": "requests==2.28.0\nbeautifulsoup4==4.11.0"
    }
  ]
}
```

**重要注意事项**：

1. **必须包含SKILL.md文件**：SKILL.md是技能的核心文档，必须存在
2. **使用YAML frontmatter**：在SKILL.md开头添加元信息
   ```yaml
   ---
   description: 技能简短描述
   keywords: [关键词1, 关键词2, 关键词3]
   version: 1.0.0
   ---
   ```
3. **技能名称格式化**：技能名称会自动格式化为目录名
4. **自动更新DESCRIPTION.md**：个人技能包的DESCRIPTION.md会自动添加新技能描述

**返回结构**：
```json
{
  "skillName": "Python爬虫技巧",
  "skillDirId": 105,
  "filesCreated": 3,
  "fileIds": [106, 107, 108]
}
```

### 2.3 skill_update - 更新个人技能

**用途**：更新个人技能的文件（可以更新现有文件或添加新文件）

**参数**：
- `skillName`: 要更新的技能名称
- `files`: 要更新或添加的文件列表

**调用示例**：
```json
{
  "tool": "skill_update",
  "skillName": "Python爬虫技巧",
  "files": [
    {
      "name": "SKILL.md",
      "content": "---\ndescription: Python爬虫常用技巧汇总（已更新）\nkeywords: [python, 爬虫, requests, beautifulsoup, selenium]\nversion: 1.1.0\n---\n\n# Python爬虫技巧\n\n## 1. 使用requests发送请求\n\n（原有内容）\n\n## 3. 使用Selenium处理动态网页（新增）\n\n```python\nfrom selenium import webdriver\ndriver = webdriver.Chrome()\ndriver.get('https://example.com')\n```"
    },
    {
      "name": "selenium示例.py",
      "content": "from selenium import webdriver\n\ndriver = webdriver.Chrome()\ndriver.get('https://example.com')\nprint(driver.title)\ndriver.quit()"
    }
  ]
}
```

**更新逻辑**：
- **现有文件**：如果文件已存在（通过文件名匹配），则更新其内容
- **新文件**：如果文件不存在，则创建新文件
- **权限检查**：只能更新自己的个人技能，不能更新其他用户的技能

**返回结构**：
```json
{
  "skillName": "Python爬虫技巧",
  "skillDirId": 105,
  "updatedFiles": ["SKILL.md"],
  "createdFiles": ["selenium示例.py"],
  "totalChanged": 2
}
```

### 2.4 更新技能的推荐流程

```
1. 调用 skill_get_personal 查看现有技能树
   → 了解技能名称和文件列表

2. 准备要更新的文件内容
   → 可以只更新部分文件，无需全部提供

3. 调用 skill_update 执行更新
   → 系统自动识别是更新还是新建
```

---

## 三、权限规则

### 3.1 全局技能（global）
- **可见性**：所有人可见
- **权限**：只读，不可修改
- **授权方式**：通过 `ai_agent_skill` 表配置授权

### 3.2 个人技能（personal）
- **可见性**：仅所有者可见（按业务系统+用户隔离）
- **权限**：所有者可读写
- **授权方式**：自动授权，无需在 `ai_agent_skill` 表中配置
- **隔离机制**：
  - 按 `business_system` 隔离不同业务系统
  - 按 `owner_user` 隔离不同用户
  - 同名用户在不同业务系统中互不影响

### 3.3 个人技能包结构
```
Personal/
└── admin的个人技能/              ← 个人技能包（每个用户一个）
    ├── DESCRIPTION.md          ← 技能包描述（自动维护）
    ├── Python爬虫技巧/          ← 技能目录1
    │   ├── SKILL.md           ← 必须文件
    │   ├── 示例代码.py
    │   └── requirements.txt
    └── Java并发编程/            ← 技能目录2
        ├── SKILL.md
        └── 示例代码.java
```

---

## 四、最佳实践

### 4.1 创建技能时

✅ **推荐做法**：
- 使用YAML frontmatter标注元信息（description、keywords、version）
- 一个技能一个目录，包含SKILL.md + 相关示例文件
- 使用Markdown格式编写SKILL.md，便于阅读和展示
- 在SKILL.md中包含清晰的标题、步骤和代码示例
- 为示例代码添加注释说明

❌ **避免做法**：
- 不要省略SKILL.md文件
- 不要使用过于复杂或过长的技能名称
- 不要在一个技能中混合多个不相关的主题

### 4.2 更新技能时

✅ **推荐做法**：
- 更新前先调用 `skill_get_personal` 查看现有结构
- 更新SKILL.md时同步更新version版本号
- 添加新文件时在SKILL.md中说明用途
- 保持文件组织清晰，相关文件放在一起

❌ **避免做法**：
- 不要盲目更新，先了解现有内容
- 不要删除已有文件（只能添加和更新）
- 不要在更新时改变技能的核心主题

### 4.3 技能命名

✅ **推荐格式**：
- `Python数据分析技巧`
- `MySQL性能优化指南`
- `React Hooks使用方法`

❌ **避免格式**：
- `我的技能1`（不够描述性）
- `Python+Java+Go全栈开发指南`（范围过大）
- `test123`（无意义名称）

### 4.4 SKILL.md编写示例

```markdown
---
description: Python数据分析常用技巧和工具使用指南
keywords: [python, pandas, numpy, matplotlib, 数据分析]
version: 1.0.0
---

# Python数据分析技巧

## 概述
本技能介绍Python数据分析的常用技巧，包括pandas数据处理、numpy数值计算和matplotlib可视化。

## 1. Pandas数据处理

### 1.1 读取CSV文件
```python
import pandas as pd
df = pd.read_csv('data.csv')
print(df.head())
```

### 1.2 数据清洗
```python
# 删除缺失值
df_cleaned = df.dropna()

# 填充缺失值
df_filled = df.fillna(0)
```

## 2. Numpy数值计算

### 2.1 数组创建
```python
import numpy as np
arr = np.array([1, 2, 3, 4, 5])
print(arr.mean())  # 平均值
```

## 3. Matplotlib可视化

### 3.1 折线图
```python
import matplotlib.pyplot as plt
plt.plot([1, 2, 3, 4], [1, 4, 9, 16])
plt.xlabel('X轴')
plt.ylabel('Y轴')
plt.title('示例折线图')
plt.show()
```

## 参考资料
- Pandas官方文档: https://pandas.pydata.org/
- Numpy官方文档: https://numpy.org/
- Matplotlib官方文档: https://matplotlib.org/
```

---

## 五、常见问题

### Q1: 创建技能时忘记添加SKILL.md怎么办？
A: 系统会返回错误 "必须包含 SKILL.md 文件"。请在files数组中添加SKILL.md文件。

### Q2: 可以删除个人技能吗？
A: 当前版本不支持通过工具删除技能。需要联系管理员或通过后台管理界面删除。

### Q3: 更新技能时会覆盖原有内容吗？
A: 只会覆盖你指定更新的文件。未包含在更新请求中的文件保持不变。

### Q4: 可以创建多少个个人技能？
A: 没有数量限制，但建议保持技能数量合理，便于管理和查找。

### Q5: 个人技能可以分享给其他用户吗？
A: 当前版本个人技能仅所有者可见。如需分享，可以将技能内容提交给管理员，转为全局技能。

### Q6: YAML frontmatter是必须的吗？
A: SKILL.md必须存在，但YAML frontmatter是可选的。建议添加，以便系统更好地索引和展示技能。

---

## 六、工具快速参考

| 工具名称 | 用途 | 必需参数 | 返回内容 |
|---------|------|---------|---------|
| `skills_list` | 列出所有技能包 | 无 | 全局+个人技能包列表 |
| `skill_tree` | 查看技能包目录 | `skillId` | 文件列表+SKILL.md元信息 |
| `skill_read` | 读取文件内容 | `fileId` | 文件完整内容 |
| `skill_get_personal` | 查看个人技能树 | 无 | 个人技能列表 |
| `skill_create` | 创建个人技能 | `skillName`, `files` | 创建结果 |
| `skill_update` | 更新个人技能 | `skillName`, `files` | 更新结果 |

---

## 七、总结

Skill工具系统采用渐进式披露（Pull Model）设计，避免一次性加载过多内容：

1. **查看技能**：skills_list → skill_tree → skill_read（三步走）
2. **创建技能**：准备文件树 → skill_create（一步完成）
3. **更新技能**：skill_get_personal → skill_update（两步走）

遵循本指南的最佳实践，可以高效地管理和使用技能系统，提升AI Agent的能力。
