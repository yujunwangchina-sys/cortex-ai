<template>
  <div class="chat-input-box">
    <!-- 压缩提示 -->
    <CompressingAnimation :state="compressing?.state || 'idle'" :data="compressing?.data" />
    
    <!-- 文件上传进度 -->
    <FileUploadProgress :files="uploadingFiles" />
    
    <!-- 排队提示 -->
    <transition name="slide-down">
      <div v-if="queuedMessages.length > 0" class="queued-messages">
        <div v-for="(msg, index) in queuedMessages" :key="index" class="queued-item">
          <div class="queued-content">
            <el-icon class="queue-icon"><Clock /></el-icon>
            <span class="queue-text">排队中：{{ truncateText(msg, 50) }}</span>
          </div>
          <div class="queued-actions">
            <el-button 
              link 
              size="small" 
              @click="handleGuideMessage(index)"
              class="guide-btn"
            >
              <el-icon><Position /></el-icon>
              引导
            </el-button>
            <el-button 
              link 
              size="small" 
              @click="handleCancelQueue(index)"
              class="cancel-btn"
            >
              <el-icon><Close /></el-icon>
            </el-button>
          </div>
        </div>
      </div>
    </transition>

    <div class="input-container">
      <!-- File attachments - 移到输入框上方 -->
      <div v-if="attachedFiles.length > 0" class="attached-files">
        <div v-for="file in attachedFiles" :key="file.fileId" class="file-chip" :class="{ uploading: file.uploading, error: file.error }">
          <!-- 上传中：显示旋转圈 -->
          <el-icon v-if="file.uploading" class="file-chip-icon loading-icon"><Loading /></el-icon>
          <!-- 上传成功：显示对号（短暂显示） -->
          <el-icon v-else-if="file.success" class="file-chip-icon success-icon"><CircleCheck /></el-icon>
          <!-- 上传失败：显示叉号 -->
          <el-icon v-else-if="file.error" class="file-chip-icon error-icon"><CircleClose /></el-icon>
          <!-- 正常状态：显示文档图标 -->
          <el-icon v-else class="file-chip-icon"><Document /></el-icon>
          
          <span class="file-chip-name" :title="file.fileName">{{ file.fileName }}</span>
          
          <!-- 上传中显示进度 -->
          <span v-if="file.uploading" class="file-chip-progress">{{ file.progress }}%</span>
          
          <!-- 只有在非上传状态下才显示删除按钮 -->
          <el-icon v-if="!file.uploading" class="file-chip-remove" @click="handleRemoveFile(file.fileId)"><Close /></el-icon>
        </div>
      </div>
      
      <el-input
        v-model="inputText"
        type="textarea"
        :rows="2"
        placeholder="随心输入（支持粘贴图片和长文本）"
        @keydown.enter.exact.prevent="handleSend"
        @paste="handlePaste"
        :disabled="false"
        class="chat-input"
      />
      <div class="input-toolbar">
        <div class="toolbar-left">
          <el-upload
            multiple
            :show-file-list="false"
            :before-upload="handleFileUpload"
            accept=".doc,.docx,.xls,.xlsx,.ppt,.pptx,.pdf,.txt,.md,.csv,.json,.jpg,.jpeg,.png,.gif,.webp,.bmp"
          >
            <el-button link class="toolbar-btn">
              <el-icon><FolderOpened /></el-icon>
            </el-button>
          </el-upload>

          <!-- Voice recording button -->
          <el-button link class='toolbar-btn' :class='{ recording: isRecording, transcribing: transcribing }' @click='toggleRecording' :disabled='transcribing'>
            <el-icon><Microphone /></el-icon>
          </el-button>
          <span v-if='isRecording' class='recording-timer'>{{ formatTime(recordingTime) }}</span>
          <span v-if='transcribing' class='recording-timer'>转写中…</span>
          
          <el-dropdown trigger="click" @command="handleApprovalChange">
            <el-button link class="toolbar-btn approval-btn">
              <el-icon><Lock /></el-icon>
              <span class="btn-text">{{ approvalLabel }}</span>
              <el-icon class="arrow-icon"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="always">请求批准</el-dropdown-item>
                <el-dropdown-item command="full">完全访问</el-dropdown-item>
                <el-dropdown-item command="auto">自动执行</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
        
        <div class="toolbar-right">
          <!-- Token 统计 -->
          <ContextProgress
            :used-tokens="contextUsage.usedTokens"
            :max-tokens="contextUsage.maxTokens"
            :percentage="contextUsage.percentage"
            :visible="contextUsage.visible"
          />
          
          <!-- 模型选择器 - 按供应商分组 -->
          <el-dropdown trigger="hover" placement="top" @command="handleModelChange">
            <el-button link class="toolbar-btn model-btn">
              <el-icon><Setting /></el-icon>
              <span class="btn-text">{{ supplierLabel }}</span>
              <el-icon class="arrow-icon"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu class="model-dropdown-menu">
                <template v-for="supplier in modelsBySupplier" :key="supplier.supplierName">
                  <div class="supplier-group">
                    <div class="supplier-header">{{ supplier.supplierName }}</div>
                    <el-dropdown-item
                      v-for="m in supplier.models"
                      :key="m.modelId"
                      :command="m.modelId"
                      :class="{ 'is-active': m.modelId === selectedModelId }"
                      class="model-item"
                    >
                      <el-icon v-if="m.modelId === selectedModelId" class="check-icon">
                        <CircleCheck />
                      </el-icon>
                      <span class="model-name">{{ m.modelName }}</span>
                    </el-dropdown-item>
                  </div>
                </template>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          
          <!-- 发送按钮 - 始终显示，streaming 时点击会自动打断 -->
          <el-button
            class="send-btn"
            :class="{ 'interrupt-mode': streaming }"
            :disabled="!inputText.trim() && !streaming"
            @click="handleSend"
            :title="streaming ? '发送（将自动打断当前回复）' : '发送'"
            circle
          >
            <el-icon><Top /></el-icon>
          </el-button>
          
          <!-- 停止按钮 - 仅在 streaming 时显示在发送按钮旁边 -->
          <el-button
            v-if="streaming"
            class="stop-btn-inline"
            @click="handleStop"
            title="停止生成"
            circle
          >
            <el-icon><VideoPause /></el-icon>
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { FolderOpened, Lock, ArrowDown, Top, VideoPause, Clock, Position, Close, Microphone, Document, Setting, Loading, CircleCheck, CircleClose } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getToken } from '@/utils/auth'
import ContextProgress from './ContextProgress.vue'
import CompressingAnimation from './CompressingAnimation.vue'
import FileUploadProgress from './FileUploadProgress.vue'

const props = defineProps({
  attachedFiles: {
    type: Array,
    default: () => []
  },
  streaming: {
    type: Boolean,
    default: false
  },
  approvalMode: {
    type: String,
    default: 'always'
  },
  selectedModelId: {
    type: Number,
    default: null
  },
  models: {
    type: Array,
    default: () => []
  },
  queuedMessages: {
    type: Array,
    default: () => []
  },
  contextUsage: {
    type: Object,
    default: () => ({
      usedTokens: 0,
      maxTokens: 183616,  // 默认值，实际会从后端更新
      percentage: 0,
      visible: true
    })
  },
  compressing: {
    type: Object,
    default: () => ({ state: 'idle', data: null })
  },
  uploadingFiles: {
    type: Array,
    default: () => []
  },
  attachedFiles: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits([
  'send', 
  'stop', 
  'guide-message', 
  'cancel-queue',
  'update:approvalMode', 
  'update:selectedModelId', 
  'file-upload',
  'remove-file'
])

const inputText = ref('')

const approvalLabel = computed(() => {
  const labels = {
    always: '请求批准',
    full: '完全访问',
    auto: '自动执行'
  }
  return labels[props.approvalMode] || '请求批准'
})

const modelLabel = computed(() => {
  const model = props.models.find(m => m.modelId === props.selectedModelId)
  return model ? model.modelName : '选择模型'
})

// 当前选中模型的供应商名称
const supplierLabel = computed(() => {
  const model = props.models.find(m => m.modelId === props.selectedModelId)
  return model && model.supplierName ? model.supplierName : '选择供应商'
})

// 按供应商分组的模型列表
const modelsBySupplier = computed(() => {
  const groups = {}
  
  props.models.forEach(model => {
    const supplierName = model.supplierName || '未知供应商'
    if (!groups[supplierName]) {
      groups[supplierName] = {
        supplierName,
        models: []
      }
    }
    groups[supplierName].models.push(model)
  })
  
  // 转换为数组并排序
  return Object.values(groups).sort((a, b) => {
    // 按供应商名称排序
    return a.supplierName.localeCompare(b.supplierName, 'zh-CN')
  })
})

function handleSend() {
  const text = inputText.value.trim()
  if (text) {
    if (props.disabled) {
      // 如果正在处理，加入队列
      emit('send', text, true) // true表示加入队列
      inputText.value = ''
      ElMessage.info('消息已加入队列，当前回复完成后自动发送')
    } else {
      emit('send', text, false)
      inputText.value = ''
    }
  }
}

function handleStop() {
  emit('stop')
}

function handleGuideMessage(index) {
  emit('guide-message', index)
}

function handleCancelQueue(index) {
  emit('cancel-queue', index)
}

function handleApprovalChange(mode) {
  emit('update:approvalMode', mode)
}

function handleModelChange(modelId) {
  const oldModelId = props.selectedModelId
  const oldModel = props.models.find(m => m.modelId === oldModelId)
  const newModel = props.models.find(m => m.modelId === modelId)
  
  emit('update:selectedModelId', modelId)
  
  // 输出切换日志
  if (oldModel && newModel) {
    console.log('🔄 用户切换模型:', oldModel.modelName, '→', newModel.modelName)
    ElMessage.success(`已切换到 ${newModel.modelName}`)
  }
}

function handleFileUpload(file) {
  emit('file-upload', file)
  return false // 阻止自动上传
}

function handleRemoveFile(fileId) {
  emit('remove-file', fileId)
}

function truncateText(text, maxLength) {
  if (text.length <= maxLength) return text
  return text.substring(0, maxLength) + '...'
}

// Voice recording: record -> stop -> transcribe -> insert text into input box
const isRecording = ref(false)
const transcribing = ref(false)
const recordingTime = ref(0)
let mediaRecorder = null
let audioChunks = []
let recordingTimer = null

async function toggleRecording() {
  if (transcribing.value) return
  if (isRecording.value) { stopRecording() } else { await startRecording() }
}

async function startRecording() {
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    mediaRecorder = new MediaRecorder(stream)
    audioChunks = []
    mediaRecorder.ondataavailable = (e) => { if (e.data.size > 0) audioChunks.push(e.data) }
    mediaRecorder.onstop = async () => {
      const blob = new Blob(audioChunks, { type: 'audio/webm' })
      stream.getTracks().forEach(t => t.stop())
      await transcribeAndInsert(blob)
    }
    mediaRecorder.start()
    isRecording.value = true
    recordingTime.value = 0
    recordingTimer = setInterval(() => { recordingTime.value++ }, 1000)
  } catch(e) { ElMessage.error('麦克风访问失败') }
}

function stopRecording() {
  if (mediaRecorder && isRecording.value) {
    mediaRecorder.stop()
    isRecording.value = false
    clearInterval(recordingTimer)
  }
}

// Convert recorded blob (webm/opus) to 16kHz mono WAV for sherpa-onnx
async function blobToWav(blob) {
  const arrayBuffer = await blob.arrayBuffer()
  const audioCtx = new (window.AudioContext || window.webkitAudioContext)({ sampleRate: 16000 })
  const audioBuffer = await audioCtx.decodeAudioData(arrayBuffer)
  const numFrames = audioBuffer.length
  const channels = audioBuffer.numberOfChannels
  const sampleRate = audioBuffer.sampleRate
  const mono = new Float32Array(numFrames)
  for (let ch = 0; ch < channels; ch++) {
    const data = audioBuffer.getChannelData(ch)
    for (let i = 0; i < numFrames; i++) {
      mono[i] += data[i] / channels
    }
  }
  const buffer = new ArrayBuffer(44 + numFrames * 2)
  const view = new DataView(buffer)
  const writeString = (offset, str) => { for (let i = 0; i < str.length; i++) view.setUint8(offset + i, str.charCodeAt(i)) }
  writeString(0, 'RIFF')
  view.setUint32(4, 36 + numFrames * 2, true)
  writeString(8, 'WAVE')
  writeString(12, 'fmt ')
  view.setUint32(16, 16, true)
  view.setUint16(20, 1, true)
  view.setUint16(22, 1, true)
  view.setUint32(24, sampleRate, true)
  view.setUint32(28, sampleRate * 2, true)
  view.setUint16(32, 2, true)
  view.setUint16(34, 16, true)
  writeString(36, 'data')
  view.setUint32(40, numFrames * 2, true)
  let offset = 44
  for (let i = 0; i < numFrames; i++) {
    let s = Math.max(-1, Math.min(1, mono[i]))
    view.setInt16(offset, s < 0 ? s * 0x8000 : s * 0x7FFF, true)
    offset += 2
  }
  audioCtx.close()
  return new Blob([view], { type: 'audio/wav' })
}

// Record-then-transcribe: convert to wav, call backend, insert text into input box
async function transcribeAndInsert(blob) {
  transcribing.value = true
  try {
    const wavBlob = await blobToWav(blob)
    const formData = new FormData()
    formData.append('file', wavBlob, 'voice.wav')
    const result = await new Promise((resolve, reject) => {
      const xhr = new XMLHttpRequest()
      xhr.addEventListener('load', () => {
        try {
          const res = JSON.parse(xhr.responseText)
          if (xhr.status === 200 && res.code === 200) {
            resolve(res.data)
          } else {
            reject(new Error(res.msg || '语音转写失败'))
          }
        } catch (e) {
          reject(new Error('解析响应失败'))
        }
      })
      xhr.addEventListener('error', () => reject(new Error('网络错误')))
      xhr.open('POST', import.meta.env.VITE_APP_BASE_API + '/agent/api/file/transcribe')
      xhr.setRequestHeader('Authorization', 'Bearer ' + getToken())
      xhr.send(formData)
    })
    const text = result && result.text
    if (text) {
      inputText.value = inputText.value.trim()
      inputText.value = inputText.value ? inputText.value + ' ' + text : text
      ElMessage.success('语音已转文字')
    } else {
      ElMessage.warning('语音转写结果为空')
    }
  } catch (e) {
    ElMessage.error(e.message || '语音转写失败')
  } finally {
    transcribing.value = false
  }
}

function formatTime(s) {
  const m = Math.floor(s / 60)
  const sec = s % 60
  return m + ':' + String(sec).padStart(2, '0')
}

// 粘贴处理：支持图片和长文本自动转文件
async function handlePaste(event) {
  const items = event.clipboardData?.items
  if (!items) return
  
  // 1. 检查是否粘贴了图片
  for (let item of items) {
    if (item.type.indexOf('image') !== -1) {
      event.preventDefault() // 阻止默认粘贴行为
      
      const file = item.getAsFile()
      if (file) {
        // 移除成功提示，静默上传
        // ElMessage.info('检测到图片，正在上传...')
        
        // 生成文件名
        const ext = file.type.split('/')[1] || 'png'
        const fileName = `粘贴图片_${new Date().getTime()}.${ext}`
        
        // 创建新的File对象（带有正确的文件名）
        const namedFile = new File([file], fileName, { type: file.type })
        
        // 触发文件上传
        emit('file-upload', namedFile)
      }
      return // 处理完图片后直接返回
    }
  }
  
  // 2. 检查是否粘贴了长文本（超过1000字符）
  setTimeout(() => {
    const text = inputText.value
    const LONG_TEXT_THRESHOLD = 1000 // 超过1000字符视为长文本
    
    if (text.length > LONG_TEXT_THRESHOLD) {
      // 直接转为文件，不询问
      const blob = new Blob([text], { type: 'text/plain;charset=utf-8' })
      const fileName = `长文本_${new Date().toISOString().slice(0, 19).replace(/:/g, '-')}.txt`
      const file = new File([blob], fileName, { type: 'text/plain' })
      
      // 清空输入框
      inputText.value = ''
      
      // 触发文件上传
      emit('file-upload', file)
      
      // 移除成功提示
      // ElMessage.success(`已将 ${text.length} 字符的长文本转为txt文件`)
    }
  }, 100) // 延迟100ms以确保text已更新到inputText
}
</script>

<style scoped>
.chat-input-box {
  padding: 10px 0 14px;
  border-top: 1px solid #f0f0f0;
  background: #ffffff;
  width: 100%;
}

/* 排队消息提示 */
.queued-messages {
  max-width: 1200px;
  width: 100%;
  margin: 0 auto 12px;
  padding: 0 10px;
}

.queued-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  background: linear-gradient(135deg, #fff9e6 0%, #fff3d6 100%);
  border: 1px solid #ffe6a0;
  border-radius: 8px;
  margin-bottom: 6px;
  animation: slideIn 0.3s ease-out;
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.queued-content {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.queue-icon {
  color: #e6a23c;
  font-size: 16px;
  flex-shrink: 0;
}

.queue-text {
  color: #666;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.queued-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.guide-btn {
  color: #409eff;
  font-size: 13px;
  padding: 4px 8px;
}

.guide-btn:hover {
  background: rgba(64, 158, 255, 0.1);
  border-radius: 4px;
}

.cancel-btn {
  color: #909399;
  padding: 4px 6px;
}

.cancel-btn:hover {
  color: #f56c6c;
  background: rgba(245, 108, 108, 0.1);
  border-radius: 4px;
}

/* 过渡动画 */
.slide-down-enter-active,
.slide-down-leave-active {
  transition: all 0.3s ease;
}

.slide-down-enter-from {
  opacity: 0;
  transform: translateY(-10px);
}

.slide-down-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}

.input-container {
  max-width: 1200px;
  width: 100%;
  margin: 0 auto;
  background: #ffffff;
  border: 1px solid #e0e0e0;
  border-radius: 16px;
  padding: 6px 10px;
  transition: all 0.3s;
}

.input-container:hover {
  border-color: #c0c0c0;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.input-container:focus-within {
  border-color: #a0a0a0;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.chat-input :deep(.el-textarea__inner) {
  border: none;
  background: transparent;
  padding: 6px 4px;
  resize: none;
  font-size: 15px;
  line-height: 1.5;
  color: #1a1a1a;
  box-shadow: none;
  min-height: 48px !important;
  max-height: 48px !important;
  overflow-y: auto;
}

.chat-input :deep(.el-textarea__inner):focus {
  box-shadow: none;
}

.chat-input :deep(.el-textarea__inner)::placeholder {
  color: #b0b0b0;
}

.input-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 2px;
  padding-top: 6px;
  border-top: 1px solid #f5f5f5;
  min-height: 32px;
}

.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 2px;
}

.toolbar-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  font-size: 13px;
  color: #666;
  transition: all 0.2s;
  height: 28px;
}

.toolbar-btn:hover {
  color: #333;
  background: #f5f5f5;
  border-radius: 6px;
}

.btn-text {
  font-size: 13px;
}

.arrow-icon {
  font-size: 11px;
  margin-left: 2px;
}

.send-btn {
  width: 30px;
  height: 30px;
  background: var(--haier-blue, #006BB7);
  border: none;
  color: #fff;
  transition: all 0.2s;
  margin-left: 6px;
}

/* 打断模式下的发送按钮 - 橙色提示 */
.send-btn.interrupt-mode {
  background: #ff9800;
  animation: pulse-orange 2s ease-in-out infinite;
}

@keyframes pulse-orange {
  0%, 100% {
    box-shadow: 0 0 0 0 rgba(255, 152, 0, 0.5);
  }
  50% {
    box-shadow: 0 0 0 4px rgba(255, 152, 0, 0);
  }
}

.send-btn.interrupt-mode:hover:not(:disabled) {
  background: #fb8c00;
  transform: scale(1.05);
}

.send-btn:hover:not(:disabled) {
  background: var(--haier-blue-dark, #004C8C);
  transform: scale(1.05);
}

.send-btn:disabled {
  background: #e0e0e0;
  color: #999;
  cursor: not-allowed;
}

.send-btn .el-icon {
  font-size: 16px;
}

/* 内联停止按钮 - 在 streaming 时显示在发送按钮旁边 */
.stop-btn-inline {
  width: 30px;
  height: 30px;
  background: #f56c6c;
  border: none;
  color: #fff;
  transition: all 0.2s;
  margin-left: 6px;
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% {
    box-shadow: 0 0 0 0 rgba(245, 108, 108, 0.7);
  }
  50% {
    box-shadow: 0 0 0 6px rgba(245, 108, 108, 0);
  }
}

.stop-btn-inline:hover {
  background: #f78989;
  transform: scale(1.05);
}

.stop-btn-inline .el-icon {
  font-size: 16px;
}

/* 保留旧的 stop-btn 样式以防兼容性问题 */
.stop-btn {
  width: 30px;
  height: 30px;
  background: #f56c6c;
  border: none;
  border-radius: 6px;
  color: #fff;
  transition: all 0.2s;
  margin-left: 6px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  animation: pulse 1.5s ease-in-out infinite;
}

.stop-btn:hover {
  background: #f78989;
  transform: scale(1.05);
}

.stop-btn .el-icon {
  font-size: 16px;
}
.attached-files {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 8px 4px;
  margin-bottom: 6px;
}

.file-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: #f5f7fa;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  font-size: 13px;
  color: #606266;
  max-width: 250px;
  transition: all 0.2s;
}

.file-chip.uploading {
  background: #ecf5ff;
  border-color: #b3d8ff;
}

.file-chip.error {
  background: #fef0f0;
  border-color: #fbc4c4;
}

.file-chip:hover:not(.uploading) {
  background: #e9ecef;
  border-color: #c0c4cc;
}

.file-chip-icon {
  color: #909399;
  font-size: 16px;
  flex-shrink: 0;
}

.file-chip-icon.loading-icon {
  color: #409eff;
  animation: rotate 1s linear infinite;
}

.file-chip-icon.success-icon {
  color: #67c23a;
  animation: scaleIn 0.3s ease-out;
}

.file-chip-icon.error-icon {
  color: #f56c6c;
}

@keyframes rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

@keyframes scaleIn {
  from {
    transform: scale(0);
    opacity: 0;
  }
  to {
    transform: scale(1);
    opacity: 1;
  }
}

.file-chip-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
}

.file-chip-progress {
  font-size: 11px;
  color: #409eff;
  font-weight: 600;
  flex-shrink: 0;
}

.file-chip-remove {
  cursor: pointer;
  color: #c0c4cc;
  font-size: 14px;
  flex-shrink: 0;
  transition: color 0.2s;
}

.file-chip-remove:hover {
  color: #f56c6c;
}
.toolbar-btn.recording {
  color: #f56c6c !important;
  animation: pulse 1.5s infinite;
}
.recording-timer {
  font-size: 12px;
  color: #f56c6c;
  font-variant-numeric: tabular-nums;
}
@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

/* ========== 供应商分组菜单样式 ========== */
.model-dropdown-menu {
  min-width: 220px;
  max-height: 500px;
  overflow-y: auto;
}

.supplier-group {
  margin-bottom: 8px;
}

.supplier-group:last-child {
  margin-bottom: 0;
}

.supplier-header {
  padding: 8px 16px 4px;
  font-size: 12px;
  font-weight: 600;
  color: #909399;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  border-bottom: 1px solid #f0f0f0;
  background: #fafafa;
}

.model-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px 8px 24px !important;
}

.model-item.is-active {
  background: #ecf5ff;
  color: #409eff;
}

.model-item .check-icon {
  font-size: 16px;
  color: #409eff;
  flex-shrink: 0;
}

.model-item .model-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ========== 移动端适配 ========== */
@media (max-width: 768px) {
  .chat-input-box {
    padding: 8px 0 10px;
  }
  
  .queued-messages {
    padding: 0 8px;
    margin-bottom: 8px;
  }
  
  .queued-item {
    padding: 8px 10px;
    font-size: 12px;
  }
  
  .queue-text {
    font-size: 12px;
  }
  
  .input-container {
    padding: 4px 8px;
    border-radius: 12px;
  }
  
  .chat-input :deep(.el-textarea__inner) {
    font-size: 14px;
    padding: 4px;
    min-height: 40px !important;
    max-height: 40px !important;
  }
  
  .input-toolbar {
    padding-top: 4px;
    min-height: 28px;
  }
  
  .toolbar-left,
  .toolbar-right {
    gap: 0;
  }
  
  .toolbar-btn {
    padding: 4px 6px;
    font-size: 12px;
    height: 26px;
  }
  
  /* 隐藏按钮文字，只显示图标 */
  .toolbar-btn .btn-text {
    display: none;
  }
  
  /* 隐藏下拉箭头 */
  .toolbar-btn .arrow-icon {
    display: none;
  }
  
  /* 审批按钮只显示图标 */
  .approval-btn {
    min-width: auto;
  }
  
  /* 模型按钮只显示图标，使用统一的图标 */
  .model-btn {
    min-width: auto;
  }
  
  .model-btn .el-icon {
    font-size: 14px;
  }
  
  /* 隐藏语音录制时间显示在移动端 */
  .recording-timer {
    font-size: 11px;
  }
  
  .send-btn,
  .stop-btn,
  .stop-btn-inline {
    width: 28px;
    height: 28px;
    margin-left: 4px;
  }
  
  .send-btn .el-icon,
  .stop-btn .el-icon,
  .stop-btn-inline .el-icon {
    font-size: 14px;
  }
  
  /* 附件显示 */
  .attached-files {
    padding: 6px 4px;
    gap: 6px;
    margin-bottom: 4px;
  }
  
  .file-chip {
    padding: 4px 8px;
    font-size: 12px;
    max-width: 180px;
  }
  
  .file-chip-icon {
    font-size: 14px;
  }
}

@media (max-width: 480px) {
  .input-container {
    padding: 4px 6px;
  }
  
  .toolbar-btn {
    padding: 4px;
  }
  
  .send-btn,
  .stop-btn,
  .stop-btn-inline {
    width: 26px;
    height: 26px;
  }
  
  .file-chip {
    max-width: 120px;
    font-size: 11px;
  }
}

</style>
