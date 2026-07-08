<template>
  <div class="context-progress" :class="statusClass" v-if="visible">
    <el-tooltip :content="tooltipText" placement="top">
      <div class="progress-circle" @click="showDetail = !showDetail">
        <svg width="24" height="24" viewBox="0 0 24 24">
          <!-- 背景圆 -->
          <circle
            cx="12"
            cy="12"
            r="10"
            fill="none"
            stroke="#e8e8e8"
            stroke-width="2"
          />
          <!-- 进度圆 -->
          <circle
            cx="12"
            cy="12"
            r="10"
            fill="none"
            :stroke="strokeColor"
            stroke-width="2"
            :stroke-dasharray="circumference"
            :stroke-dashoffset="dashOffset"
            stroke-linecap="round"
            transform="rotate(-90 12 12)"
            class="progress-path"
          />
        </svg>
        <div class="progress-text">{{ percentage }}%</div>
      </div>
    </el-tooltip>

  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { Close } from '@element-plus/icons-vue'

const props = defineProps({
  usedTokens: {
    type: Number,
    default: 0
  },
  maxTokens: {
    type: Number,
    default: 183616 // 默认值，实际值从后端SSE事件中获取（基于模型配置）
  },
  percentage: {
    type: Number,
    default: 0
  },
  visible: {
    type: Boolean,
    default: true
  }
})

const showDetail = ref(false)

// SVG圆形进度计算
const circumference = computed(() => 2 * Math.PI * 10) // r=10
const dashOffset = computed(() => {
  const progress = props.percentage / 100
  return circumference.value * (1 - progress)
})

// 剩余tokens
const remainingTokens = computed(() => props.maxTokens - props.usedTokens)

// 状态样式
const statusClass = computed(() => {
  if (props.percentage >= 90) return 'status-critical'
  if (props.percentage >= 70) return 'status-warning'
  return 'status-normal'
})

const strokeColor = computed(() => {
  if (props.percentage >= 90) return '#f56c6c'
  if (props.percentage >= 70) return '#e6a23c'
  return '#2d8c2d'
})

const barColor = computed(() => {
  if (props.percentage >= 90) return 'linear-gradient(90deg, #f56c6c, #ff8080)'
  if (props.percentage >= 70) return 'linear-gradient(90deg, #e6a23c, #f0b960)'
  return 'linear-gradient(90deg, #2d8c2d, #4caf50)'
})

const statusText = computed(() => {
  if (props.percentage >= 90) return '⚠️ 上下文即将满，即将自动压缩'
  if (props.percentage >= 70) return '⚡ 上下文使用较高'
  return '✓ 上下文充足'
})

const tooltipText = computed(() => {
  return `上下文使用: ${props.percentage}% (${formatTokens(props.usedTokens)}/${formatTokens(props.maxTokens)} tokens)`
})

function formatTokens(num) {
  if (num >= 1000) {
    return (num / 1000).toFixed(1) + 'K'
  }
  return num.toString()
}
</script>

<style scoped>
.context-progress {
  position: relative;
  display: inline-flex;
  align-items: center;
  animation: slideIn 0.3s ease-out;
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: scale(0.8);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

.progress-circle {
  position: relative;
  width: 24px;
  height: 24px;
  cursor: pointer;
  transition: transform 0.2s;
}

.progress-circle:hover {
  transform: scale(1.1);
}

.progress-circle svg {
  display: block;
  width: 24px;
  height: 24px;
}

.progress-path {
  transition: stroke-dashoffset 0.5s ease, stroke 0.3s ease;
}

.progress-text {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  font-size: 8px;
  font-weight: bold;
  color: #333;
  pointer-events: none;
}

.status-normal .progress-text {
  color: #2d8c2d;
}

.status-warning .progress-text {
  color: #e6a23c;
}

.status-critical .progress-text {
  color: #f56c6c;
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.6;
  }
}

/* 详细信息面板 */
.detail-panel {
  position: absolute;
  bottom: 35px;
  left: 0;
  width: 280px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
  overflow: hidden;
  z-index: 1000;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.detail-header .title {
  font-size: 14px;
  font-weight: 600;
}

.detail-header .close-icon {
  cursor: pointer;
  font-size: 16px;
  transition: transform 0.2s;
}

.detail-header .close-icon:hover {
  transform: scale(1.2);
}

.detail-content {
  padding: 16px;
}

.stat-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  font-size: 13px;
}

.stat-row .label {
  color: #666;
}

.stat-row .value {
  font-weight: 600;
  color: #333;
  font-family: 'Consolas', 'Monaco', monospace;
}

.stat-row .value.remaining {
  color: #2d8c2d;
}

.progress-bar {
  height: 8px;
  background: #f0f0f0;
  border-radius: 4px;
  overflow: hidden;
  margin: 12px 0;
}

.bar-fill {
  height: 100%;
  border-radius: 4px;
  transition: width 0.5s ease, background 0.3s ease;
}

.status-text {
  text-align: center;
  font-size: 12px;
  font-weight: 500;
  margin-top: 8px;
}

/* ========== 移动端适配 ========== */
@media (max-width: 768px) {
  .progress-circle {
    width: 20px;
    height: 20px;
  }
  
  .progress-circle svg {
    width: 20px;
    height: 20px;
  }
  
  .progress-text {
    font-size: 7px;
  }
  
  .detail-panel {
    width: 240px;
    bottom: 30px;
  }
  
  .detail-header {
    padding: 10px 12px;
  }
  
  .detail-header .title {
    font-size: 13px;
  }
  
  .detail-content {
    padding: 12px;
  }
  
  .stat-row {
    font-size: 12px;
    margin-bottom: 8px;
  }
}

@media (max-width: 480px) {
  .progress-circle {
    width: 18px;
    height: 18px;
  }
  
  .progress-circle svg {
    width: 18px;
    height: 18px;
  }
  
  .progress-text {
    font-size: 6px;
  }
}
</style>
