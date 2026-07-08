<template>
  <div class="app-container">
    <el-card class="box-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span><el-icon><Monitor /></el-icon> 系统信息</span>
          <el-button
            type="primary"
            icon="Refresh"
            @click="refreshEnvironment"
            :loading="loading"
          >刷新检测</el-button>
        </div>
      </template>
      <el-descriptions :column="3" border v-loading="loading">
        <el-descriptions-item label="操作系统">
          {{ systemInfo.osType || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="系统架构">
          {{ systemInfo.osArch || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="系统版本">
          {{ systemInfo.osVersion || '-' }}
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card class="box-card" shadow="never" style="margin-top: 20px;">
      <template #header>
        <div class="card-header">
          <span><el-icon><FolderOpened /></el-icon> 虚拟环境</span>
          <el-button
            type="primary"
            icon="Refresh"
            @click="loadMcpEnvStatus"
            :loading="mcpEnvLoading"
            size="small"
          >刷新状态</el-button>
        </div>
      </template>
      
      <el-descriptions :column="2" border v-loading="mcpEnvLoading">
        <el-descriptions-item label="环境根目录" :span="2">
          <span style="font-family: 'Consolas', monospace; font-size: 12px;">
            {{ mcpEnvStatus.basePath || '-' }}
          </span>
        </el-descriptions-item>
        <el-descriptions-item label="Python虚拟环境">
          <div style="display: flex; align-items: center; gap: 10px;">
            <el-tag v-if="mcpEnvStatus.pythonVenv?.exists" type="success" size="small">
              已创建
            </el-tag>
            <el-tag v-else type="info" size="small">
              未创建
            </el-tag>
            <el-button
              v-if="!mcpEnvStatus.pythonVenv?.exists"
              type="primary"
              size="small"
              :loading="creatingPythonEnv"
              @click="handleCreatePythonEnv"
            >创建Python环境</el-button>
            <span v-if="mcpEnvStatus.pythonVenv?.pythonExecutable" style="color: #67C23A; font-size: 12px;">
              <el-icon><CircleCheck /></el-icon> Python可执行
            </span>
          </div>
        </el-descriptions-item>
        <el-descriptions-item label="Node.js环境">
          <div style="display: flex; align-items: center; gap: 10px;">
            <el-tag v-if="mcpEnvStatus.nodeEnv?.exists" type="success" size="small">
              已创建
            </el-tag>
            <el-tag v-else type="info" size="small">
              未创建
            </el-tag>
            <el-button
              v-if="!mcpEnvStatus.nodeEnv?.exists"
              type="success"
              size="small"
              :loading="creatingNodeEnv"
              @click="handleCreateNodeEnv"
            >创建Node环境</el-button>
            <span v-if="mcpEnvStatus.nodeEnv?.nodeModules" style="color: #67C23A; font-size: 12px;">
              <el-icon><CircleCheck /></el-icon> node_modules存在
            </span>
          </div>
        </el-descriptions-item>
        <el-descriptions-item label="Python环境路径" v-if="mcpEnvStatus.pythonVenv?.path">
          <span style="font-family: 'Consolas', monospace; font-size: 12px; color: #606266;">
            {{ mcpEnvStatus.pythonVenv.path }}
          </span>
        </el-descriptions-item>
        <el-descriptions-item label="Node环境路径" v-if="mcpEnvStatus.nodeEnv?.path">
          <span style="font-family: 'Consolas', monospace; font-size: 12px; color: #606266;">
            {{ mcpEnvStatus.nodeEnv.path }}
          </span>
        </el-descriptions-item>
      </el-descriptions>
      
      <el-alert
        v-if="!mcpEnvStatus.pythonVenv?.exists || !mcpEnvStatus.nodeEnv?.exists"
        type="warning"
        :closable="false"
        style="margin-top: 15px;"
      >
        <template #title>
          <div style="font-size: 14px;">
            <el-icon><Warning /></el-icon> 
            插件需要独立的虚拟环境，请点击上方按钮创建所需环境
          </div>
        </template>
      </el-alert>
    </el-card>

    <el-card class="box-card" shadow="never" style="margin-top: 20px;">
      <template #header>
        <div class="card-header">
          <span><el-icon><Setting /></el-icon> 环境检测</span>
        </div>
      </template>
      
      <el-table 
        :data="runtimeList" 
        v-loading="loading" 
        style="width: 100%"
        border
        stripe
        highlight-current-row
      >
        <el-table-column label="运行时" align="center" width="100" prop="name">
          <template #default="scope">
            <el-tag :type="getTagType(scope.row.name)" size="default">
              {{ scope.row.name.toUpperCase() }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="110">
          <template #default="scope">
            <el-tag v-if="scope.row.available" type="success">
             已安装
            </el-tag>
            <el-tag v-else type="danger">
              未安装
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="版本" align="center" width="500" prop="version">
          <template #default="scope">
            <span v-if="scope.row.available">{{ scope.row.version || '未知版本' }}</span>
            <span v-else style="color: #999;">-</span>
          </template>
        </el-table-column>
        <el-table-column label="安装路径" align="left" show-overflow-tooltip prop="path">
          <template #default="scope">
            <span v-if="scope.row.available && scope.row.path" style="font-family: 'Consolas', monospace; font-size: 12px;">
              {{ scope.row.path }}
            </span>
            <span v-else style="color: #999;">-</span>
          </template>
        </el-table-column>
        <el-table-column label="说明" align="left" show-overflow-tooltip>
          <template #default="scope">
            {{ getRuntimeDescription(scope.row.name) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="120" fixed="right" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button 
              v-if="!scope.row.available" 
              link 
              type="primary" 
              icon="Document" 
              @click="showInstallGuide(scope.row.name)"
            >安装指南</el-button>
            <el-button 
              v-if="!scope.row.available" 
              link 
              type="warning" 
              icon="Setting" 
              @click="showCustomPathDialog(scope.row.name)"
            >自定义路径</el-button>
            <el-button 
              v-if="scope.row.available" 
              link 
              type="success" 
              icon="Setting" 
              @click="showCustomPathDialog(scope.row.name)"
            >配置路径</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 安装指南对话框 -->
    <el-dialog
      v-model="installGuideVisible"
      :title="'安装指南 - ' + currentRuntimeName.toUpperCase()"
      width="700px"
      append-to-body
    >
      <el-alert
        v-if="currentInstallGuide"
        :type="currentInstallGuide.required ? 'error' : 'warning'"
        :closable="false"
        style="margin-bottom: 20px;"
      >
        <template #title>
          <div v-if="currentInstallGuide.required">
            <el-icon><Warning /></el-icon> 
            此运行时为<strong>必需组件</strong>，请优先安装
          </div>
          <div v-else-if="currentInstallGuide.recommended">
            <el-icon><InfoFilled /></el-icon> 
            此运行时为<strong>推荐组件</strong>，可提升使用体验
          </div>
        </template>
      </el-alert>

      <div v-if="currentInstallGuide" class="install-guide-content">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="组件名称">
            {{ currentInstallGuide.name }}
          </el-descriptions-item>
          <el-descriptions-item label="说明">
            {{ currentInstallGuide.description }}
          </el-descriptions-item>
          <el-descriptions-item label="安装方式" v-if="currentInstallGuide.method">
            {{ currentInstallGuide.method }}
          </el-descriptions-item>
          <el-descriptions-item label="官网" v-if="currentInstallGuide.url">
            <el-link :href="currentInstallGuide.url" type="primary" target="_blank">
              {{ currentInstallGuide.url }}
            </el-link>
          </el-descriptions-item>
        </el-descriptions>

        <div v-if="currentInstallGuide.steps" style="margin-top: 20px;">
          <h4 style="margin-bottom: 10px;">
            <el-icon><List /></el-icon> 安装步骤：
          </h4>
          <el-steps direction="vertical" :active="currentInstallGuide.steps.length">
            <el-step
              v-for="(step, index) in currentInstallGuide.steps"
              :key="index"
              :title="'步骤 ' + (index + 1)"
              :description="step"
            />
          </el-steps>
        </div>

        <div v-if="currentInstallGuide.command" style="margin-top: 20px;">
          <h4 style="margin-bottom: 10px;">
            <el-icon><Terminal /></el-icon> 验证命令：
          </h4>
          <el-input
            v-model="currentInstallGuide.command"
            readonly
            style="font-family: monospace;"
          >
            <template #append>
              <el-button
                icon="DocumentCopy"
                @click="copyCommand(currentInstallGuide.command)"
              >复制</el-button>
            </template>
          </el-input>
        </div>

        <div v-if="currentInstallGuide.benefits" style="margin-top: 20px;">
          <h4 style="margin-bottom: 10px;">
            <el-icon><Star /></el-icon> 优势特性：
          </h4>
          <ul class="benefits-list">
            <li v-for="(benefit, index) in currentInstallGuide.benefits" :key="index">
              {{ benefit }}
            </li>
          </ul>
        </div>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="installGuideVisible = false">关 闭</el-button>
          <el-button type="primary" @click="refreshEnvironment">
            <el-icon><Refresh /></el-icon> 重新检测
          </el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 自定义路径配置对话框 -->
    <el-dialog
      v-model="customPathDialogVisible"
      :title="'自定义路径配置 - ' + currentRuntimeName.toUpperCase()"
      width="600px"
      append-to-body
    >
      <el-alert
        type="info"
        :closable="false"
        style="margin-bottom: 20px;"
      >
        <template #title>
          如果系统自动检测不到 {{ currentRuntimeName.toUpperCase() }}，您可以手动指定可执行文件的完整路径
        </template>
      </el-alert>

      <el-form :model="customPathForm" :rules="customPathRules" ref="customPathFormRef" label-width="120px">
        <el-form-item label="启用自定义路径" prop="customPathEnabled">
          <el-switch v-model="customPathForm.customPathEnabled" />
          <span style="margin-left: 10px; color: #909399; font-size: 12px;">
            开启后将使用自定义路径而非系统PATH
          </span>
        </el-form-item>

        <el-form-item 
          label="可执行文件路径" 
          prop="executablePath"
          v-if="customPathForm.customPathEnabled"
        >
          <el-input
            v-model="customPathForm.executablePath"
            placeholder="请输入可执行文件的完整路径"
            clearable
          >
            <template #append>
              <el-button 
                icon="View" 
                @click="handleVerifyPath"
                :loading="verifyingPath"
              >验证</el-button>
            </template>
          </el-input>
          <div style="margin-top: 8px; font-size: 12px; color: #909399;">
            <div>Windows示例: C:\Python310\python.exe 或 C:\Program Files\nodejs\node.exe</div>
            <div>Linux示例: /usr/bin/python3 或 /usr/local/bin/node</div>
            <div>Mac示例: /usr/local/bin/python3 或 /opt/homebrew/bin/node</div>
          </div>
        </el-form-item>

        <el-form-item label="验证结果" v-if="verifyResult && customPathForm.customPathEnabled">
          <el-alert
            :type="verifyResult.success ? 'success' : 'error'"
            :closable="false"
          >
            <template #title>
              <div v-if="verifyResult.success">
                <div>✓ 路径验证成功</div>
                <div v-if="verifyResult.version" style="margin-top: 5px; font-size: 12px;">
                  版本: {{ verifyResult.version }}
                </div>
              </div>
              <div v-else>
                ✗ {{ verifyResult.message }}
              </div>
            </template>
          </el-alert>
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="customPathDialogVisible = false">取 消</el-button>
          <el-button 
            type="primary" 
            @click="handleSaveCustomPath"
            :loading="savingCustomPath"
          >保 存</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="RuntimeEnvironment">
import { detectEnvironment, getMcpEnvStatus, createMcpPythonEnv, createMcpNodeEnv, getRuntimeConfig, saveRuntimeConfig, verifyRuntimePath } from '@/api/plugin/runtime'
import { ElMessage, ElMessageBox } from 'element-plus'

const { proxy } = getCurrentInstance()

const loading = ref(false)
const systemInfo = ref({})
const runtimeList = ref([])
const installGuides = ref({})
const installGuideVisible = ref(false)
const currentRuntimeName = ref('')
const currentInstallGuide = ref(null)

// MCP环境相关状态
const mcpEnvLoading = ref(false)
const mcpEnvStatus = ref({
  basePath: '',
  pythonVenv: { path: '', exists: false, pythonExecutable: false },
  nodeEnv: { path: '', exists: false, nodeModules: false }
})
const creatingPythonEnv = ref(false)
const creatingNodeEnv = ref(false)

// 自定义路径配置相关状态
const customPathDialogVisible = ref(false)
const customPathFormRef = ref(null)
const customPathForm = ref({
  runtimeType: '',
  executablePath: '',
  customPathEnabled: false
})
const customPathRules = ref({
  executablePath: [
    { required: true, message: '请输入可执行文件路径', trigger: 'blur' }
  ]
})
const verifyingPath = ref(false)
const savingCustomPath = ref(false)
const verifyResult = ref(null)

/** 加载MCP环境状态 */
function loadMcpEnvStatus() {
  mcpEnvLoading.value = true
  getMcpEnvStatus().then(response => {
    mcpEnvStatus.value = response.data
    mcpEnvLoading.value = false
  }).catch(() => {
    mcpEnvLoading.value = false
  })
}

/** 创建Python虚拟环境 */
function handleCreatePythonEnv() {
  ElMessageBox.confirm(
    '将在配置目录下创建插件Python虚拟环境，用于安装和运行所有Python插件。创建过程可能需要1-2分钟，是否继续？',
    '确认创建',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'info'
    }
  ).then(() => {
    creatingPythonEnv.value = true
    createMcpPythonEnv().then(response => {
      ElMessage.success(response.msg || 'Python环境创建成功')
      creatingPythonEnv.value = false
      // 刷新状态
      loadMcpEnvStatus()
    }).catch(() => {
      creatingPythonEnv.value = false
    })
  }).catch(() => {
    // 取消操作
  })
}

/** 创建Node.js环境 */
function handleCreateNodeEnv() {
  ElMessageBox.confirm(
    '将在配置目录下创建插件Node.js环境，用于安装和运行所有Node.js插件。创建过程较快，是否继续？',
    '确认创建',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'info'
    }
  ).then(() => {
    creatingNodeEnv.value = true
    createMcpNodeEnv().then(response => {
      ElMessage.success(response.msg || 'Node.js环境创建成功')
      creatingNodeEnv.value = false
      // 刷新状态
      loadMcpEnvStatus()
    }).catch(() => {
      creatingNodeEnv.value = false
    })
  }).catch(() => {
    // 取消操作
  })
}

/** 刷新环境检测 */
function refreshEnvironment() {
  loading.value = true
  detectEnvironment().then(response => {
    systemInfo.value = response.data.system
    installGuides.value = response.data.installGuide || {}
    
    // 转换运行时数据为数组
    const runtimes = response.data.runtimes || {}
    runtimeList.value = [
      { name: 'python', ...runtimes.python },
      { name: 'node', ...runtimes.node },
    ]
    
    loading.value = false
    ElMessage.success('环境检测完成')
  }).catch(() => {
    loading.value = false
  })
}

/** 获取Tag类型 */
function getTagType(name) {
  const typeMap = {
    'python': 'warning',
    'node': 'success',
  }
  return typeMap[name] || 'info'
}

/** 获取运行时描述 */
function getRuntimeDescription(name) {
  const descMap = {
    'python': 'Python运行环境，用于运行Python插件',
    'node': 'Node.js运行环境，用于运行Node.js插件',
  }
  return descMap[name] || ''
}

/** 显示安装指南 */
function showInstallGuide(name) {
  currentRuntimeName.value = name
  currentInstallGuide.value = installGuides.value[name] || null
  
  if (!currentInstallGuide.value) {
    ElMessage.warning('暂无安装指南')
    return
  }
  
  installGuideVisible.value = true
}

/** 复制命令 */
function copyCommand(command) {
  navigator.clipboard.writeText(command).then(() => {
    ElMessage.success('命令已复制到剪贴板')
  }).catch(() => {
    ElMessage.error('复制失败，请手动复制')
  })
}

/** 检查运行时是否可用 */
function isRuntimeAvailable(name) {
  const runtime = runtimeList.value.find(r => r.name === name)
  return runtime && runtime.available
}

/** 显示自定义路径配置对话框 */
function showCustomPathDialog(runtimeType) {
  currentRuntimeName.value = runtimeType
  verifyResult.value = null
  
  // 加载已有配置
  getRuntimeConfig(runtimeType).then(response => {
    if (response.data) {
      customPathForm.value = {
        runtimeType: runtimeType,
        executablePath: response.data.executablePath || '',
        customPathEnabled: response.data.customPathEnabled || false
      }
    } else {
      customPathForm.value = {
        runtimeType: runtimeType,
        executablePath: '',
        customPathEnabled: false
      }
    }
    customPathDialogVisible.value = true
  }).catch(() => {
    customPathForm.value = {
      runtimeType: runtimeType,
      executablePath: '',
      customPathEnabled: false
    }
    customPathDialogVisible.value = true
  })
}

/** 验证路径 */
function handleVerifyPath() {
  if (!customPathForm.value.executablePath || !customPathForm.value.executablePath.trim()) {
    ElMessage.warning('请先输入可执行文件路径')
    return
  }
  
  verifyingPath.value = true
  verifyResult.value = null
  
  verifyRuntimePath({
    executablePath: customPathForm.value.executablePath,
    runtimeType: customPathForm.value.runtimeType
  }).then(response => {
    verifyingPath.value = false
    const data = response.data || {}
    
    if (response.code === 200 && data.available) {
      verifyResult.value = {
        success: true,
        version: data.version,
        message: '路径验证成功'
      }
      ElMessage.success('路径验证成功')
    } else {
      verifyResult.value = {
        success: false,
        message: data.message || response.msg || '路径验证失败'
      }
      ElMessage.error(verifyResult.value.message)
    }
  }).catch(error => {
    verifyingPath.value = false
    verifyResult.value = {
      success: false,
      message: error.msg || '验证请求失败'
    }
  })
}

/** 保存自定义路径配置 */
function handleSaveCustomPath() {
  if (!customPathFormRef.value) return
  
  customPathFormRef.value.validate(valid => {
    if (valid || !customPathForm.value.customPathEnabled) {
      savingCustomPath.value = true
      
      saveRuntimeConfig(customPathForm.value).then(response => {
        savingCustomPath.value = false
        ElMessage.success('配置保存成功')
        customPathDialogVisible.value = false
        
        // 刷新环境检测
        refreshEnvironment()
      }).catch(() => {
        savingCustomPath.value = false
      })
    }
  })
}

// 初始化加载
refreshEnvironment()
loadMcpEnvStatus()
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-header span {
  font-size: 16px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
}

.box-card {
  margin-bottom: 20px;
}

.install-guide-content {
  padding: 10px;
}

.benefits-list {
  list-style: none;
  padding: 0;
  margin: 10px 0;
}

.benefits-list li {
  padding: 8px 0;
  color: #67C23A;
  font-size: 14px;
  border-bottom: 1px dashed #EBEEF5;
}

.benefits-list li:last-child {
  border-bottom: none;
}
</style>
