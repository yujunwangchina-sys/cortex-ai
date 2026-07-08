<template>
  <div class="app-container">
    <el-row :gutter="16">
      <!-- 左侧:测试用例 -->
      <el-col :span="10">
        <el-card shadow="never">
          <template #header>
            <div style="display: flex; justify-content: space-between; align-items: center;">
              <span>测试用例</span>
              <div>
                <el-button size="small" type="primary" icon="Plus" @click="handleAddCase">新增</el-button>
                <el-button size="small" type="success" icon="VideoPlay" @click="handleRunAll" :loading="runningAll">全部执行</el-button>
              </div>
            </div>
          </template>
          <el-select v-model="currentKbId" placeholder="选择知识库" filterable style="width: 100%; margin-bottom: 12px;" @change="loadCases">
            <el-option v-for="kb in kbList" :key="kb.id" :label="kb.kbName" :value="kb.id" />
          </el-select>
          <el-table :data="caseList" v-loading="loading" size="small" @row-click="handleCaseClick" highlight-current-row>
            <el-table-column label="名称" prop="testName" min-width="120" show-overflow-tooltip />
            <el-table-column label="问题" prop="query" min-width="150" show-overflow-tooltip />
            <el-table-column label="操作" width="120" align="center">
              <template #default="{ row }">
                <el-button link size="small" type="primary" @click.stop="handleRun(row)">执行</el-button>
                <el-button link size="small" type="danger" @click.stop="handleDelete(row)">删</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <!-- 右侧:执行结果 -->
      <el-col :span="14">
        <el-card shadow="never">
          <template #header><span>执行结果</span></template>
          <div v-if="currentResult">
            <el-row :gutter="16" style="margin-bottom: 16px;">
              <el-col :span="8">
                <el-statistic title="召回率" :value="currentResult.recallScore * 100" :precision="2" suffix="%" />
              </el-col>
              <el-col :span="8">
                <el-statistic title="准确率" :value="currentResult.precisionScore * 100" :precision="2" suffix="%" />
              </el-col>
              <el-col :span="8">
                <el-statistic title="平均相似度" :value="currentResult.avgScore" :precision="4" />
              </el-col>
            </el-row>
            <el-descriptions :column="2" border size="small" style="margin-bottom: 12px;">
              <el-descriptions-item label="查询">{{ currentResult.query }}</el-descriptions-item>
              <el-descriptions-item label="返回数">{{ currentResult.topKUsed }}</el-descriptions-item>
              <el-descriptions-item label="命中文档">{{ currentResult.hitDocIds || '无' }}</el-descriptions-item>
              <el-descriptions-item label="执行时间">{{ currentResult.runTime }}</el-descriptions-item>
            </el-descriptions>
            <div v-if="hitChunks.length > 0">
              <div style="font-weight: bold; margin-bottom: 8px;">命中分块:</div>
              <el-card v-for="(chunk, idx) in hitChunks" :key="idx" shadow="hover" style="margin-bottom: 8px;">
                <div style="display: flex; justify-content: space-between; margin-bottom: 4px;">
                  <el-tag size="small">[{{ idx + 1 }}] {{ chunk.document_name || '未知' }}</el-tag>
                  <el-tag size="small" type="success">{{ formatScore(chunk.score) }}</el-tag>
                </div>
                <div style="color: #606266; font-size: 13px; line-height: 1.6;">
                  <template v-for="(seg, si) in parseChunkSegments(chunk.content, chunk.image_path)" :key="si">
                    <span v-if="seg.type === 'text'" style="white-space: pre-wrap;">{{ seg.value }}</span>
                    <el-image
                      v-else
                      :src="seg.url"
                      :preview-src-list="[seg.url]"
                      fit="contain"
                      preview-teleported
                      style="max-width: 300px; max-height: 200px; border-radius: 4px; border: 1px solid #e4e7ed; vertical-align: middle; margin: 4px 0;"
                    />
                  </template>
                </div>
              </el-card>
            </div>
          </div>
          <el-empty v-else description="选择测试用例并执行" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 测试用例编辑对话框 -->
    <el-dialog :title="caseDialogTitle" v-model="caseDialogVisible" width="600px">
      <el-form :model="caseForm" label-width="100px">
        <el-form-item label="名称" required>
          <el-input v-model="caseForm.testName" placeholder="测试名称" />
        </el-form-item>
        <el-form-item label="查询问题" required>
          <el-input v-model="caseForm.query" type="textarea" :rows="2" placeholder="测试问题" />
        </el-form-item>
        <el-form-item label="期望文档ID">
          <el-input v-model="caseForm.expectedDocIds" placeholder="逗号分隔，如 1,2,3" />
        </el-form-item>
        <el-form-item label="期望关键词">
          <el-input v-model="caseForm.expectedKeywords" placeholder="逗号分隔" />
        </el-form-item>
        <el-form-item label="元数据过滤">
          <el-input v-model="caseForm.metadataFilter" placeholder='JSON，如 {"doc_category":"财务"}' />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="caseDialogVisible = false">取 消</el-button>
        <el-button type="primary" @click="submitCase" :loading="saving">保 存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, getCurrentInstance } from 'vue'
import { listTestCase, addTestCase, updateTestCase, deleteTestCase, runTest, runAllTests, availableKnowledgeBase } from '@/api/knowledge/knowledge'
import { ElMessageBox } from 'element-plus'

const { proxy } = getCurrentInstance()
const loading = ref(false)
const saving = ref(false)
const runningAll = ref(false)
const kbList = ref([])
const caseList = ref([])
const currentKbId = ref(null)
const currentResult = ref(null)
const caseDialogVisible = ref(false)
const caseDialogTitle = ref('')
const caseForm = reactive({})

const baseUrl = import.meta.env.VITE_APP_BASE_API
const hitChunks = computed(() => {
  if (!currentResult.value?.hitChunks) return []
  try { return JSON.parse(currentResult.value.hitChunks) } catch { return [] }
})

function loadCases() {
  if (!currentKbId.value) return
  loading.value = true
  listTestCase({ kbId: currentKbId.value, pageNum: 1, pageSize: 999 }).then(res => {
    caseList.value = res.rows || []
  }).finally(() => { loading.value = false })
}

function handleAddCase() {
  if (!currentKbId.value) { proxy.$modal.msgWarning('请先选择知识库'); return }
  Object.assign(caseForm, { id: null, kbId: currentKbId.value, testName: '', query: '', expectedDocIds: '', expectedKeywords: '', metadataFilter: '', status: '0' })
  caseDialogTitle.value = '新增测试用例'
  caseDialogVisible.value = true
}

function submitCase() {
  if (!caseForm.testName || !caseForm.query) { proxy.$modal.msgWarning('名称和问题不能为空'); return }
  saving.value = true
  const action = caseForm.id ? updateTestCase(caseForm) : addTestCase(caseForm)
  action.then(() => {
    proxy.$modal.msgSuccess('保存成功')
    caseDialogVisible.value = false
    loadCases()
  }).finally(() => { saving.value = false })
}

function handleRun(row) {
  if (!row.id) { proxy.$modal.msgError('测试用例ID缺失'); return }
  runTest(row.id).then(res => {
    currentResult.value = res.data
    proxy.$modal.msgSuccess('执行完成')
  })
}

function handleRunAll() {
  if (!currentKbId.value) return
  runningAll.value = true
  runAllTests(currentKbId.value).then(res => {
    const results = res.data || []
    if (results.length > 0) {
      currentResult.value = results[results.length - 1]
    }
    proxy.$modal.msgSuccess(`执行完成，共 ${results.length} 个用例`)
    loadCases()
  }).finally(() => { runningAll.value = false })
}

function handleDelete(row) {
  ElMessageBox.confirm('确认删除该测试用例?', '提示').then(() => {
    return deleteTestCase(row.id)
  }).then(() => {
    proxy.$modal.msgSuccess('删除成功')
    loadCases()
  }).catch(() => {})
}

function handleCaseClick(row) {
  if (row.id) {
    Object.assign(caseForm, row)
  }
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
function formatScore(score) {
  return score != null ? Number(score).toFixed(4) : '-'
}

onMounted(() => {
  availableKnowledgeBase().then(res => { kbList.value = res.data || [] })
})
</script>
