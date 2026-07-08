<template>
  <el-dialog
    v-model="dialogVisible"
    title="扫描插件"
    width="1000px"
    :close-on-click-modal="false"
    @open="handleOpen"
    @close="handleClose"
  >
    <div class="mcp-loader">
      <!-- 操作按钮 -->
      <el-row :gutter="10" class="mb20">
        <el-col :span="1.5">
          <el-button type="primary" icon="Search" @click="handleScan" :loading="scanning">
            {{ scanning ? '扫描中...' : '扫描本地包' }}
          </el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="success" icon="RefreshRight" @click="handleScan">
            刷新
          </el-button>
        </el-col>
        <el-col :span="1.5">
          <el-input 
            v-model="searchKeyword" 
            placeholder="搜索包名..." 
            clearable 
            style="width: 300px;"
            prefix-icon="Search"
          />
        </el-col>
      </el-row>

      <!-- Python/Node/内置插件子Tab -->
      <el-tabs v-model="activeTab" class="scan-tabs">
        <!-- Python包 -->
        <el-tab-pane label="Python包" name="python">
          <el-alert 
            v-if="!scanning && scanResult && scanResult.pythonAvailable === false && pythonPackages.length === 0" 
            title="Python环境扫描失败，请检查Python和pip是否已正确安装并配置到系统PATH" 
            type="warning" 
            :closable="false"
            show-icon
            class="mb20"
          />
          
          <el-table 
            v-loading="scanLoading" 
            :data="filteredPythonPackages" 
            border 
            stripe 
            height="450"
            highlight-current-row
            :empty-text="scanResult && !scanResult.pythonAvailable ? 'Python环境不可用，无法扫描' : '暂无MCP包'"
          >
            <el-table-column label="插件名称" align="center" prop="displayName" width="150" show-overflow-tooltip />
            <el-table-column label="包名" align="left" prop="packageName" min-width="200" show-overflow-tooltip />
            <el-table-column label="分类" align="center" prop="category" width="100">
              <template #default="scope">
                <el-tag v-if="scope.row.category === 'database'" type="primary" size="small">数据库</el-tag>
                <el-tag v-else-if="scope.row.category === 'file_system'" type="success" size="small">文件系统</el-tag>
                <el-tag v-else-if="scope.row.category === 'web_search'" type="warning" size="small">网络搜索</el-tag>
                <el-tag v-else type="info" size="small">工具</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="版本" align="center" prop="version" width="100" />
            <el-table-column label="描述" align="left" prop="description" min-width="180" show-overflow-tooltip />
            <el-table-column label="状态" align="center" width="100">
              <template #default="scope">
                <el-tag v-if="scope.row.enabled" type="success" size="small">已有实例</el-tag>
                <el-tag v-else type="info" size="small">未加载</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" align="center" width="100" fixed="right">
              <template #default="scope">
                <el-button 
                  type="primary" 
                  size="small"
                  @click="handleLoadPackage(scope.row)"
                  :loading="scope.row.loading"
                >
                  加载
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- Node.js包 -->
        <el-tab-pane label="Node.js包" name="node">
          <el-alert 
            v-if="!scanning && scanResult && scanResult.nodeAvailable === false && nodePackages.length === 0" 
            title="Node.js环境扫描失败，请检查Node.js和npm是否已正确安装并配置到系统PATH" 
            type="warning" 
            :closable="false"
            show-icon
            class="mb20"
          />
          
          <el-table 
            v-loading="scanLoading" 
            :data="filteredNodePackages" 
            border 
            stripe 
            height="450"
            highlight-current-row
            :empty-text="scanResult && !scanResult.nodeAvailable ? 'Node.js环境不可用，无法扫描' : '暂无MCP包'"
          >
            <el-table-column label="插件名称" align="center" prop="displayName" width="150" show-overflow-tooltip />
            <el-table-column label="包名" align="left" prop="packageName" min-width="250" show-overflow-tooltip />
            <el-table-column label="分类" align="center" prop="category" width="100">
              <template #default="scope">
                <el-tag v-if="scope.row.category === 'database'" type="primary" size="small">数据库</el-tag>
                <el-tag v-else-if="scope.row.category === 'file_system'" type="success" size="small">文件系统</el-tag>
                <el-tag v-else-if="scope.row.category === 'web_search'" type="warning" size="small">网络搜索</el-tag>
                <el-tag v-else type="info" size="small">工具</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="版本" align="center" prop="version" width="100" />
            <el-table-column label="描述" align="left" prop="description" min-width="180" show-overflow-tooltip />
            <el-table-column label="状态" align="center" width="100">
              <template #default="scope">
                <el-tag v-if="scope.row.enabled" type="success" size="small">已有实例</el-tag>
                <el-tag v-else type="info" size="small">未加载</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" align="center" width="100" fixed="right">
              <template #default="scope">
                <el-button 
                  type="primary" 
                  size="small"
                  @click="handleLoadPackage(scope.row)"
                  :loading="scope.row.loading"
                >
                  加载
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 内置插件 -->
        <el-tab-pane label="内置插件" name="builtin">
          <el-alert 
            title="内置插件由Java实现，无需安装外部包，直接加载即可使用" 
            type="info" 
            :closable="false"
            show-icon
            class="mb20"
          />
          
          <el-table 
            v-loading="scanLoading" 
            :data="filteredBuiltinPlugins" 
            border 
            stripe 
            height="450"
            highlight-current-row
            :empty-text="'暂无内置插件'"
          >
            <el-table-column label="图标" align="center" width="60">
              <template #default="scope">
                <span style="font-size: 24px;">{{ scope.row.emoji || '🔧' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="插件名称" align="center" prop="displayName" width="150" show-overflow-tooltip />
            <el-table-column label="分类" align="center" prop="category" width="100">
              <template #default="scope">
                <el-tag v-if="scope.row.category === 'database'" type="primary" size="small">数据库</el-tag>
                <el-tag v-else-if="scope.row.category === 'file_system'" type="success" size="small">文件系统</el-tag>
                <el-tag v-else-if="scope.row.category === 'web_search'" type="warning" size="small">网络搜索</el-tag>
                <el-tag v-else type="info" size="small">工具</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="版本" align="center" prop="version" width="80" />
            <el-table-column label="工具数" align="center" width="80">
              <template #default="scope">
                <el-tag size="small">{{ scope.row.tools ? scope.row.tools.length : 0 }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="描述" align="left" prop="description" min-width="180" show-overflow-tooltip />
            <el-table-column label="可用性" align="center" width="100">
              <template #default="scope">
                <el-tag v-if="scope.row.available" type="success" size="small">可用</el-tag>
                <el-tooltip v-else content="需要配置环境变量" placement="top">
                  <el-tag type="warning" size="small">需配置</el-tag>
                </el-tooltip>
              </template>
            </el-table-column>
            <el-table-column label="状态" align="center" width="120">
              <template #default="scope">
                <el-tag v-if="scope.row.instanceCount > 0" type="success" size="small">
                  {{ scope.row.instanceCount }} 个实例
                </el-tag>
                <el-tag v-else type="info" size="small">未加载</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" align="center" width="120" fixed="right">
              <template #default="scope">
                <el-button 
                  type="primary" 
                  size="small"
                  @click="handleLoadBuiltinPlugin(scope.row)"
                  :loading="scope.row.loading"
                >
                  {{ scope.row.instanceCount > 0 ? '再次加载' : '加载' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>

    <template #footer>
      <el-button @click="handleClose">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { scanMcpPackages, enableMcpPackage, loadBuiltinPlugin } from '@/api/plugin/plugin'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:visible', 'loaded'])

const dialogVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val)
})

const scanning = ref(false)
const scanLoading = ref(false)
const activeTab = ref('python')
const scanResult = ref(null)
const pythonPackages = ref([])
const nodePackages = ref([])
const builtinPlugins = ref([])
const searchKeyword = ref('')
const loadedPackages = ref([]) // 已加载的包名列表

/** 过滤后的Python包 */
const filteredPythonPackages = computed(() => {
  if (!searchKeyword.value) return pythonPackages.value
  const keyword = searchKeyword.value.toLowerCase()
  return pythonPackages.value.filter(pkg => 
    pkg.packageName.toLowerCase().includes(keyword) ||
    pkg.displayName.toLowerCase().includes(keyword)
  )
})

/** 过滤后的Node包 */
const filteredNodePackages = computed(() => {
  if (!searchKeyword.value) return nodePackages.value
  const keyword = searchKeyword.value.toLowerCase()
  return nodePackages.value.filter(pkg => 
    pkg.packageName.toLowerCase().includes(keyword) ||
    pkg.displayName.toLowerCase().includes(keyword)
  )
})

/** 过滤后的内置插件 */
const filteredBuiltinPlugins = computed(() => {
  if (!searchKeyword.value) return builtinPlugins.value
  const keyword = searchKeyword.value.toLowerCase()
  return builtinPlugins.value.filter(plugin => 
    plugin.pluginName.toLowerCase().includes(keyword) ||
    plugin.displayName.toLowerCase().includes(keyword) ||
    (plugin.description && plugin.description.toLowerCase().includes(keyword))
  )
})

/** 扫描插件 */
function handleScan() {
  scanning.value = true
  scanLoading.value = true
  
  scanMcpPackages().then(response => {
    scanResult.value = response.data || {}
    
    // 获取已加载的包列表（从扫描结果中获取）
    loadedPackages.value = [
      ...(scanResult.value.pythonPackages || []).filter(pkg => pkg.enabled).map(pkg => pkg.packageName),
      ...(scanResult.value.nodePackages || []).filter(pkg => pkg.enabled).map(pkg => pkg.packageName)
    ]
    
    // 处理Python包，根据enabled状态设置
    pythonPackages.value = (scanResult.value.pythonPackages || []).map(pkg => ({
      ...pkg,
      loading: false,
      enabled: pkg.enabled || false // 使用后端返回的enabled状态
    }))
    
    // 处理Node.js包，根据enabled状态设置
    nodePackages.value = (scanResult.value.nodePackages || []).map(pkg => ({
      ...pkg,
      loading: false,
      enabled: pkg.enabled || false // 使用后端返回的enabled状态
    }))
    
    // 处理内置插件
    builtinPlugins.value = (scanResult.value.builtinPlugins || []).map(plugin => ({
      ...plugin,
      loading: false,
      instanceCount: plugin.instanceCount || 0 // 使用 instanceCount 表示已加载实例数量
    }))
    
    ElMessage.success('扫描完成')
  }).catch(() => {
    ElMessage.error('扫描失败')
  }).finally(() => {
    scanning.value = false
    scanLoading.value = false
  })
}

/** 加载MCP包 - 允许重复加载创建多个实例 */
function handleLoadPackage(packageInfo) {
  // 如果已经有实例，提示用户输入插件名称
  if (packageInfo.enabled) {
    ElMessageBox.prompt('该插件已有实例，请为新插件起一个名称（如：生产数据库、测试数据库）', '输入插件名称', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPattern: /^.{1,50}$/,
      inputErrorMessage: '插件名称长度应在1-50个字符之间',
      inputPlaceholder: '请输入插件名称'
    }).then(({ value }) => {
      doLoadPackage(packageInfo, value)
    }).catch(() => {
      // 用户取消
    })
  } else {
    // 首次加载，直接创建默认名称
    doLoadPackage(packageInfo, null)
  }
}

/** 执行加载操作 */
function doLoadPackage(packageInfo, pluginName) {
  packageInfo.loading = true
  
  const data = {
    packageName: packageInfo.packageName,
    runtimeType: packageInfo.runtimeType,
    version: packageInfo.version,
    pluginName: pluginName, // 传递插件名称
    envVars: null,
    requireApproval: '0'
  }
  
  enableMcpPackage(data).then(() => {
    ElMessage.success(pluginName ? `插件 "${pluginName}" 已创建` : '已加载到插件列表，请在列表中配置并启用')
    packageInfo.enabled = true
    emit('loaded')
  }).catch(() => {
    ElMessage.error('加载失败')
  }).finally(() => {
    packageInfo.loading = false
  })
}

/** 加载内置插件 - 允许重复加载创建多个实例 */
function handleLoadBuiltinPlugin(pluginInfo) {
  // 如果已经有实例，提示用户输入插件名称
  if (pluginInfo.instanceCount > 0) {
    ElMessageBox.prompt(`该插件已有 ${pluginInfo.instanceCount} 个实例，请为新插件起一个名称（如：生产环境、测试环境）`, '输入插件名称', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPattern: /^.{1,50}$/,
      inputErrorMessage: '插件名称长度应在1-50个字符之间',
      inputPlaceholder: '请输入插件名称'
    }).then(({ value }) => {
      doLoadBuiltinPlugin(pluginInfo, value)
    }).catch(() => {
      // 用户取消
    })
  } else {
    // 首次加载，直接创建
    doLoadBuiltinPlugin(pluginInfo, null)
  }
}

/** 执行内置插件加载操作 */
function doLoadBuiltinPlugin(pluginInfo, pluginName) {
  pluginInfo.loading = true
  
  const data = {
    builtinClass: pluginInfo.builtinClass,
    pluginName: pluginName // 传递插件名称
  }
  
  loadBuiltinPlugin(data).then(() => {
    ElMessage.success(pluginName ? `插件 "${pluginName}" 已创建` : '已加载到插件列表，请在列表中配置并启用')
    pluginInfo.instanceCount = (pluginInfo.instanceCount || 0) + 1 // 实例数量+1
    emit('loaded')
  }).catch(() => {
    ElMessage.error('加载失败')
  }).finally(() => {
    pluginInfo.loading = false
  })
}

/** 打开对话框时自动扫描 */
function handleOpen() {
  if (pythonPackages.value.length === 0 && nodePackages.value.length === 0 && builtinPlugins.value.length === 0) {
    handleScan()
  }
}

/** 关闭对话框 */
function handleClose() {
  emit('update:visible', false)
}
</script>

<style lang="scss" scoped>
.mcp-loader {
  .scan-tabs {
    margin-top: 16px;
  }
}
</style>
