# Skill管理系统完整升级指南

## 📋 概述

本次升级为Skill管理系统带来了全面的功能增强和界面优化，包括专业的代码编辑器、Markdown可视化编辑、数据库结构优化等。

---

## 🎯 主要功能

### 1. 技能包结构支持

#### 目录结构
```
apple (技能包)
├── DESCRIPTION.md (技能包描述)
├── apple-notes (技能)
│   ├── SKILL.md (必需的技能入口文件)
│   ├── utils (文件夹)
│   │   └── helper.py
│   └── notes_helper.py
└── apple-reminders (技能)
    └── SKILL.md
```

#### SKILL.md 标准格式
```markdown
---
name: apple-notes
description: "Apple Notes via notectl: create, list, search notes."
version: 1.0.0
author: Hermes Agent
license: MIT
platforms: [macos]
metadata:
  hermes:
    tags: [Notes, Apple, macOS, productivity]
prerequisites:
  commands: [notectl]
---

# 技能内容...
```

---

## 🎨 界面优化

### 左侧树形结构

#### 样式特点
- ✅ **紧凑布局** - 节点高度28px，间距更合理
- ✅ **稳重配色** - 使用蚂蚁设计色系
- ✅ **图标区分** - 不同文件类型显示不同颜色图标
- ✅ **去除标签** - 移除了右侧的"包"和"技能"标签

#### 颜色方案
| 类型 | 颜色 | 说明 |
|------|------|------|
| 技能包 | #d46b08 (橙褐色) | 稳重的橙色 |
| 技能文件夹 | #389e0d (深绿色) | 专业的绿色 |
| 普通文件夹 | #d48806 (金黄色) | 传统文件夹色 |
| Markdown | #0958d9 (深蓝色) | 文档色 |
| JavaScript | #d4b106 (暗黄色) | JS特征色 |
| Python | #1765ad (深蓝色) | Python官方色 |
| Java | #0958d9 (深蓝色) | 企业级蓝色 |

---

## 📝 编辑器功能

### 1. Markdown编辑器 (Vditor)

#### 功能特性
- ✅ **三种模式**
  - 所见即所得 (WYSIWYG)
  - 即时渲染 (IR)
  - 源码模式 (SV)
- ✅ **@提及功能** - 输入@可快速引用
  - `@file[文件名](路径)` - 引用文件
  - `@plugin[插件名](插件名)` - 引用插件
- ✅ **丰富的工具栏**
  - 标题、加粗、斜体、删除线
  - 引用、列表、任务列表
  - 代码块、表格、链接
  - 撤销/重做、全屏等

### 2. 代码编辑器 (Monaco Editor)

#### 支持的语言
- JavaScript/TypeScript (.js, .jsx, .ts, .tsx)
- Python (.py)
- Java (.java)
- C/C++ (.c, .cpp, .h, .hpp)
- Go (.go)
- Rust (.rs)
- PHP (.php)
- HTML/CSS (.html, .css, .scss, .sass)
- JSON/XML/YAML (.json, .xml, .yaml, .yml)
- Shell脚本 (.sh, .bat, .ps1)

#### 功能特性
- ✅ 智能代码补全
- ✅ 语法高亮
- ✅ 错误检测
- ✅ 代码格式化
- ✅ 多光标编辑
- ✅ 查找替换
- ✅ 小地图导航

---

## 🗄️ 数据库变更

### 新增字段

| 字段名 | 类型 | 说明 |
|--------|------|------|
| node_type | varchar(50) | 节点类型：skill_package, skill, file, directory |
| file_extension | varchar(20) | 文件扩展名（如.md, .js, .py）|
| skill_metadata | json | 技能元数据（从SKILL.md解析）|
| mime_type | varchar(100) | 文件MIME类型 |

### 新增索引
```sql
CREATE INDEX idx_node_type ON skill_node(node_type);
CREATE INDEX idx_file_extension ON skill_node(file_extension);
```

### 新增视图
```sql
CREATE VIEW v_skill_list AS
SELECT 
    s.id,
    s.name as skill_name,
    JSON_EXTRACT(s.skill_metadata, '$.description') as description,
    JSON_EXTRACT(s.skill_metadata, '$.version') as version,
    p.name as package_name
FROM skill_node s
LEFT JOIN skill_node p ON s.parent_id = p.id
WHERE s.node_type = 'skill' AND s.name = 'SKILL.md';
```

---

## 🖱️ 右键菜单逻辑

### 空白区域
- 新建技能包

### 技能包节点
- 新建技能
- 新建DESCRIPTION.md
- 重命名
- 删除

### 技能/文件夹节点
- 新建文件夹
- 新建文件
- 重命名
- 删除

### 文件节点
- 重命名
- 删除

---

## 📦 前端依赖

### 新增包
```json
{
  "@guolao/vue-monaco-editor": "1.5.2",
  "monaco-editor": "0.52.2",
  "vditor": "3.10.8",
  "highlight.js": "11.11.1"
}
```

### 安装命令
```bash
cd Cortex-Vue3
npm install
```

---

## 🚀 部署步骤

### 1. 数据库升级
```bash
# 执行SQL脚本
mysql -u root -p cortex-vue < sql/skill_node_upgrade.sql
```

### 2. 后端编译
```bash
# 清理旧文件
quick-clean.bat

# 重新编译
mvn clean compile

# 或在IDEA中
Build -> Rebuild Project
```

### 3. 前端安装依赖
```bash
cd Cortex-Vue3
npm install
```

### 4. 启动服务
```bash
# 后端
启动 CortexApplication

# 前端
npm run dev
```

---

## 🎯 使用指南

### 创建技能包
1. 在左侧树空白处右键
2. 选择"新建技能包"
3. 输入技能包名称（如：apple）

### 创建技能
1. 右键技能包节点
2. 选择"新建技能"
3. 输入技能名称（如：apple-notes）
4. 系统自动创建SKILL.md模板

### 编辑文件
1. 点击文件节点
2. 右侧自动打开对应编辑器
   - `.md` 文件 → Vditor Markdown编辑器
   - `.js/.py/.java` 等 → Monaco代码编辑器
   - 其他文件 → 文本编辑器

### 使用@引用
1. 在Markdown编辑器中输入 `@`
2. 选择 `@file` 或 `@plugin`
3. 或点击工具栏"@文件引用"/"@插件引用"按钮

---

## ⚠️ 注意事项

### 1. 浏览器要求
- 推荐使用 Chrome 90+、Edge 90+、Firefox 88+
- Monaco Editor 首次加载需要3-5秒

### 2. 性能建议
- 大文件（>1MB）编辑时注意内存使用
- 关闭不用的文件释放资源

### 3. SKILL.md规范
- 必须包含YAML前置元数据（用---包裹）
- name字段必填
- description字段必填

### 4. 文件命名
- 技能包名：只能包含字母、数字、下划线、连字符
- 技能名：只能包含字母、数字、下划线、连字符
- 文件名：避免特殊字符 `\ / : * ? " < > |`

---

## 🐛 问题排查

### Monaco Editor不显示
```bash
# 检查依赖是否安装
npm list @guolao/vue-monaco-editor monaco-editor

# 重新安装
npm install @guolao/vue-monaco-editor monaco-editor --save
```

### Vditor样式错乱
```bash
# 检查CSS是否正确导入
# 在 MarkdownEditorNew.vue 中应有：
import 'vditor/dist/index.css'
```

### 数据库字段不存在
```bash
# 确认是否执行了升级脚本
mysql -u root -p cortex-vue < sql/skill_node_upgrade.sql

# 检查字段
SHOW COLUMNS FROM skill_node;
```

### 后端编译错误
```bash
# 清理target目录
quick-clean.bat

# 重新编译
mvn clean compile -DskipTests
```

---

## 📚 技术栈

### 后端
- Spring Boot
- MyBatis
- MySQL 5.7+

### 前端
- Vue 3
- Element Plus
- Monaco Editor (代码编辑器)
- Vditor (Markdown编辑器)

---

## 🔄 版本历史

### v2.0.0 (2026-07-01)
- ✅ 完整的技能包结构支持
- ✅ Monaco代码编辑器集成
- ✅ Vditor Markdown编辑器集成
- ✅ @提及功能
- ✅ 数据库结构优化
- ✅ 界面样式优化
- ✅ 右键菜单增强

### v1.0.0 (2024-06-30)
- 基础的Skill管理功能

---

## 📞 技术支持

如有问题，请检查：
1. [Monaco Editor 文档](https://microsoft.github.io/monaco-editor/)
2. [Vditor 文档](https://b3log.org/vditor/)
3. 项目文档目录: `doc/`

---

## 📝 后续优化计划

- [ ] 添加代码片段(Snippets)支持
- [ ] 支持协同编辑
- [ ] 技能市场功能
- [ ] 技能版本管理
- [ ] 技能测试框架
- [ ] 导入/导出技能包
- [ ] 技能依赖管理
- [ ] AI辅助编写技能
