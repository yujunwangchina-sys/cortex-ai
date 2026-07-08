<template>
  <div class="app-container">
    <div style="margin-bottom: 16px; display: flex; align-items: center; gap: 12px;">
      <el-button icon="ArrowLeft" @click="goBack">返回</el-button>
      <span style="font-size: 18px; font-weight: bold;">{{ kbName }} - 文档管理</span>
    </div>

    <!-- 上传区 -->
    <el-upload
      ref="uploadRef"
      :action="uploadUrl"
      :headers="uploadHeaders"
      :show-file-list="false"
      :on-success="handleUploadSuccess"
      :on-error="handleUploadError"
      :before-upload="beforeUpload"
      drag
      multiple
      style="margin-bottom: 16px;"
    >
      <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
      <div class="el-upload__text">拖拽文件到此处或<em>点击上传</em></div>
      <template #tip>
        <div class="el-upload__tip">支持 PDF / Word / Excel / PPT / TXT / Markdown，单文件不超过 50MB</div>
      </template>
    </el-upload>

    <!-- 文档列表 -->
    <el-table v-loading="loading" :data="docList">
      <el-table-column label="文件名" prop="fileName" min-width="150" show-overflow-tooltip />
      <el-table-column label="类型" prop="fileType" width="80" align="center" />
      <el-table-column label="大小" width="100" align="center">
        <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
      </el-table-column>
      <el-table-column label="分类" prop="docCategory" width="100" show-overflow-tooltip />
      <el-table-column label="标签" prop="docTags" width="120" show-overflow-tooltip />
      <el-table-column label="分块" prop="chunkCount" width="70" align="center" />
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="80" align="center">
        <template #default="{ row }">
          <el-button link type="primary" icon="View" @click="handleChunks(row)">分块</el-button>
          <el-button link type="primary" icon="Edit" @click="handleMetadata(row)">标注</el-button>
          <el-button link type="primary" icon="RefreshRight" @click="handleReprocess(row)">重处理</el-button>
          <el-button link type="danger" icon="Delete" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 元数据标注对话框 -->
    <el-dialog title="元数据标注" v-model="metaDialogVisible" width="600px">
      <el-form :model="metaForm" label-width="100px">
        <el-form-item label="文件名">
          <span>{{ metaForm.fileName }}</span>
        </el-form-item>
        <el-form-item label="文档分类">
          <el-input v-model="metaForm.docCategory" placeholder="如:财务、技术、管理" />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="metaForm.docTags" placeholder="逗号分隔，如:政策,2024,重要" />
        </el-form-item>
        <el-form-item label="来源">
          <el-input v-model="metaForm.docSource" placeholder="文档来源" />
        </el-form-item>
        <el-form-item label="作者">
          <el-input v-model="metaForm.docAuthor" placeholder="文档作者" />
        </el-form-item>
        <el-form-item label="生效日期">
          <el-date-picker v-model="metaForm.effectiveDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="metaDialogVisible = false">取 消</el-button>
        <el-button type="primary" @click="submitMetadata" :loading="saving">保 存</el-button>
      </template>
    </el-dialog>

    <!-- 分块查看对话框 -->
    <el-dialog title="文档分块" v-model="chunkDialogVisible" width="900px">
      <el-table :data="chunkList" max-height="500" border>
        <el-table-column label="#" prop="chunkIndex" width="60" align="center" />
        <el-table-column label="内容" min-width="500">
          <template #default="{ row }">
            <div style="color: #606266; line-height: 1.6; max-height: 200px; overflow-y: auto;">
              <template v-for="(seg, si) in parseChunkSegments(row.content, row.imagePath)" :key="si">
                <span v-if="seg.type === 'text'" style="white-space: pre-wrap;">{{ seg.value }}</span>
                <el-image
                  v-else
                  :src="seg.url"
                  :preview-src-list="[seg.url]"
                  fit="contain"
                  preview-teleported
                  style="max-width: 200px; max-height: 150px; border-radius: 4px; border: 1px solid #e4e7ed; vertical-align: middle; margin: 2px 0;"
                />
              </template>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="Token" prop="tokenCount" width="80" align="center" />
        <el-table-column label="Milvus ID" prop="milvusId" width="120" show-overflow-tooltip />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, getCurrentInstance } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { UploadFilled } from '@element-plus/icons-vue'
import { getToken } from '@/utils/auth'
import { listDocument, getDocumentChunks, updateDocument, deleteDocument, reprocessDocument } from '@/api/knowledge/knowledge'

const { proxy } = getCurrentInstance()
const route = useRoute()
const router = useRouter()

const kbId = route.query.kbId
const kbName = route.query.kbName || '知识库'
const baseUrl = import.meta.env.VITE_APP_BASE_API
const uploadUrl = import.meta.env.VITE_APP_BASE_API + '/knowledge/document/upload/' + kbId
const uploadHeaders = { Authorization: 'Bearer ' + getToken() }

const loading = ref(false)
const saving = ref(false)
const docList = ref([])
const total = ref(0)
const queryParams = reactive({ pageNum: 1, pageSize: 10, kbId: kbId })
const metaDialogVisible = ref(false)
const metaForm = reactive({})
const chunkDialogVisible = ref(false)
const chunkList = ref([])
let pollTimer = null

function getList() {
  loading.value = true
  listDocument(queryParams).then(res => {
    docList.value = res.rows
    total.value = res.total
    checkPolling()
  }).finally(() => { loading.value = false })
}

function checkPolling() {
  const hasProcessing = docList.value.some(d => d.status === '0' || d.status === '1')
  if (hasProcessing && !pollTimer) {
    pollTimer = setInterval(() => getList(), 3000)
  } else if (!hasProcessing && pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

function beforeUpload(file) {
  if (!kbId) { proxy.$modal.msgError('知识库ID缺失，请从知识库列表进入'); return false }
  const maxSize = 50 * 1024 * 1024
  if (file.size > maxSize) {
    proxy.$modal.msgError('文件大小不能超过50MB')
    return false
  }
  return true
}

function handleUploadSuccess() {
  proxy.$modal.msgSuccess('上传成功，正在处理')
  getList()
}

function handleUploadError() {
  proxy.$modal.msgError('上传失败')
}

function statusText(s) {
  return { '0': '待处理', '1': '处理中', '2': '已索引', '3': '失败' }[s] || s
}

function statusType(s) {
  return { '0': 'info', '1': 'warning', '2': 'success', '3': 'danger' }[s] || 'info'
}

function formatSize(bytes) {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + 'B'
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + 'KB'
  return (bytes / 1048576).toFixed(1) + 'MB'
}

function parseChunkSegments(content, imagePath) {
  if (!content) return [{ type: 'text', value: '' }]
  let displayContent = content.replace(/\n\[img\d+:desc\][^\n]*/g, '')
  if (!imagePath) {
    displayContent = displayContent.replace(/\[img\d+\]/g, '')
    return [{ type: 'text', value: displayContent }]
  }
  let paths = []
  try { paths = JSON.parse(imagePath) } catch (e) { paths = [imagePath] }
  const segments = []
  const regex = /\[img(\d+)\]/g
  let lastIndex = 0
  let match
  while ((match = regex.exec(displayContent)) !== null) {
    if (match.index > lastIndex) {
      segments.push({ type: 'text', value: displayContent.slice(lastIndex, match.index) })
    }
    const imgNum = parseInt(match[1])
    const pathIdx = imgNum - 1
    if (pathIdx >= 0 && pathIdx < paths.length) {
      segments.push({ type: 'image', value: paths[pathIdx], url: baseUrl + '/profile/' + paths[pathIdx] })
    }
    lastIndex = regex.lastIndex
  }
  if (lastIndex < displayContent.length) {
    segments.push({ type: 'text', value: displayContent.slice(lastIndex) })
  }
  return segments
}
function handleMetadata(row) {
  Object.assign(metaForm, row)
  metaDialogVisible.value = true
}

function submitMetadata() {
  saving.value = true
  updateDocument(metaForm).then(() => {
    proxy.$modal.msgSuccess('标注保存成功')
    metaDialogVisible.value = false
    getList()
  }).finally(() => { saving.value = false })
}

function handleChunks(row) {
  getDocumentChunks(row.id).then(res => {
    chunkList.value = res.data || []
    chunkDialogVisible.value = true
  })
}

function handleReprocess(row) {
  if (!row.id) { proxy.$modal.msgError('文档ID缺失'); return }
  reprocessDocument(row.id).then(() => {
    proxy.$modal.msgSuccess('已重新处理')
    getList()
  })
}

function handleDelete(row) {
  proxy.$modal.confirm('确认删除文档及其所有分块?').then(() => {
    return deleteDocument(row.id)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}

function goBack() { router.push('/knowledge/base') }

onMounted(() => { getList() })
onUnmounted(() => { if (pollTimer) clearInterval(pollTimer) })
</script>
