<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
      <el-form-item label="会话ID" prop="sessionId">
        <el-input v-model="queryParams.sessionId" placeholder="会话ID" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="事件类型" prop="eventType">
        <el-select v-model="queryParams.eventType" placeholder="事件类型" clearable>
          <el-option label="LLM调用" value="llm_call" />
          <el-option label="工具调用" value="tool_call" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="状态" clearable>
          <el-option label="成功" value="0" />
          <el-option label="失败" value="1" />
        </el-select>
      </el-form-item>
      <el-form-item label="业务系统" prop="businessSystem">
        <el-input v-model="queryParams.businessSystem" placeholder="业务系统" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="用户" prop="userLoginName">
        <el-input v-model="queryParams.userLoginName" placeholder="用户名" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete">删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="logList" @selection-change="handleSelectionChange"  border stripe highlight-current-row style="width: 100%">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="日志ID" align="center" prop="logId" width="80" />
      <el-table-column label="会话ID" align="center" prop="sessionId" width="240" show-overflow-tooltip />
      <el-table-column label="轮次" align="center" prop="turnId" width="120" show-overflow-tooltip />
      <el-table-column label="迭代" align="center" prop="iteration" width="60" />
      <el-table-column label="类型" align="center" prop="eventType" width="90">
        <template #default="scope">
          <el-tag :type="scope.row.eventType === 'llm_call' ? '' : 'warning'" size="small">
            {{ scope.row.eventType === 'llm_call' ? 'LLM' : '工具' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="插件/工具" align="center" width="140">
        <template #default="scope">
          <span v-if="scope.row.pluginName">{{ scope.row.pluginName }}</span>
          <span v-if="scope.row.toolName"> / {{ scope.row.toolName }}</span>
        </template>
      </el-table-column>
      <el-table-column label="耗时" align="center" prop="durationMs" width="80">
        <template #default="scope">{{ scope.row.durationMs ? scope.row.durationMs + 'ms' : '-' }}</template>
      </el-table-column>
      <el-table-column label="Token(in/out)" align="center" width="120">
        <template #default="scope">{{ scope.row.tokenInput || 0 }} / {{ scope.row.tokenOutput || 0 }}</template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status" width="70">
        <template #default="scope">
          <el-tag :type="scope.row.status === '0' ? 'success' : 'danger'" size="small">
            {{ scope.row.status === '0' ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="业务系统" align="center" prop="businessSystem" min-width="90" />
      <el-table-column label="用户" align="center" prop="userLoginName" width="90" />
      <el-table-column label="操作" align="center" width="100">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleDetail(scope.row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog v-model="detailVisible" title="日志详情" width="60%" top="5vh">
      <el-descriptions v-if="logDetail" :column="2" border>
        <el-descriptions-item label="日志ID">{{ logDetail.logId }}</el-descriptions-item>
        <el-descriptions-item label="会话ID">{{ logDetail.sessionId }}</el-descriptions-item>
        <el-descriptions-item label="轮次ID">{{ logDetail.turnId }}</el-descriptions-item>
        <el-descriptions-item label="迭代">{{ logDetail.iteration }}</el-descriptions-item>
        <el-descriptions-item label="事件类型">{{ logDetail.eventType }}</el-descriptions-item>
        <el-descriptions-item label="插件">{{ logDetail.pluginName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="工具">{{ logDetail.toolName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ logDetail.durationMs || 0 }}ms</el-descriptions-item>
        <el-descriptions-item label="Token输入">{{ logDetail.tokenInput || 0 }}</el-descriptions-item>
        <el-descriptions-item label="Token输出">{{ logDetail.tokenOutput || 0 }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ logDetail.status === '0' ? '成功' : '失败' }}</el-descriptions-item>
        <el-descriptions-item label="业务系统">{{ logDetail.businessSystem }}</el-descriptions-item>
        <el-descriptions-item label="用户">{{ logDetail.userLoginName }}</el-descriptions-item>
        <el-descriptions-item v-if="logDetail.errorMessage" label="错误信息" :span="2">{{ logDetail.errorMessage }}</el-descriptions-item>
      </el-descriptions>
      <el-divider v-if="logDetail && logDetail.inputParams" content-position="left">输入参数</el-divider>
      <pre v-if="logDetail && logDetail.inputParams" class="json-block">{{ formatJson(logDetail.inputParams) }}</pre>
      <el-divider v-if="logDetail && logDetail.outputResult" content-position="left">输出结果</el-divider>
      <pre v-if="logDetail && logDetail.outputResult" class="json-block">{{ logDetail.outputResult.substring(0, 5000) }}</pre>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, getCurrentInstance, onMounted } from 'vue'
import { listExeclog, getExeclog, delExeclog } from '@/api/runtime/execlog'

const { proxy } = getCurrentInstance()

const loading = ref(true)
const showSearch = ref(true)
const multiple = ref(true)
const total = ref(0)
const logList = ref([])
const ids = ref([])
const detailVisible = ref(false)
const logDetail = ref(null)

const queryParams = reactive({
  pageNum: 1, pageSize: 10,
  sessionId: '', eventType: '', status: '', businessSystem: '', userLoginName: ''
})

function getList() {
  loading.value = true
  listExeclog(queryParams).then(res => {
    logList.value = res.rows
    total.value = res.total
    loading.value = false
  })
}

function handleQuery() { queryParams.pageNum = 1; getList() }
function resetQuery() {
  queryParams.sessionId = ''; queryParams.eventType = ''
  queryParams.status = ''; queryParams.businessSystem = ''
  queryParams.userLoginName = ''; handleQuery()
}

function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.logId)
  multiple.value = !selection.length
}

async function handleDetail(row) {
  try {
    const res = await getExeclog(row.logId)
    logDetail.value = res.data
    detailVisible.value = true
  } catch (e) { proxy.$modal.msgError('加载详情失败') }
}

function handleDelete(row) {
  const deleteIds = row.logId ? [row.logId] : ids.value
  proxy.$modal.confirm('确认删除选中的日志?').then(() => {
    return Promise.all(deleteIds.map(id => delExeclog(id)))
  }).then(() => { getList(); proxy.$modal.msgSuccess('删除成功') }).catch(() => {})
}

function formatJson(str) {
  try { return JSON.stringify(JSON.parse(str), null, 2) } catch (e) { return str }
}

onMounted(() => getList())
</script>

<style scoped>
.app-container {
  min-height: calc(100vh - 130px);
}

.json-block { background: #f5f7fa; padding: 12px; border-radius: 4px; font-size: 12px; max-height: 300px; overflow-y: auto; white-space: pre-wrap; word-break: break-all; }
</style>