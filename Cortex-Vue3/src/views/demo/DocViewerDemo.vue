<template>
  <div class="doc-viewer-demo">
    <el-card>
      <template #header>
        <span>文档预览组件示例</span>
      </template>

      <!-- 文件列表 -->
      <el-table :data="fileList" style="width: 100%">
        <el-table-column prop="name" label="文件名" />
        <el-table-column prop="type" label="类型" width="100" />
        <el-table-column prop="size" label="大小" width="100" />
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button 
              type="primary" 
              size="small" 
              @click="previewFile(row)"
            >
              预览
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 预览对话框 -->
    <el-dialog
      v-model="previewVisible"
      :title="`预览: ${currentFile.name}`"
      width="90%"
      :destroy-on-close="true"
      :close-on-click-modal="false"
    >
      <div class="preview-container">
        <DocViewer
          v-if="currentFile.url"
          :fileUrl="currentFile.url"
          :fileName="currentFile.name"
        />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import DocViewer from '@/components/DocViewer'

const previewVisible = ref(false)
const currentFile = ref({
  url: '',
  name: ''
})

// 示例文件列表
const fileList = ref([
  {
    name: 'sample.pdf',
    type: 'PDF',
    size: '2.3 MB',
    url: 'http://example.com/files/sample.pdf'
  },
  {
    name: 'document.docx',
    type: 'Word',
    size: '1.5 MB',
    url: 'http://example.com/files/document.docx'
  },
  {
    name: 'spreadsheet.xlsx',
    type: 'Excel',
    size: '856 KB',
    url: 'http://example.com/files/spreadsheet.xlsx'
  },
  {
    name: 'presentation.pptx',
    type: 'PowerPoint',
    size: '3.2 MB',
    url: 'http://example.com/files/presentation.pptx'
  },
  {
    name: 'page.html',
    type: 'HTML',
    size: '45 KB',
    url: 'http://example.com/files/page.html'
  }
])

const previewFile = (file) => {
  currentFile.value = file
  previewVisible.value = true
}
</script>

<style scoped>
.doc-viewer-demo {
  padding: 20px;
}

.preview-container {
  height: 70vh;
  background: #f5f5f5;
}
</style>
