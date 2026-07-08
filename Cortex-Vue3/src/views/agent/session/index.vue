<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
      <el-form-item label="业务系统" prop="businessSystem">
        <el-input v-model="queryParams.businessSystem" placeholder="业务系统" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="用户名" prop="userLoginName">
        <el-input v-model="queryParams.userLoginName" placeholder="用户登录名" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="会话ID" prop="sessionId">
        <el-input v-model="queryParams.sessionId" placeholder="会话ID" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="状态" clearable>
          <el-option label="活跃" value="0" />
          <el-option label="已结束" value="1" />
        </el-select>
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

    <el-table v-loading="loading" :data="sessionList" @selection-change="handleSelectionChange" border stripe highlight-current-row style="width: 100%">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="会话ID" align="center" prop="sessionId" width="280" show-overflow-tooltip>
        <template #default="scope">
          <el-link type="primary" @click="handleDetail(scope.row)">{{ scope.row.sessionId }}</el-link>
        </template>
      </el-table-column>
      <el-table-column label="Agent" align="center" prop="agentName" width="120" />
      <el-table-column label="业务系统" align="center" prop="businessSystem" width="100" />
      <el-table-column label="用户" align="center" prop="userLoginName" width="100" />
      <el-table-column label="Token用量" align="center" prop="tokenUsage" width="100" />
      <el-table-column label="迭代次数" align="center" prop="iterationCount" width="90" />
      <el-table-column label="状态" align="center" prop="status" width="80">
        <template #default="scope">
          <el-tag :type="scope.row.status === '0' ? 'success' : 'info'">
            {{ scope.row.status === '0' ? '活跃' : '已结束' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="最后消息" align="center" prop="lastMessageTime" min-width="160" />
      <el-table-column label="操作" align="center" width="180">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleDetail(scope.row)">详情</el-button>
          <el-button link type="primary" icon="Document" @click="handleLogs(scope.row)">日志</el-button>
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog v-model="detailVisible" title="会话详情" width="70%" top="5vh">
      <div v-if="sessionDetail" class="session-detail">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="会话ID">{{ sessionDetail.sessionId }}</el-descriptions-item>
          <el-descriptions-item label="Agent">{{ sessionDetail.agentName }}</el-descriptions-item>
          <el-descriptions-item label="业务系统">{{ sessionDetail.businessSystem }}</el-descriptions-item>
          <el-descriptions-item label="用户">{{ sessionDetail.userLoginName }}</el-descriptions-item>
          <el-descriptions-item label="Token用量">{{ sessionDetail.tokenUsage }}</el-descriptions-item>
          <el-descriptions-item label="迭代次数">{{ sessionDetail.iterationCount }}</el-descriptions-item>
        </el-descriptions>
        <el-divider content-position="left">消息历史</el-divider>
        <div class="message-list">
          <div v-for="(msg, idx) in sessionMessages" :key="idx" :class="['msg-item', msg.role]">
            <el-tag size="small" :type="roleTagType(msg.role)">{{ roleLabel(msg.role) }}</el-tag>
            <div class="msg-content">{{ msg.content || '(无内容)' }}</div>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, getCurrentInstance, onMounted } from 'vue'
import { listSession, getSession, delSession } from '@/api/runtime/session'
import { getExeclogBySession } from '@/api/runtime/execlog'

const { proxy } = getCurrentInstance()

const loading = ref(true)
const showSearch = ref(true)
const multiple = ref(true)
const total = ref(0)
const sessionList = ref([])
const ids = ref([])
const detailVisible = ref(false)
const sessionDetail = ref(null)
const sessionMessages = ref([])

const queryParams = reactive({
  pageNum: 1, pageSize: 10,
  businessSystem: '', userLoginName: '', sessionId: '', status: ''
})

function getList() {
  loading.value = true
  listSession(queryParams).then(res => {
    sessionList.value = res.rows
    total.value = res.total
    loading.value = false
  })
}

function handleQuery() { queryParams.pageNum = 1; getList() }
function resetQuery() {
  queryParams.businessSystem = ''; queryParams.userLoginName = ''
  queryParams.sessionId = ''; queryParams.status = ''; handleQuery()
}

function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.sessionId)
  multiple.value = !selection.length
}

async function handleDetail(row) {
  try {
    const res = await getSession(row.sessionId)
    sessionDetail.value = res.data
    sessionMessages.value = res.data && res.data.messages ? JSON.parse(res.data.messages) : []
    detailVisible.value = true
  } catch (e) { proxy.$modal.msgError('加载详情失败') }
}

async function handleLogs(row) {
  try {
    const res = await getExeclogBySession(row.sessionId)
    sessionDetail.value = row
    sessionMessages.value = (res.data || []).map(log => ({
      role: log.eventType === 'llm_call' ? 'assistant' : 'tool',
      content: `[${log.iteration}] ${log.eventType}${log.toolName ? ' / ' + log.toolName : ''} | ${log.status === '0' ? '成功' : '失败'}${log.errorMessage ? ' / ' + log.errorMessage : ''} | ${log.durationMs || 0}ms | in:${log.tokenInput || 0} out:${log.tokenOutput || 0}`
    }))
    detailVisible.value = true
  } catch (e) { proxy.$modal.msgError('加载日志失败') }
}

function handleDelete(row) {
  const deleteIds = row.sessionId || ids.value
  proxy.$modal.confirm('确认删除选中的会话?').then(() => {
    if (Array.isArray(deleteIds)) return Promise.all(deleteIds.map(id => delSession(id)))
    return delSession(deleteIds)
  }).then(() => { getList(); proxy.$modal.msgSuccess('删除成功') }).catch(() => {})
}

function roleLabel(role) { return { user: '用户', assistant: 'AI', system: '系统', tool: '工具结果' }[role] || role }
function roleTagType(role) { return { user: 'primary', assistant: 'success', system: 'info', tool: 'warning' }[role] || '' }

onMounted(() => getList())
</script>

<style scoped>
.app-container {
  min-height: calc(100vh - 130px);
}

.message-list { max-height: 60vh; overflow-y: auto; }
.msg-item { margin-bottom: 12px; padding: 8px; border-radius: 4px; }
.msg-item.user { background: #ecf5ff; }
.msg-item.assistant { background: #f0f9eb; }
.msg-item.tool { background: #fdf6ec; }
.msg-content { margin-top: 4px; font-size: 13px; white-space: pre-wrap; word-break: break-all; }
</style>