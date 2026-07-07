# Agent聊天界面Markdown渲染升级说明

## 升级内容

### 1. AI回复消息支持Markdown渲染
- AI助手的回复内容现在会自动渲染为Markdown格式
- 去掉了AI消息的气泡背景色，内容直接显示
- AI消息宽度改为占满容器（左右保留边距）

### 2. 消息样式优化
- **用户消息**：保持原有的蓝色气泡样式
- **AI消息**：透明背景，直接显示Markdown渲染内容
- **工具消息**：保持原有的黄色背景样式

### 3. Markdown功能支持
- ✅ 标题（H1-H6）
- ✅ 段落和换行
- ✅ 代码块和行内代码
- ✅ 列表（有序列表、无序列表）
- ✅ 引用块
- ✅ 表格
- ✅ 链接
- ✅ 图片
- ✅ 分隔线
- ✅ 代码高亮（使用highlight.js）

## 安装步骤

### 1. 安装依赖

在 `RuoYi-Vue3` 目录下执行：

```bash
npm install markdown-it@^14.0.0
```

注意：`highlight.js` 已经在项目中，无需再次安装。

### 2. 重启前端服务

```bash
npm run dev
```

## 修改的文件

### 1. `package.json`
添加了 `markdown-it` 依赖

### 2. `ChatMessageItem.vue`
- 引入 `markdown-it` 和 `highlight.js`
- 为AI消息添加Markdown渲染
- 调整AI消息样式（去掉背景色）
- 添加完整的Markdown样式表

### 3. `ChatMessageList.vue`
- 引入 `markdown-it` 和 `highlight.js`
- 流式消息也支持Markdown渲染
- 调整容器宽度样式

## 效果预览

### 之前：
```
┌─────────────────────────┐
│ [AI头像] AI 助手        │
│ ┌─────────────────────┐ │
│ │ 纯文本回复内容      │ │ ← 灰色背景气泡
│ │ **加粗** 显示为源码 │ │
│ └─────────────────────┘ │
└─────────────────────────┘
```

### 之后：
```
┌──────────────────────────────────────┐
│ [AI头像] AI 助手                     │
│                                      │
│ 渲染后的Markdown内容                 │ ← 无背景
│ • 列表项                             │
│ • **加粗文本** 正确渲染              │
│                                      │
│ ```javascript                        │ ← 代码块高亮
│ console.log('Hello')                 │
│ ```                                  │
└──────────────────────────────────────┘
```

## 代码高亮主题

如果需要自定义代码高亮主题，可以在 `main.js` 中引入不同的highlight.js主题：

```javascript
// 例如引入GitHub主题
import 'highlight.js/styles/github.css'
```

常用主题：
- `github.css` - GitHub风格
- `vs.css` - Visual Studio风格
- `atom-one-dark.css` - Atom暗色主题
- `monokai.css` - Monokai主题

## 注意事项

1. **性能考虑**：Markdown渲染会占用一些性能，但对于聊天场景的文本量来说影响很小
2. **安全性**：markdown-it默认启用了HTML渲染，如果担心XSS攻击，可以设置 `html: false`
3. **样式冲突**：如果全局样式与Markdown样式冲突，可能需要调整CSS优先级

## 测试建议

测试以下Markdown格式是否正确渲染：

```markdown
# 一级标题
## 二级标题

这是一段**加粗**和*斜体*文本。

- 列表项1
- 列表项2

1. 有序列表1
2. 有序列表2

`行内代码`

\`\`\`javascript
const greeting = "Hello World";
console.log(greeting);
\`\`\`

> 这是一个引用块

| 列1 | 列2 |
|-----|-----|
| 数据1 | 数据2 |

[链接文本](https://example.com)
```

## 回滚方案

如果需要回滚到纯文本显示，只需要：

1. 在 `ChatMessageItem.vue` 中将 `renderedMarkdown` 改回 `renderedContent`
2. 恢复 `.msg-row.assistant .msg-text` 的背景色样式
