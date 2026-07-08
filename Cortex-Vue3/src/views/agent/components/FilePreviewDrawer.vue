<template>
  <el-drawer
    v-model="visible"
    :title="dialogTitle"
    direction="rtl"
    :size="drawerSize"
    destroy-on-close
    @close="handleClose"
    class="file-preview-drawer"
  >
    <div class="preview-drawer-content">
      <!-- PDF 预览 -->
      <div v-if="isPDF" class="pdf-container">
        <iframe 
          :src="fileUrl" 
          class="preview-iframe"
          frameborder="0"
        />
      </div>

      <!-- HTML 预览 -->
      <div v-else-if="isHTML" class="html-container">
        <div class="preview-toolbar">
          <el-radio-group v-model="viewMode" size="small">
            <el-radio-button value="preview">预览</el-radio-button>
            <el-radio-button value="source">源码</el-radio-button>
          </el-radio-group>
        </div>

        <div v-if="viewMode === 'preview'" class="html-preview">
          <iframe 
            v-if="htmlBlobUrl"
            :src="htmlBlobUrl" 
            class="preview-iframe"
            frameborder="0"
          />
          <div v-else class="loading-tip">
            <el-icon class="is-loading"><Loading /></el-icon>
            <span>加载中...</span>
          </div>
        </div>
        <div v-else class="html-source">
          <pre><code class="language-html" v-html="highlightedCode"></code></pre>
        </div>
      </div>

      <!-- Word 文档预览 (使用 vue-office) -->
      <div v-else-if="isWord" class="word-container">
        <VueOfficeDocx 
          :src="fileUrl"
          @rendered="onDocxRendered"
          @error="onDocxError"
        />
      </div>

      <!-- Excel 文档预览 (使用 vue-office) -->
      <div v-else-if="isExcel" class="excel-container">
        <VueOfficeExcel
          :src="fileUrl"
          @rendered="onExcelRendered"
          @error="onExcelError"
        />
      </div>

      <!-- PPT/WPS 文档提示下载 -->
      <div v-else-if="isDocumentFile" class="document-container">
        <el-result
          icon="info"
          :title="getDocumentTitle"
          sub-title="PPT 和 WPS 文档建议下载后使用本地软件打开查看"
        >
          <template #extra>
            <el-button type="primary" size="large" @click="downloadFile">
              <el-icon><Download /></el-icon>
              下载文件
            </el-button>
          </template>
        </el-result>
      </div>

      <!-- 图片预览 -->
      <div v-else-if="isImage" class="image-container">
        <el-image
          :src="fileUrl"
          fit="contain"
          :preview-src-list="[fileUrl]"
          :initial-index="0"
          style="width: 100%; height: 600px"
        />
      </div>

      <!-- 不支持的格式 -->
      <div v-else class="unsupported-container">
        <el-result
          icon="info"
          title="该格式暂不支持在线预览"
          :sub-title="`文件类型: ${fileExtension.toUpperCase()}`"
        >
          <template #extra>
            <el-button type="primary" @click="downloadFile">
              <el-icon><Download /></el-icon>
              下载文件
            </el-button>
          </template>
        </el-result>
      </div>
    </div>

    <template #footer>
      <div class="drawer-footer">
        <el-button @click="handleClose">关闭</el-button>
        <el-button type="primary" @click="downloadFile">
          <el-icon><Download /></el-icon>
          下载
        </el-button>
      </div>
    </template>
  </el-drawer>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import { Download, Loading } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import VueOfficeDocx from '@vue-office/docx'
import VueOfficeExcel from '@vue-office/excel'
import '@vue-office/docx/lib/index.css'
import '@vue-office/excel/lib/index.css'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  fileUrl: {
    type: String,
    required: true
  },
  fileName: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['update:modelValue', 'close'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const viewMode = ref('preview')
const htmlContent = ref('')
const highlightedCode = ref('')
const htmlBlobUrl = ref('')

// 响应式抽屉宽度
const drawerSize = computed(() => {
  if (window.innerWidth < 768) {
    return '100%' // 移动端全屏
  } else if (window.innerWidth < 1200) {
    return '80%' // 平板
  } else {
    return '60%' // 桌面端
  }
})

// 获取文件扩展名
const fileExtension = computed(() => {
  if (props.fileName) {
    const parts = props.fileName.split('.')
    return parts.length > 1 ? parts.pop().toLowerCase() : ''
  }
  
  // 从 URL 中提取扩展名
  try {
    const url = new URL(props.fileUrl, window.location.origin)
    const path = url.pathname
    const parts = path.split('.')
    return parts.length > 1 ? parts.pop().toLowerCase() : ''
  } catch {
    return ''
  }
})

// 判断文件类型
const isPDF = computed(() => fileExtension.value === 'pdf')
const isHTML = computed(() => ['html', 'htm'].includes(fileExtension.value))
const isWord = computed(() => ['doc', 'docx'].includes(fileExtension.value))
const isExcel = computed(() => ['xls', 'xlsx'].includes(fileExtension.value))
const isPPT = computed(() => ['ppt', 'pptx'].includes(fileExtension.value))
const isWPS = computed(() => ['wps', 'et', 'dps'].includes(fileExtension.value))
const isImage = computed(() => 
  ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'svg'].includes(fileExtension.value)
)

// 合并 PPT 和 WPS 为不支持预览的文档类型
const isDocumentFile = computed(() => isPPT.value || isWPS.value)

// 文档类型标题
const getDocumentTitle = computed(() => {
  if (isWPS.value) {
    return 'WPS 文档需要下载'
  }
  if (isPPT.value) {
    return 'PPT 文档需要下载'
  }
  return '文档需要下载'
})

// Word 渲染完成
const onDocxRendered = () => {
  console.log('Word 文档渲染完成')
}

// Word 渲染错误
const onDocxError = (error) => {
  console.error('Word 文档渲染失败:', error)
  ElMessage.error('Word 文档预览失败，请下载后查看')
}

// Excel 渲染完成
const onExcelRendered = () => {
  console.log('Excel 文档渲染完成')
}

// Excel 渲染错误
const onExcelError = (error) => {
  console.error('Excel 文档渲染失败:', error)
  ElMessage.error('Excel 文档预览失败，请下载后查看')
}

// 对话框标题
const dialogTitle = computed(() => {
  return props.fileName || '文件预览'
})

// 加载 HTML 源码
const loadHTMLSource = async () => {
  if (!isHTML.value) return
  
  try {
    const response = await fetch(props.fileUrl)
    const blob = await response.blob()
    const text = await blob.text()
    
    htmlContent.value = text
    
    // 创建 blob URL 用于 iframe 预览
    const htmlBlob = new Blob([text], { type: 'text/html' })
    htmlBlobUrl.value = URL.createObjectURL(htmlBlob)
    
    // 高亮代码
    await nextTick()
    highlightedCode.value = hljs.highlight(text, { language: 'html' }).value
  } catch (error) {
    console.error('加载HTML失败:', error)
    ElMessage.error('HTML 文件加载失败')
  }
}

// 下载文件
const downloadFile = () => {
  const link = document.createElement('a')
  link.href = props.fileUrl
  link.download = props.fileName || 'download'
  link.click()
}

// 关闭对话框
const handleClose = () => {
  // 清理 blob URL
  if (htmlBlobUrl.value) {
    URL.revokeObjectURL(htmlBlobUrl.value)
    htmlBlobUrl.value = ''
  }
  emit('close')
  emit('update:modelValue', false)
}

// 监听对话框打开
watch(() => props.modelValue, (newVal) => {
  if (newVal && isHTML.value) {
    // HTML 文件打开时立即加载
    loadHTMLSource()
  }
})

// 监听视图模式切换
watch(viewMode, (newMode) => {
  // 模式切换不需要重新加载，因为已经加载过了
})
</script>

<style scoped lang="scss">
.preview-drawer-content {
  height: calc(100vh - 120px);
  overflow: auto;

  .pdf-container,
  .word-container,
  .excel-container {
    width: 100%;
    height: calc(100vh - 180px);
    min-height: 500px;
  }

  .preview-iframe {
    width: 100%;
    height: 100%;
    border: 1px solid #dcdfe6;
    border-radius: 4px;
  }

  .html-container {
    width: 100%;
    display: flex;
    flex-direction: column;
    height: calc(100vh - 180px);
  }

  .preview-toolbar {
    flex-shrink: 0;
    margin-bottom: 10px;
    padding: 10px;
    background: #f5f7fa;
    border-radius: 4px;
  }

  .html-preview {
    flex: 1;
    overflow: hidden;
    
    .preview-iframe {
      width: 100%;
      height: 100%;
      border: 1px solid #dcdfe6;
      border-radius: 4px;
      background: white;
    }
    
    .loading-tip {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      height: 400px;
      color: #909399;
      
      .el-icon {
        font-size: 32px;
        margin-bottom: 10px;
      }
    }
  }

  .html-source {
    flex: 1;
    overflow: auto;
    background: #f6f8fa;
    padding: 16px;
    border: 1px solid #e1e4e8;
    border-radius: 6px;
    
    pre {
      margin: 0;
      
      code {
        font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
        font-size: 13px;
        line-height: 1.6;
        display: block;
      }
    }
  }

  .document-container,
  .unsupported-container {
    display: flex;
    justify-content: center;
    align-items: center;
    min-height: 400px;
  }

  .image-container {
    display: flex;
    justify-content: center;
    align-items: center;
    padding: 20px;
  }
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 10px 0;
}

/* 移动端适配 */
@media (max-width: 768px) {
  .preview-drawer-content {
    height: calc(100vh - 100px);
  }

  .pdf-container,
  .word-container,
  .excel-container {
    height: calc(100vh - 140px);
    min-height: 400px;
  }

  .html-container {
    height: calc(100vh - 140px);
  }

  .image-container {
    :deep(.el-image) {
      height: 400px !important;
    }
  }

  .preview-toolbar {
    padding: 8px;
  }
}

/* 抽屉样式优化 */
.file-preview-drawer {
  :deep(.el-drawer__header) {
    margin-bottom: 16px;
    padding-bottom: 16px;
    border-bottom: 1px solid #e8e8e8;
  }

  :deep(.el-drawer__body) {
    padding: 0 20px 20px 20px;
  }
}
</style>
