<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
      <el-form-item label="文件名称" prop="fileName">
        <el-input v-model="queryParams.fileName" placeholder="文件名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="文件类型" prop="fileType">
        <el-select v-model="queryParams.fileType" placeholder="文件类型" clearable style="width: 140px">
          <el-option label="用户上传" value="upload" />
          <el-option label="Agent生成" value="generated" />
        </el-select>
      </el-form-item>
      <el-form-item label="业务系统" prop="businessSystem">
        <el-input v-model="queryParams.businessSystem" placeholder="业务系统" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="用户名" prop="userLoginName">
        <el-input v-model="queryParams.userLoginName" placeholder="用户登录名" clearable @keyup.enter="handleQuery" />
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
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="fileList" @selection-change="handleSelectionChange" border stripe highlight-current-row style="width: 100%">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="文件ID" align="center" prop="fileId" width="80" />
      <el-table-column label="文件名称" align="left" prop="fileName" min-width="180" show-overflow-tooltip />
      <el-table-column label="类型" align="center" prop="fileType" width="100">
        <template #default="scope">
          <el-tag :type="scope.row.fileType === 'upload' ? 'primary' : 'success'">
            {{ scope.row.fileType === 'upload' ? '上传' : '生成' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="大小" align="center" width="100">
        <template #default="scope">
          {{ formatSize(scope.row.fileSize) }}
        </template>
      </el-table-column>
      <el-table-column label="业务系统" align="center" prop="businessSystem" width="100" />
      <el-table-column label="用户" align="center" prop="userLoginName" width="100" />
      <el-table-column label="会话ID" align="center" prop="sessionId" width="200" show-overflow-tooltip />
      <el-table-column label="Agent" align="center" prop="agentCode" width="100" />
      <el-table-column label="创建时间" align="center" prop="createTime" width="160" />
      <el-table-column label="操作" align="center" width="140">
        <template #default="scope">
          <el-button link type="primary" icon="Download" @click="handleDownload(scope.row)">下载</el-button>
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
  </div>
</template>

<script setup>
import { ref, reactive, getCurrentInstance } from 'vue'
import { listFile, delFile } from '@/api/agent/file'
import { getToken } from '@/utils/auth'

const { proxy } = getCurrentInstance()

const fileList = ref([])
const loading = ref(true)
const showSearch = ref(true)
const multiple = ref(true)
const total = ref(0)
const ids = ref([])

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  fileName: undefined,
  fileType: undefined,
  businessSystem: undefined,
  userLoginName: undefined,
  sessionId: undefined
})

function getList() {
  loading.value = true
  listFile(queryParams).then(res => {
    fileList.value = res.rows
    total.value = res.total
    loading.value = false
  })
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm('queryRef')
  handleQuery()
}

function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.fileId)
  multiple.value = !selection.length
}

function handleDelete(row) {
  const fileIds = row.fileId || ids.value
  proxy.$modal.confirm('确认删除选中的文件记录？物理文件也会一并删除。').then(() => {
    return delFile(fileIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}

function handleDownload(row) {
  const xhr = new XMLHttpRequest()
  xhr.responseType = 'blob'
  xhr.onload = () => {
    if (xhr.status === 200) {
      const url = URL.createObjectURL(xhr.response)
      const a = document.createElement('a')
      a.href = url
      a.download = row.fileName || 'download'
      a.click()
      URL.revokeObjectURL(url)
    }
  }
  xhr.open('GET', import.meta.env.VITE_APP_BASE_API + '/agent/api/file/download/' + row.fileId)
  xhr.setRequestHeader('Authorization', 'Bearer ' + getToken())
  xhr.send()
}

function handleExport() {
  proxy.download('agent/file/export', { ...queryParams }, `agent_file_${new Date().getTime()}.xlsx`)
}

function formatSize(bytes) {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1048576).toFixed(1) + ' MB'
}

getList()
</script>