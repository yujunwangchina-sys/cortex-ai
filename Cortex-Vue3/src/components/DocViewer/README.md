# DocViewer 文档预览组件

一个轻量级的文档预览组件，支持 PDF、Word、Excel、PowerPoint 和 HTML 文件预览。

## 📦 安装依赖

```bash
npm install docx-preview xlsx
```

## 📥 下载 PDF.js

1. 从 [PDF.js GitHub Releases](https://github.com/mozilla/pdf.js/releases) 下载最新版本
2. 解压后，将 `build` 和 `web` 文件夹复制到 `public/pdfjs/` 目录

或者使用 CDN：
```bash
# 下载 PDF.js (推荐使用 v3.11.174 或更高版本)
cd public
mkdir pdfjs
cd pdfjs
wget https://github.com/mozilla/pdf.js/releases/download/v3.11.174/pdfjs-3.11.174-dist.zip
unzip pdfjs-3.11.174-dist.zip
```

## 🚀 使用方法

### 基本用法

```vue
<template>
  <DocViewer
    :fileUrl="fileUrl"
    :fileName="fileName"
  />
</template>

<script setup>
import DocViewer from '@/components/DocViewer'

const fileUrl = 'http://example.com/document.pdf'
const fileName = 'document.pdf'
</script>
```

### 在对话框中使用

```vue
<template>
  <el-dialog
    v-model="previewVisible"
    title="文件预览"
    width="80%"
    :destroy-on-close="true"
  >
    <div style="height: 600px;">
      <DocViewer
        :fileUrl="currentFile.url"
        :fileName="currentFile.name"
      />
    </div>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
import DocViewer from '@/components/DocViewer'

const previewVisible = ref(false)
const currentFile = ref({
  url: '',
  name: ''
})

const previewFile = (file) => {
  currentFile.value = file
  previewVisible.value = true
}
</script>
```

## 📋 支持的文件格式

| 格式 | 扩展名 | 预览方式 | 说明 |
|------|--------|----------|------|
| PDF | `.pdf` | PDF.js | 本地渲染，支持缩放、搜索 |
| Word | `.doc`, `.docx` | docx-preview | 客户端渲染，样式还原度高 |
| Excel | `.xls`, `.xlsx` | xlsx | 转换为 HTML 表格 |
| PowerPoint | `.ppt`, `.pptx` | Office Online Viewer | 使用微软在线服务 |
| HTML | `.html`, `.htm` | iframe | 沙箱模式预览 |

## ⚙️ Props

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| fileUrl | String | 是 | - | 文件的 URL 地址 |
| fileName | String | 否 | '' | 文件名（用于识别文件类型） |

## 🔧 高级配置

### 自定义 PDF.js 路径

如果 PDF.js 不在 `public/pdfjs/` 目录，可以修改组件中的路径：

```javascript
const pdfUrl = computed(() => {
  return `/your-custom-path/viewer.html?file=${encodeURIComponent(props.fileUrl)}`
})
```

### 自定义 Word 渲染选项

在 `loadDocx()` 方法中修改 `renderAsync` 的选项：

```javascript
await renderAsync(blob, docxContainer.value, null, {
  className: 'docx-wrapper',
  inWrapper: true,
  breakPages: true,  // 是否分页
  ignoreWidth: false,  // 是否忽略宽度
  ignoreHeight: false,  // 是否忽略高度
  // 更多选项...
})
```

## 📝 注意事项

### CORS 跨域问题

如果文件在不同域名下，需要服务器配置 CORS 头：

```
Access-Control-Allow-Origin: *
Access-Control-Allow-Methods: GET, HEAD
```

### PowerPoint 预览限制

PPT/PPTX 使用微软的 Office Online Viewer 服务：
- ✅ 优点：无需额外依赖，样式完美
- ⚠️ 缺点：需要文件 URL 可公网访问
- 🔒 私有文件：考虑使用 [PPTXjs](https://github.com/meshesha/PPTXjs) 等客户端库

### PDF.js 替代方案

如果不想下载 PDF.js，可以使用 CDN 或 npm 包：

```bash
npm install pdfjs-dist
```

```vue
<script setup>
import * as pdfjsLib from 'pdfjs-dist'

// 配置 worker
pdfjsLib.GlobalWorkerOptions.workerSrc = 
  'https://cdn.jsdelivr.net/npm/pdfjs-dist@3.11.174/build/pdf.worker.min.js'
</script>
```

## 🎨 样式定制

组件使用了 scoped 样式，如需自定义，可以使用 `:deep()` 选择器：

```vue
<style>
.doc-viewer :deep(.docx-wrapper) {
  font-family: '宋体', SimSun, serif;
  font-size: 14px;
}

.doc-viewer :deep(table td) {
  padding: 12px;
  background: #fafafa;
}
</style>
```

## 🐛 常见问题

### 1. PDF 无法显示

**原因**：PDF.js 文件未正确放置

**解决**：
- 检查 `public/pdfjs/web/viewer.html` 是否存在
- 确认控制台没有 404 错误

### 2. Word 样式不正确

**原因**：字体缺失或样式复杂

**解决**：
- 确保系统安装了常用字体
- 复杂样式可能需要调整 renderAsync 选项

### 3. Excel 表格太宽

**原因**：列数过多或单元格宽度过大

**解决**：
- 添加横向滚动条
- 使用 `zoom` CSS 属性缩放

```css
.sheet-content {
  overflow-x: auto;
  transform: scale(0.8);
  transform-origin: top left;
}
```

## 📚 参考资料

- [PDF.js](https://mozilla.github.io/pdf.js/)
- [docx-preview](https://github.com/VolodymyrBaydalka/docxjs)
- [SheetJS (xlsx)](https://sheetjs.com/)
- [Office Online Viewer](https://docs.microsoft.com/en-us/microsoft-365/cloud-storage-partner-program/online/)
