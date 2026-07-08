<template>
  <div class="app-container">
    <el-tabs v-model="activeTab" type="border-card">
      <!-- Python包管理 -->
      <el-tab-pane label="Python 包管理" name="python">
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button
              type="primary"
              plain
              icon="Plus"
              @click="handleInstall('python')"
              v-hasPermi="['plugin:runtime:detect']"
            >安装包</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button
              type="success"
              plain
              icon="Refresh"
              @click="refreshPythonPackages"
              :loading="pythonLoading"
            >刷新列表</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-input
              v-model="pythonSearchKeyword"
              placeholder="搜索已安装的包..."
              clearable
              style="width: 300px;"
              prefix-icon="Search"
            />
          </el-col>
        </el-row>

        <el-alert
          type="info"
          :closable="false"
          style="margin-bottom: 15px;"
        >
          <template #title>
            <div>
              <el-icon><InfoFilled /></el-icon>
              Python包管理说明：显示MCP Python虚拟环境中安装的包。安装包时默认使用清华镜像源加速下载。
            </div>
          </template>
        </el-alert>

        <el-table
          v-loading="pythonLoading"
          :data="filteredPythonPackages"
          style="width: 100%"
          max-height="500"
          border
          stripe
          highlight-current-row
        >
          <el-table-column label="包名" align="center" prop="name" show-overflow-tooltip />
          <el-table-column label="版本" align="center" prop="version" width="150" />
          <el-table-column label="来源" align="center" prop="source" width="100">
            <template #default="scope">
              <el-tag type="success" size="small">pip</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" align="center" width="120">
            <template #default="scope">
              <el-button
                link
                type="danger"
                icon="Delete"
                @click="handleUninstall('python', scope.row.name)"
                v-hasPermi="['plugin:runtime:detect']"
              >卸载</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-empty v-if="!pythonLoading && pythonPackages.length === 0" description="暂无已安装的Python包" />
      </el-tab-pane>

      <!-- Node.js包管理 -->
      <el-tab-pane label="Node.js 包管理" name="node">
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button
              type="primary"
              plain
              icon="Plus"
              @click="handleInstall('node')"
              v-hasPermi="['plugin:runtime:detect']"
            >安装包</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button
              type="success"
              plain
              icon="Refresh"
              @click="refreshNodePackages"
              :loading="nodeLoading"
            >刷新列表</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-input
              v-model="nodeSearchKeyword"
              placeholder="搜索已安装的包..."
              clearable
              style="width: 300px;"
              prefix-icon="Search"
            />
          </el-col>
        </el-row>

        <el-alert
          type="info"
          :closable="false"
          style="margin-bottom: 15px;"
        >
          <template #title>
            <div>
              <el-icon><InfoFilled /></el-icon>
              Node.js包管理说明：这里显示的是全局已安装的npm包。安装包时默认使用淘宝镜像源加速下载。
            </div>
          </template>
        </el-alert>

        <el-table
          v-loading="nodeLoading"
          :data="filteredNodePackages"
          style="width: 100%"
          max-height="500"
          border
          stripe
          highlight-current-row
        >
          <el-table-column label="包名" align="center" prop="name" show-overflow-tooltip />
          <el-table-column label="版本" align="center" prop="version" width="150" />
          <el-table-column label="来源" align="center" prop="source" width="100">
            <template #default="scope">
              <el-tag type="success" size="small">npm</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" align="center" width="120">
            <template #default="scope">
              <el-button
                link
                type="danger"
                icon="Delete"
                @click="handleUninstall('node', scope.row.name)"
                v-hasPermi="['plugin:runtime:detect']"
              >卸载</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-empty v-if="!nodeLoading && nodePackages.length === 0" description="暂无已安装的Node.js包" />
      </el-tab-pane>
    </el-tabs>

    <!-- 安装包对话框 -->
    <el-dialog
      v-model="installDialogVisible"
      :title="'安装' + (currentPackageType === 'python' ? ' Python' : ' Node.js') + '包'"
      width="500px"
      append-to-body
    >
      <el-form :model="installForm" :rules="installRules" ref="installFormRef" label-width="80px">
        <el-form-item label="包名" prop="packageName">
          <el-input
            v-model="installForm.packageName"
            placeholder="如: requests, openai, @modelcontextprotocol/server-*"
            clearable
          >
          </el-input>
        </el-form-item>
        <el-form-item label="版本" prop="version">
          <el-input
            v-model="installForm.version"
            placeholder="留空安装最新版本"
            clearable
          >
          </el-input>
        </el-form-item>
        <el-form-item label="镜像源" prop="useMirror">
          <el-switch
            v-model="installForm.useMirror"
            active-text="是"
            inactive-text="否"
          />
          <el-tooltip
            content="使用国内镜像源可以加速下载"
            placement="top"
          >
            <el-icon style="margin-left: 5px;"><QuestionFilled /></el-icon>
          </el-tooltip>
        </el-form-item>
        <el-alert
          type="warning"
          :closable="false"
          style="margin-top: 10px;"
        >
          <template #title>
            <div style="font-size: 12px;">
              安装过程可能需要几分钟时间，请耐心等待...
            </div>
          </template>
        </el-alert>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="installDialogVisible = false">取 消</el-button>
          <el-button
            type="primary"
            @click="submitInstall"
            :loading="installing"
          >
            <el-icon v-if="!installing"><Download /></el-icon>
            {{ installing ? '安装中...' : '开始安装' }}
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="PackageManager">
import {
  installPythonPackage,
  uninstallPythonPackage,
  listPythonPackages,
  installNodePackage,
  uninstallNodePackage,
  listNodePackages
} from '@/api/plugin/package'
import { ElMessage, ElMessageBox } from 'element-plus'

const { proxy } = getCurrentInstance()

const activeTab = ref('python')
const pythonLoading = ref(false)
const nodeLoading = ref(false)
const installing = ref(false)
const installDialogVisible = ref(false)
const currentPackageType = ref('python')
const pythonPackages = ref([])
const nodePackages = ref([])
const pythonSearchKeyword = ref('')
const nodeSearchKeyword = ref('')

const installForm = ref({
  packageName: '',
  version: '',
  useMirror: true
})

const installRules = {
  packageName: [
    { required: true, message: '请输入包名', trigger: 'blur' }
  ]
}

// 推荐的MCP包
const recommendedPackages = ref([
  {
    type: 'python',
    runtime: 'pip',
    name: 'mcp-server-sqlite',
    displayName: 'SQLite数据库',
    description: '提供SQLite数据库查询和操作功能，支持SQL查询、表管理等'
  },
  {
    type: 'node',
    runtime: 'npm',
    name: '@modelcontextprotocol/server-filesystem',
    displayName: '文件系统',
    description: '提供文件和目录操作功能，支持读写文件、列出目录等'
  },
  {
    type: 'node',
    runtime: 'npm',
    name: '@modelcontextprotocol/server-git',
    displayName: 'Git版本控制',
    description: '提供Git操作功能，查看提交历史、diff、status等'
  },
  {
    type: 'node',
    runtime: 'npm',
    name: '@modelcontextprotocol/server-brave-search',
    displayName: 'Brave搜索',
    description: '提供网络搜索功能，需要配置Brave API Key'
  },
  {
    type: 'python',
    runtime: 'pip',
    name: 'openai',
    displayName: 'OpenAI SDK',
    description: 'OpenAI官方Python SDK，用于调用OpenAI API'
  },
  {
    type: 'python',
    runtime: 'pip',
    name: 'requests',
    displayName: 'HTTP请求库',
    description: 'Python最流行的HTTP请求库，简单易用'
  }
])

/** 计算属性：过滤后的Python包 */
const filteredPythonPackages = computed(() => {
  if (!pythonSearchKeyword.value) {
    return pythonPackages.value
  }
  return pythonPackages.value.filter(pkg =>
    pkg.name.toLowerCase().includes(pythonSearchKeyword.value.toLowerCase())
  )
})

/** 计算属性：过滤后的Node.js包 */
const filteredNodePackages = computed(() => {
  if (!nodeSearchKeyword.value) {
    return nodePackages.value
  }
  return nodePackages.value.filter(pkg =>
    pkg.name.toLowerCase().includes(nodeSearchKeyword.value.toLowerCase())
  )
})

/** 刷新Python包列表 */
function refreshPythonPackages() {
  pythonLoading.value = true
  listPythonPackages().then(response => {
    try {
      const data = typeof response.msg === 'string' ? JSON.parse(response.msg) : response.msg
      pythonPackages.value = Array.isArray(data) ? data : []
      ElMessage.success('刷新成功')
    } catch (e) {
      pythonPackages.value = []
      ElMessage.warning('解析包列表失败')
    }
    pythonLoading.value = false
  }).catch(() => {
    pythonLoading.value = false
  })
}

/** 刷新Node.js包列表 */
function refreshNodePackages() {
  nodeLoading.value = true
  listNodePackages().then(response => {
    try {
      const data = typeof response.msg === 'string' ? JSON.parse(response.msg) : response.msg
      if (data && data.dependencies) {
        nodePackages.value = Object.keys(data.dependencies).map(name => {
          const pkgData = data.dependencies[name]
          return {
            name: name,
            version: pkgData.version || pkgData,
            source: pkgData.source || 'npm'
          }
        })
      } else {
        nodePackages.value = []
      }
      ElMessage.success('刷新成功')
    } catch (e) {
      nodePackages.value = []
      ElMessage.warning('解析包列表失败')
    }
    nodeLoading.value = false
  }).catch(() => {
    nodeLoading.value = false
  })
}

/** 打开安装对话框 */
function handleInstall(type) {
  currentPackageType.value = type
  installForm.value = {
    packageName: '',
    version: '',
    useMirror: true
  }
  installDialogVisible.value = true
}

/** 提交安装 */
function submitInstall() {
  proxy.$refs.installFormRef.validate(valid => {
    if (valid) {
      installing.value = true
      
      const installFunc = currentPackageType.value === 'python'
        ? installPythonPackage
        : installNodePackage
      
      installFunc(installForm.value).then(response => {
        ElMessage.success('安装成功')
        installDialogVisible.value = false
        installing.value = false
        
        // 刷新列表
        if (currentPackageType.value === 'python') {
          refreshPythonPackages()
        } else {
          refreshNodePackages()
        }
      }).catch(() => {
        installing.value = false
      })
    }
  })
}

/** 卸载包 */
function handleUninstall(type, packageName) {
  ElMessageBox.confirm(
    `确认要卸载"${packageName}"包吗？`,
    '提示',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(() => {
    const uninstallFunc = type === 'python'
      ? uninstallPythonPackage
      : uninstallNodePackage
    
    uninstallFunc(packageName).then(response => {
      ElMessage.success('卸载成功')
      // 刷新列表
      if (type === 'python') {
        refreshPythonPackages()
      } else {
        refreshNodePackages()
      }
    })
  }).catch(() => {})
}

// 监听tab切换，自动加载数据
watch(activeTab, (newVal) => {
  if (newVal === 'python' && pythonPackages.value.length === 0) {
    refreshPythonPackages()
  } else if (newVal === 'node' && nodePackages.value.length === 0) {
    refreshNodePackages()
  }
})

// 初始化加载Python包列表
refreshPythonPackages()
</script>

<style scoped>
/* 无需额外样式 */
</style>
