<template>
  <!-- 审批卡片 - 从输入框上方滑出 -->
  <transition name="approval-slide">
    <div v-if="visible" class="approval-card">
      <div class="approval-header">
        <div class="header-left">
          <div class="icon-wrapper">
            <el-icon class="approval-icon">
              <Lock />
            </el-icon>
          </div>
          <div class="header-text">
            <h3>🔐 工具调用授权</h3>
            <p class="subtitle">请确认是否允许执行以下操作</p>
          </div>
        </div>
        <div class="countdown-badge" :class="{ urgent: countdown <= 5 }">
          <el-icon class="clock-icon">
            <Clock />
          </el-icon>
          <span class="countdown-text">{{ countdown }}s</span>
        </div>
      </div>

      <div class="approval-body">
        <div class="info-grid">
          <div class="info-item">
            <div class="info-icon">🔌</div>
            <div class="info-content">
              <span class="info-label">插件名称</span>
              <span class="info-value">{{ approvalData.pluginName }}</span>
            </div>
          </div>
          <div class="info-item">
            <div class="info-icon">🔧</div>
            <div class="info-content">
              <span class="info-label">工具名称</span>
              <span class="info-value">{{ approvalData.toolName }}</span>
            </div>
          </div>
        </div>

        <div class="params-section">
          <div class="params-header">
            <span class="params-label">📋 调用参数</span>
            <el-tag size="small" effect="plain" type="info">JSON</el-tag>
          </div>
          <pre class="params-content">{{ formatArguments(approvalData.arguments) }}</pre>
        </div>
      </div>

      <div class="approval-footer">
        <el-button
          size="default"
          @click="handleReject"
          :disabled="loading"
          class="reject-btn"
        >
          <el-icon><Close /></el-icon>
          拒绝
        </el-button>
        <el-button
          size="default"
          type="danger"
          @click="handleApprove"
          :loading="loading"
          class="approve-btn"
        >
          <el-icon><Check /></el-icon>
          批准执行
        </el-button>
      </div>
    </div>
  </transition>
</template>

<script setup>
import { ref, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Lock, Clock, Check, Close } from '@element-plus/icons-vue'
import request from '@/utils/request'

const visible = ref(false)
const loading = ref(false)
const countdown = ref(50)  // 前端50秒，后端60秒，留10秒缓冲
let countdownTimer = null

const approvalData = ref({
  grantId: null,
  pluginName: '',
  toolName: '',
  arguments: '',
  reason: ''
})

const emit = defineEmits(['approved', 'rejected', 'timeout'])

// 显示审批卡片
function show(data) {
  debugger  // 🔴 断点6：进入show方法
  console.log('🔔🔔🔔 ApprovalDialog.show() 被调用')
  console.log('   - 接收到的data:', JSON.stringify(data))
  console.log('   - visible当前值:', visible.value)
  
  approvalData.value = {
    grantId: data.grantId,
    pluginName: data.pluginName || '未知插件',
    toolName: data.toolName || '未知工具',
    arguments: data.arguments || '{}',
    reason: data.reason || ''
  }
  
  console.log('   - approvalData已设置:', JSON.stringify(approvalData.value))
  
  countdown.value = 50  // 前端50秒，后端60秒，留10秒缓冲
  
  debugger  // 🔴 断点7：准备设置visible为true
  visible.value = true
  console.log('   - visible已设置为true，当前值:', visible.value)
  
  // 启动倒计时
  startCountdown()
  console.log('   - 倒计时已启动')
  console.log('✅✅✅ ApprovalDialog显示完成')
}

// 启动倒计时
function startCountdown() {
  if (countdownTimer) {
    clearInterval(countdownTimer)
  }
  
  countdownTimer = setInterval(() => {
    countdown.value--
    
    if (countdown.value <= 0) {
      clearInterval(countdownTimer)
      countdownTimer = null
      handleTimeout()
    }
  }, 1000)
}

// 停止倒计时
function stopCountdown() {
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
}

// 超时处理
function handleTimeout() {
  ElMessage.warning('审批超时，已自动拒绝')
  emit('timeout', approvalData.value.grantId)
  visible.value = false
}

// 格式化参数显示
function formatArguments(args) {
  try {
    if (typeof args === 'string') {
      const parsed = JSON.parse(args)
      return JSON.stringify(parsed, null, 2)
    }
    return JSON.stringify(args, null, 2)
  } catch (e) {
    return args || '{}'
  }
}

// 批准
async function handleApprove() {
  console.log('🟢 用户点击批准按钮 [grantId={}]', approvalData.value.grantId)
  stopCountdown()
  loading.value = true
  try {
    await request({
      url: `/agent/api/approval/${approvalData.value.grantId}/approve`,
      method: 'post'
    })
    console.log('✅ 批准请求已发送 [grantId={}]', approvalData.value.grantId)
    ElMessage.success('已批准工具调用')
    emit('approved', approvalData.value.grantId)
    visible.value = false
  } catch (e) {
    console.error('❌ 批准失败:', e)
    ElMessage.error('批准失败: ' + (e.message || '未知错误'))
    startCountdown()
  } finally {
    loading.value = false
  }
}

// 拒绝
async function handleReject() {
  console.log('🔴 用户点击拒绝按钮 [grantId={}]', approvalData.value.grantId)
  stopCountdown()
  loading.value = true
  try {
    await request({
      url: `/agent/api/approval/${approvalData.value.grantId}/reject`,
      method: 'post',
      data: {
        reason: '用户拒绝'
      }
    })
    console.log('✅ 拒绝请求已发送 [grantId={}]', approvalData.value.grantId)
    ElMessage.warning('已拒绝工具调用')
    emit('rejected', approvalData.value.grantId)
    visible.value = false
  } catch (e) {
    console.error('❌ 拒绝失败:', e)
    ElMessage.error('拒绝失败: ' + (e.message || '未知错误'))
    startCountdown()
  } finally {
    loading.value = false
  }
}

onUnmounted(() => {
  stopCountdown()
})

defineExpose({ show })
</script>

<style scoped>
.approval-card {
  position: absolute;
  bottom: 120px;
  left: 50%;
  transform: translateX(-50%);
  width: 85%;
  max-width: 700px;
  background: #ffffff;
  border-radius: 12px;
  box-shadow: 0 12px 40px rgba(245, 108, 108, 0.15), 0 4px 12px rgba(0, 0, 0, 0.08);
  overflow: hidden;
  z-index: 2000;
  border: 2px solid #f56c6c;
}

/* 滑入动画 - 从底部向上 */
.approval-slide-enter-active {
  animation: slideUp 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.approval-slide-leave-active {
  animation: slideDown 0.25s cubic-bezier(0.4, 0, 0.6, 1);
}

@keyframes slideUp {
  from {
    transform: translateX(-50%) translateY(100%);
    opacity: 0;
  }
  to {
    transform: translateX(-50%) translateY(0);
    opacity: 1;
  }
}

@keyframes slideDown {
  from {
    transform: translateX(-50%) translateY(0);
    opacity: 1;
  }
  to {
    transform: translateX(-50%) translateY(100%);
    opacity: 0;
  }
}

/* 头部 */
.approval-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 24px;
  background: linear-gradient(135deg, #fff5f5 0%, #ffffff 100%);
  border-bottom: 2px solid #f56c6c;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 14px;
}

.icon-wrapper {
  width: 42px;
  height: 42px;
  background: linear-gradient(135deg, #f56c6c 0%, #ff8585 100%);
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(245, 108, 108, 0.3);
}

.approval-icon {
  font-size: 20px;
  color: #ffffff;
}

.header-text h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  line-height: 1.3;
  color: #303133;
  letter-spacing: 0.3px;
}

.header-text .subtitle {
  margin: 4px 0 0 0;
  font-size: 12px;
  color: #909399;
  font-weight: normal;
}

.countdown-badge {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  background: #ffffff;
  border: 2px solid #f56c6c;
  border-radius: 20px;
  font-size: 15px;
  font-weight: 600;
  color: #f56c6c;
  transition: all 0.3s;
  box-shadow: 0 2px 8px rgba(245, 108, 108, 0.2);
}

.countdown-badge.urgent {
  background: #fef0f0;
  border-color: #f56c6c;
  animation: pulse 1s infinite;
}

.clock-icon {
  font-size: 16px;
}

@keyframes pulse {
  0%, 100% {
    transform: scale(1);
    box-shadow: 0 2px 8px rgba(245, 108, 108, 0.2);
  }
  50% {
    transform: scale(1.05);
    box-shadow: 0 4px 16px rgba(245, 108, 108, 0.4);
  }
}

/* 主体 */
.approval-body {
  padding: 20px 24px;
  background: #ffffff;
}

.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin-bottom: 16px;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  background: linear-gradient(135deg, #fff5f5 0%, #ffffff 100%);
  border-radius: 10px;
  border: 1.5px solid #f56c6c;
  transition: all 0.2s;
}

.info-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(245, 108, 108, 0.15);
}

.info-icon {
  font-size: 24px;
  flex-shrink: 0;
}

.info-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
  min-width: 0;
}

.info-label {
  font-size: 11px;
  color: #f56c6c;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.info-value {
  font-size: 14px;
  color: #303133;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.params-section {
  margin-bottom: 0;
}

.params-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.params-label {
  font-size: 13px;
  color: #303133;
  font-weight: 600;
}

.params-content {
  margin: 0;
  padding: 12px 14px;
  background: linear-gradient(135deg, #fff5f5 0%, #ffffff 100%);
  border: 1.5px solid #f56c6c;
  border-radius: 10px;
  font-family: 'Courier New', Consolas, monospace;
  font-size: 12px;
  color: #303133;
  line-height: 1.6;
  max-height: 140px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-word;
}

.params-content::-webkit-scrollbar {
  width: 6px;
}

.params-content::-webkit-scrollbar-thumb {
  background: linear-gradient(180deg, #f56c6c 0%, #ff8585 100%);
  border-radius: 3px;
}

.params-content::-webkit-scrollbar-track {
  background: #fef0f0;
  border-radius: 3px;
}

/* 底部 */
.approval-footer {
  padding: 14px 24px;
  background: linear-gradient(135deg, #ffffff 0%, #fff5f5 100%);
  border-top: 2px solid #f56c6c;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.reject-btn {
  min-width: 90px;
  border-radius: 8px;
  font-weight: 500;
  padding: 10px 20px;
  border: 2px solid #909399;
  color: #606266;
  background: #ffffff;
  transition: all 0.2s;
}

.reject-btn:hover {
  border-color: #606266;
  color: #303133;
  background: #f5f7fa;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(96, 98, 102, 0.15);
}

.approve-btn {
  min-width: 100px;
  border-radius: 8px;
  font-weight: 600;
  padding: 10px 24px;
  background: linear-gradient(135deg, #f56c6c 0%, #ff8585 100%);
  border: none;
  transition: all 0.2s;
  box-shadow: 0 4px 12px rgba(245, 108, 108, 0.3);
}

.approve-btn:hover {
  background: linear-gradient(135deg, #ff5252 0%, #ff6b6b 100%);
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(245, 108, 108, 0.4);
}

/* 响应式 */
@media (max-width: 768px) {
  .approval-card {
    width: 95%;
    max-width: none;
  }
  
  .approval-header {
    padding: 14px 18px;
  }
  
  .approval-body {
    padding: 16px 18px;
  }
  
  .icon-wrapper {
    width: 36px;
    height: 36px;
  }
  
  .approval-icon {
    font-size: 18px;
  }
  
  .header-text h3 {
    font-size: 14px;
  }
  
  .info-grid {
    grid-template-columns: 1fr;
  }
  
  .approval-footer {
    padding: 12px 18px;
  }
}
</style>
