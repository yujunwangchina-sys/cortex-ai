<template>
  <el-dialog
    v-model="dialogVisible"
    :title="`编辑系统提示词 - ${agentName}`"
    fullscreen
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <!-- 工具栏 -->
    <div class="editor-toolbar">
      <div class="toolbar-info">
        <el-icon :size="20" color="#1890ff">
          <Memo />
        </el-icon>
        <span class="agent-name">{{ agentName }}</span>
        <el-tag v-if="isModified" type="warning" size="small" effect="dark">
          未保存
        </el-tag>
      </div>
      <div class="toolbar-actions">
        <el-button
          type="success"
          size="default"
          :icon="saving ? '' : 'CircleCheck'"
          :loading="saving"
          :disabled="!isModified"
          @click="handleSave"
        >
          {{ saving ? '保存中' : '保存' }}
        </el-button>
        <el-button
          size="default"
          :icon="CloseBold"
          @click="handleClose"
        >
          关闭
        </el-button>
      </div>
    </div>

    <!-- 编辑器 -->
    <div class="editor-content">
      <MarkdownEditor
        v-model="systemPrompt"
        placeholder="请输入系统级提示词，支持Markdown格式..."
        @change="handleContentChange"
        @save="handleSave"
      />
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAgent, updateAgent } from '@/api/agent/agent'
import MarkdownEditor from '@/views/skill/components/SimpleMarkdownEditor.vue'
import { Memo, CircleCheck, CloseBold } from '@element-plus/icons-vue'

const props = defineProps({
  visible: Boolean,
  agentId: [Number, String]
})

const emit = defineEmits(['update:visible', 'success'])

const dialogVisible = ref(false)
const loading = ref(false)
const saving = ref(false)
const agentName = ref('')
const systemPrompt = ref('')
const originalPrompt = ref('')
const isModified = ref(false)
const agentData = ref(null)

watch(() => props.visible, (val) => {
  dialogVisible.value = val
  if (val && props.agentId) {
    loadAgentData()
  }
})

watch(dialogVisible, (val) => {
  if (!val) {
    emit('update:visible', false)
  }
})

/** 加载Agent数据 */
function loadAgentData() {
  loading.value = true
  getAgent(props.agentId).then(response => {
    agentData.value = response.data
    agentName.value = response.data.agentName || ''
    systemPrompt.value = response.data.systemPrompt || ''
    originalPrompt.value = systemPrompt.value
    isModified.value = false
    loading.value = false
  }).catch(() => {
    ElMessage.error('加载Agent数据失败')
    loading.value = false
  })
}

/** 内容变化 */
function handleContentChange() {
  isModified.value = systemPrompt.value !== originalPrompt.value
}

/** 保存 */
function handleSave() {
  if (!agentData.value) return
  
  saving.value = true
  const data = {
    ...agentData.value,
    systemPrompt: systemPrompt.value
  }
  
  updateAgent(data).then(() => {
    ElMessage.success('保存成功')
    originalPrompt.value = systemPrompt.value
    isModified.value = false
    emit('success')
  }).finally(() => {
    saving.value = false
  })
}

/** 关闭对话框 */
function handleClose() {
  if (isModified.value) {
    ElMessageBox.confirm(
      '系统提示词已修改，是否保存？',
      '提示',
      {
        confirmButtonText: '保存',
        cancelButtonText: '不保存',
        distinguishCancelAndClose: true,
        type: 'warning'
      }
    ).then(() => {
      // 保存后关闭
      handleSave()
      dialogVisible.value = false
    }).catch(action => {
      if (action === 'cancel') {
        // 不保存直接关闭
        dialogVisible.value = false
      }
      // 点击右上角关闭不做任何操作
    })
  } else {
    dialogVisible.value = false
  }
}
</script>

<style lang="scss" scoped>
:deep(.el-dialog__header) {
  padding: 0;
  margin: 0;
}

:deep(.el-dialog__body) {
  padding: 0;
  height: calc(100vh - 100px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.editor-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  border-bottom: 1px solid #e4e7ed;
  background: white;
  flex-shrink: 0;
  height: 56px;
  
  .toolbar-info {
    display: flex;
    align-items: center;
    gap: 10px;
    
    .agent-name {
      font-size: 15px;
      font-weight: 500;
      color: #303133;
    }
  }
  
  .toolbar-actions {
    display: flex;
    gap: 10px;
  }
}

.editor-content {
  flex: 1;
  overflow: hidden;
  background: #f5f7fa;
  min-height: 0;
  height: calc(100vh - 156px);
  
  :deep(.markdown-editor-wrapper) {
    height: 100%;
    
    .md-editor {
      height: 100% !important;
    }
  }
}
</style>
