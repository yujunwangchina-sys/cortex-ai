<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="插件名称" prop="pluginName">
        <el-input
          v-model="queryParams.pluginName"
          placeholder="请输入插件名称"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="插件类型" prop="pluginType">
        <el-select v-model="queryParams.pluginType" placeholder="请选择插件类型" clearable>
          <el-option label="MCP插件" value="mcp" />
          <el-option label="内置插件" value="builtin" />
        </el-select>
      </el-form-item>
      <el-form-item label="插件分类" prop="category">
        <el-select v-model="queryParams.category" placeholder="请选择插件分类" clearable>
          <el-option label="数据库" value="database" />
          <el-option label="文件系统" value="file_system" />
          <el-option label="网络搜索" value="web_search" />
          <el-option label="实用工具" value="utility" />
          <el-option label="自定义" value="custom" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable>
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
          v-hasPermi="['plugin:list:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="Upload"
          @click="handleLoadMcp"
          v-hasPermi="['plugin:list:add']"
        >扫描插件</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="Edit"
          :disabled="single"
          @click="handleUpdate"
          v-hasPermi="['plugin:list:edit']"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="Delete"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['plugin:list:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="RefreshRight"
          @click="handleReloadAll"
          v-hasPermi="['plugin:list:edit']"
        >重新加载全部</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>
    <!-- 插件列表 -->
    <el-table v-loading="loading" :data="pluginList" @selection-change="handleSelectionChange" border stripe highlight-current-row>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="插件名称" align="center" prop="pluginName" width="180" show-overflow-tooltip />
      <el-table-column label="类型" align="center" prop="pluginType" width="80">
        <template #default="scope">
          <el-tag v-if="scope.row.pluginType === 'mcp'" type="success">MCP</el-tag>
          <el-tag v-else-if="scope.row.pluginType === 'builtin'" type="info">内置</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="分类" align="center" prop="category" width="100">
        <template #default="scope">
          <dict-tag :options="categoryOptions" :value="scope.row.category" />
        </template>
      </el-table-column>
      <el-table-column label="运行时" align="center" prop="runtimeType" width="80">
        <template #default="scope">
          <el-tag v-if="scope.row.runtimeType === 'pip'" type="warning" size="small">pip</el-tag>
          <el-tag v-else-if="scope.row.runtimeType === 'npm'" type="primary" size="small">npm</el-tag>
          <el-tag v-else-if="scope.row.runtimeType === 'venv'" type="success" size="small">venv</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="包名" align="center" prop="packageName" width="200" show-overflow-tooltip />
      <el-table-column label="版本" align="center" prop="version" width="80" />
      <el-table-column label="官方" align="center" prop="isOfficial" width="60">
        <template #default="scope">
          <el-tag v-if="scope.row.isOfficial === '1'" type="success" size="small">是</el-tag>
          <el-tag v-else type="info" size="small">否</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="需要审批" align="center" width="90">
        <template #default="scope">
          <el-tag v-if="scope.row.requireApproval === '1'" type="warning" size="small">
            是
          </el-tag>
          <el-tag v-else type="info" size="small">否</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" width="80">
        <template #default="scope">
          <el-switch
            v-model="scope.row.status"
            active-value="0"
            inactive-value="1"
            @change="handleStatusChange(scope.row)"
          ></el-switch>
        </template>
      </el-table-column>
      <el-table-column label="运行状态" align="center" width="100" v-if="showRunningStatus">
        <template #default="scope">
          <template v-if="scope.row.pluginType === 'mcp'">
            <el-tag v-if="scope.row.isRunning" type="success" size="small">
              运行中
            </el-tag>
            <el-tag v-else type="info" size="small">
              已停止
            </el-tag>
          </template>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="描述" align="center" prop="description" show-overflow-tooltip />
      <el-table-column label="操作" align="center" width="280" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-tooltip content="测试连接" placement="top">
            <el-button link type="primary" icon="Connection" @click="handleTest(scope.row)" v-hasPermi="['plugin:list:test']"></el-button>
          </el-tooltip>
          <el-tooltip content="启动" placement="top" v-if="scope.row.pluginType === 'mcp'  && !scope.row.isRunning ">
            <el-button link type="success" icon="VideoPlay" @click="handleStart(scope.row)" v-hasPermi="['plugin:list:edit']"></el-button>
          </el-tooltip>
          <el-tooltip content="停止" placement="top" v-if="scope.row.pluginType === 'mcp'  && scope.row.isRunning ">
            <el-button link type="warning" icon="VideoPause" @click="handleStop(scope.row)" v-hasPermi="['plugin:list:edit']"></el-button>
          </el-tooltip>
          <el-tooltip content="同步工具" placement="top">
            <el-button link type="info" icon="Refresh" @click="handleSyncTools(scope.row)" v-hasPermi="['plugin:tool:sync']"></el-button>
          </el-tooltip>
          <el-tooltip content="查看日志" placement="top" v-if="scope.row.pluginType === 'mcp' && scope.row.isRunning">
            <el-button link type="warning" icon="Document" @click="handleViewLogs(scope.row)" v-hasPermi="['plugin:list:query']"></el-button>
          </el-tooltip>
          <el-tooltip content="修改" placement="top">
            <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['plugin:list:edit']"></el-button>
          </el-tooltip>
          <el-tooltip content="删除" placement="top">
            <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['plugin:list:remove']"></el-button>
          </el-tooltip>
          <el-tooltip content="查看工具" placement="top">
            <el-button link type="primary" icon="Tools" @click="handleViewTools(scope.row)" v-hasPermi="['plugin:tool:query']"></el-button>
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

    <!-- 添加或修改插件对话框 -->
    <el-dialog :title="title" v-model="open" width="700px" append-to-body>
      <el-form ref="pluginRef" :model="form" :rules="rules" label-width="100px">
        <el-row>
          <el-col :span="24">
            <el-form-item label="插件名称" prop="pluginName">
              <el-input v-model="form.pluginName" placeholder="请输入插件名称（如：生产数据库、测试环境）" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="插件类型" prop="pluginType">
              <el-select v-model="form.pluginType" placeholder="请选择插件类型" @change="handlePluginTypeChange">
                <el-option label="MCP插件" value="mcp" />
                <el-option label="内置插件" value="builtin" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="插件分类" prop="category">
              <el-select v-model="form.category" placeholder="请选择插件分类">
                <el-option label="数据库" value="database" />
                <el-option label="文件系统" value="file_system" />
                <el-option label="网络搜索" value="web_search" />
                <el-option label="实用工具" value="utility" />
                <el-option label="自定义" value="custom" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        
        <!-- MCP插件专用字段 -->
        <template v-if="form.pluginType === 'mcp'">
          <el-row>
            <el-col :span="18">
              <el-form-item label="包名" prop="packageName">
                <el-input 
                  v-model="form.packageName" 
                  placeholder="如: mcp-echarts 或 @modelcontextprotocol/server-filesystem"
                  clearable
                />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label=" " label-width="10px">
                <el-button 
                  type="primary" 
                  icon="Search"
                  @click="handleDetectPackage"
                  :loading="detecting"
                  style="width: 100%"
                >
                  {{ detecting ? '检测中...' : '智能检测' }}
                </el-button>
              </el-form-item>
            </el-col>
          </el-row>
          
          <el-alert
            v-if="detectionMessage"
            :title="detectionMessage"
            :type="detectionType"
            :closable="false"
            show-icon
            style="margin-bottom: 15px"
          />
          
          <el-row>
            <el-col :span="12">
              <el-form-item label="运行时类型" prop="runtimeType">
                <el-select v-model="form.runtimeType" placeholder="请选择运行时类型">
                  <el-option label="pip (Python)" value="pip" />
                  <el-option label="npm (Node.js)" value="npm" />
                  <el-option label="venv (Python旧版)" value="venv" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="版本" prop="version">
                <el-input v-model="form.version" placeholder="如: 1.0.0 或 latest" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="启动命令">
            <el-input
              v-model="form.startCommand"
              type="textarea"
              :rows="2"
              placeholder='如: ["python", "-m", "mcp_server_sqlite"]'
            />
          </el-form-item>
          <el-form-item label="环境变量">
            <el-input
              v-model="form.envVars"
              type="textarea"
              :rows="2"
              placeholder='如: {"API_KEY": "your-key"}'
            />
          </el-form-item>
        </template>
        
        <!-- 内置插件专用字段 -->
        <template v-if="form.pluginType === 'builtin'">
          <el-form-item label="Java类名" prop="builtinClass">
            <el-input v-model="form.builtinClass" placeholder="如: com.cortex.plugin.builtin.MyPlugin" />
          </el-form-item>
          <el-form-item label="环境变量">
            <el-input
              v-model="form.envVars"
              type="textarea"
              :rows="3"
              placeholder='JSON格式，如: {"API_KEY": "your-key", "DATABASE_URL": "jdbc:mysql://..."}'
            />
            <div style="margin-top: 8px; font-size: 12px; color: #909399;">
              💡 内置插件可以通过环境变量配置API密钥、数据库连接等敏感信息
            </div>
          </el-form-item>
        </template>
        
        <el-row>
          <el-col :span="12">
            <el-form-item label="版本号" prop="version">
              <el-input v-model="form.version" placeholder="如: 1.0.0" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="作者" prop="author">
              <el-input v-model="form.author" placeholder="请输入作者" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="是否官方" prop="isOfficial">
              <el-radio-group v-model="form.isOfficial">
                <el-radio label="1">是</el-radio>
                <el-radio label="0">否</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="需要审批" prop="requireApproval">
              <el-radio-group v-model="form.requireApproval">
                <el-radio label="1">
                  是
                </el-radio>
                <el-radio label="0">否</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="24">
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio label="0">启用</el-radio>
                <el-radio label="1">禁用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="插件描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入插件描述" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 查看工具对话框 -->
    <el-dialog title="插件工具列表" v-model="toolsDialogVisible" width="900px" append-to-body>
      <el-table v-loading="toolsLoading" :data="toolsList" max-height="400" border stripe highlight-current-row>
        <el-table-column label="工具名称" align="center" prop="toolName" show-overflow-tooltip />
        <el-table-column label="工具编码" align="center" prop="toolCode" show-overflow-tooltip />
        <el-table-column label="描述" align="center" prop="description" show-overflow-tooltip />
        <el-table-column label="状态" align="center" width="80">
          <template #default="scope">
            <el-tag v-if="scope.row.status === '0'" type="success">启用</el-tag>
            <el-tag v-else type="danger">禁用</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="120">
          <template #default="scope">
            <el-button
              link
              type="primary"
              icon="View"
              @click="handleViewToolDetail(scope.row)"
            >详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="toolsDialogVisible = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 工具详情对话框 -->
    <el-dialog 
      title="工具详情" 
      v-model="toolDetailDialogVisible" 
      width="900px" 
      append-to-body
      :close-on-click-modal="false"
    >
      <div v-if="currentTool" class="tool-detail-container">
        <!-- 基本信息卡片 -->
        <el-card shadow="never" class="detail-card">
          <template #header>
            <div class="card-header">
              <span><el-icon><Operation /></el-icon> 基本信息</span>
            </div>
          </template>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="工具名称" label-align="right">
              <el-tag type="primary">{{ currentTool.toolName }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="工具编码" label-align="right">
              <el-text type="info">{{ currentTool.toolCode }}</el-text>
            </el-descriptions-item>
            <el-descriptions-item label="描述" :span="2" label-align="right">
              {{ currentTool.description || '-' }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>

        <!-- 参数定义卡片 -->
        <el-card shadow="never" class="detail-card" style="margin-top: 16px;">
          <template #header>
            <div class="card-header">
              <span><el-icon><DocumentCopy /></el-icon> 参数定义</span>
            </div>
          </template>
          
          <el-tabs v-model="activeSchemaTab">
            <el-tab-pane label="输入参数" name="input">
              <div v-if="currentTool.inputSchema" class="schema-content">
                <el-alert 
                  type="info" 
                  :closable="false"
                  style="margin-bottom: 12px;"
                >
                  <template #title>
                    <span style="font-size: 13px;">JSON Schema格式，描述工具接受的参数结构</span>
                  </template>
                </el-alert>
                <pre class="json-viewer">{{ formatJson(currentTool.inputSchema) }}</pre>
              </div>
              <el-empty v-else description="无输入参数定义" :image-size="80" />
            </el-tab-pane>
            
            <el-tab-pane label="输出参数" name="output">
              <div v-if="currentTool.outputSchema" class="schema-content">
                <el-alert 
                  type="success" 
                  :closable="false"
                  style="margin-bottom: 12px;"
                >
                  <template #title>
                    <span style="font-size: 13px;">JSON Schema格式，描述工具返回的数据结构</span>
                  </template>
                </el-alert>
                <pre class="json-viewer">{{ formatJson(currentTool.outputSchema) }}</pre>
              </div>
              <el-empty v-else description="无输出参数定义" :image-size="80" />
            </el-tab-pane>
          </el-tabs>
        </el-card>

        <!-- 使用示例卡片 -->
        <el-card shadow="never" class="detail-card" style="margin-top: 16px;">
          <template #header>
            <div class="card-header">
              <span><el-icon><Document /></el-icon> 使用示例</span>
            </div>
          </template>
          
          <el-tabs v-model="activeExampleTab">
            <el-tab-pane label="示例输入" name="exampleInput">
              <div v-if="currentTool.exampleInput" class="example-content">
                <div class="example-header">
                  <el-text type="primary">调用此工具时的参数示例：</el-text>
                  <el-button 
                    size="small" 
                    @click="copyToClipboard(formatJson(currentTool.exampleInput))"
                    :icon="DocumentCopy"
                  >复制</el-button>
                </div>
                <pre class="json-viewer example">{{ formatJson(currentTool.exampleInput) }}</pre>
              </div>
              <el-empty v-else description="无示例输入" :image-size="80" />
            </el-tab-pane>
            
            <el-tab-pane label="示例输出" name="exampleOutput">
              <div v-if="currentTool.exampleOutput" class="example-content">
                <div class="example-header">
                  <el-text type="success">工具返回的数据示例：</el-text>
                  <el-button 
                    size="small" 
                    @click="copyToClipboard(formatJson(currentTool.exampleOutput))"
                    :icon="DocumentCopy"
                  >复制</el-button>
                </div>
                <pre class="json-viewer example">{{ formatJson(currentTool.exampleOutput) }}</pre>
              </div>
              <el-empty v-else description="无示例输出" :image-size="80" />
            </el-tab-pane>
          </el-tabs>
        </el-card>
      </div>
      
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="toolDetailDialogVisible = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 加载MCP包对话框 -->
    <McpLoader 
      v-model:visible="mcpLoaderVisible" 
      @loaded="handleMcpLoaded"
    />

    <!-- 日志查看器 -->
    <LogViewer ref="logViewerRef" />
  </div>
</template>

<script setup name="PluginList">
import { onMounted, onUnmounted } from 'vue'
import {
  listPlugin,
  getPlugin,
  addPlugin,
  updatePlugin,
  delPlugin,
  testConnection,
  startPlugin,
  stopPlugin,
  syncTools,
  reloadAllPlugins,
  listPluginTools,
  detectPackage
} from '@/api/plugin/plugin'
import McpLoader from './components/McpLoader.vue'
import LogViewer from './components/LogViewer.vue'

const { proxy } = getCurrentInstance()

const { sys_normal_disable } = proxy.useDict('sys_normal_disable')

// 数据
const pluginList = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref('')
const categoryOptions = ref([])
const toolsDialogVisible = ref(false)
const toolDetailDialogVisible = ref(false)
const activeSchemaTab = ref('input')
const activeExampleTab = ref('exampleInput')
const toolsList = ref([])
const toolsLoading = ref(false)
const currentTool = ref(null)
const mcpLoaderVisible = ref(false)
const showRunningStatus = ref(true) // 显示运行状态列
const refreshTimer = ref(null) // 自动刷新定时器
const logViewerRef = ref(null) // 日志查看器引用
const detecting = ref(false) // 检测中标志
const detectionMessage = ref('') // 检测消息
const detectionType = ref('info') // 消息类型：success/warning/error/info

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    pluginName: null,
    pluginType: null,
    category: null,
    status: null
  },
  rules: {
    pluginName: [
      { required: true, message: '插件名称不能为空', trigger: 'blur' }
    ],
    pluginType: [
      { required: true, message: '插件类型不能为空', trigger: 'change' }
    ]
  }
})

const { queryParams, form, rules } = toRefs(data)

/** 查询插件列表 */
function getList() {
  loading.value = true
  listPlugin(queryParams.value).then(response => {
    pluginList.value = response.rows
    total.value = response.total
    loading.value = false
  })
}

/** 取消按钮 */
function cancel() {
  open.value = false
  reset()
}

/** 表单重置 */
function reset() {
  form.value = {
    pluginId: null,
    pluginName: null,
    pluginType: null,
    category: null,
    runtimeType: null,
    packageName: null,
    startCommand: null,
    envVars: null,
    builtinClass: null,
    version: null,
    author: null,
    isOfficial: '0',
    requireApproval: '0',
    status: '0',
    description: null,
    remark: null
  }
  proxy.resetForm('pluginRef')
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm('queryRef')
  handleQuery()
}

/** 多选框选中数据 */
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.pluginId)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

/** 智能检测包 */
async function handleDetectPackage() {
  if (!form.value.packageName || !form.value.packageName.trim()) {
    proxy.$modal.msgWarning('请先输入包名')
    return
  }
  
  detecting.value = true
  detectionMessage.value = '正在检测包，请稍候...'
  detectionType.value = 'info'
  
  try {
    const response = await detectPackage(form.value.packageName.trim())
    
    if (response.code === 200 && response.data.success) {
      const data = response.data
      
      // 自动填充所有字段
      form.value.runtimeType = data.runtimeType
      form.value.version = data.version
      form.value.startCommand = data.startCommand
      form.value.envVars = data.envVars || '{}'
      form.value.pluginName = data.pluginName
      form.value.category = data.category
      form.value.description = data.description
      form.value.isOfficial = data.isOfficial
      
      if (data.downloaded) {
        detectionMessage.value = `✅ 检测成功！包已自动下载并配置（运行时：${data.runtimeType}，版本：${data.version}）`
        detectionType.value = 'success'
      } else {
        detectionMessage.value = `✅ 检测成功！包已存在（运行时：${data.runtimeType}，版本：${data.version}）`
        detectionType.value = 'success'
      }
      
      proxy.$modal.msgSuccess('检测成功，所有字段已自动填充')
    } else {
      const data = response.data
      detectionMessage.value = `❌ ${data.errorMessage || '检测失败'}`
      detectionType.value = 'error'
      
      // 显示详细信息
      if (data.detectionDetails) {
        console.log('检测详情：\n' + data.detectionDetails)
      }
      
      proxy.$modal.msgError(data.errorMessage || '检测失败，请检查包名是否正确')
    }
  } catch (error) {
    console.error('检测包失败:', error)
    detectionMessage.value = '❌ 检测失败：' + error.message
    detectionType.value = 'error'
    proxy.$modal.msgError('检测失败：' + error.message)
  } finally {
    detecting.value = false
  }
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  detectionMessage.value = ''
  open.value = true
  title.value = '添加插件'
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset()
  detectionMessage.value = ''
  const _pluginId = row.pluginId || ids.value
  getPlugin(_pluginId).then(response => {
    form.value = response.data
    open.value = true
    title.value = '修改插件'
  })
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs['pluginRef'].validate(valid => {
    if (valid) {
      if (form.value.pluginId != null) {
        updatePlugin(form.value).then(response => {
          proxy.$modal.msgSuccess('修改成功')
          open.value = false
          getList()
        })
      } else {
        addPlugin(form.value).then(response => {
          proxy.$modal.msgSuccess('新增成功')
          open.value = false
          getList()
        })
      }
    }
  })
}

/** 删除按钮操作 */
function handleDelete(row) {
  const _pluginIds = row.pluginId || ids.value
  proxy.$modal.confirm('是否确认删除插件编号为"' + _pluginIds + '"的数据项？').then(function() {
    return delPlugin(_pluginIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}

/** 插件类型改变 */
function handlePluginTypeChange(val) {
  if (val === 'builtin') {
    form.value.runtimeType = null
    form.value.packageName = null
    form.value.startCommand = null
    form.value.envVars = null
  } else if (val === 'mcp') {
    form.value.builtinClass = null
  }
}

/** 状态修改 */
function handleStatusChange(row) {
  let text = row.status === '0' ? '启用' : '停用'
  proxy.$modal.confirm('确认要"' + text + '""' + row.pluginName + '"插件吗？').then(function() {
    return updatePlugin(row)
  }).then(() => {
    proxy.$modal.msgSuccess(text + '成功')
  }).catch(function() {
    row.status = row.status === '0' ? '1' : '0'
  })
}

/** 测试连接 */
function handleTest(row) {
  proxy.$modal.loading('正在测试连接...')
  testConnection(row).then(response => {
    proxy.$modal.closeLoading()
    proxy.$modal.msgSuccess('连接成功: ' + response.msg)
  }).catch(() => {
    proxy.$modal.closeLoading()
  })
}

/** 启动插件 */
function handleStart(row) {
  proxy.$modal.loading('正在启动插件...')
  startPlugin(row.pluginName).then(response => {
    proxy.$modal.closeLoading()
    proxy.$modal.msgSuccess('启动成功: ' + response.msg)
    // 立即刷新以更新运行状态
    getList()
  }).catch(() => {
    proxy.$modal.closeLoading()
  })
}

/** 停止插件 */
function handleStop(row) {
  proxy.$modal.loading('正在停止插件...')
  stopPlugin(row.pluginName).then(response => {
    proxy.$modal.closeLoading()
    proxy.$modal.msgSuccess('停止成功: ' + response.msg)
    // 立即刷新以更新运行状态
    getList()
  }).catch(() => {
    proxy.$modal.closeLoading()
  })
}

/** 同步工具 */
function handleSyncTools(row) {
  proxy.$modal.loading('正在同步工具...')
  syncTools(row.pluginName).then(response => {
    proxy.$modal.closeLoading()
    proxy.$modal.msgSuccess('同步成功: ' + response.msg)
  }).catch(() => {
    proxy.$modal.closeLoading()
  })
}

/** 重新加载全部 */
function handleReloadAll() {
  proxy.$modal.confirm('确认要重新加载所有启用的MCP插件吗？').then(function() {
    proxy.$modal.loading('正在重新加载...')
    return reloadAllPlugins()
  }).then(response => {
    proxy.$modal.closeLoading()
    proxy.$modal.msgSuccess(response.msg)
    getList()
  }).catch(() => {
    proxy.$modal.closeLoading()
  })
}

/** 查看工具 */
function handleViewTools(row) {
  toolsLoading.value = true
  toolsDialogVisible.value = true
  listPluginTools({ pluginId: row.pluginId }).then(response => {
    toolsList.value = response.rows
    toolsLoading.value = false
  }).catch(() => {
    toolsLoading.value = false
  })
}

/** 查看日志 */
function handleViewLogs(row) {
  if (logViewerRef.value) {
    logViewerRef.value.open(row.pluginName, row.pluginName)
  }
}

/** 查看工具详情 */
function handleViewToolDetail(row) {
  currentTool.value = row
  toolDetailDialogVisible.value = true
}

/** 格式化JSON */
function formatJson(jsonStr) {
  if (!jsonStr) return ''
  try {
    const obj = typeof jsonStr === 'string' ? JSON.parse(jsonStr) : jsonStr
    return JSON.stringify(obj, null, 2)
  } catch (e) {
    return jsonStr
  }
}

/** 复制到剪贴板 */
function copyToClipboard(text) {
  navigator.clipboard.writeText(text).then(() => {
    ElMessage.success('已复制到剪贴板')
  }).catch(() => {
    // 降级方案
    const textarea = document.createElement('textarea')
    textarea.value = text
    document.body.appendChild(textarea)
    textarea.select()
    try {
      document.execCommand('copy')
      ElMessage.success('已复制到剪贴板')
    } catch (err) {
      ElMessage.error('复制失败，请手动复制')
    }
    document.body.removeChild(textarea)
  })
}

/** 加载MCP包 */
function handleLoadMcp() {
  mcpLoaderVisible.value = true
}

/** MCP包加载成功 */
function handleMcpLoaded() {
  getList()
}

/** 启动自动刷新（已禁用，需要时点击搜索按钮刷新） */
function startAutoRefresh() {
  // 已禁用自动刷新，用户可以通过搜索按钮手动刷新列表
}

/** 停止自动刷新 */
function stopAutoRefresh() {
  if (refreshTimer.value) {
    clearInterval(refreshTimer.value)
    refreshTimer.value = null
  }
}

// 组件挂载时不再启动自动刷新
// onMounted(() => {
//   startAutoRefresh()
// })

// 组件卸载时清理定时器
onUnmounted(() => {
  stopAutoRefresh()
})

// 初始化分类字典
categoryOptions.value = [
  { label: '数据库', value: 'database' },
  { label: '文件系统', value: 'file_system' },
  { label: '网络搜索', value: 'web_search' },
  { label: '实用工具', value: 'utility' },
  { label: '自定义', value: 'custom' }
]

getList()

</script>

<style scoped lang="scss">
</style>


<style scoped>
.tool-detail-container {
  max-height: 70vh;
  overflow-y: auto;
}

.detail-card {
  border-radius: 8px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 15px;
}

.schema-content,
.example-content {
  padding: 4px 0;
}

.example-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding: 8px 12px;
  background-color: #f5f7fa;
  border-radius: 4px;
}

.json-viewer {
  background-color: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 12px;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
  color: #2c3e50;
  max-height: 400px;
  overflow: auto;
  margin: 0;
  white-space: pre-wrap;
  word-wrap: break-word;
}

.json-viewer.example {
  background-color: #fafafa;
  border-color: #dcdfe6;
}

/* 美化滚动条 */
.json-viewer::-webkit-scrollbar,
.tool-detail-container::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.json-viewer::-webkit-scrollbar-track,
.tool-detail-container::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 4px;
}

.json-viewer::-webkit-scrollbar-thumb,
.tool-detail-container::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 4px;
}

.json-viewer::-webkit-scrollbar-thumb:hover,
.tool-detail-container::-webkit-scrollbar-thumb:hover {
  background: #a8a8a8;
}

/* Tab样式优化 */
:deep(.el-tabs__nav-wrap::after) {
  background-color: #e4e7ed;
}

:deep(.el-tabs__item) {
  font-weight: 500;
}

/* 空状态样式 */
:deep(.el-empty) {
  padding: 40px 0;
}
</style>
