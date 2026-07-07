# Agent 会话文档预览功能说明

## 功能概述

在 Agent 会话界面中，现已集成文档预览功能。当 Agent 输出包含文件下载链接时，用户可以直接在线预览，无需下载到本地。

## 支持的文件类型

### 1. PDF 文档
- **预览方式**: 浏览器内置 PDF 查看器
- **功能**: 支持页面导航、缩放等基本操作
- **扩展名**: `.pdf`

### 2. HTML 文档
- **预览方式**: iframe 渲染 + 源码查看
- **功能**: 
  - 预览模式：完整渲染 HTML 页面
  - 源码模式：查看 HTML 源代码（带语法高亮）
- **扩展名**: `.html`, `.htm`

### 3. Office 文档
- **预览方式**: Microsoft Office Online 在线预览服务
- **功能**: 完整的文档渲染，支持复杂格式
- **扩展名**: 
  - Word: `.doc`, `.docx`
  - Excel: `.xls`, `.xlsx`
  - PowerPoint: `.ppt`, `.pptx`
- **注意**: 需要文件 URL 为公网可访问，内网环境需要配置反向代理

### 4. WPS 文档
- **当前状态**: 暂不支持直接预览
- **处理方式**: 提示用户下载后使用 WPS 或 Office 打开
- **扩展名**: `.wps`, `.et`, `.dps`
- **未来支持**: 可通过格式转换服务实现在线预览

### 5. 图片文件
- **预览方式**: Element Plus 图片预览组件
- **功能**: 支持缩放、旋转等操作
- **扩展名**: `.jpg`, `.jpeg`, `.png`, `.gif`, `.webp`, `.bmp`, `.svg`

## 使用方式

### 在会话中使用

当 Agent 返回包含文件链接的消息时，会自动显示：

1. **下载链接**: 点击可直接下载文件
2. **预览按钮**: 点击可在弹窗中预览文件

示例：
```markdown
这是您需要的文件：[报告.pdf](/agent/api/file/download/123)
```

渲染后会显示为：
- `报告.pdf` (下载链接)
- `[预览]` (预览按钮)

### 预览操作

1. 点击预览按钮，弹出文档预览对话框
2. 对话框中展示文件内容
3. 可以使用工具栏进行操作（取决于文件类型）
4. 点击"下载"按钮可下载文件
5. 点击"关闭"按钮或按 ESC 键关闭预览

## 技术实现

### 前端组件

1. **FilePreviewDialog.vue**: 文档预览对话框组件
   - 根据文件类型选择不同的预览方式
   - 支持 PDF、HTML、Office、图片等格式

2. **ChatMessageItem.vue**: 聊天消息组件
   - 在 Markdown 渲染中自动识别文件链接
   - 为可预览文件添加预览按钮
   - 集成文档预览对话框

3. **DocumentPreview.vue**: 文档预览核心组件
   - 子组件: PDFViewer, HTMLViewer, OfficeViewer
   - 统一的预览界面和交互

### 后端支持

1. **AgentFileController.java**: 文件管理接口
   - `/agent/api/file/view/{fileId}`: 文件查看接口
   - `/agent/api/file/download/{fileId}`: 文件下载接口

2. **DocumentPreviewController.java**: 文档预览接口
   - `/preview/document/upload`: 文档上传
   - `/preview/document/list`: 文档列表
   - `/preview/document/{fileName}`: 文档删除
   - `/preview/document/download/{fileName}`: 文档下载

## 配置说明

### Office 文档预览配置

如果需要预览 Office 文档，需要确保：

1. **公网访问**: 文件 URL 必须是公网可访问的
2. **CORS 配置**: 服务器需要配置 CORS 允许 Office Online 访问

内网环境解决方案：
- 使用反向代理将内网文件暴露到公网
- 使用 KKFileView 等开源预览服务（需要单独部署）
- 使用 Office Web Apps Server（企业方案）

### KKFileView 集成（可选）

KKFileView 是一个开源的文件在线预览解决方案，支持更多格式：

1. 下载部署 KKFileView: https://gitee.com/kekingcn/file-online-preview
2. 修改 `OfficeViewer.vue` 中的 `kkfileviewBaseUrl`
3. 重启前端服务

## 已知限制

1. **WPS 格式**: 暂不支持直接预览，需要下载
2. **Office 预览**: 内网环境需要额外配置
3. **大文件**: 超大文件可能加载较慢
4. **浏览器兼容**: 部分功能依赖现代浏览器特性

## 未来优化方向

1. **格式支持**: 增加更多文档格式支持（Markdown、CAD 图纸等）
2. **预览增强**: 添加更多预览工具（批注、搜索、打印等）
3. **离线预览**: 支持本地文件预览
4. **缓存优化**: 减少重复加载
5. **WPS 转换**: 集成 WPS 格式转换服务

## 相关文件

### 前端
- `RuoYi-Vue3/src/views/agent/components/FilePreviewDialog.vue`
- `RuoYi-Vue3/src/views/agent/components/ChatMessageItem.vue`
- `RuoYi-Vue3/src/views/preview/index.vue`
- `RuoYi-Vue3/src/views/preview/components/DocumentPreview.vue`
- `RuoYi-Vue3/src/views/preview/components/PDFViewer.vue`
- `RuoYi-Vue3/src/views/preview/components/HTMLViewer.vue`
- `RuoYi-Vue3/src/views/preview/components/OfficeViewer.vue`

### 后端
- `ruoyi-admin/src/main/java/com/ruoyi/web/controller/agent/AgentFileController.java`
- `ruoyi-admin/src/main/java/com/ruoyi/web/controller/preview/DocumentPreviewController.java`

### API
- `RuoYi-Vue3/src/api/preview/document.js`

## 更新日志

### v1.0.0 (2026-07-07)
- ✅ 初始版本发布
- ✅ 支持 PDF、HTML、Office 文档预览
- ✅ 支持图片预览
- ✅ 集成到 Agent 会话界面
- ✅ 添加独立的文档预览页面
