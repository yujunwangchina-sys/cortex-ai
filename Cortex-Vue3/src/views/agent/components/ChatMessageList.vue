<template>
  <div class="message-list-container" ref="containerRef">
    <div class="message-list-inner">
      <!-- 消息列表 -->
      <template v-for="(msg, idx) in messages" :key="'msg-' + idx">
        <!-- 压缩分割线 -->
        <div v-if="msg.role === 'compression_divider'" class="compression-divider">
          <span class="divider-line"></span>
          <span class="divider-text">{{ msg.content }}</span>
          <span class="divider-line"></span>
        </div>
        <!-- 普通消息 -->
        <ChatMessageItem
          v-else
          :message="msg"
          :user-login-name="userLoginName"
          :agent-name="agentName"
          :agent-avatar="agentAvatar"
        />
      </template>
      <!-- 流式输出中 -->
      <div v-if="streaming" class="msg-row assistant streaming">
        <div class="msg-avatar">
          <el-avatar v-if="agentAvatar" :size="36" :src="getAvatarUrl(agentAvatar)" />
          <el-icon v-else><ChatDotRound /></el-icon>
        </div>
        <div class="msg-bubble">
          <div class="msg-role">
            <span>{{ agentName }}</span>
            <span class="elapsed-time">{{ formatElapsed(elapsedTime) }}</span>
          </div>
          
          <!-- 工具调用过程 -->
          <el-collapse 
            v-if="streamToolCalls && streamToolCalls.length > 0" 
            v-model="activeToolCollapse"
            class="tool-calls-collapse"
          >
            <el-collapse-item name="toolCalls">
              <template #title>
                <div class="collapse-header-wrapper">
                  <span class="collapse-title">🔧 执行过程</span>
                  <span class="tool-count-badge">{{ streamToolCalls.length }}</span>
                </div>
              </template>
              
              <!-- 完整工具列表（倒序显示，新工具在前） -->
              <div class="tool-calls-list">
                <div 
                  v-for="(call, idx) in reversedToolCalls" 
                  :key="idx" 
                  class="tool-call-card"
                  :class="{ 'tool-call-error': call.status === 'error' }"
                >
                  <div class="tool-call-header">
                    <div class="tool-name-row">
                      <span class="tool-emoji">🔧</span>
                      <span class="tool-full-name">{{ call.toolName }}</span>
                      <el-tag 
                        :type="getStatusType(call.status)" 
                        size="small" 
                        class="tool-status-tag"
                      >
                        {{ getStatusText(call.status) }}
                      </el-tag>
                    </div>
                    <div v-if="call.pluginName" class="tool-plugin-name">
                      来自: {{ call.pluginName }}
                    </div>
                  </div>
                  
                  <div v-if="call.result" class="tool-call-result">
                    <div class="result-label">执行结果:</div>
                    <div class="result-content">{{ truncateResult(call.result) }}</div>
                  </div>
                </div>
              </div>
            </el-collapse-item>
          </el-collapse>
          
          <div class="msg-text markdown-content">
            <template v-if="streamContent">
              <span v-html="renderStreamMarkdown"></span>
            </template>
            <template v-else>
              <span class="typing-dots">
                <span></span><span></span><span></span>
              </span>
            </template>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick, onUnmounted } from 'vue'
import { ChatDotRound } from '@element-plus/icons-vue'
import ChatMessageItem from './ChatMessageItem.vue'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'

const md = new MarkdownIt({
  html: true,
  linkify: true,
  typographer: true,
  highlight: function (str, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return '<pre class="hljs"><code>' +
               hljs.highlight(str, { language: lang, ignoreIllegals: true }).value +
               '</code></pre>'
      } catch (__) {}
    }
    return '<pre class="hljs"><code>' + md.utils.escapeHtml(str) + '</code></pre>'
  }
})

const props = defineProps({
  messages: {
    type: Array,
    required: true
  },
  streaming: {
    type: Boolean,
    default: false
  },
  streamContent: {
    type: String,
    default: ''
  },
  streamToolCalls: {
    type: Array,
    default: () => []
  },
  userLoginName: {
    type: String,
    default: 'admin'
  },
  agentName: {
    type: String,
    default: 'AI 助手'
  },
  agentAvatar: {
    type: String,
    default: ''
  }
})

const containerRef = ref(null)
const elapsedTime = ref(0)
const activeToolCollapse = ref([]) // 默认折叠执行过程
let timer = null
let protectionTimer = null // 保护性定时器

// 计算属性：倒序工具列表（新工具在前）
const reversedToolCalls = computed(() => {
  return [...props.streamToolCalls].reverse()
})

// 截断结果文本（移动端优化）
const truncateResult = (text) => {
  if (!text) return ''
  const maxLength = 200
  if (text.length <= maxLength) return text
  return text.substring(0, maxLength) + '...'
}

const renderStreamMarkdown = computed(() => {
  let content = props.streamContent || ''
  
  // 清理多余的空行（3个或更多连续换行符压缩为2个）
  content = content.replace(/\n{3,}/g, '\n\n')
  
  // 转换知识库图片引用 [图片N: /profile/path] 为markdown图片语法
  content = content.replace(/\[图片(\d+):\s*(\/profile\/[^\]\s]+)\]/g, '![图片$1]($2)')
  
  let html = md.render(content)
  
  // 自动转换下载链接为完整URL
  html = html.replace(
    /href="(\/agent\/api\/file\/download\/\d+)"/g,
    (match, url) => {
      const fullUrl = import.meta.env.VITE_APP_BASE_API + url
      return `href="${fullUrl}" download`
    }
  )
  
  // 知识库图片URL补全
  html = html.replace(
    /src="(\/profile\/[^"]+)"/g,
    (match, url) => `src="${import.meta.env.VITE_APP_BASE_API}${url}"`
  )
  
  return html
})

const renderStreamContent = computed(() => {
  const cleaned = cleanText(props.streamContent || '')
  return escapeHtml(cleaned).replace(/\n/g, '<br>')
})

function escapeHtml(text) {
  const div = document.createElement('div')
  div.textContent = text
  return div.innerHTML
}

function cleanText(text) {
  if (!text) return ''
  
  // 去除开头和结尾的空白字符
  text = text.trim()
  
  // 将连续3个及以上的换行符替换为2个换行符
  text = text.replace(/\n{3,}/g, '\n\n')
  
  return text
}

function getStatusType(status) {
  const map = {
    'running': 'info',
    'success': 'success',
    'error': 'danger',
    'rejected': 'warning',
    'blocked': 'warning'
  }
  return map[status] || 'info'
}

function getStatusText(status) {
  const map = {
    'running': '执行中',
    'success': '成功',
    'error': '失败',
    'rejected': '已拒绝',
    'blocked': '已阻止'
  }
  return map[status] || status
}

function getAvatarUrl(avatar) {
  if (!avatar) return ''
  if (avatar.startsWith('http')) return avatar
  return import.meta.env.VITE_APP_BASE_API + avatar
}

function scrollToBottom() {
  if (containerRef.value) {
    containerRef.value.scrollTop = containerRef.value.scrollHeight
  }
}

// 启动/停止计时器
function startTimer() {
  // 先停止旧的计时器
  stopTimer()
  
  // 重置时间为0
  elapsedTime.value = 0
  
  console.log('⏱️ 启动计时器')
  timer = setInterval(() => {
    elapsedTime.value++
    console.log('⏱️ 计时:', elapsedTime.value, '秒')
  }, 1000)
}

function stopTimer() {
  if (timer) {
    console.log('⏹️ 停止计时器，最终时间:', elapsedTime.value, '秒')
    clearInterval(timer)
    timer = null
  }
}

// 格式化计时: 0:05, 1:23, 12:34
function formatElapsed(seconds) {
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return m + ':' + String(s).padStart(2, '0')
}

// 监听消息变化，自动滚动到底部
watch(() => props.messages.length, () => {
  nextTick(() => scrollToBottom())
})

watch(() => props.streamContent, (newVal) => {
  console.log('🔄 streamContent 更新:', newVal ? newVal.length + '字符' : '空')
  nextTick(() => scrollToBottom())
})

watch(() => props.streaming, (newVal, oldVal) => {
  console.log('🔄 streaming 状态变化:', oldVal, '→', newVal)
  if (newVal) {
    startTimer()
    // 启动保护性定时器，防止streaming卡住
    if (protectionTimer) {
      clearTimeout(protectionTimer)
    }
    protectionTimer = setTimeout(() => {
      if (props.streaming) {
        console.warn('⚠️ streaming状态异常，已超过15分钟，强制停止计时器')
        stopTimer()
      }
    }, 15 * 60 * 1000) // 15分钟
  } else {
    stopTimer()
    // 清除保护性定时器
    if (protectionTimer) {
      clearTimeout(protectionTimer)
      protectionTimer = null
    }
  }
}, { immediate: true }) // ✅ 改为 immediate: true，确保初始状态也会触发

// 组件卸载时清理定时器
onUnmounted(() => {
  console.log('🧹 组件卸载，清理计时器和保护性定时器')
  stopTimer()
  if (protectionTimer) {
    clearTimeout(protectionTimer)
    protectionTimer = null
  }
})

defineExpose({
  scrollToBottom
})
</script>

<style scoped>
.message-list-container {
  flex: 1;
  overflow-y: auto;
  scroll-behavior: smooth;
  background: #ffffff;
}

.message-list-inner {
  max-width: 800px;
  margin: 0 auto;
  padding: 32px 24px;
  min-height: 100%;
}

.msg-row {
  display: flex;
  gap: 12px;
  margin-bottom: 0;
  animation: slideIn 0.3s ease-out;
}

.msg-row.assistant {
  max-width: 100%;
}

.msg-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #f5f5f5;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 18px;
  color: #666;
}

.msg-bubble {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
}

.msg-role {
  font-size: 12px;
  color: #999;
  margin-bottom: 6px;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 8px;
}

.elapsed-time {
  font-size: 11px;
  color: #999;
  font-weight: 400;
  font-variant-numeric: tabular-nums;
}

.msg-text {
  padding: 10px 14px;
  border-radius: 12px;
  background: #f8f8f8;
  color: #1a1a1a;
  line-height: 1.5;
  word-wrap: break-word;
  word-break: break-word;
  white-space: pre-wrap;
  max-width: 100%;
  overflow-wrap: break-word;
}

.msg-row.assistant .msg-text {
  background: transparent;
  padding: 0;
}

.markdown-content {
  font-size: 14px;
  line-height: 1.5;
  max-width: 100%;
  overflow-x: hidden;
  word-wrap: break-word;
}

.markdown-content :deep(h1),
.markdown-content :deep(h2),
.markdown-content :deep(h3),
.markdown-content :deep(h4),
.markdown-content :deep(h5),
.markdown-content :deep(h6) {
  margin: 0;
  font-weight: 600;
  line-height: 1.3;
  color: #1a1a1a;
}

.markdown-content :deep(h1) { font-size: 28px; }
.markdown-content :deep(h2) { font-size: 24px; }
.markdown-content :deep(h3) { font-size: 20px; }
.markdown-content :deep(h4) { font-size: 16px; }

.markdown-content :deep(p) {
  margin: 0;
  line-height: 1.5;
}

.markdown-content :deep(code) {
  background: #f5f5f5;
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 0.9em;
  color: #e83e8c;
}

.markdown-content :deep(pre) {
  background: #f8f8f8;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  padding: 16px;
  overflow-x: auto;
  margin: 0;
  max-width: 100%;
}

.markdown-content :deep(pre code) {
  background: transparent;
  padding: 0;
  color: inherit;
  font-size: 13px;
  line-height: 1.6;
}

.markdown-content :deep(ul),
.markdown-content :deep(ol) {
  margin: 0;
  padding-left: 24px;
}

.markdown-content :deep(li) {
  margin: 0;
  line-height: 1.5;
}

.markdown-content :deep(blockquote) {
  border-left: 4px solid #e0e0e0;
  padding-left: 16px;
  margin: 0;
  color: #666;
  font-style: italic;
}

.markdown-content :deep(table) {
  border-collapse: collapse;
  width: 100%;
  max-width: 100%;
  margin: 0;
  overflow-x: auto;
}

.markdown-content :deep(table th),
.markdown-content :deep(table td) {
  border: 1px solid #e8e8e8;
  padding: 8px 12px;
  text-align: left;
  vertical-align: top;
}

.markdown-content :deep(table th) {
  background: #f5f5f5;
  font-weight: 600;
}

.markdown-content :deep(a) {
  color: var(--haier-blue, #006BB7);
  text-decoration: none;
}

.markdown-content :deep(a:hover) {
  text-decoration: underline;
}

.markdown-content :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: 8px;
  margin: 0;
}

.markdown-content :deep(hr) {
  border: none;
  border-top: 1px solid #e8e8e8;
  margin: 0;
}

/* 工具调用折叠框样式 - 移动端优化版 */
.tool-calls-collapse {
  margin-bottom: 10px;
  border: 1px solid #e0e0e0;
  border-radius: 6px;
  background: #f8f9fa;
  max-width: 100%;
  overflow: hidden;
}

.tool-calls-collapse :deep(.el-collapse-item__header) {
  background: linear-gradient(to right, #f0f2f5, #fafbfc);
  border: none;
  padding: 8px 12px;
  font-size: 13px;
  color: #333;
  line-height: 22px;
  min-height: 38px;
  height: auto;
}

.tool-calls-collapse :deep(.el-collapse-item__wrap) {
  border: none;
  background: transparent;
}

.tool-calls-collapse :deep(.el-collapse-item__content) {
  padding: 8px;
  background: #fff;
}

/* 折叠头部布局 */
.collapse-header-wrapper {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.collapse-title {
  font-weight: 600;
  font-size: 13px;
  color: #333;
  flex-shrink: 0;
}

.tool-count-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  background: #409eff;
  color: #fff;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 500;
}

/* 工具调用列表 */
.tool-calls-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

/* 单个工具调用卡片 */
.tool-call-card {
  padding: 10px;
  background: #fafafa;
  border: 1px solid #e8e8e8;
  border-radius: 6px;
  transition: all 0.2s ease;
}

.tool-call-card:hover {
  background: #f0f2f5;
  border-color: #d0d0d0;
}

.tool-call-card.tool-call-error {
  background: #fff3f3;
  border-color: #ffc9c9;
}

/* 工具头部信息 */
.tool-call-header {
  margin-bottom: 8px;
}

.tool-name-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
  flex-wrap: wrap;
}

.tool-emoji {
  font-size: 14px;
  flex-shrink: 0;
}

.tool-full-name {
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  font-weight: 600;
  color: #333;
  flex: 1;
  min-width: 0;
  word-break: break-word;
}

.tool-status-tag {
  flex-shrink: 0;
  height: 20px;
  line-height: 20px;
  padding: 0 8px;
  font-size: 11px;
}

.tool-plugin-name {
  font-size: 11px;
  color: #999;
  padding-left: 20px;
}

/* 工具执行结果 */
.tool-call-result {
  padding-top: 8px;
  border-top: 1px dashed #e0e0e0;
}

.result-label {
  font-size: 11px;
  color: #666;
  font-weight: 500;
  margin-bottom: 4px;
}

.result-content {
  font-size: 12px;
  color: #333;
  line-height: 1.6;
  word-break: break-word;
  white-space: pre-wrap;
  background: #fff;
  padding: 8px;
  border-radius: 4px;
  border: 1px solid #f0f0f0;
  max-height: 150px;
  overflow-y: auto;
}

.tool-more {
  font-size: 11px;
  color: #999;
  font-style: italic;
  padding: 1px 6px;
}

/* 结果内容样式已移到 tool-call-result 中 */

.typing-dots {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  height: 20px;
}

.typing-dots span {
  width: 6px;
  height: 6px;
  background: #999;
  border-radius: 50%;
  animation: typing 1.4s infinite;
}

.typing-dots span:nth-child(2) {
  animation-delay: 0.2s;
}

.typing-dots span:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes typing {
  0%, 60%, 100% {
    opacity: 0.3;
    transform: scale(0.8);
  }
  30% {
    opacity: 1;
    transform: scale(1);
  }
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* ========== 移动端适配 ========== */
@media (max-width: 768px) {
  .message-list-inner {
    padding: 16px 12px;
  }
  
  .msg-row {
    gap: 8px;
  }
  
  .msg-avatar {
    width: 32px;
    height: 32px;
    font-size: 16px;
  }
  
  .msg-role {
    font-size: 11px;
    margin-bottom: 4px;
  }
  
  .elapsed-time {
    font-size: 10px;
  }
  
  .msg-text {
    padding: 8px 12px;
    font-size: 14px;
  }
  
  .markdown-content {
    font-size: 14px;
  }
  
  .markdown-content :deep(h1) { font-size: 22px; }
  .markdown-content :deep(h2) { font-size: 20px; }
  .markdown-content :deep(h3) { font-size: 18px; }
  .markdown-content :deep(h4) { font-size: 16px; }
  
  .markdown-content :deep(pre) {
    padding: 12px;
    font-size: 12px;
  }
  
  .markdown-content :deep(pre code) {
    font-size: 12px;
  }
  
  .tool-calls-collapse :deep(.el-collapse-item__header) {
    padding: 6px 10px;
    font-size: 12px;
    min-height: 32px;
  }
  
  .tool-calls-collapse :deep(.el-collapse-item__content) {
    padding: 6px;
  }
  
  .collapse-title {
    font-size: 12px;
  }
  
  .tool-count-badge {
    min-width: 18px;
    height: 18px;
    font-size: 10px;
  }
  
  .tool-call-card {
    padding: 8px;
  }
  
  .tool-full-name {
    font-size: 12px;
  }
  
  .tool-plugin-name {
    font-size: 10px;
    padding-left: 18px;
  }
  
  .tool-status-tag {
    height: 18px;
    line-height: 18px;
    padding: 0 6px;
    font-size: 10px;
  }
  
  .result-content {
    font-size: 11px;
    padding: 6px;
    max-height: 120px;
  }
  
  .result-label {
    font-size: 10px;
  }
}

@media (max-width: 480px) {
  .message-list-inner {
    padding: 12px 8px;
  }
  
  .msg-avatar {
    width: 28px;
    height: 28px;
    font-size: 14px;
  }
  
  .msg-text {
    padding: 8px 10px;
    font-size: 13px;
  }
  
  .markdown-content {
    font-size: 13px;
  }
  
  .markdown-content :deep(h1) { font-size: 20px; }
  .markdown-content :deep(h2) { font-size: 18px; }
  .markdown-content :deep(h3) { font-size: 16px; }
  .markdown-content :deep(h4) { font-size: 14px; }
}

.compression-divider {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
  margin: 4px 0;
}
.compression-divider .divider-line {
  flex: 1;
  height: 1px;
  background: #dcdfe6;
}
.compression-divider .divider-text {
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
  user-select: none;
}

</style>
