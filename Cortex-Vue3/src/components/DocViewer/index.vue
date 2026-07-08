<template>
  <div class="doc-viewer">
    <!-- PDF 预览 -->
    <div v-if="fileType === 'pdf'" class="pdf-viewer">
      <iframe 
        :src="pdfUrl" 
        frameborder="0"
        class="doc-iframe"
      ></iframe>
    </div>

    <!-- HTML 预览 -->
    <div v-else-if="fileType === 'html'" class="html-viewer">
      <iframe 
        :src="fileUrl" 
        frameborder="0"
        sandbox="allow-scripts allow-same-origin"
        class="doc-iframe"
      ></iframe>
    </div>

    <!-- Word 预览 -->
    <div v-else-if="['doc', 'docx'].includes(fileType)" class="word-viewer">
      <div ref="docxContainer" class="docx-container"></div>
    </div>

    <!-- Excel 预览 -->
    <div v-else-if="['xls', 'xlsx'].includes(fileType)" class="excel-viewer">
      <div ref="excelContainer" class="excel-container"></div>
    </div>

    <!-- PowerPoint 预览（使用 Office Online Viewer） -->
    <div v-else-if="['ppt', 'pptx'].includes(fileType)" class="ppt-viewer">
      <iframe 
        :src="officeViewerUrl" 
        frameborder="0"
        class="doc-iframe"
      ></iframe>
    </div>

    <!-- 不支持的格式 -->
    <div v-else class="unsupported">
      <el-empty description="不支持预览此文件格式">
        <el-button type="primary" @click="downloadFile">下载文件</el-button>
      </el-empty>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { renderAsync } from 'docx-preview'
import * as XLSX from 'xlsx'

const props = defineProps({
  fileUrl: {
    type: String,
    required: true
  },
  fileName: {
    type: String,
    default: ''
  }
})

const docxContainer = ref(null)
const excelContainer = ref(null)

// 获取文件类型
const fileType = computed(() => {
  const ext = props.fileName.split('.').pop()?.toLowerCase() || 
               props.fileUrl.split('.').pop()?.toLowerCase()
  return ext
})

// PDF.js viewer URL
const pdfUrl = computed(() => {
  // 使用 PDF.js 的 web viewer
  return `/pdfjs/web/viewer.html?file=${encodeURIComponent(props.fileUrl)}`
})

// Office Online Viewer URL (Microsoft 提供的免费预览服务)
const officeViewerUrl = computed(() => {
  return `https://view.officeapps.live.com/op/embed.aspx?src=${encodeURIComponent(props.fileUrl)}`
})

// 加载 Word 文档
const loadDocx = async () => {
  try {
    const response = await fetch(props.fileUrl)
    const blob = await response.blob()
    
    if (docxContainer.value) {
      await renderAsync(blob, docxContainer.value, null, {
        className: 'docx-wrapper',
        inWrapper: true,
        ignoreWidth: false,
        ignoreHeight: false,
        ignoreFonts: false,
        breakPages: true,
        ignoreLastRenderedPageBreak: true,
        experimental: true,
        trimXmlDeclaration: true
      })
    }
  } catch (error) {
    console.error('加载 Word 文档失败:', error)
  }
}

// 加载 Excel 文档
const loadExcel = async () => {
  try {
    const response = await fetch(props.fileUrl)
    const arrayBuffer = await response.arrayBuffer()
    const workbook = XLSX.read(arrayBuffer, { type: 'array' })
    
    if (excelContainer.value) {
      let html = '<div class="excel-sheets">'
      
      workbook.SheetNames.forEach((sheetName, index) => {
        const worksheet = workbook.Sheets[sheetName]
        const htmlTable = XLSX.utils.sheet_to_html(worksheet, {
          header: '',
          footer: ''
        })
        
        html += `
          <div class="excel-sheet" ${index > 0 ? 'style="display:none"' : ''}>
            <div class="sheet-header">
              <h3>${sheetName}</h3>
              <div class="sheet-tabs">
                ${workbook.SheetNames.map((name, i) => 
                  `<button class="sheet-tab ${i === index ? 'active' : ''}" data-index="${i}">${name}</button>`
                ).join('')}
              </div>
            </div>
            <div class="sheet-content">${htmlTable}</div>
          </div>
        `
      })
      
      html += '</div>'
      excelContainer.value.innerHTML = html
      
      // 添加 sheet 切换功能
      excelContainer.value.querySelectorAll('.sheet-tab').forEach(tab => {
        tab.addEventListener('click', (e) => {
          const index = e.target.dataset.index
          excelContainer.value.querySelectorAll('.excel-sheet').forEach((sheet, i) => {
            sheet.style.display = i == index ? 'block' : 'none'
          })
          excelContainer.value.querySelectorAll('.sheet-tab').forEach(t => {
            t.classList.remove('active')
          })
          e.target.classList.add('active')
        })
      })
    }
  } catch (error) {
    console.error('加载 Excel 文档失败:', error)
  }
}

// 下载文件
const downloadFile = () => {
  window.open(props.fileUrl, '_blank')
}

// 监听文件变化，重新加载
watch(() => props.fileUrl, () => {
  loadDocument()
})

// 加载文档
const loadDocument = () => {
  if (['doc', 'docx'].includes(fileType.value)) {
    loadDocx()
  } else if (['xls', 'xlsx'].includes(fileType.value)) {
    loadExcel()
  }
}

onMounted(() => {
  loadDocument()
})
</script>

<style scoped>
.doc-viewer {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #f5f5f5;
}

.doc-iframe {
  width: 100%;
  height: 100%;
  border: none;
}

/* Word 预览样式 */
.word-viewer {
  flex: 1;
  overflow: auto;
  padding: 20px;
  background: #fff;
}

.docx-container {
  max-width: 800px;
  margin: 0 auto;
  background: white;
  padding: 40px;
  box-shadow: 0 0 10px rgba(0,0,0,0.1);
}

.docx-container :deep(.docx-wrapper) {
  font-family: 'Calibri', '微软雅黑', sans-serif;
}

/* Excel 预览样式 */
.excel-viewer {
  flex: 1;
  overflow: auto;
  background: #fff;
}

.excel-container {
  padding: 20px;
}

.sheet-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 10px;
  border-bottom: 2px solid #409eff;
}

.sheet-header h3 {
  margin: 0;
  color: #303133;
}

.sheet-tabs {
  display: flex;
  gap: 5px;
}

.sheet-tab {
  padding: 6px 12px;
  border: 1px solid #dcdfe6;
  background: #fff;
  cursor: pointer;
  border-radius: 4px;
  transition: all 0.3s;
}

.sheet-tab:hover {
  background: #f5f7fa;
  border-color: #409eff;
}

.sheet-tab.active {
  background: #409eff;
  color: #fff;
  border-color: #409eff;
}

.sheet-content {
  overflow: auto;
}

.sheet-content :deep(table) {
  border-collapse: collapse;
  width: 100%;
  background: white;
}

.sheet-content :deep(td),
.sheet-content :deep(th) {
  border: 1px solid #dcdfe6;
  padding: 8px;
  text-align: left;
  min-width: 80px;
}

.sheet-content :deep(th) {
  background: #f5f7fa;
  font-weight: bold;
}

.sheet-content :deep(tr:hover) {
  background: #f5f7fa;
}

/* 不支持的格式 */
.unsupported {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
}

/* 响应式 */
@media (max-width: 768px) {
  .docx-container {
    padding: 20px;
  }
  
  .sheet-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }
  
  .sheet-tabs {
    width: 100%;
    overflow-x: auto;
  }
}
</style>
