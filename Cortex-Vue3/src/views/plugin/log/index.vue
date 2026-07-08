<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="会话ID" prop="sessionId">
        <el-input
          v-model="queryParams.sessionId"
          placeholder="请输入会话ID"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="工具名称" prop="toolName">
        <el-input
          v-model="queryParams.toolName"
          placeholder="请输入工具名称"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="执行状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择执行状态" clearable>
          <el-option label="成功" value="0" />
          <el-option label="失败" value="1" />
        </el-select>
      </el-form-item>
      <el-form-item label="执行时间" style="width: 308px">
        <el-date-picker
          v-model="dateRange"
          value-format="YYYY-MM-DD"
          type="daterange"
          range-separator="-"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
        ></el-date-picker>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="Delete"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['plugin:log:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="Delete"
          @click="handleCleanAll"
          v-hasPermi="['plugin:log:remove']"
        >清空日志</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="logList" @selection-change="handleSelectionChange" border stripe highlight-current-row>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="日志ID" align="center" prop="logId" width="80" />
      <el-table-column label="会话ID" align="center" prop="sessionId" min-width="200" show-overflow-tooltip />
      <el-table-column label="插件名称" align="center" prop="pluginName" min-width="150" show-overflow-tooltip />
      <el-table-column label="工具名称" align="center" prop="toolName" min-width="150" show-overflow-tooltip />
      <el-table-column label="执行状态" align="center" width="100">
        <template #default="scope">
          <el-tag v-if="scope.row.status === '0'" type="success">成功</el-tag>
          <el-tag v-else type="danger">失败</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="执行时长" align="center" width="120">
        <template #default="scope">
          <el-tag v-if="scope.row.executionTime < 1000" type="success">
            {{ scope.row.executionTime }}ms
          </el-tag>
          <el-tag v-else-if="scope.row.executionTime < 5000" type="warning">
            {{ scope.row.executionTime }}ms
          </el-tag>
          <el-tag v-else type="danger">
            {{ scope.row.executionTime }}ms
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="执行时间" align="center" prop="createTime" width="160">
        <template #default="scope">
          <span>{{ parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="150" fixed="right" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-tooltip content="查看详情" placement="top">
            <el-button link type="primary" icon="View" @click="handleView(scope.row)" v-hasPermi="['plugin:log:query']"></el-button>
          </el-tooltip>
          <el-tooltip content="删除" placement="top">
            <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['plugin:log:remove']"></el-button>
          </el-tooltip>
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

    <!-- 日志详情对话框 -->
    <el-dialog title="执行日志详情" v-model="detailDialogVisible" width="900px" append-to-body>
      <el-descriptions :column="2" border v-if="currentLog">
        <el-descriptions-item label="日志ID">{{ currentLog.logId }}</el-descriptions-item>
        <el-descriptions-item label="会话ID">{{ currentLog.sessionId }}</el-descriptions-item>
        <el-descriptions-item label="插件名称">{{ currentLog.pluginName }}</el-descriptions-item>
        <el-descriptions-item label="工具名称">{{ currentLog.toolName }}</el-descriptions-item>
        <el-descriptions-item label="用户ID">{{ currentLog.userId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="执行状态">
          <el-tag v-if="currentLog.status === '0'" type="success">成功</el-tag>
          <el-tag v-else type="danger">失败</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="执行时长">
          {{ currentLog.executionTime }}ms
        </el-descriptions-item>
        <el-descriptions-item label="执行时间">
          {{ parseTime(currentLog.createTime) }}
        </el-descriptions-item>
      </el-descriptions>

      <el-divider>输入参数</el-divider>
      <el-input
        v-model="currentLog.inputParams"
        type="textarea"
        :rows="6"
        readonly
        style="font-family: monospace;"
      />

      <el-divider v-if="currentLog.status === '0'">输出结果</el-divider>
      <el-input
        v-if="currentLog.status === '0'"
        v-model="currentLog.outputResult"
        type="textarea"
        :rows="8"
        readonly
        style="font-family: monospace;"
      />

      <el-divider v-if="currentLog.status === '1'">错误信息</el-divider>
      <el-alert
        v-if="currentLog.status === '1'"
        :title="currentLog.errorMessage"
        type="error"
        :closable="false"
        show-icon
        style="white-space: pre-wrap; word-break: break-all;"
      />

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="detailDialogVisible = false">关 闭</el-button>
          <el-button type="primary" @click="copyLogDetail">
            <el-icon><DocumentCopy /></el-icon> 复制全部
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="ExecutionLog">
import { listExecutionLogs, delExecutionLogs } from '@/api/plugin/plugin'
import { ElMessage, ElMessageBox } from 'element-plus'

const { proxy } = getCurrentInstance()

const logList = ref([])
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const multiple = ref(true)
const total = ref(0)
const dateRange = ref([])
const detailDialogVisible = ref(false)
const currentLog = ref(null)

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    sessionId: null,
    pluginName: null,
    toolName: null,
    status: null
  }
})

const { queryParams } = toRefs(data)

/** 查询日志列表 */
function getList() {
  loading.value = true
  const params = proxy.addDateRange(queryParams.value, dateRange.value, 'CreateTime')
  listExecutionLogs(params).then(response => {
    logList.value = response.rows
    total.value = response.total
    loading.value = false
  })
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

/** 重置按钮操作 */
function resetQuery() {
  dateRange.value = []
  proxy.resetForm('queryRef')
  handleQuery()
}

/** 多选框选中数据 */
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.logId)
  multiple.value = !selection.length
}

/** 查看详情 */
function handleView(row) {
  currentLog.value = { ...row }
  // 格式化JSON
  if (currentLog.value.inputParams) {
    try {
      const parsed = JSON.parse(currentLog.value.inputParams)
      currentLog.value.inputParams = JSON.stringify(parsed, null, 2)
    } catch (e) {
      // 保持原样
    }
  }
  if (currentLog.value.outputResult) {
    try {
      const parsed = JSON.parse(currentLog.value.outputResult)
      currentLog.value.outputResult = JSON.stringify(parsed, null, 2)
    } catch (e) {
      // 保持原样
    }
  }
  detailDialogVisible.value = true
}

/** 删除按钮操作 */
function handleDelete(row) {
  const logIds = row.logId || ids.value
  ElMessageBox.confirm('是否确认删除日志编号为"' + logIds + '"的数据项？', '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    return delExecutionLogs(logIds)
  }).then(() => {
    getList()
    ElMessage.success('删除成功')
  }).catch(() => {})
}

/** 清空全部日志 */
function handleCleanAll() {
  ElMessageBox.confirm('是否确认清空所有执行日志？此操作不可恢复！', '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    // 获取所有日志ID
    const allIds = logList.value.map(item => item.logId).join(',')
    if (allIds) {
      return delExecutionLogs(allIds)
    } else {
      ElMessage.warning('暂无日志可清空')
      return Promise.reject()
    }
  }).then(() => {
    getList()
    ElMessage.success('清空成功')
  }).catch(() => {})
}

/** 复制日志详情 */
function copyLogDetail() {
  if (!currentLog.value) return
  
  const content = `
=== 执行日志详情 ===
日志ID: ${currentLog.value.logId}
会话ID: ${currentLog.value.sessionId}
插件名称: ${currentLog.value.pluginName}
工具名称: ${currentLog.value.toolName}
用户ID: ${currentLog.value.userId || '-'}
执行状态: ${currentLog.value.status === '0' ? '成功' : '失败'}
执行时长: ${currentLog.value.executionTime}ms
执行时间: ${proxy.parseTime(currentLog.value.createTime)}

--- 输入参数 ---
${currentLog.value.inputParams || '无'}

${currentLog.value.status === '0' ? '--- 输出结果 ---\n' + (currentLog.value.outputResult || '无') : '--- 错误信息 ---\n' + (currentLog.value.errorMessage || '无')}
  `.trim()
  
  navigator.clipboard.writeText(content).then(() => {
    ElMessage.success('已复制到剪贴板')
  }).catch(() => {
    ElMessage.error('复制失败，请手动复制')
  })
}

getList()
</script>

<style scoped>
.el-divider {
  margin: 20px 0;
}
</style>
