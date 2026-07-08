<template>
  <div class="widget-chat-wrap">
    <div v-if="loading" class="widget-state">
      <el-icon class="is-loading" :size="40"><Loading /></el-icon>
      <p class="widget-state-text">正在连接助手...</p>
    </div>
    <div v-else-if="errorMsg" class="widget-state widget-error">
      <el-icon :size="40"><WarningFilled /></el-icon>
      <p class="widget-state-text">{{ errorMsg }}</p>
      <el-button type="primary" size="small" @click="doAuth">重试</el-button>
    </div>
    <AgentChat
      v-else-if="ready"
      :agent-code="agentCode"
      :agent-name="agentName"
      :business-system="businessSystem"
    />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { Loading, WarningFilled } from '@element-plus/icons-vue'
import { setToken } from '@/utils/auth'
import useUserStore from '@/store/modules/user'
import AgentChat from './AgentChat.vue'

const route = useRoute()
const userStore = useUserStore()

const loading = ref(true)
const ready = ref(false)
const errorMsg = ref('')
const agentCode = ref('')
const agentName = ref('AI助手')
const businessSystem = ref('cortex')

const BASE_API = import.meta.env.VITE_APP_BASE_API

async function doAuth() {
  loading.value = true
  errorMsg.value = ''
  ready.value = false

  const apiKey = route.query.apiKey
  const user = route.query.user
  if (!apiKey || !user) {
    errorMsg.value = '缺少 apiKey 或 user 参数'
    loading.value = false
    return
  }
  try {
    const resp = await fetch(`${BASE_API}/agent/widget/auth`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Api-Key': String(apiKey) },
      body: JSON.stringify({ userLoginName: String(user) })
    })
    const json = await resp.json()
    if (json.code !== 200 || !json.data || !json.data.token) {
      errorMsg.value = (json && json.msg) ? json.msg : '鉴权失败'
      loading.value = false
      return
    }
    // 写入令牌，后续所有组件 getToken()/request 走标准 Bearer 鉴权
    setToken(json.data.token)
    // 设置当前用户名，AgentChat 用它作为 userLoginName
    userStore.name = json.data.userLoginName
    userStore.nickName = json.data.userLoginName
    agentCode.value = json.data.agentCode
    agentName.value = json.data.agentName || 'AI助手'
    businessSystem.value = json.data.businessSystem || 'cortex'
    ready.value = true
    loading.value = false
  } catch (e) {
    errorMsg.value = '连接失败: ' + e.message
    loading.value = false
  }
}

onMounted(doAuth)
</script>

<style scoped>
.widget-chat-wrap {
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  background: #ffffff;
}
.widget-state {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  color: #94a3b8;
}
.widget-state-text {
  margin: 0;
  font-size: 14px;
}
.widget-error {
  color: #ef4444;
}
</style>