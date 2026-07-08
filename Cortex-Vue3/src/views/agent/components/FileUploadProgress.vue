<template>
  <Transition name="fade">
    <div v-if="files.length > 0" class="file-upload-progress">
      <div
        v-for="file in files"
        :key="file.id"
        class="progress-item"
      >
        <!-- 左侧圆圈/勾 -->
        <div class="status-icon">
          <el-icon v-if="file.error" class="icon-error">
            <CircleClose />
          </el-icon>
          <el-icon v-else-if="file.progress === 100" class="icon-success">
            <CircleCheck />
          </el-icon>
          <div v-else class="icon-loading">
            <div class="spinner"></div>
          </div>
        </div>
        
        <!-- 文件名 -->
        <span class="file-name">{{ file.name }}</span>
        
        <!-- 错误提示 -->
        <span v-if="file.error" class="error-text">上传失败</span>
      </div>
    </div>
  </Transition>
</template>

<script setup>
import { CircleClose, CircleCheck } from '@element-plus/icons-vue'

defineProps({
  files: {
    type: Array,
    default: () => []
  }
})
</script>

<style scoped lang="scss">
.file-upload-progress {
  margin-bottom: 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.progress-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  background: #f5f7fa;
  border-radius: 4px;
  font-size: 13px;
  color: #606266;
}

.status-icon {
  flex-shrink: 0;
  width: 16px;
  height: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.icon-success {
  color: #67c23a;
  font-size: 16px;
}

.icon-error {
  color: #f56c6c;
  font-size: 16px;
}

.icon-loading {
  width: 14px;
  height: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.spinner {
  width: 12px;
  height: 12px;
  border: 2px solid #409eff;
  border-top-color: transparent;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.file-name {
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.error-text {
  color: #f56c6c;
  font-size: 12px;
  flex-shrink: 0;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* ========== 移动端适配 ========== */
@media (max-width: 768px) {
  .file-upload-progress {
    margin-bottom: 6px;
    gap: 4px;
  }
  
  .progress-item {
    padding: 5px 10px;
    font-size: 12px;
    gap: 6px;
  }
  
  .status-icon {
    width: 14px;
    height: 14px;
  }
  
  .icon-success,
  .icon-error {
    font-size: 14px;
  }
  
  .spinner {
    width: 10px;
    height: 10px;
  }
  
  .error-text {
    font-size: 11px;
  }
}

@media (max-width: 480px) {
  .progress-item {
    padding: 4px 8px;
    font-size: 11px;
  }
  
  .file-name {
    max-width: 150px;
  }
}
</style>
