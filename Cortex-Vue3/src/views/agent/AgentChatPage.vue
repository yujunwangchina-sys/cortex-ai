<template>
  <div class="agent-chat-page">
    <AgentChat :agent-code="agentCode" :agent-name="agentName" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getAgentByCode } from '@/api/agent/agent'
import AgentChat from './AgentChat.vue'

const route = useRoute()

const agentCode = ref(route.params.agentCode || '')
const agentName = ref('')

onMounted(async () => {
  if (agentCode.value) {
    try {
      const res = await getAgentByCode(agentCode.value)
      if (res.data) {
        agentName.value = res.data.agentName || agentCode.value
      }
    } catch (e) {
      agentName.value = agentCode.value
    }
  }
})
</script>

<style scoped>
.agent-chat-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 84px);
  overflow: hidden;
}

.agent-chat-page :deep(.agent-chat) {
  height: 100%;
}

@media (max-width: 768px) {
  .agent-chat-page {
    height: 100vh;
    padding: 0;
  }
  
  .agent-chat-page :deep(.agent-chat) {
    height: 100%;
    border-radius: 0;
  }
}
</style>