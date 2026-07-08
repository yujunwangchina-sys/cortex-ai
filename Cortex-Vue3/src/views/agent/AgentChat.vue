<template>
  <div class="agent-chat">
    <!-- 左侧会话列表 -->
    <div class="sidebar-wrapper" :class="{ collapsed: sidebarCollapsed }">
      <ChatSidebar
          :sessions="sessionList"
          :current-session-id="currentSessionId"
          :collapsed="false"
          @new-session="startNewSession"
          @select-session="selectSession"
          @delete-session="deleteSession"
          @close="sidebarCollapsed = true"
      />
    </div>

    <!-- 遮罩层(移动端) -->
    <div 
      v-if="!sidebarCollapsed" 
      class="sidebar-overlay"
      @click="sidebarCollapsed = true"
    ></div>

    <!-- 主聊天区域 -->
    <div class="chat-main">
      <!-- 折叠按钮 -->
      <div class="sidebar-toggle" @click="sidebarCollapsed = !sidebarCollapsed">
        <el-icon>
          <component :is="sidebarCollapsed ? 'Expand' : 'Fold'"/>
        </el-icon>
      </div>
      <!-- Voice call button -->
      <div class="voice-call-btn" @click="voiceCallVisible = true">
        <el-icon><Phone/></el-icon>
        <span class="beta-tag">内测</span>
      </div>
      <!-- 欢迎界面（无消息时） -->
      <div v-if="displayMessages.length === 0 && !streaming" class="welcome-screen">
        <h1 class="welcome-title">你想要 {{ agentName || 'AI助手' }} 帮你做什么？</h1>
        <div class="welcome-input-wrapper">
          <ChatInputBox
              v-model:approval-mode="approvalMode"
              v-model:selected-model-id="selectedModelId"
              :models="modelOptions"
              :streaming="streaming"
              :queued-messages="messageQueue"
              :context-usage="contextUsage"
              :attached-files="attachedFiles"
          :compressing="compressing"
              @send="sendMessage"
              @stop="handleStop"
              @guide-message="handleGuideMessage"
              @cancel-queue="handleCancelQueue"
              @file-upload="handleFileUpload"
              @remove-file="handleRemoveFile"
          />
        </div>
      </div>

      <!-- 聊天界面（有消息时） -->
      <template v-else>
        <ChatMessageList
            ref="messageListRef"
            :messages="displayMessages"
            :streaming="streaming"
            :stream-content="streamBuffer"
            :stream-tool-calls="toolCalls"
            :user-login-name="currentUser"
            :agent-name="agentInfo.agentName"
            :agent-avatar="agentInfo.avatar"
        />
        <ChatInputBox
            v-model:approval-mode="approvalMode"
            v-model:selected-model-id="selectedModelId"
            :models="modelOptions"
            :streaming="streaming"
            :queued-messages="messageQueue"
            :context-usage="contextUsage"
            :attached-files="attachedFiles"
            :compressing="compressing"
            :uploading-files="uploadingFiles"
            @send="sendMessage"
            @stop="handleStop"
            @guide-message="handleGuideMessage"
            @cancel-queue="handleCancelQueue"
            @file-upload="handleFileUpload"
            @remove-file="handleRemoveFile"
        />
      </template>

      <!-- 审批弹窗 -->
      <ApprovalDialog
          ref="approvalDialogRef"
          @approved="handleApprovalApproved"
          @rejected="handleApprovalRejected"
          @timeout="handleApprovalTimeout"
      />
      <!-- Voice Call Modal -->
      <VoiceCallModal
          :visible="voiceCallVisible"
          :session-id="currentSessionId"
          :agent-code="agentCode"
          :agent-name="agentName"
          :model-id="selectedModelId"
          @close="voiceCallVisible = false"
          @message="handleVoiceMessage"
      />
    </div>
  </div>
</template>

<script setup>
import {ref, reactive, onMounted, nextTick, watch} from 'vue'
import {Expand, Fold, Phone} from '@element-plus/icons-vue'
import {ElMessage, ElNotification} from 'element-plus'
import request from '@/utils/request'
import {getToken} from '@/utils/auth'
import useUserStore from '@/store/modules/user'
import ChatSidebar from './components/ChatSidebar.vue'
import ChatMessageList from './components/ChatMessageList.vue'
import ChatInputBox from './components/ChatInputBox.vue'
import ApprovalDialog from './components/ApprovalDialog.vue'

import VoiceCallModal from './components/VoiceCallModal.vue'
const props = defineProps({
  agentCode: {type: String, required: true},
  businessSystem: {type: String, default: 'cortex'},
  agentName: {type: String, default: 'AI助手'}
})

const userStore = useUserStore()

// Agent信息
const agentInfo = ref({
  agentName: props.agentName,
  avatar: ''
})

// 移动端检测
const isMobile = ref(false)
const checkMobile = () => {
  isMobile.value = window.innerWidth <= 768
  // 移动端默认折叠侧边栏
  if (isMobile.value) {
    sidebarCollapsed.value = true
  }
}

// 状态
const sidebarCollapsed = ref(false)
const sessionList = ref([])
const currentSessionId = ref('')
const voiceCallVisible = ref(false)
const currentUser = ref('')
const currentBusinessSystem = ref(props.businessSystem || 'cortex')
const displayMessages = ref([])
const streaming = ref(false)
const streamBuffer = ref('')
const toolCalls = ref([])  // 当前回合的工具调用记录
const attachedFiles = ref([])
const modelOptions = ref([])
const selectedModelId = ref(null)
const pendingPreferredModelId = ref(null)
const approvalMode = ref('always')
const messageListRef = ref(null)
const approvalDialogRef = ref(null)
const pendingApprovals = ref(new Map()) // 存储待审批的请求
const approvedToolsInSession = ref(new Set()) // 记录当前会话已批准的工具（用于auto模式）

// 消息队列
const messageQueue = ref([])
let currentEventSource = null // 当前的SSE连接

// 上下文使用情况
const contextUsage = ref({
  usedTokens: 0,
  maxTokens: 183616, // 默认值，会从后端SSE事件更新
  percentage: 0,
  visible: true  // 改为 true，始终显示
})

// 压缩动画状态
const compressing = ref({ state: 'idle', data: null })

// 文件上传进度
const uploadingFiles = ref([])

onMounted(() => {
  currentUser.value = userStore.name || 'admin'
  loadAgentInfo()
  loadSessions()
  loadModels()

  // 检查是否移动端
  checkMobile()
  window.addEventListener('resize', checkMobile)

  // 检查审批弹窗组件是否挂载
  setTimeout(() => {
    console.log('🔍 延迟检查审批弹窗组件:', approvalDialogRef.value ? '已挂载' : '未挂载')
    if (approvalDialogRef.value) {
      console.log('✅ approvalDialogRef 可用，类型:', typeof approvalDialogRef.value)
      console.log('✅ show方法存在:', typeof approvalDialogRef.value.show === 'function' ? '是' : '否')
    }
  }, 1000)
  
  // 如果在 iframe 中（小窗模式），发送标题给父窗口
  if (window.parent !== window) {
    updateWidgetTitle()
    
    // 监听来自父窗口的新建会话消息
    window.addEventListener('message', (e) => {
      if (e.data && e.data.type === 'cortex-new-session') {
        console.log('📥 收到新建会话消息')
        startNewSession()
      }
    })
  }
})

// 加载Agent信息
async function loadAgentInfo() {
  try {
    const res = await request({url: `/agent/agent/code/${props.agentCode}`})
    if (res.data) {
      agentInfo.value.agentName = res.data.agentName || props.agentName
      agentInfo.value.avatar = res.data.avatar || ''

      // 解析模型偏好，设置默认聊天模型
      if (res.data.modelPreference) {
        try {
          const pref = JSON.parse(res.data.modelPreference)
          if (pref.chat) {
            pendingPreferredModelId.value = pref.chat
            if (modelOptions.value.length > 0) {
              const exists = modelOptions.value.find(m => m.modelId === pref.chat)
              if (exists) {
                selectedModelId.value = pref.chat
              }
            }
          }
        } catch (e) {
          console.warn('解析模型偏好失败:', e)
        }
      }
    }
  } catch (e) {
    console.error('加载Agent信息失败:', e)
  }
}

// 更新小窗标题（iframe模式）
function updateWidgetTitle() {
  // 获取业务系统名称，转大写并拼接 " FOR AI"
  const businessSystem = currentBusinessSystem.value || 'CORTEX'
  const title = businessSystem.toUpperCase() + ' FOR AI'
  
  // 发送标题给父窗口
  if (window.parent !== window) {
    window.parent.postMessage({
      type: 'cortex-widget-title',
      title: title
    }, '*')
    console.log('📤 发送小窗标题:', title)
  }
}

// 监听业务系统变化，更新小窗标题
watch(currentBusinessSystem, () => {
  if (window.parent !== window) {
    updateWidgetTitle()
  }
})

// 监听模型切换，立即更新前端的maxTokens显示
watch(selectedModelId, (newModelId, oldModelId) => {
  if (newModelId && newModelId !== oldModelId && modelOptions.value.length > 0) {
    const newModel = modelOptions.value.find(m => m.modelId === newModelId)
    if (newModel && newModel.contextLength) {
      const outputReserved = newModel.maxTokens || 16384
      const newMaxTokens = newModel.contextLength - outputReserved
      
      // 立即更新前端显示
      contextUsage.value.maxTokens = newMaxTokens
      contextUsage.value.percentage = Math.round((contextUsage.value.usedTokens / newMaxTokens) * 100)
      
      console.log('🔄 模型切换，前端maxTokens已更新:', newModel.modelName, newMaxTokens)
      
      // 如果有会话ID，通知用户
      if (currentSessionId.value && oldModelId) {
        const oldModel = modelOptions.value.find(m => m.modelId === oldModelId)
        ElNotification({
          title: '模型已切换',
          message: `${oldModel?.modelName || '旧模型'} → ${newModel.modelName}\n上下文限制: ${newMaxTokens.toLocaleString()} tokens`,
          type: 'info',
          duration: 4000
        })
      }
    }
  }
})

// 加载模型列表
async function loadModels() {
  try {
    const res = await request({url: '/agent/api/models'})
    modelOptions.value = res.data || []
    if (modelOptions.value.length > 0 && !selectedModelId.value) {
      // 优先使用Agent配置的默认模型
      if (pendingPreferredModelId.value) {
        const exists = modelOptions.value.find(m => m.modelId === pendingPreferredModelId.value)
        if (exists) {
          selectedModelId.value = pendingPreferredModelId.value
          return
        }
      }
      selectedModelId.value = modelOptions.value[0].modelId
    }
  } catch (e) {
    console.error('加载模型失败:', e)
  }
}

// 加载会话列表
async function loadSessions() {
  try {
    const res = await request({
      url: '/agent/api/session/list',
      params: {
        pageNum: 1,
        pageSize: 100,
        businessSystem: currentBusinessSystem.value,
        userLoginName: currentUser.value
      }
    })
    sessionList.value = res.rows || []
  } catch (e) {
    console.error('加载会话失败:', e)
  }
}

// 会话状态管理：为每个会话维护独立的 SSE 连接和消息缓冲区
const sessionStates = ref(new Map()) // sessionId -> { reader, messages, streamBuffer, toolCalls, streaming }

// 新建会话
function startNewSession() {
  // 保存当前会话的 SSE 连接到后台，让它继续完成
  if (currentSessionId.value && currentEventSource) {
    console.log('� 保存当前会话的 SSE 连接到后台，会话ID:', currentSessionId.value)
    sessionStates.value.set(currentSessionId.value, {
      reader: currentEventSource,
      messages: [...displayMessages.value],
      streamBuffer: streamBuffer.value,
      toolCalls: [...toolCalls.value],
      streaming: streaming.value
    })
  }
  
  // 清空前端显示状态（新会话）
  currentSessionId.value = ''
  currentEventSource = null
  displayMessages.value = []
  streamBuffer.value = ''
  attachedFiles.value = []  // 清空附件
  approvedToolsInSession.value.clear() // 清空已批准工具记录
  toolCalls.value = []  // 清空工具调用记录
  streaming.value = false
  
  // 清空消息队列
  messageQueue.value = []
  
  // 重置 token 统计
  contextUsage.value.usedTokens = 0
  contextUsage.value.percentage = 0
  console.log('🆕 新建会话，旧会话的 SSE 连接已保存到后台继续运行')
}

// 选择会话
async function selectSession(sessionId) {
  // 保存当前会话的 SSE 连接到后台，让它继续完成
  if (currentSessionId.value && currentEventSource) {
    console.log('🔄 切换会话，保存当前会话的 SSE 连接到后台，会话ID:', currentSessionId.value)
    sessionStates.value.set(currentSessionId.value, {
      reader: currentEventSource,
      messages: [...displayMessages.value],
      streamBuffer: streamBuffer.value,
      toolCalls: [...toolCalls.value],
      streaming: streaming.value
    })
  }
  
  // 重置当前状态
  currentEventSource = null
  streaming.value = false
  streamBuffer.value = ''
  toolCalls.value = []
  messageQueue.value = []
  
  try {
    const res = await request({url: '/agent/api/session/' + sessionId})
    const session = res.data
    if (session) {
      currentSessionId.value = sessionId
      currentBusinessSystem.value = session.businessSystem || 'cortex'

      // 解析消息
      let messages = []
      if (session.messages) {
        messages = typeof session.messages === 'string'
            ? JSON.parse(session.messages)
            : session.messages
      }

      // 🔴 关键：先检查该会话是否有后台积累的消息，如果有则添加到 messages 数组
      const backgroundState = sessionStates.value.get(sessionId)
      if (backgroundState && backgroundState.messages.length > 0) {
        console.log('🔄 发现后台积累的消息，添加到消息列表，消息数:', backgroundState.messages.length)
        messages.push(...backgroundState.messages)
        
        // 清理已合并的后台状态
        backgroundState.messages = []
        backgroundState.streamBuffer = ''
        backgroundState.toolCalls = []
        
        // 清除新消息标记
        const sess = sessionList.value.find(s => s.sessionId === sessionId)
        if (sess) {
          sess.hasNewMessage = false
        }
      }

      // 过滤掉不需要显示的消息：
      // 1. tool 角色的消息（工具结果已经在折叠框中显示）
      // 2. 空内容的 assistant 消息（避免显示多个空的 AI 助手气泡）
      displayMessages.value = messages.filter(msg => {
        // 过滤 tool 角色
        if (msg.role === 'tool') return false
        
        // 过滤空内容的 assistant 消息
        if (msg.role === 'assistant') {
          const hasContent = msg.content && msg.content.trim()
          const hasToolCalls = msg.toolCalls && msg.toolCalls.length > 0
          const hasAudio = msg.isAudio && msg.audioUrl
          
          // 至少要有内容、工具调用或音频其中之一
          return hasContent || hasToolCalls || hasAudio
        }
        
        return true
      })

      console.log('✅ 加载会话消息，过滤前:', messages.length, '过滤后:', displayMessages.value.length)
      
      // 调试：检查消息结构
      if (displayMessages.value.length > 0) {
        console.log('📝 第一条消息示例:', JSON.stringify(displayMessages.value[0], null, 2))
      }
      
      // 调试：检查是否有files字段
      const messagesWithFiles = displayMessages.value.filter(msg => msg.files && msg.files.length > 0)
      if (messagesWithFiles.length > 0) {
        console.log('📎 发现包含文件的消息:', messagesWithFiles.length, '条')
        messagesWithFiles.forEach(msg => {
          console.log('  - 文件:', msg.files)
        })
      } else {
        console.log('📎 没有发现包含文件的消息')
        // 检查是否有用户消息
        const userMessages = displayMessages.value.filter(msg => msg.role === 'user')
        if (userMessages.length > 0) {
          console.log('👤 用户消息示例:', JSON.stringify(userMessages[0], null, 2))
        }
      }

      // 更新 token 统计信息（从后端的 tokenUsage 字段）
      if (session.tokenUsage !== undefined && session.tokenUsage !== null) {
        contextUsage.value.usedTokens = session.tokenUsage
        
        // 根据会话的模型ID获取对应的maxTokens
        if (session.modelId) {
          const model = modelOptions.value.find(m => m.modelId === session.modelId)
          if (model && model.contextLength) {
            const outputReserved = model.maxTokens || 16384
            contextUsage.value.maxTokens = model.contextLength - outputReserved
            console.log('📊 使用会话模型的上下文配置:', model.modelName, contextUsage.value.maxTokens)
            
            // 同步更新选中的模型（保持一致性）
            selectedModelId.value = session.modelId
          } else {
            console.warn('⚠️ 会话的模型ID未找到对应配置，使用默认值:', session.modelId)
          }
        }
        
        contextUsage.value.percentage = Math.round((contextUsage.value.usedTokens / contextUsage.value.maxTokens) * 100)
        contextUsage.value.visible = true
        console.log('📊 加载会话 token 统计:', contextUsage.value.usedTokens, '/', contextUsage.value.maxTokens, `(${contextUsage.value.percentage}%)`)
        
        // 如果有模型切换历史，输出日志
        if (session.modelSwitchHistory) {
          try {
            const history = JSON.parse(session.modelSwitchHistory)
            console.log('🔄 会话模型切换历史:', history)
          } catch (e) {
            console.error('解析模型切换历史失败:', e)
          }
        }
      } else {
        // 如果后端没有返回 token 信息，重置为 0
        contextUsage.value.usedTokens = 0
        contextUsage.value.percentage = 0
        console.log('⚠️ 会话没有 token 统计信息，已重置')
      }

      // 切换会话时清空已批准工具记录
      approvedToolsInSession.value.clear()
    }
  } catch (e) {
    console.error('加载会话消息失败:', e)
    ElMessage.error('加载会话失败')
  }
}

// 删除会话
async function deleteSession(sessionId) {
  try {
    await request({
      url: '/agent/api/session/' + sessionId,
      method: 'delete'
    })
    ElMessage.success('删除成功')

    // 如果删除的是当前会话，清空显示
    if (sessionId === currentSessionId.value) {
      startNewSession()
    }

    // 重新加载会话列表
    await loadSessions()
  } catch (e) {
    console.error('删除会话失败:', e)
    ElMessage.error('删除失败: ' + (e.message || '未知错误'))
  }
}

// 发送消息
function handleVoiceMessage(msg) {
  if (msg.role === 'assistant' && msg.content) {
    displayMessages.value.push({role: 'assistant', content: msg.content})
  }
}

async function sendMessage(text, isQueued = false) {
  if (!text) return

  console.log('📤 发送消息:', text, '当前streaming状态:', streaming.value)

  // 🔥 如果正在streaming，自动打断当前回复
  if (streaming.value && !isQueued) {
    console.log('⚠️ 检测到新消息，自动打断当前回复')
    await handleStop() // 停止当前流式输出
    // 短暂延迟确保状态清理完成
    await new Promise(resolve => setTimeout(resolve, 100))
  }

  // 添加用户消息
  const pendingFiles = attachedFiles.value.length > 0 ? [...attachedFiles.value] : []
  displayMessages.value.push({
    role: 'user',
    content: text,
    files: pendingFiles.length > 0 ? pendingFiles : undefined
  })

  // 开始流式传输
  streaming.value = true
  streamBuffer.value = ''
  toolCalls.value = []  // 清空工具调用记录
  
  // 🔴 关键：记录发送消息时的 sessionId，用于后续数据隔离
  const messageSessionId = currentSessionId.value || null

  const body = {
    agentCode: props.agentCode,
    userLoginName: currentUser.value,
    businessSystem: currentBusinessSystem.value,
    message: text,
    sessionId: messageSessionId,
    modelId: selectedModelId.value,
    approvalMode: approvalMode.value,
    fileIds: pendingFiles.length > 0 ? pendingFiles.map(f => f.fileId) : null,
  }
  attachedFiles.value = []  // Clear after sending

  try {
    const response = await fetch(import.meta.env.VITE_APP_BASE_API + '/agent/api/chat/stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer ' + getToken(),
        'X-Business-System': currentBusinessSystem.value
      },
      body: JSON.stringify(body)
    })

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`)
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let lastActivityTime = Date.now()
    const TIMEOUT_MS = 120000 // 120秒无数据超时

    // 保存当前的reader，用于打断
    currentEventSource = reader

    while (true) {
      // 检查超时
      if (Date.now() - lastActivityTime > TIMEOUT_MS) {
        console.warn('⚠️ SSE连接超时，超过120秒无数据')
        ElMessage.warning('连接超时，请重新发送消息')
        break
      }

      const {done, value} = await reader.read()

      if (done) {
        console.log('✅ SSE流结束，会话ID:', messageSessionId)
        break
      }

      // 更新活动时间
      lastActivityTime = Date.now()

      // 解码数据块
      const chunk = decoder.decode(value, {stream: true})
      buffer += chunk

      console.log('🔵 收到数据块，会话ID:', messageSessionId, '当前会话ID:', currentSessionId.value, '数据长度:', chunk.length)

      // 按行分割处理SSE数据
      const lines = buffer.split('\n')

      // 保留最后一个不完整的行
      buffer = lines.pop() || ''

      // 处理每一行
      for (const line of lines) {
        const trimmed = line.trim()

        // 跳过空行和注释
        if (!trimmed || trimmed.startsWith(':')) continue

        // 处理 data: 开头的行
        if (trimmed.startsWith('data:')) {
          const jsonStr = trimmed.substring(5).trim()

          if (!jsonStr || jsonStr === '[DONE]') continue

          try {
            const event = JSON.parse(jsonStr)
            
            // 🔴 关键：检查会话ID是否匹配，如果不匹配则路由到后台会话
            if (event.data && event.data.sessionId) {
              const eventSessionId = event.data.sessionId
              
              // 如果事件的 sessionId 不是当前显示的会话，路由到后台
              if (eventSessionId !== currentSessionId.value) {
                console.log('📦 事件属于后台会话，会话ID:', eventSessionId, '事件类型:', event.type)
                handleBackgroundSessionEvent(eventSessionId, event)
                continue
              }
            }
            
            console.log('📨 当前会话事件:', event.type, '会话ID:', messageSessionId)

            // 立即处理事件
            handleSSEEvent(event)
          } catch (e) {
            console.error('❌ 解析失败:', jsonStr, e)
          }
        }
      }
    }

    // 处理缓冲区剩余数据
    if (buffer.trim()) {
      const trimmed = buffer.trim()
      if (trimmed.startsWith('data:')) {
        const jsonStr = trimmed.substring(5).trim()
        if (jsonStr && jsonStr.startsWith('{')) {
          try {
            const event = JSON.parse(jsonStr)
            
            // 同样检查会话ID
            if (event.data && event.data.sessionId && event.data.sessionId !== currentSessionId.value) {
              console.log('📦 缓冲区事件属于后台会话，会话ID:', event.data.sessionId)
              handleBackgroundSessionEvent(event.data.sessionId, event)
            } else {
              console.log('📨 缓冲区事件:', event.type)
              handleSSEEvent(event)
            }
          } catch (e) {
            console.error('❌ 缓冲区解析失败:', e)
          }
        }
      }
    }
  } catch (e) {
    console.error('❌ 发送失败:', e)
    ElMessage.error('发送失败: ' + e.message)
  } finally {
    // 🔴 清理状态的条件：
    // 1. messageSessionId 和 currentSessionId 相同
    // 2. 或者两者都为空/null（新会话场景）
    const shouldCleanup = 
      messageSessionId === currentSessionId.value || 
      (!messageSessionId && !currentSessionId.value)
    
    if (shouldCleanup) {
      console.log('🧹 清理前端状态，messageSessionId:', messageSessionId, 'currentSessionId:', currentSessionId.value)
      streaming.value = false
      currentEventSource = null

      // 如果有流式内容，添加为assistant消息
      if (streamBuffer.value && streamBuffer.value.trim()) {
        const assistantMsg = {
          role: 'assistant',
          content: streamBuffer.value
        }

        // 如果有工具调用记录，附加上去
        if (toolCalls.value.length > 0) {
          assistantMsg.toolCalls = [...toolCalls.value]
        }

        displayMessages.value.push(assistantMsg)
        console.log('✅ 添加assistant消息到displayMessages')
      }

      // 处理队列中的下一条消息
      await processNextQueuedMessage()
    } else {
      console.log('📦 后台会话完成，会话ID:', messageSessionId, '不清理前端状态')
      
      // 后台会话完成，保存到 sessionStates
      if (messageSessionId && streamBuffer.value) {
        const state = sessionStates.value.get(messageSessionId) || { messages: [] }
        state.messages.push({
          role: 'assistant',
          content: streamBuffer.value,
          toolCalls: toolCalls.value.length > 0 ? [...toolCalls.value] : undefined
        })
        sessionStates.value.set(messageSessionId, state)
        console.log('✅ 后台会话消息已保存，会话ID:', messageSessionId)
      }
    }
  }
}

// 处理队列中的下一条消息
async function processNextQueuedMessage() {
  if (messageQueue.value.length > 0) {
    const nextMessage = messageQueue.value.shift()
    console.log('📤 处理队列消息:', nextMessage)
    await nextTick()
    sendMessage(nextMessage, true) // true表示从队列发送
  }
}

// 停止当前生成
async function handleStop() {
  if (!currentSessionId.value) {
    ElMessage.warning('没有活动的会话')
    return
  }

  try {
    console.log('🛑 请求后端中断会话:', currentSessionId.value)

    // 调用后端API中断会话
    await request({
      url: `/agent/api/session/${currentSessionId.value}/interrupt`,
      method: 'post'
    })

    // 取消前端的stream读取
    if (currentEventSource) {
      currentEventSource.cancel().catch(() => {
      })
      currentEventSource = null
    }

    streaming.value = false

    // 保存已生成的部分
    if (streamBuffer.value && streamBuffer.value.trim()) {
      displayMessages.value.push({
        role: 'assistant',
        content: streamBuffer.value + '\n\n_[用户已停止生成]_',
        toolCalls: toolCalls.value.length > 0 ? [...toolCalls.value] : undefined
      })
    }

    streamBuffer.value = ''
    toolCalls.value = []

    ElMessage.success('已停止生成')

    // 处理队列中的下一条消息
    await nextTick()
    processNextQueuedMessage()

  } catch (error) {
    console.error('❌ 停止失败:', error)
    ElMessage.error('停止失败: ' + (error.message || '未知错误'))
  }
}

// 引导消息到当前会话（将排队消息加入当前会话继续）
function handleGuideMessage(index) {
  const message = messageQueue.value[index]
  if (!message) return

  // 从队列中移除
  messageQueue.value.splice(index, 1)

  console.log('🔄 引导消息到当前会话:', message)

  // 如果当前正在streaming，提示会在当前回复完成后自动发送
  if (streaming.value) {
    // 插入到队首，优先处理
    messageQueue.value.unshift(message)
    ElMessage.success('消息已引导，将在当前回复完成后立即发送')
  } else {
    // 如果没有在streaming，立即发送
    ElMessage.success('消息已引导并发送')
    sendMessage(message, true)
  }
}

// 取消排队消息
async function handleCancelQueue(index) {
  const message = messageQueue.value[index]
  if (message) {
    messageQueue.value.splice(index, 1)
    ElMessage.success('已取消排队')
  }

  streamBuffer.value = ''
  toolCalls.value = []

  // 只重新加载会话列表（不重新加载当前会话，避免重复显示）
  await loadSessions()
}


// 处理后台会话的 SSE 事件（不在当前显示的会话）
function handleBackgroundSessionEvent(sessionId, event) {
  // 获取或创建后台会话状态
  let state = sessionStates.value.get(sessionId)
  if (!state) {
    state = {
      reader: null,
      messages: [],
      streamBuffer: '',
      toolCalls: [],
      streaming: true
    }
    sessionStates.value.set(sessionId, state)
  }
  
  // 处理不同类型的事件
  switch (event.type) {
    case 'content_delta':
      if (event.data && event.data.delta) {
        state.streamBuffer += event.data.delta
        console.log('📦 后台会话累积内容:', sessionId, state.streamBuffer.length, '字符')
      }
      break
      
    case 'tool_call_start':
      state.toolCalls.push({
        pluginName: event.data.pluginName,
        toolName: event.data.toolName,
        arguments: event.data.arguments,
        status: 'running',
        startTime: Date.now()
      })
      console.log('📦 后台会话工具调用:', sessionId, event.data.toolName)
      break
      
    case 'tool_call_end':
      const lastCall = state.toolCalls[state.toolCalls.length - 1]
      if (lastCall) {
        lastCall.status = event.data.status
        lastCall.duration = event.data.durationMs
      }
      break
      
    case 'tool_result':
      const lastCallWithResult = state.toolCalls[state.toolCalls.length - 1]
      if (lastCallWithResult) {
        lastCallWithResult.result = event.data.result
        lastCallWithResult.success = event.data.success
      }
      break
      
    case 'done':
      console.log('✅ 后台会话完成:', sessionId)
      // 保存最终消息
      if (state.streamBuffer && state.streamBuffer.trim()) {
        state.messages.push({
          role: 'assistant',
          content: state.streamBuffer,
          toolCalls: state.toolCalls.length > 0 ? [...state.toolCalls] : undefined
        })
        console.log('✅ 后台会话消息已保存，会话ID:', sessionId, '消息数:', state.messages.length)
      }
      state.streaming = false
      
      // 更新会话列表（标记有新消息）
      const session = sessionList.value.find(s => s.sessionId === sessionId)
      if (session) {
        session.hasNewMessage = true  // 标记有新消息
        console.log('🔔 后台会话有新消息，会话ID:', sessionId)
      }
      break
      
    case 'error':
      console.error('❌ 后台会话错误:', sessionId, event.data)
      state.streaming = false
      break
  }
}

// 处理SSE事件
function handleSSEEvent(event) {
  switch (event.type) {
    case 'session_created':
      currentSessionId.value = event.data.sessionId
      console.log('✅ 会话创建:', currentSessionId.value)
      
      // 添加新会话到列表（使用默认标题）
      sessionList.value.unshift({
        sessionId: event.data.sessionId,
        agentName: event.data.agentName,
        title: '新会话',
        createTime: new Date().toISOString(),
        lastMessageTime: new Date().toISOString(),
        status: '0'
      })
      break

    case 'title_generated':
      // 标题生成完成，更新会话列表
      console.log('📝 标题生成:', event.data.title)
      const session = sessionList.value.find(s => s.sessionId === event.data.sessionId)
      if (session) {
        session.title = event.data.title
        console.log('✅ 会话标题已更新:', session.title)
      }
      break

    case 'content_delta':
      if (event.data && event.data.delta) {
        streamBuffer.value += event.data.delta
        console.log('💬 累积内容:', streamBuffer.value.length, '字符')
      }
      break

    case 'llm_start':
      console.log('🚀 LLM开始，迭代:', event.data.iteration)
      break

    case 'tool_call_start':
      // 记录工具调用开始
      toolCalls.value.push({
        pluginName: event.data.pluginName,
        toolName: event.data.toolName,
        arguments: event.data.arguments,
        status: 'running',
        startTime: Date.now()
      })
      console.log('🔧 工具调用开始:', event.data.pluginName, '-', event.data.toolName)
      break

    case 'tool_call_end':
      // 更新工具调用结束状态
      const lastCall = toolCalls.value[toolCalls.value.length - 1]
      if (lastCall) {
        lastCall.status = event.data.status
        lastCall.duration = event.data.durationMs
      }
      console.log('✅ 工具调用结束:', event.data.toolName, event.data.status)
      break

    case 'tool_result':
      // 更新工具调用结果
      const lastCallWithResult = toolCalls.value[toolCalls.value.length - 1]
      if (lastCallWithResult) {
        lastCallWithResult.result = event.data.result
        lastCallWithResult.success = event.data.success
      }
      console.log('📊 工具结果:', event.data.toolName, event.data.success ? '成功' : '失败')
      // TTS: detect voice_speak result and add audio player
      if (event.data.toolName === 'voice_speak' && event.data.success) {
        try {
          const tr = JSON.parse(event.data.result)
          if (tr.audioUrl) {
            displayMessages.value.push({ role: 'assistant', content: '', audioUrl: tr.audioUrl, isAudio: true })
          }
        } catch(e) {}
      }
      break

    case 'clear_current_message':
      // 审批被拒绝：清除误导性内容，等待AI自己决定如何回复
      console.log('🔄 审批被拒绝，清除误导性内容，等待AI重新生成回复')
      streamBuffer.value = ''
      toolCalls.value = []

      // 删除最后的"等待审批"提示和误导性的assistant消息
      while (displayMessages.value.length > 0) {
        const lastMsg = displayMessages.value[displayMessages.value.length - 1]
        if (lastMsg.role === 'system' && lastMsg.isApprovalWaiting) {
          displayMessages.value.pop() // 删除等待审批提示
        } else if (lastMsg.role === 'assistant') {
          displayMessages.value.pop() // 删除误导性的assistant消息
          console.log('🗑️ 已删除误导性的assistant消息')
          break
        } else {
          break
        }
      }

      // 不添加任何提示，让AI在下一轮自己生成回复
      break

    case 'approval_required':
      console.log('🔐 收到审批请求，停止内容输出', event.data)
      // 收到审批请求时，清空streamBuffer，显示"等待审批"提示
      if (streamBuffer.value && streamBuffer.value.trim()) {
        // 如果有部分内容，先添加到消息中
        displayMessages.value.push({
          role: 'assistant',
          content: streamBuffer.value
        })
        streamBuffer.value = ''
        console.log('✅ 已保存流式内容到消息列表')
      }
      // 添加"等待审批"提示消息
      displayMessages.value.push({
        role: 'system',
        content: `⏸️ 等待用户审批工具调用：${event.data.pluginName}.${event.data.toolName}`,
        isApprovalWaiting: true,
        grantId: event.data.grantId
      })
      console.log('✅ 已添加等待审批提示消息')

      // 立即调用审批处理
      console.log('🚀 立即调用handleApprovalRequired')
      handleApprovalRequired(event.data)
      break

    case 'info':
      // 信息提示（如上下文压缩、模型切换等）
      console.log('ℹ️ 系统信息:', event.data)
      break

    case 'context_usage':
      // 更新上下文使用情况（使用后端发送的实时数据，包含模型配置的maxTokens）
      if (event.data) {
        const oldMaxTokens = contextUsage.value.maxTokens
        
        contextUsage.value.usedTokens = event.data.usedTokens || 0
        contextUsage.value.maxTokens = event.data.maxTokens || 183616  // 后端会发送模型的实际上下文大小
        contextUsage.value.percentage = event.data.percentage || Math.round((contextUsage.value.usedTokens / contextUsage.value.maxTokens) * 100)
        contextUsage.value.visible = true
        
        // 检测到maxTokens变化，说明切换了模型
        if (oldMaxTokens !== contextUsage.value.maxTokens && oldMaxTokens !== 183616) {
          console.log('🔄 检测到模型切换，上下文限制已更新:', oldMaxTokens, '→', contextUsage.value.maxTokens)
          ElNotification({
            title: '模型已切换',
            message: `上下文限制已更新为 ${contextUsage.value.maxTokens.toLocaleString()} tokens`,
            type: 'info',
            duration: 3000
          })
        }
        
        console.log('📊 上下文使用更新:', contextUsage.value.usedTokens, '/', contextUsage.value.maxTokens, `(${contextUsage.value.percentage}%)`)
      }
      break


    case 'compressing_started':
      // compressing started - show animation
      compressing.value = { state: 'compressing', data: null }
      break

    case 'context_compressed':
      // compressing done - show completion, no system message
      if (event.data) {
        const beforeCount = event.data.beforeCount
        const afterCount = event.data.afterCount
        compressing.value = { state: 'done', data: { beforeCount, afterCount } }
        setTimeout(() => {
          displayMessages.value.push({
            role: 'compression_divider',
            content: '上下文已压缩 ' + beforeCount + '条消息->' + afterCount + '条消息'
          })
          compressing.value = { state: 'idle', data: null }
        }, 3000)
      }
      break

    case 'done':
      console.log('✅ 对话完成')
      // 确保停止streaming状态
      streaming.value = false
      break

    case 'error':
      console.error('❌ SSE错误事件:', event.data)
      streaming.value = false
      if (event.data && event.data.message) {
        ElMessage.error('错误: ' + event.data.message)
      }
      break

    default:
      console.log('ℹ️ 其他事件:', event.type)
  }
}

// 处理审批请求
function handleApprovalRequired(data) {
  debugger  // 🔴 断点1：进入审批处理
  console.log('🔐 收到审批请求:', JSON.stringify(data))
  console.log('   - 当前审批模式:', approvalMode.value)

  // 存储审批信息
  pendingApprovals.value.set(data.grantId, data)

  // 根据审批模式处理
  const toolKey = `${data.pluginName}:${data.toolName}`

  // 完全访问模式：自动批准
  if (approvalMode.value === 'full') {
    console.log('✅ 完全访问模式，自动批准')
    autoApprove(data.grantId)
    return
  }

  // 自动执行模式：检查是否已批准过
  if (approvalMode.value === 'auto') {
    if (approvedToolsInSession.value.has(toolKey)) {
      console.log('✅ 自动执行模式，工具已批准过，自动批准')
      autoApprove(data.grantId)
      return
    } else {
      console.log('⚠️ 自动执行模式，首次调用，需要弹窗审批')
    }
  }

  // 请求批准模式(always) 或 自动执行首次调用：需要弹窗
  console.log('🚀 准备弹窗，approvalDialogRef:', approvalDialogRef.value)

  if (!approvalDialogRef.value) {
    console.error('❌❌❌ approvalDialogRef 为 null/undefined!')
    debugger  // 🔴 断点2：ref为空
    return
  }

  if (typeof approvalDialogRef.value.show !== 'function') {
    console.error('❌❌❌ show 方法不存在!', approvalDialogRef.value)
    debugger  // 🔴 断点3：show方法不存在
    return
  }

  debugger  // 🔴 断点4：准备调用show
  try {
    approvalDialogRef.value.show(data)
    console.log('✅✅✅ show方法已调用')
  } catch (e) {
    console.error('❌❌❌ 调用show方法失败:', e)
    debugger  // 🔴 断点5：调用失败
  }
}

// 自动批准（用于full和auto模式）
async function autoApprove(grantId) {
  try {
    await request({
      url: `/agent/api/approval/${grantId}/approve`,
      method: 'post'
    })
    console.log('✅ 自动批准成功:', grantId)
    pendingApprovals.value.delete(grantId)
  } catch (e) {
    console.error('自动批准失败:', e)
    ElMessage.error('自动批准失败: ' + (e.message || '未知错误'))
  }
}

// 审批通过
function handleApprovalApproved(grantId) {
  console.log('✅ 审批通过:', grantId)

  // 移除"等待审批"提示消息
  displayMessages.value = displayMessages.value.filter(
      msg => !(msg.isApprovalWaiting && msg.grantId === grantId)
  )

  // 记录已批准的工具（用于auto模式）
  const approval = pendingApprovals.value.get(grantId)
  if (approval) {
    const toolKey = `${approval.pluginName}:${approval.toolName}`
    approvedToolsInSession.value.add(toolKey)
    console.log('📝 记录已批准工具:', toolKey)
  }

  pendingApprovals.value.delete(grantId)
}

// 审批拒绝
function handleApprovalRejected(grantId) {
  console.log('❌ 审批拒绝:', grantId)

  // 移除"等待审批"提示消息
  displayMessages.value = displayMessages.value.filter(
      msg => !(msg.isApprovalWaiting && msg.grantId === grantId)
  )

  pendingApprovals.value.delete(grantId)
  // 刷新会话
  if (currentSessionId.value) {
    loadSessions()
  }
}

// 审批超时
function handleApprovalTimeout(grantId) {
  console.log('⏰ 审批超时:', grantId)

  // 移除"等待审批"提示消息
  displayMessages.value = displayMessages.value.filter(
      msg => !(msg.isApprovalWaiting && msg.grantId === grantId)
  )

  pendingApprovals.value.delete(grantId)
  // 超时自动拒绝，不需要额外处理
  // 后端已经在轮询超时后自动设置为拒绝状态
}

// 文件上传
async function handleFileUpload(file) {
  const allowedExts = ['.doc','.docx','.xls','.xlsx','.ppt','.pptx','.pdf','.txt','.md','.csv','.json','.jpg','.jpeg','.png','.gif','.webp','.bmp']
  const ext = '.' + file.name.split('.').pop().toLowerCase()
  if (!allowedExts.includes(ext)) { 
    ElMessage.warning('不支持的文件类型: ' + ext)
    return false 
  }
  if (file.size > 50 * 1024 * 1024) { 
    ElMessage.warning('文件大小不能超过 50MB') 
    return false 
  }
  
  // 使用当前sessionId，新会话时使用'temp'
  const uploadSessionId = currentSessionId.value || 'temp'
  console.log('📤 文件上传 [sessionId=' + uploadSessionId + ', fileName=' + file.name + ']')
  
  // 生成临时fileId（上传完成后会被真实fileId替换）
  const tempFileId = 'temp_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9)
  
  // 立即添加到附件列表，显示为上传中状态
  const fileItem = reactive({
    fileId: tempFileId,
    fileName: file.name,
    uploading: true,  // 上传中标记
    progress: 0       // 上传进度
  })
  attachedFiles.value.push(fileItem)
  
  const formData = new FormData()
  formData.append('file', file)
  formData.append('sessionId', uploadSessionId)
  formData.append('agentCode', props.agentCode)
  formData.append('businessSystem', currentBusinessSystem.value)
  formData.append('userLoginName', currentUser.value)
  
  try {
    // 使用 XMLHttpRequest 来追踪上传进度
    await new Promise((resolve, reject) => {
      const xhr = new XMLHttpRequest()
      
      // 监听上传进度
      xhr.upload.addEventListener('progress', (e) => {
        if (e.lengthComputable) {
          const percentComplete = Math.round((e.loaded / e.total) * 100)
          fileItem.progress = percentComplete
        }
      })
      
      // 监听完成
      xhr.addEventListener('load', () => {
        if (xhr.status === 200) {
          try {
            const result = JSON.parse(xhr.responseText)
            if (result.code === 200 && result.data) {
              // 上传成功，更新为真实fileId和完成状态
              fileItem.fileId = result.data.fileId
              fileItem.fileName = result.data.fileName
              fileItem.uploading = false
              fileItem.success = true
              
              // 延迟300ms后隐藏对号，只保留文件名
              setTimeout(() => {
                fileItem.success = false
              }, 1500)
              
              resolve()
            } else {
              throw new Error(result.msg || '上传失败')
            }
          } catch (e) {
            reject(e)
          }
        } else {
          reject(new Error('HTTP ' + xhr.status))
        }
      })
      
      // 监听错误
      xhr.addEventListener('error', () => {
        reject(new Error('网络错误'))
      })
      
      xhr.open('POST', import.meta.env.VITE_APP_BASE_API + '/agent/api/file/upload')
      xhr.setRequestHeader('Authorization', 'Bearer ' + getToken())
      xhr.send(formData)
    })
  } catch (error) {
    console.error('文件上传失败:', error)
    // 标记为错误状态
    fileItem.uploading = false
    fileItem.error = true
    // 3秒后自动移除失败的文件
    setTimeout(() => {
      attachedFiles.value = attachedFiles.value.filter(f => f.fileId !== tempFileId)
    }, 3000)
  }
}

function handleRemoveFile(fileId) {
  attachedFiles.value = attachedFiles.value.filter(f => f.fileId !== fileId)
}

</script>

<style scoped>
.agent-chat {
  display: flex;
  height: 100%;
  background: #ffffff;
  position: relative;
}

.sidebar-wrapper {
  width: 320px;
  flex-shrink: 0;
  transition: all 0.3s ease;
  background: #ffffff;
  border-right: 1px solid #f0f0f0;
  z-index: 1000;
}

.sidebar-wrapper.collapsed {
  width: 0;
  min-width: 0;
  overflow: hidden;
  border-right: none;
}

/* 遮罩层 */
.sidebar-overlay {
  display: none;
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 999;
}

.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  position: relative;
}

.sidebar-toggle {
  position: absolute;
  top: 20px;
  left: 20px;
  width: 36px;
  height: 36px;
  background: #ffffff;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  z-index: 100;
}

.sidebar-toggle:hover {
  background: #f7f8fa;
  border-color: #d0d0d0;
  box-shadow: 0 3px 12px rgba(0, 0, 0, 0.12);
}

.sidebar-toggle .el-icon {
  font-size: 18px;
  color: #666;
}

.welcome-screen {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 40px;
  max-width: 2000px;
  margin: 0 auto;
  width: 100%;
}

.welcome-title {
  font-size: 36px;
  font-weight: 600;
  color: #1a1a1a;
  margin: 0 0 60px 0;
  text-align: center;
  line-height: 1.4;
}

.welcome-input-wrapper {
  width: 100%;
  max-width: 900px;
}

.chat-main > :deep(.chat-input-box) {
  width: calc(100% - 10px);
  margin: 0 5px 12px;
}
@media (min-width: 1520px) {
  .chat-main > :deep(.chat-input-box) {
    width: 100%;
    margin: 0 auto 12px;
  }
}

.welcome-input-wrapper :deep(.chat-input-box) {
  padding: 10px 24px 14px;
}

/* ========== 移动端适配 ========== */
@media (max-width: 768px) {
  /* 侧边栏改为浮动覆盖 */
  .sidebar-wrapper {
    position: fixed;
    top: 0;
    left: 0;
    height: 100%;
    width: 280px;
    transform: translateX(0);
    box-shadow: 2px 0 8px rgba(0, 0, 0, 0.15);
  }
  
  .sidebar-wrapper.collapsed {
    transform: translateX(-100%);
  }
  
  /* 显示遮罩 */
  .sidebar-overlay {
    display: block;
  }
  
  .sidebar-wrapper.collapsed + .sidebar-overlay {
    display: none;
  }
  
  /* 主区域占满 */
  .chat-main {
    width: 100%;
  }
  
  /* 调整折叠按钮位置 */
  .sidebar-toggle {
    top: 12px;
    left: 12px;
    width: 40px;
    height: 40px;
  }
  
  /* 欢迎界面 */
  .welcome-screen {
    padding: 40px 16px;
  }
  
  .welcome-title {
    font-size: 24px;
    margin-bottom: 40px;
  }
  
  .welcome-input-wrapper :deep(.chat-input-box) {
    padding: 10px 12px 14px;
  }
}

@media (max-width: 480px) {
  .welcome-title {
    font-size: 20px;
    margin-bottom: 30px;
  }
  
  .sidebar-wrapper {
    width: 85%;
    max-width: 280px;
  }
}
.voice-call-btn {
  position: absolute; top: 16px; right: 16px; z-index: 10;
  border-radius: 18px; padding: 0 10px; height: 32px;
  background: rgba(255,255,255,0.1); color: #4ade80;
  display: flex; align-items: center; justify-content: center; gap: 4px;
  cursor: pointer; transition: all 0.2s; font-size: 16px;
}
.voice-call-btn:hover { background: rgba(74,222,128,0.2); transform: scale(1.05); }
.beta-tag {
  font-size: 10px; font-weight: 600; color: #f59e0b;
  background: rgba(245,158,11,0.15); padding: 1px 5px; border-radius: 4px;
  line-height: 1.4;
}

</style>
