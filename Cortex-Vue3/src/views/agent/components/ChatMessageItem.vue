<template>
  <div :class="['msg-row', message.role, { 'approval-waiting': message.isApprovalWaiting }]">
    <div class="msg-avatar">
      <span v-if="message.role === 'user'" class="user-avatar-text">{{ userAvatarChar }}</span>
      <el-avatar v-else-if="message.role === 'assistant' && agentAvatar" :size="36" :src="getAvatarUrl(agentAvatar)" />
      <el-icon v-else-if="message.role === 'assistant'"><ChatDotRound /></el-icon>
      <el-icon v-else-if="message.isApprovalWaiting"><Lock /></el-icon>
      <el-icon v-else><Tools /></el-icon>
    </div>
    <div class="msg-bubble">
      <div class="msg-role">
        <span>{{ roleLabel }}</span>
        <div v-if="message.role === 'assistant' && message.content" class="msg-tts-bar" :class="{ 'tts-active': ttsPlaying || ttsLoading }">
          <el-button v-if="!ttsPlaying && !ttsLoading" link class="tts-btn" @click="speakMessage" title="朗读">
            <el-icon><VideoPlay /></el-icon>
          </el-button>
          <el-button v-if="ttsLoading" link class="tts-btn" disabled title="合成中">
            <el-icon class="tts-spin"><Loading /></el-icon>
          </el-button>
          <el-button v-if="ttsPlaying" link class="tts-btn tts-stop" @click="stopSpeaking" title="停止">
            <el-icon><VideoPause /></el-icon>
          </el-button>
        </div>
      </div>
      
      <!-- 工具调用过程（历史消息） -->
      <el-collapse 
        v-if="message.role === 'assistant' && message.toolCalls && message.toolCalls.length > 0" 
        v-model="activeToolCollapse"
        class="tool-calls-collapse"
      >
        <el-collapse-item name="toolCalls">
          <template #title>
            <div class="collapse-header-wrapper">
              <span class="collapse-title">🔧 执行过程</span>
              <span class="tool-count-badge">{{ message.toolCalls.length }}</span>
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
      
      <!-- TTS audio player -->
      <div v-if="message.isAudio && message.audioUrl" class="msg-audio">
        <el-icon class="audio-icon"><VideoPlay /></el-icon>
        <audio :src="getAudioUrl(message.audioUrl)" controls class="audio-player"></audio>
      </div>
      <!-- File attachments -->
      <div v-if="message.role === 'user' && message.files && message.files.length > 0" class="msg-files">
        <div v-for="file in message.files" :key="file.fileId" class="msg-file-chip">
          <span class="msg-file-icon">{{ getFileEmoji(file.fileName) }}</span>
          <span class="msg-file-name">{{ file.fileName }}</span>
        </div>
      </div>
      <div v-if="message.role === 'assistant'" 
           class="msg-text markdown-content" 
           v-html="renderedMarkdown"
           @click="handleMarkdownClick"></div>
      <div v-else class="msg-text" :class="{ 'approval-waiting-text': message.isApprovalWaiting }" v-html="renderedContent"></div>

    </div>
  </div>

  <!-- 文件预览抽屉 -->
  <FilePreviewDrawer
    v-model="showPreviewDialog"
    :file-url="previewFileUrl"
    :file-name="previewFileName"
  />
</template>

<script setup>
import { computed, ref } from 'vue'
import { ChatDotRound, Tools, Lock, VideoPlay, VideoPause, Loading } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getToken } from '@/utils/auth'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'
import FilePreviewDrawer from './FilePreviewDrawer.vue'

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
  message: {
    type: Object,
    required: true
  },
  userLoginName: {
    type: String,
    default: '我'
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

const activeToolCollapse = ref([]) // 默认折叠

// 文件预览相关
const showPreviewDialog = ref(false)
const previewFileUrl = ref('')
const previewFileName = ref('')

// 打开文件预览
function openFilePreview(url, fileName) {
  previewFileUrl.value = url
  previewFileName.value = fileName
  showPreviewDialog.value = true
  console.log('打开文件预览:', fileName, url)
}

// 处理 Markdown 内容中的点击事件
function handleMarkdownClick(event) {
  const target = event.target
  
  // 检查是否点击了预览图标（SVG 或其内部的 path）
  let previewIcon = null
  if (target.classList.contains('preview-icon')) {
    previewIcon = target
  } else if (target.closest('.preview-icon')) {
    previewIcon = target.closest('.preview-icon')
  }
  
  if (previewIcon) {
    event.preventDefault()
    event.stopPropagation()
    
    const fileUrl = previewIcon.getAttribute('data-url')
    const fileName = previewIcon.getAttribute('data-name')
    
    if (fileUrl && fileName) {
      openFilePreview(fileUrl, fileName)
    }
  }
}

// 计算用户头像显示的首字符
const userAvatarChar = computed(() => {
  const name = props.userLoginName || '我'
  if (!name) return 'U'
  
  const firstChar = name.charAt(0)
  
  // 判断是否为字母（a-z, A-Z）
  if (/[a-zA-Z]/.test(firstChar)) {
    return firstChar.toUpperCase()
  }
  
  // 非字母（包括汉字）直接返回首字符
  return firstChar
})

// TTS playback state
const ttsLoading = ref(false)
const ttsPlaying = ref(false)
let ttsAudio = null

async function speakMessage() {
  const text = props.message.content || ''
  if (!text.trim()) return
  stopSpeaking()
  ttsLoading.value = true
  try {
    const blob = await new Promise((resolve, reject) => {
      const xhr = new XMLHttpRequest()
      xhr.responseType = 'blob'
      xhr.addEventListener('load', () => {
        if (xhr.status === 200) {
          resolve(xhr.response)
        } else {
          reject(new Error('语音合成失败'))
        }
      })
      xhr.addEventListener('error', () => reject(new Error('网络错误')))
      xhr.open('POST', import.meta.env.VITE_APP_BASE_API + '/agent/api/file/speak')
      xhr.setRequestHeader('Authorization', 'Bearer ' + getToken())
      xhr.setRequestHeader('Content-Type', 'application/json')
      xhr.send(JSON.stringify({ text }))
    })
    const url = URL.createObjectURL(blob)
    ttsAudio = new Audio(url)
    ttsAudio.onended = () => { ttsPlaying.value = false }
    await ttsAudio.play()
    ttsPlaying.value = true
  } catch (e) {
    ElMessage.error(e.message || '语音合成失败')
  } finally {
    ttsLoading.value = false
  }
}

function stopSpeaking() {
  if (ttsAudio) {
    ttsAudio.pause()
    ttsAudio = null
  }
  ttsPlaying.value = false
}

// 计算属性：倒序工具列表（新工具在前）
const reversedToolCalls = computed(() => {
  if (!props.message.toolCalls) return []
  return [...props.message.toolCalls].reverse()
})

// 截断结果文本（移动端优化）
const truncateResult = (text) => {
  if (!text) return ''
  const maxLength = 200
  if (text.length <= maxLength) return text
  return text.substring(0, maxLength) + '...'
}

const roleLabel = computed(() => {
  if (props.message.isApprovalWaiting) {
    return '⏸️ 等待审批'
  }
  if (props.message.role === 'user') {
    return props.userLoginName || '我'
  }
  if (props.message.role === 'assistant') {
    return props.agentName || 'AI 助手'
  }
  return {
    system: '系统',
    tool: '工具结果'
  }[props.message.role] || props.message.role
})

const renderedMarkdown = computed(() => {
  let content = props.message.content || ''
  
  // 清理多余的空行（3个或更多连续换行符压缩为2个）
  content = content.replace(/\n{3,}/g, '\n\n')
  
  // 转换知识库图片引用 [图片N: /profile/path] 为markdown图片语法
  content = content.replace(/\[图片(\d+):\s*(\/profile\/[^\]\s]+)\]/g, '![图片$1]($2)')
  
  let html = md.render(content)
  
  // 自动转换图片src为完整URL（包括view和download接口）
  html = html.replace(
    /src="(\/agent\/api\/file\/(view|download)\/\d+)"/g,
    (match, url) => {
      const fullUrl = import.meta.env.VITE_APP_BASE_API + url
      console.log('🔄 Assistant消息-替换图片src:', url, '→', fullUrl)
      return `src="${fullUrl}"`
    }
  )
  
  // 知识库图片URL补全
  html = html.replace(
    /src="(\/profile\/[^"]+)"/g,
    (match, url) => `src="${import.meta.env.VITE_APP_BASE_API}${url}"`
  )
  
  // 自动转换下载链接为完整URL，并添加预览功能
  html = html.replace(
    /href="(\/agent\/api\/file\/(view|download)\/\d+)"([^>]*)>(.*?)<\/a>/g,
    (match, url, type, attrs, linkText) => {
      const fullUrl = import.meta.env.VITE_APP_BASE_API + url
      const downloadAttr = url.includes('/download/') ? ' download' : ''
      
      // 检测文件类型，如果是可预览的文件，添加预览按钮
      const fileName = linkText.trim()
      const ext = fileName.split('.').pop().toLowerCase()
      // 支持 PDF、HTML、Word、Excel 和图片预览
      const previewableTypes = ['pdf', 'html', 'htm', 'doc', 'docx', 'xls', 'xlsx', 'jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'svg']
      
      if (previewableTypes.includes(ext)) {
        console.log('🔄 Assistant消息-替换可预览链接:', url, '→', fullUrl)
        return `<a href="${fullUrl}"${downloadAttr}${attrs} target="_blank">${linkText}</a><svg class="preview-icon" data-url="${fullUrl}" data-name="${fileName}" viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg" title="预览"><path fill="currentColor" d="M512 160c320 0 512 352 512 352S832 864 512 864 0 512 0 512s192-352 512-352zm0 64c-225.28 0-384.128 208.064-436.8 288 52.608 79.872 211.456 288 436.8 288 225.28 0 384.128-208.064 436.8-288-52.608-79.872-211.456-288-436.8-288zm0 64a224 224 0 1 1 0 448 224 224 0 0 1 0-448zm0 64a160.192 160.192 0 0 0-160 160c0 88.192 71.744 160 160 160s160-71.808 160-160-71.744-160-160-160z"></path></svg>`
      }
      
      console.log('🔄 Assistant消息-替换链接href:', url, '→', fullUrl)
      return `<a href="${fullUrl}"${downloadAttr}${attrs} target="_blank">${linkText}</a>`
    }
  )
  
  return html
})

const renderedContent = computed(() => {
  const content = props.message.content || ''
  if (props.message.role === 'tool') {
    // 检测是否包含HTML img标签
    const hasHtmlImage = content.includes('<img')
    
    // 如果包含HTML img标签，直接处理HTML（不经过markdown）
    if (hasHtmlImage) {
      console.log('🖼️ Tool消息包含HTML图片标签:', content)
      
      // 直接替换img标签中的src为完整URL
      let html = content.replace(/src="(\/agent\/api\/file\/(view|download)\/\d+)"/g, (match, url) => {
        const fullUrl = import.meta.env.VITE_APP_BASE_API + url
        console.log('🔄 替换图片src:', url, '→', fullUrl)
        return `src="${fullUrl}"`
      })
      
      console.log('✅ 处理后的HTML:', html)
      return html
    }
    
    // 检测是否包含markdown格式的图片或文件链接
    const hasMarkdownImage = content.includes('![') || content.includes('](')
    const hasFileLink = content.includes('/agent/api/file/download/') || 
                        content.includes('/agent/api/file/view/')
    
    // 如果包含markdown图片或文件链接，使用markdown渲染
    if (hasMarkdownImage || hasFileLink) {
      console.log('🖼️ Tool消息包含Markdown图片，渲染markdown:', content)
      let html = md.render(content)
      console.log('📄 Markdown渲染后的HTML:', html)
      
      // 自动转换图片src为完整URL（包括view和download接口）
      const imgSrcRegex = /src="(\/agent\/api\/file\/(view|download)\/\d+)"/g
      html = html.replace(imgSrcRegex, (match, url) => {
        const fullUrl = import.meta.env.VITE_APP_BASE_API + url
        console.log('🔄 替换图片src:', url, '→', fullUrl)
        return `src="${fullUrl}"`
      })
      
      // 自动转换下载链接为完整URL
      const hrefRegex = /href="(\/agent\/api\/file\/(view|download)\/\d+)"/g
      html = html.replace(hrefRegex, (match, url) => {
        const fullUrl = import.meta.env.VITE_APP_BASE_API + url
        const downloadAttr = url.includes('/download/') ? ' download' : ''
        console.log('🔄 替换链接href:', url, '→', fullUrl)
        return `href="${fullUrl}"${downloadAttr}`
      })
      
      console.log('✅ 最终HTML:', html)
      return html
    }
    
    // 否则保持纯文本显示（用于JSON等结构化数据）
    return '<pre class="tool-pre">' + escapeHtml(content) + '</pre>'
  }
  const cleaned = cleanText(content)
  return escapeHtml(cleaned).replace(/\n/g, '<br>')
})

function escapeHtml(text) {
  const div = document.createElement('div')
  div.textContent = text
  return div.innerHTML
}

function cleanText(text) {
  if (!text) return ''
  text = text.trim()
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

function getFileEmoji(fileName) {
  const ext = fileName.split('.').pop().toLowerCase()
  const map = {
    pdf: 'PDF',
    doc: 'DOC', docx: 'DOC',
    xls: 'XLS', xlsx: 'XLS',
    ppt: 'PPT', pptx: 'PPT',
    txt: 'TXT', md: 'MD',
    jpg: 'IMG', jpeg: 'IMG', png: 'IMG', gif: 'IMG', webp: 'IMG', bmp: 'IMG',
    csv: 'CSV', json: 'JSON', xml: 'XML', html: 'HTML', htm: 'HTML'
  }
  return map[ext] || 'FILE'
}

function getAudioUrl(url) {
  return import.meta.env.VITE_APP_BASE_API + url
}

function getAvatarUrl(avatar) {
  if (!avatar) return ''
  if (avatar.startsWith('http')) return avatar
  return import.meta.env.VITE_APP_BASE_API + avatar
}

</script>

<style scoped>
.msg-row {
  display: flex;
  gap: 12px;
  margin-bottom: 0;
  animation: slideIn 0.3s ease-out;
  max-width: 100%;
}

.msg-row.user {
  flex-direction: row-reverse;
  justify-content: flex-start;
  max-width: 70%;
  margin-left: auto;
}

.msg-row.assistant {
  max-width: 100%;
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

.msg-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 18px;
  color: #666;
}

/* 确保 el-avatar 也不被挤压 */
.msg-avatar :deep(.el-avatar) {
  flex-shrink: 0;
  width: 36px !important;
  height: 36px !important;
}

.msg-row.user .msg-avatar {
  background: #409EFF;
  color: #fff;
}

.user-avatar-text {
  font-size: 16px;
  font-weight: 600;
  line-height: 1;
}

.msg-row.assistant .msg-avatar {
  background: #f5f5f5;
  color: #666;
}

.msg-row.tool .msg-avatar {
  background: #FFA726;
  color: #fff;
}

.msg-row.approval-waiting .msg-avatar {
  background: #FF9800;
  color: #fff;
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.8;
    transform: scale(1.05);
  }
}

.msg-bubble {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
  overflow-wrap: break-word;
  word-break: break-word;
}

.msg-row.user .msg-bubble {
  align-items: flex-end;
}

.msg-role {
  display: flex;
  align-items: center;
  font-size: 12px;
  color: #999;
  margin-bottom: 6px;
  font-weight: 500;
}

.msg-text {
  padding: 10px 14px;
  border-radius: 12px;
  line-height: 1.5;
  word-wrap: break-word;
  word-break: break-word;
  white-space: pre-wrap;
  max-width: 100%;
  overflow-wrap: break-word;
}

.msg-row.user .msg-text {
  background: #409EFF;
  color: #fff;
}

.msg-row.assistant .msg-text {
  background: transparent;
  color: #1a1a1a;
  padding: 0;
  border-radius: 0;
}

.msg-row.tool .msg-text {
  background: #FFF8E1;
  border: 1px solid #FFE082;
  color: #333;
}

.msg-text.approval-waiting-text {
  background: #FFF3E0;
  border: 2px solid #FF9800;
  color: #E65100;
  font-weight: 500;
  animation: glow 1.5s ease-in-out infinite;
}

@keyframes glow {
  0%, 100% {
    box-shadow: 0 0 5px rgba(255, 152, 0, 0.3);
  }
  50% {
    box-shadow: 0 0 15px rgba(255, 152, 0, 0.6);
  }
}

.tool-pre {
  margin: 0;
  padding: 8px;
  background: #fff;
  border-radius: 4px;
  font-size: 12px;
  overflow-x: auto;
}

/* Markdown 样式 */
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

/* 文件预览图标样式 */
.markdown-content :deep(.preview-icon) {
  width: 16px;
  height: 16px;
  margin-left: 4px;
  cursor: pointer;
  color: #409eff;
  opacity: 0.7;
  transition: all 0.2s;
  vertical-align: middle;
}

.markdown-content :deep(.preview-icon:hover) {
  opacity: 1;
  transform: scale(1.2);
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
.msg-files {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}

.msg-file-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 10px;
  background: #e8f4fd;
  border: 1px solid #b3d8ff;
  border-radius: 12px;
  font-size: 12px;
  color: #409eff;
  max-width: 220px;
}

.msg-file-icon {
  font-weight: 700;
  font-size: 10px;
  background: #409eff;
  color: #fff;
  padding: 1px 4px;
  border-radius: 3px;
  flex-shrink: 0;
}

.msg-file-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.msg-audio {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #f0f9eb;
  border-radius: 8px;
  margin-bottom: 8px;
}
.audio-icon {
  color: #67c23a;
  font-size: 18px;
}
.audio-player {
  height: 32px;
  flex: 1;
}
.msg-tts-bar {
  margin-left: 8px;
  opacity: 0;
  transition: opacity 0.2s ease;
}
.msg-row.assistant:hover .msg-tts-bar,
.msg-tts-bar.tts-active {
  opacity: 1;
}
.tts-btn {
  padding: 2px 6px;
  font-size: 14px;
  color: #999;
  height: auto;
}
.tts-btn:hover {
  color: var(--haier-blue, #006BB7);
}
.tts-stop {
  color: var(--haier-blue, #006BB7) !important;
}
.tts-spin {
  animation: tts-spin 1s linear infinite;
}
@keyframes tts-spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* ========== 移动端适配 ========== */
@media (max-width: 768px) {
  .msg-row {
    gap: 8px;
  }
  
  .msg-row.user {
    max-width: 85%;
  }
  
  .msg-avatar {
    width: 32px;
    height: 32px;
    font-size: 16px;
  }
  
  /* 移动端 el-avatar 固定尺寸 */
  .msg-avatar :deep(.el-avatar) {
    width: 32px !important;
    height: 32px !important;
  }
  
  .user-avatar-text {
    font-size: 14px;
  }
  
  .msg-role {
    font-size: 11px;
    margin-bottom: 4px;
  }
  
  .msg-text {
    padding: 8px 12px;
    font-size: 14px;
  }
  
  .msg-row.assistant .msg-text {
    padding: 0;
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
  
  .msg-files {
    gap: 4px;
    margin-bottom: 6px;
  }
  
  .msg-file-chip {
    padding: 2px 8px;
    font-size: 11px;
    max-width: 180px;
  }
  
  .msg-file-icon {
    font-size: 9px;
    padding: 1px 3px;
  }
  
  .msg-audio {
    padding: 6px 10px;
    margin-bottom: 6px;
  }
  
  .audio-icon {
    font-size: 16px;
  }
  
  .audio-player {
    height: 28px;
  }
}

@media (max-width: 480px) {
  .msg-row.user {
    max-width: 90%;
  }
  
  .msg-avatar {
    width: 28px;
    height: 28px;
    font-size: 14px;
  }
  
  /* 小屏幕 el-avatar 固定尺寸 */
  .msg-avatar :deep(.el-avatar) {
    width: 28px !important;
    height: 28px !important;
  }
  
  .user-avatar-text {
    font-size: 12px;
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
  
  .msg-file-chip {
    max-width: 140px;
    font-size: 10px;
  }
}
</style>
