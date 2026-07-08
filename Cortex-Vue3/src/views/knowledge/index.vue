<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
      <el-form-item label="知识库名称" prop="kbName">
        <el-input v-model="queryParams.kbName" placeholder="请输入知识库名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
          <el-option label="正常" value="0" />
          <el-option label="停用" value="1" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['knowledge:base:add']">新建知识库</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <!-- 知识库列表 -->
    <el-table v-loading="loading" :data="kbList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="知识库名称" prop="kbName" min-width="160" show-overflow-tooltip />
      <el-table-column label="编码" prop="kbCode" width="140" show-overflow-tooltip />
      <el-table-column label="嵌入模型" prop="embeddingModelName" width="160" show-overflow-tooltip />
      <el-table-column label="重排序模型" prop="rerankModelName" width="140" show-overflow-tooltip />
      <el-table-column label="文档数" prop="documentCount" width="80" align="center" />
      <el-table-column label="分块数" prop="chunkCount" width="80" align="center" />
      <el-table-column label="状态" prop="status" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === '0' ? 'success' : 'danger'" size="small">
            {{ row.status === '0' ? '正常' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" prop="createTime" width="160" />
      <el-table-column label="操作" width="280" align="center">
        <template #default="{ row }">
          <el-button link type="primary" icon="Document" @click="handleDocument(row)">文档</el-button>
          <el-button link type="primary" icon="RefreshRight" @click="handleRebuild(row)" v-hasPermi="['knowledge:base:edit']">重建</el-button>
          <el-button link type="primary" icon="Edit" @click="handleEdit(row)" v-hasPermi="['knowledge:base:edit']">修改</el-button>
          <el-button link type="danger" icon="Delete" @click="handleDelete(row)" v-hasPermi="['knowledge:base:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 新建/编辑对话框 -->
    <el-dialog :title="dialogTitle" v-model="dialogVisible" width="600px" :close-on-click-modal="false">
      <el-form ref="kbFormRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="知识库名称" prop="kbName">
          <el-input v-model="form.kbName" placeholder="请输入知识库名称" />
        </el-form-item>
        <el-form-item label="知识库编码" prop="kbCode">
          <el-input v-model="form.kbCode" placeholder="英文编码，如 finance-kb" :disabled="form.id != null" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="知识库描述" />
        </el-form-item>
        <el-form-item label="嵌入模型" prop="embeddingModelId">
          <el-select v-model="form.embeddingModelId" placeholder="选择嵌入模型" filterable style="width: 100%">
            <el-option v-for="m in embeddingModels" :key="m.modelId" :label="m.modelName" :value="m.modelId" />
          </el-select>
        </el-form-item>
        <el-form-item label="分块大小" prop="chunkSize">
          <el-input-number v-model="form.chunkSize" :min="100" :max="2000" :step="100" />
          <span class="form-tip">字符数</span>
        </el-form-item>
        <el-form-item label="分块重叠" prop="chunkOverlap">
          <el-input-number v-model="form.chunkOverlap" :min="0" :max="500" :step="10" />
          <span class="form-tip">字符数</span>
        </el-form-item>
        <el-form-item label="分块分隔符" prop="chunkSeparator">
          <el-input v-model="form.chunkSeparator" placeholder="如 \n\n 按段落分块，留空自动分块" />
        </el-form-item>
        <el-form-item label="提取文档图片" prop="extractImages">
          <el-switch v-model="form.extractImages" active-value="1" inactive-value="0" />
          <span class="form-tip">开启后入库时提取图片并用多模态模型生成描述</span>
        </el-form-item>
        <el-form-item label="生成图片描述" prop="imageDescEnabled">
          <el-switch v-model="form.imageDescEnabled" active-value="1" inactive-value="0" :disabled="form.extractImages !== '1'" />
          <span class="form-tip">用多模态模型生成图片文字描述，关闭则仅提取图片不生成描述</span>
        </el-form-item>
        <el-form-item label="检索返回数" prop="topK">
          <el-input-number v-model="form.topK" :min="1" :max="20" />
        </el-form-item>
        <el-form-item label="相似度阈值" prop="scoreThreshold">
          <el-input-number v-model="form.scoreThreshold" :min="0" :max="1" :step="0.05" :precision="2" />
        </el-form-item>
        <el-form-item label="重排序模型" prop="rerankModelId">
          <el-select v-model="form.rerankModelId" placeholder="不使用重排序" filterable clearable style="width: 100%">
            <el-option v-for="m in rerankModels" :key="m.modelId" :label="m.modelName" :value="m.modelId" />
          </el-select>
        </el-form-item>
        <el-form-item label="重排序返回数" prop="rerankTopN">
          <el-input-number v-model="form.rerankTopN" :min="1" :max="20" />
          <span class="form-tip">留空同检索返回数</span>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio value="0">正常</el-radio>
            <el-radio value="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取 消</el-button>
        <el-button type="primary" @click="submitForm" :loading="saving">确 定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, getCurrentInstance } from 'vue'
import { useRouter } from 'vue-router'
import { listKnowledgeBase, getKnowledgeBase, addKnowledgeBase, updateKnowledgeBase, deleteKnowledgeBase, rebuildIndex } from '@/api/knowledge/knowledge'
import { listModel } from '@/api/supplier/model'

const { proxy } = getCurrentInstance()
const router = useRouter()

const loading = ref(false)
const saving = ref(false)
const showSearch = ref(true)
const kbList = ref([])
const total = ref(0)
const ids = ref([])
const embeddingModels = ref([])
const rerankModels = ref([])
const dialogVisible = ref(false)
const dialogTitle = ref('')

const queryParams = reactive({ pageNum: 1, pageSize: 10, kbName: undefined, status: undefined })
const form = reactive({})
const rules = {
  kbName: [{ required: true, message: '请输入知识库名称', trigger: 'blur' }],
  kbCode: [{ required: true, message: '请输入知识库编码', trigger: 'blur' }],
  embeddingModelId: [{ required: true, message: '请选择嵌入模型', trigger: 'change' }]
}

function getList() {
  loading.value = true
  listKnowledgeBase(queryParams).then(res => {
    kbList.value = res.rows
    total.value = res.total
  }).finally(() => { loading.value = false })
}

function loadEmbeddingModels() {
  listModel({ modelType: 'embedding', status: '0', pageNum: 1, pageSize: 999 }).then(res => {
    embeddingModels.value = res.rows || []
  })
}

function loadRerankModels() {
  listModel({ modelType: 'rerank', status: '0', pageNum: 1, pageSize: 999 }).then(res => {
    rerankModels.value = res.rows || []
  })
}

function handleQuery() { queryParams.pageNum = 1; getList() }
function resetQuery() { proxy.resetForm('queryRef'); handleQuery() }
function handleSelectionChange(selection) { ids.value = selection.map(item => item.id) }

function handleAdd() {
  Object.assign(form, { id: null, kbName: '', kbCode: '', description: '', embeddingModelId: null, chunkSize: 500, chunkOverlap: 50, chunkSeparator: null, extractImages: '0', imageDescEnabled: '0', topK: 5, scoreThreshold: 0.5, rerankModelId: null, rerankTopN: null, status: '0' })
  dialogTitle.value = '新建知识库'
  dialogVisible.value = true
}

function handleEdit(row) {
  getKnowledgeBase(row.id).then(res => {
    Object.assign(form, res.data)
    dialogTitle.value = '修改知识库'
    dialogVisible.value = true
  })
}

function submitForm() {
  proxy.$refs.kbFormRef.validate(valid => {
    if (!valid) return
    saving.value = true
    const action = form.id ? updateKnowledgeBase(form) : addKnowledgeBase(form)
    action.then(() => {
      proxy.$modal.msgSuccess(form.id ? '修改成功' : '新建成功')
      dialogVisible.value = false
      getList()
    }).finally(() => { saving.value = false })
  })
}

function handleDelete(row) {
  proxy.$modal.confirm('删除知识库将同时删除所有文档和向量数据，确认删除?').then(() => {
    return deleteKnowledgeBase(row.id)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}

function handleRebuild(row) {
  if (!row.id) { proxy.$modal.msgError('知识库ID缺失'); return }
  proxy.$modal.confirm('重建索引将重新处理所有文档，可能需要较长时间，确认?').then(() => {
    loading.value = true
    rebuildIndex(row.id).then(() => {
      proxy.$modal.msgSuccess('索引重建完成')
      getList()
    }).finally(() => { loading.value = false })
  }).catch(() => {})
}

function handleDocument(row) {
  router.push({ path: '/knowledge/document', query: { kbId: row.id, kbName: row.kbName } })
}

onMounted(() => { getList(); loadEmbeddingModels(); loadRerankModels() })
</script>

<style scoped>
.form-tip { margin-left: 8px; color: #909399; font-size: 12px; }
</style>
