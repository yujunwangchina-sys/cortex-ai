# Skill管理编辑器升级说明

## 概述

本次升级为Skill管理系统集成了专业的编辑器组件，提供更好的编辑体验：

### 主要功能

1. **Markdown文件** - 使用 TOAST UI Editor
   - ✅ 可视化编辑(WYSIWYG)模式
   - ✅ Markdown源码模式
   - ✅ 实时预览
   - ✅ 支持代码块、表格、图片等
   - ✅ 自定义工具栏

2. **代码文件** - 使用 Monaco Editor (VS Code编辑器)
   - ✅ 语法高亮
   - ✅ 智能代码提示
   - ✅ 代码补全
   - ✅ 错误检测
   - ✅ 支持多种语言: JavaScript, TypeScript, Python, Java, C/C++, Go, Rust, PHP等

3. **界面优化**
   - ✅ 移除顶部"新建文件夹"和"新建文件"按钮
   - ✅ 显示文件扩展名
   - ✅ 文件类型标签(Markdown, Python, JavaScript等)
   - ✅ 文件图标颜色区分

## 安装步骤

### 1. 安装依赖

在 `RuoYi-Vue3` 目录下运行安装脚本:

```bash
# Windows
install-skill-editor-deps.bat

# 或手动安装
npm install monaco-editor@0.52.2 --save
npm install @monaco-editor/vue@1.0.0 --save
npm install @toast-ui/editor@3.2.2 --save
npm install @toast-ui/vue-editor@3.2.1 --save
npm install highlight.js@11.11.1 --save
```

### 2. 重启开发服务器

```bash
npm run dev
```

## 文件结构

### 新增组件

```
RuoYi-Vue3/src/views/skill/components/
├── CodeEditor.vue           # Monaco代码编辑器组件
├── MarkdownEditorNew.vue    # TOAST UI Markdown编辑器组件
├── SkillEditor.vue          # 主编辑器组件(已更新)
└── SkillTree.vue            # 文件树组件(已更新)
```

## 支持的文件类型

### Markdown文件 (.md)
- 可视化编辑模式
- Markdown源码模式
- 支持@file和@plugin引用

### 代码文件
- **JavaScript/TypeScript**: .js, .jsx, .ts, .tsx
- **Python**: .py
- **Java**: .java
- **C/C++**: .c, .cpp, .h, .hpp
- **C#**: .cs
- **Go**: .go
- **Rust**: .rs
- **PHP**: .php
- **Ruby**: .rb
- **Swift**: .swift
- **Kotlin**: .kt
- **Scala**: .scala
- **Web**: .html, .css, .scss, .sass, .less
- **数据**: .json, .xml, .yaml, .yml
- **脚本**: .sql, .sh, .bat, .ps1
- **其他**: .r, .m, .lua

### 普通文本文件
- 使用简单的文本编辑器

## 使用说明

### Markdown编辑

1. 点击Markdown文件，自动打开TOAST UI编辑器
2. 默认进入**可视化编辑模式**
3. 点击工具栏切换到**Markdown源码**模式
4. 支持插入文件引用(@file)和插件引用(@plugin)

### 代码编辑

1. 点击代码文件(.py, .js等)，自动打开Monaco编辑器
2. 编辑器会根据文件扩展名自动识别语言
3. 支持代码补全、语法检查、代码格式化
4. 快捷键与VS Code相同

### 主题切换

可以在`SkillEditor.vue`中修改Monaco Editor主题:

```javascript
const editorTheme = ref('vs-dark') // vs, vs-dark, hc-black
```

## 配置选项

### Monaco Editor配置

在 `CodeEditor.vue` 中可以调整编辑器选项:

```javascript
const editorOptions = computed(() => ({
  fontSize: 14,           // 字体大小
  tabSize: 2,            // Tab大小
  minimap: {             // 小地图
    enabled: true
  },
  wordWrap: 'on',        // 自动换行
  lineNumbers: 'on',     // 行号显示
  // ... 更多选项
}))
```

### TOAST UI Editor配置

在 `MarkdownEditorNew.vue` 中可以调整工具栏:

```javascript
toolbarItems: [
  ['heading', 'bold', 'italic', 'strike'],
  ['hr', 'quote'],
  ['ul', 'ol', 'task'],
  ['table', 'image', 'link'],
  ['code', 'codeblock']
]
```

## 依赖版本

| 包名 | 版本 | 用途 |
|------|------|------|
| monaco-editor | 0.52.2 | Monaco编辑器核心 |
| @monaco-editor/vue | 1.0.0 | Monaco的Vue3封装 |
| @toast-ui/editor | 3.2.2 | TOAST UI编辑器核心 |
| @toast-ui/vue-editor | 3.2.1 | TOAST UI的Vue3封装 |
| highlight.js | 11.11.1 | 代码高亮支持 |

## 注意事项

1. **Monaco Editor加载较大** - 首次加载可能需要几秒钟
2. **浏览器兼容性** - 建议使用Chrome, Edge, Firefox最新版
3. **内存占用** - 打开大文件时注意内存使用
4. **保存提醒** - 文件修改后会显示"未保存"标签

## 未来优化

- [ ] 添加代码片段(Snippets)支持
- [ ] 添加多光标编辑
- [ ] 添加查找替换功能
- [ ] 支持自定义主题
- [ ] 支持协同编辑
- [ ] 添加文件对比功能
- [ ] 支持图片上传到服务器

## 问题排查

### 编辑器不显示
1. 检查是否安装了所有依赖
2. 查看浏览器控制台是否有错误
3. 尝试清除缓存重新构建

### 代码提示不工作
1. 确保Monaco Editor正确加载
2. 检查文件语言类型是否正确识别

### 样式错乱
1. 确保正确导入CSS文件
2. 检查是否有样式冲突

## 技术支持

如有问题，请查看:
- [Monaco Editor文档](https://microsoft.github.io/monaco-editor/)
- [TOAST UI Editor文档](https://ui.toast.com/tui-editor)
