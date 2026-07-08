<template>
  <el-dialog
    :title="'运行日志 - ' + pluginName"
    v-model="visible"
    width="900px"
    :close-on-click-modal="false"
    append-to-body
  >
    <div class="log-container">
      <div class="log-header">
        <el-space>
          <el-button
            type="primary"
            size="small"
            :icon="Refresh"
            @click="handleRefresh"
            :loading="loading"
          >
            刷新
          </el-button>
          <el-button
            size="small"
            :icon="Download"
            @click="handleDownload"
          >
            下载日志
          </el-button>
          <el-button
            size="small"
            :icon="Delete"
            @click="handleClear"
          >
            清空
          </el-button>
          <el-switch
            v-model="autoScroll"
            active-text="自动滚动"
            style="margin-left: 10px"
          />
        </el-space>
        <div class="log-stats">
          <el-tag size="small">共 {{ logs.length }} 行</el-tag>
        </div>
      </div>

      <div class="log-content" ref="logContent">
        <div v-if="loading && logs.length === 0" class="log-empty">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>加载中...</span>
        </div>
        <div v-else-if="logs.length === 0" class="log-empty">
          <el-icon><DocumentCopy /></el-icon>
          <span>暂无日志</span>
        </div>
        <div v-else class="log-lines">
          <div
            v-for="(log, index) in logs"
            :key="index"
            :class="['log-line', getLogClass(log)]"
          >
            <span class="log-number">{{ index + 1 }}</span>
            <span class="log-text">{{ log }}</span>
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'
import { getPluginLogs } from '@/api/plugin/plugin'
import { ElMessage } from 'element-plus'
import { Refresh, Download, Delete, Loading, DocumentCopy } from '@element-plus/icons-vue'

const visible = ref(false)
const loading = ref(false)
const logs = ref([])
const pluginName = ref('')
const autoScroll = ref(true)
const logContent = ref(null)

// 打开对话框
const open = (name) => {
  pluginName.value = name
  visible.value = true
  logs.value = []
  loadLogs()
}

// 加载日志
const loadLogs = async () => {
  loading.value = true
  try {
    const response = await getPluginLogs(pluginName.value)
    if (response.code === 200) {
      logs.value = response.data || []
      if (autoScroll.value) {
        await nextTick()
        scrollToBottom()
      }
    } else {
      ElMessage.error(response.msg || '获取日志失败')
    }
  } catch (error) {
    console.error('获取日志失败:', error)
    ElMessage.error('获取日志失败: ' + error.message)
  } finally {
    loading.value = false
  }
}

// 刷新日志
const handleRefresh = () => {
  loadLogs()
}

// 下载日志
const handleDownload = () => {
  if (logs.value.length === 0) {
    ElMessage.warning('暂无日志可下载')
    return
  }

  const content = logs.value.join('\n')
  const blob = new Blob([content], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${pluginName.value}_${new Date().getTime()}.log`
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
  
  ElMessage.success('日志已下载')
}

// 清空显示
const handleClear = () => {
  logs.value = []
  ElMessage.success('日志已清空')
}

// 滚动到底部
const scrollToBottom = () => {
  if (logContent.value) {
    const element = logContent.value
    element.scrollTop = element.scrollHeight
  }
}

// 监听日志变化，自动滚动
watch(() => logs.value.length, () => {
  if (autoScroll.value && visible.value) {
    nextTick(() => scrollToBottom())
  }
})

// 获取日志行的类样式
const getLogClass = (log) => {
  if (log.includes('[ERR]') || log.includes('ERROR') || log.includes('error')) {
    return 'log-error'
  }
  if (log.includes('WARN') || log.includes('warn')) {
    return 'log-warn'
  }
  if (log.includes('[OUT]')) {
    return 'log-out'
  }
  return ''
}

defineExpose({
  open
})
</script>

<style scoped lang="scss">
.log-container {
  height: 600px;
  display: flex;
  flex-direction: column;
}

.log-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px;
  background: #f5f7fa;
  border-radius: 4px;
  margin-bottom: 10px;
}

.log-stats {
  display: flex;
  gap: 10px;
}

.log-content {
  flex: 1;
  overflow-y: auto;
  background: #1e1e1e;
  border-radius: 4px;
  padding: 10px;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.5;
  color: #d4d4d4;
}

.log-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #909399;
  gap: 10px;

  .el-icon {
    font-size: 48px;
  }
}

.log-lines {
  .log-line {
    display: flex;
    margin-bottom: 2px;
    padding: 2px 0;
    white-space: pre-wrap;
    word-break: break-all;

    &:hover {
      background: rgba(255, 255, 255, 0.05);
    }

    .log-number {
      display: inline-block;
      width: 50px;
      text-align: right;
      padding-right: 10px;
      color: #858585;
      user-select: none;
      flex-shrink: 0;
    }

    .log-text {
      flex: 1;
      color: #d4d4d4;
    }

    &.log-error .log-text {
      color: #f56c6c;
    }

    &.log-warn .log-text {
      color: #e6a23c;
    }

    &.log-out .log-text {
      color: #67c23a;
    }
  }
}

/* 自定义滚动条 */
.log-content::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.log-content::-webkit-scrollbar-track {
  background: #2d2d2d;
  border-radius: 4px;
}

.log-content::-webkit-scrollbar-thumb {
  background: #555;
  border-radius: 4px;

  &:hover {
    background: #666;
  }
}
</style>
