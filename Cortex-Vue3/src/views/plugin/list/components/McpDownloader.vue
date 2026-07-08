<template>
  <el-dialog
    v-model="dialogVisible"
    title="下载MCP包"
    width="800px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <div class="mcp-downloader">
      <el-alert 
        title="下载MCP包" 
        type="info" 
        :closable="false"
        class="mb20"
      >
        <p>选择运行时和包名，系统将自动下载并安装MCP包</p>
      </el-alert>

      <el-form :model="form" ref="formRef" :rules="rules" label-width="100px">
        <el-form-item label="运行时" prop="runtimeType">
          <el-select v-model="form.runtimeType" placeholder="请选择运行时类型" style="width: 100%;">
            <el-option label="pip (Python)" value="pip" />
            <el-option label="npm (Node.js)" value="npm" />
          </el-select>
        </el-form-item>
        
        <el-form-item label="包名" prop="packageName">
          <el-input 
            v-model="form.packageName" 
            placeholder="如: mcp-server-sqlite 或 @modelcontextprotocol/server-filesystem"
          />
        </el-form-item>

        <el-form-item label="版本" prop="version">
          <el-input 
            v-model="form.version" 
            placeholder="留空安装最新版本，如: 1.0.0"
          />
        </el-form-item>

        <el-alert 
          type="warning" 
          :closable="false"
          style="margin-top: 10px;"
        >
          <template #title>
            <div style="font-size: 12px;">
              下载可能需要几分钟，请耐心等待...
            </div>
          </template>
        </el-alert>
      </el-form>
    </div>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" @click="handleDownload" :loading="downloading">
        <el-icon v-if="!downloading"><Download /></el-icon>
        {{ downloading ? '下载中...' : '开始下载' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { installPythonPackage, installNodePackage } from '@/api/plugin/package'
import { enableMcpPackage } from '@/api/plugin/plugin'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:visible', 'downloaded'])

const dialogVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val)
})

const formRef = ref(null)
const downloading = ref(false)

const form = reactive({
  runtimeType: '',
  packageName: '',
  version: ''
})

const rules = {
  runtimeType: [
    { required: true, message: '请选择运行时类型', trigger: 'change' }
  ],
  packageName: [
    { required: true, message: '请输入包名', trigger: 'blur' }
  ]
}

/** 下载并安装 */
function handleDownload() {
  formRef.value.validate(valid => {
    if (valid) {
      downloading.value = true
      
      const installFunc = form.runtimeType === 'pip' 
        ? installPythonPackage 
        : installNodePackage
      
      const installData = {
        packageName: form.packageName,
        version: form.version,
        useMirror: true
      }
      
      installFunc(installData).then(() => {
        ElMessage.success('下载安装成功，正在加载到插件列表...')
        
        // 下载成功后自动加载到插件列表
        const enableData = {
          packageName: form.packageName,
          runtimeType: form.runtimeType,
          envVars: null,
          requireApproval: '0'
        }
        
        return enableMcpPackage(enableData)
      }).then(() => {
        ElMessage.success('加载成功，可在插件列表中查看和配置')
        emit('downloaded')
        handleClose()
      }).catch((error) => {
        ElMessage.error('操作失败: ' + (error.msg || '未知错误'))
      }).finally(() => {
        downloading.value = false
      })
    }
  })
}

/** 关闭对话框 */
function handleClose() {
  formRef.value?.resetFields()
  emit('update:visible', false)
}
</script>

<style lang="scss" scoped>
.mcp-downloader {
  // 样式
}
</style>
