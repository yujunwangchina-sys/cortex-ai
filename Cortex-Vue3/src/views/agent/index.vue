<template>
  <div class="app-container">
    <!-- 搜索栏 -->
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
      <el-form-item label="Agent名称" prop="agentName">
        <el-input
          v-model="queryParams.agentName"
          placeholder="请输入Agent名称"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="Agent状态" clearable>
          <el-option label="启用" value="0" />
          <el-option label="禁用" value="1" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 操作按钮 -->
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="Plus"
          @click="handleAdd"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="Delete"
          :disabled="multiple"
          @click="handleDelete"
        >删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <!-- 数据表格 -->
    <el-table v-loading="loading" :data="agentList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="头像" align="center" width="80">
        <template #default="scope">
          <AgentAvatar 
            :agent-id="scope.row.id" 
            :avatar="scope.row.avatar"
            :size="40"
            @success="handleAvatarSuccess(scope.row, $event)"
          />
        </template>
      </el-table-column>
      <el-table-column label="Agent名称" align="center" prop="agentName" />
      <el-table-column label="Agent编码" align="center" prop="agentCode" />
      <el-table-column label="业务系统" align="center" prop="businessSystem" width="120">
        <template #default="scope">
          <el-tag v-if="scope.row.businessSystem" type="info" size="small">
            {{ scope.row.businessSystem }}
          </el-tag>
          <span v-else style="color: #909399;">通用</span>
        </template>
      </el-table-column>
      <el-table-column label="API密钥" align="center" prop="apiKey" width="180" show-overflow-tooltip>
        <template #default="scope">
          <span v-if="scope.row.apiKey" style="font-family: monospace; font-size: 12px;">
            {{ scope.row.apiKey.substring(0, 20) }}...
          </span>
          <span v-else style="color: #909399;">-</span>
        </template>
      </el-table-column>
      <el-table-column label="描述" align="center" prop="description" show-overflow-tooltip />
      <el-table-column label="状态" align="center" prop="status">
        <template #default="scope">
          <el-tag :type="scope.row.status === '0' ? 'success' : 'danger'">
            {{ scope.row.status === '0' ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="排序" align="center" prop="sortOrder" />
      <el-table-column label="创建时间" align="center" prop="createTime" width="180" />
      <el-table-column label="操作" align="center" width="380" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)">修改</el-button>
          <el-button link type="primary" icon="Key" @click="handlePermission(scope.row)">权限</el-button>
          <el-button link type="warning" icon="ChatDotRound" @click="handleChat(scope.row)">对话</el-button>
          <el-button link type="success" icon="Memo" @click="handleEditPrompt(scope.row)">系统提示词</el-button>
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />

    <!-- 新增/修改对话框 -->
    <AgentForm
      v-model:visible="formVisible"
      :agent-id="currentAgentId"
      @success="getList"
    />

    <!-- 权限分配对话框 -->
    <AgentPermission
      v-model:visible="permissionVisible"
      :agent-id="currentAgentId"
    />

    <!-- 系统提示词编辑器 -->
    <SystemPromptEditor
      v-model:visible="promptEditorVisible"
      :agent-id="currentAgentId"
      @success="getList"
    />
  
    <!-- 对话弹窗 -->
    <el-dialog v-model="chatVisible" :title="chatTitle" width="80%" top="5vh" destroy-on-close class="chat-dialog">
      <AgentChat :agent-code="chatAgentCode" :agent-name="chatAgentName" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, getCurrentInstance } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
import { listAgent, delAgent } from '@/api/agent/agent'
import AgentForm from './components/AgentForm.vue'
import AgentPermission from './components/AgentPermission.vue'
import SystemPromptEditor from './components/SystemPromptEditor.vue'
import AgentChat from './AgentChat.vue'
import AgentAvatar from './components/AgentAvatar.vue'

const loading = ref(true)
const multiple = ref(true)
const showSearch = ref(true)
const total = ref(0)
const agentList = ref([])
const ids = ref([])

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  agentName: '',
  status: ''
})

const formVisible = ref(false)
const permissionVisible = ref(false)
const promptEditorVisible = ref(false)
const chatVisible = ref(false)
const currentAgentId = ref(null)
const chatAgentCode = ref('')
const chatAgentName = ref('')
const chatTitle = ref('')

function getList() {
  loading.value = true
  listAgent(queryParams).then(res => {
    agentList.value = res.rows
    total.value = res.total
    loading.value = false
    
    // 调试：检查avatar字段
    console.log('📋 Agent列表加载完成，记录数:', res.rows.length)
    if (res.rows && res.rows.length > 0) {
      res.rows.forEach((row, index) => {
        console.log(`  Agent[${index}]: id=${row.id}, name=${row.agentName}, avatar="${row.avatar}"`)
      })
    }
  }).catch(error => {
    console.error('❌ 加载列表失败:', error)
    loading.value = false
  })
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  queryParams.agentName = ''
  queryParams.status = ''
  handleQuery()
}

function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.id)
  multiple.value = !selection.length
}

function handleAdd() {
  currentAgentId.value = null
  formVisible.value = true
}

function handleUpdate(row) {
  currentAgentId.value = row.id
  formVisible.value = true
}

function handlePermission(row) {
  currentAgentId.value = row.id
  permissionVisible.value = true
}

function handleEditPrompt(row) {
  currentAgentId.value = row.id
  promptEditorVisible.value = true
}

function handleChat(row) {
  router.push('/agent/chat/' + row.agentCode)
}

function handleDelete(row) {
  const deleteIds = row.id || ids.value
  proxy.$modal.confirm('确认删除选中的Agent?').then(() => {
    return delAgent(deleteIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}

function handleAvatarSuccess(row, imgUrl) {
  row.avatar = imgUrl
  getList()
}

const { proxy } = getCurrentInstance()

onMounted(() => {
  getList()
})
</script>
