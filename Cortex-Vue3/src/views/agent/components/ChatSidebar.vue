<template>
  <aside class="chat-sidebar" :class="{ collapsed }">
    <div class="sidebar-header">
      <el-button @click="$emit('new-session')" class="new-session-btn">
        <el-icon><Plus /></el-icon>
        <span>新建会话</span>
      </el-button>
      <!-- 移动端关闭按钮 -->
      <el-button @click="$emit('close')" class="close-btn">
        <el-icon><Close /></el-icon>
      </el-button>
    </div>

    <div class="sidebar-sessions">
      <div class="sessions-header">
        <span class="sessions-title">我的会话</span>
        <span class="sessions-count">{{ sessions.length }}</span>
      </div>
      
      <div class="sessions-list">
        <div
          v-for="session in sessions"
          :key="session.sessionId"
          :class="['session-item', { active: session.sessionId === currentSessionId }]"
          @click="$emit('select-session', session.sessionId)"
        >
          <div class="session-content">
            <span class="session-title">{{ session.title || '新会话' }}</span>
            <div class="session-actions">
              <span class="session-time">{{ formatTime(session.createTime) }}</span>
              <el-icon 
                class="delete-icon" 
                @click.stop="handleDelete(session)"
                title="删除会话"
              >
                <Delete />
              </el-icon>
            </div>
          </div>
        </div>
      </div>
      
      <div v-if="sessions.length === 0" class="empty-state">
        <el-icon :size="24"><ChatLineSquare /></el-icon>
        <p>暂无会话</p>
      </div>
    </div>
  </aside>
</template>

<script setup>
import { Plus, ChatLineSquare, Delete, Close } from '@element-plus/icons-vue'
import { ElMessageBox, ElMessage } from 'element-plus'

defineProps({
  sessions: {
    type: Array,
    required: true
  },
  currentSessionId: {
    type: String,
    default: ''
  },
  collapsed: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['new-session', 'select-session', 'delete-session', 'close'])

function formatTime(timeStr) {
  if (!timeStr) return ''
  
  // 确保正确解析时间字符串
  // 如果是纯数字字符串（时间戳），转换为数字
  let date
  if (/^\d+$/.test(timeStr)) {
    date = new Date(parseInt(timeStr))
  } else {
    // ISO 格式字符串，直接解析
    // 如果没有时区信息，默认会按本地时区解析
    date = new Date(timeStr)
  }
  
  // 检查日期是否有效
  if (isNaN(date.getTime())) {
    return timeStr
  }
  
  const now = new Date()
  const diff = now - date
  
  if (diff < 0) return '刚刚' // 时间在未来，可能是时区问题
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
  if (diff < 604800000) return Math.floor(diff / 86400000) + '天前'
  
  const month = date.getMonth() + 1
  const day = date.getDate()
  return `${month}月${day}日`
}

function handleDelete(session) {
  ElMessageBox.confirm(
    `确定要删除会话"${session.title || '新会话'}"吗？删除后将无法恢复。`,
    '删除确认',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(() => {
    emit('delete-session', session.sessionId)
  }).catch(() => {
    // 用户取消
  })
}
</script>

<style scoped>
.chat-sidebar {
  width: 320px;
  height: 100%;
  background: #ffffff;
  border-right: 1px solid #f0f0f0;
  display: flex;
  flex-direction: column;
  transition: all 0.3s ease;
  flex-shrink: 0;
  overflow: hidden;
}

.chat-sidebar.collapsed {
  width: 0;
  min-width: 0;
  overflow: hidden;
  border-right: none;
  opacity: 0;
  visibility: hidden;
}

.sidebar-header {
  padding: 12px 10px;
  border-bottom: 1px solid #f0f0f0;
  flex-shrink: 0;
  display: flex;
  gap: 8px;
}

.new-session-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 6px;
  padding: 8px 12px;
  background: #ffffff;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  color: #1a1a1a;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.2s;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}

.new-session-btn:hover {
  background: #f7f8fa;
  border-color: #d0d0d0;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.06);
  transform: translateY(-1px);
}

.close-btn {
  display: none;
  width: 40px;
  height: 40px;
  padding: 0;
  background: #ffffff;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  flex-shrink: 0;
}

.close-btn:hover {
  background: #f7f8fa;
  border-color: #d0d0d0;
}

.sidebar-sessions {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 10px 8px;
  min-height: 0;
}

/* 美化滚动条 */
.sidebar-sessions::-webkit-scrollbar {
  width: 6px;
}

.sidebar-sessions::-webkit-scrollbar-track {
  background: transparent;
}

.sidebar-sessions::-webkit-scrollbar-thumb {
  background: #e0e0e0;
  border-radius: 3px;
}

.sidebar-sessions::-webkit-scrollbar-thumb:hover {
  background: #c0c0c0;
}

.sessions-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 8px;
  margin-bottom: 4px;
}

.sessions-title {
  font-size: 12px;
  font-weight: 600;
  color: #8a8a8a;
}

.sessions-count {
  font-size: 11px;
  color: #bbb;
}

.sessions-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.session-item {
  padding: 6px 8px;
  cursor: pointer;
  border-radius: 8px;
  transition: all 0.2s;
  background: #ffffff;
}

.session-item:hover {
  background: #f7f8fa;
}

.session-item.active {
  background: #f5f5f5;
  border-left: 3px solid var(--haier-blue, #006BB7);
  padding-left: 5px;
}

.session-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.session-title {
  flex: 1;
  font-size: 13px;
  font-weight: 400;
  color: #1a1a1a;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-item.active .session-title {
  font-weight: 500;
}

.session-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.session-time {
  font-size: 11px;
  color: #bbb;
}

.delete-icon {
  font-size: 14px;
  color: #bbb;
  transition: all 0.2s;
  opacity: 0;
  cursor: pointer;
}

.session-item:hover .delete-icon {
  opacity: 1;
}

.delete-icon:hover {
  color: #f56c6c;
  transform: scale(1.1);
}

.empty-state {
  text-align: center;
  padding: 40px 20px;
  color: #ccc;
}

.empty-state p {
  margin-top: 8px;
  font-size: 13px;
  color: #999;
}

/* ========== 移动端适配 ========== */
@media (max-width: 768px) {
  .close-btn {
    display: flex;
    align-items: center;
    justify-content: center;
  }
  
  .sidebar-header {
    padding: 10px 8px;
  }
  
  .new-session-btn {
    font-size: 13px;
    padding: 8px 10px;
  }
  
  .session-item {
    padding: 8px;
  }
  
  .session-title {
    font-size: 14px;
  }
}
</style>
