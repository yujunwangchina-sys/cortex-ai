<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
      <el-form-item label="授权状态" prop="grantStatus">
        <el-select v-model="queryParams.grantStatus" placeholder="授权状态" clearable>
          <el-option label="已授权" value="0" />
          <el-option label="已拒绝" value="1" />
          <el-option label="待决定" value="2" />
        </el-select>
      </el-form-item>
      <el-form-item label="业务系统" prop="businessSystem">
        <el-input v-model="queryParams.businessSystem" placeholder="业务系统" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="会话ID" prop="sessionId">
        <el-input v-model="queryParams.sessionId" placeholder="会话ID" clearable @keyup.enter="handleQuery" />
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

    <el-table v-loading="loading" :data="approvalList" @selection-change="handleSelectionChange" border stripe highlight-current-row style="width: 100%">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="授权ID" align="center" prop="grantId" width="80" />
      <el-table-column label="会话ID" align="center" prop="sessionId" width="240" show-overflow-tooltip />
      <el-table-column label="插件名称" align="center" prop="pluginName" width="140" />
      <el-table-column label="授权状态" align="center" prop="grantStatus" width="100">
        <template #default="scope">
          <el-tag :type="statusTagType(scope.row.grantStatus)">{{ statusLabel(scope.row.grantStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="授权人" align="center" prop="grantedBy" width="100" />
      <el-table-column label="业务系统" align="center" prop="businessSystem" min-width="100" />
      <el-table-column label="拒绝理由" align="center" prop="rejectReason" width="160" show-overflow-tooltip />
      <el-table-column label="创建时间" align="center" prop="createTime" width="160" />
      <el-table-column label="操作" align="center" width="200">
        <template #default="scope">
          <el-button v-if="scope.row.grantStatus === '2'" link type="success" icon="Check" @click="handleApprove(scope.row)">批准</el-button>
          <el-button v-if="scope.row.grantStatus === '2'" link type="danger" icon="Close" @click="handleReject(scope.row)">拒绝</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog v-model="rejectVisible" title="拒绝原因" width="400px">
      <el-input v-model="rejectReason" type="textarea" :rows="3" placeholder="请输入拒绝理由" />
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" @click="confirmReject">确认拒绝</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, getCurrentInstance, onMounted } from 'vue'
import { listApproval, approvePlugin, rejectPlugin, delApproval } from '@/api/runtime/approval'

const { proxy } = getCurrentInstance()

const loading = ref(true)
const showSearch = ref(true)
const multiple = ref(true)
const total = ref(0)
const approvalList = ref([])
const ids = ref([])
const rejectVisible = ref(false)
const rejectReason = ref('')
const currentGrantId = ref(null)

const queryParams = reactive({
  pageNum: 1, pageSize: 10,
  grantStatus: '', businessSystem: '', sessionId: ''
})

function getList() {
  loading.value = true
  listApproval(queryParams).then(res => {
    approvalList.value = res.rows
    total.value = res.total
    loading.value = false
  })
}

function handleQuery() { queryParams.pageNum = 1; getList() }
function resetQuery() {
  queryParams.grantStatus = ''; queryParams.businessSystem = ''
  queryParams.sessionId = ''; handleQuery()
}

function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.grantId)
  multiple.value = !selection.length
}

function statusLabel(s) { return { '0': '已授权', '1': '已拒绝', '2': '待决定' }[s] || s }
function statusTagType(s) { return { '0': 'success', '1': 'danger', '2': 'warning' }[s] || '' }

async function handleApprove(row) {
  await approvePlugin(row.grantId)
  proxy.$modal.msgSuccess('已批准')
  getList()
}

function handleReject(row) {
  currentGrantId.value = row.grantId
  rejectReason.value = ''
  rejectVisible.value = true
}

async function confirmReject() {
  await rejectPlugin(currentGrantId.value, rejectReason.value)
  rejectVisible.value = false
  proxy.$modal.msgSuccess('已拒绝')
  getList()
}

function handleDelete(row) {
  const deleteIds = row.grantId ? [row.grantId] : ids.value
  proxy.$modal.confirm('确认删除选中的审批记录?').then(() => {
    return Promise.all(deleteIds.map(id => delApproval(id)))
  }).then(() => { getList(); proxy.$modal.msgSuccess('删除成功') }).catch(() => {})
}

onMounted(() => getList())
</script>

<style scoped>
.app-container {
  min-height: calc(100vh - 130px);
}
</style>