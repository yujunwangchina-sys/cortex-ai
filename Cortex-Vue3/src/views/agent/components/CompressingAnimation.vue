<template>
  <Transition name="fade">
    <div v-if="state === 'compressing' || state === 'done'" class="compressing-bar">
      <span v-if="state === 'compressing'" class="compressing-text">
        正在压缩上下文...
      </span>
      <span v-else class="compressing-done">
        已完成上下文压缩({{ data?.beforeCount }}条消息→{{ data?.afterCount }}条消息)
      </span>
    </div>
  </Transition>
</template>

<script setup>
const props = defineProps({
  state: {
    type: String,
    default: 'idle'
  },
  data: {
    type: Object,
    default: null
  }
})
</script>

<style scoped lang="scss">
.compressing-bar {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 8px 16px;
  margin-bottom: 8px;
  user-select: none;
}

/* 压缩中: 文字颜色从左往右波动变浅灰，来回滚动 */
.compressing-text {
  font-size: 13px;
  letter-spacing: 0.5px;
  background: linear-gradient(
    90deg,
    #999 0%,
    #999 25%,
    #ccc 40%,
    #e8e8e8 50%,
    #ccc 60%,
    #999 75%,
    #999 100%
  );
  background-size: 280% 100%;
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  color: transparent;
  animation: scroll-gray 2.5s ease-in-out infinite;
}

@keyframes scroll-gray {
  0%   { background-position: 0% 0; }
  50%  { background-position: 100% 0; }
  100% { background-position: 0% 0; }
}

/* 压缩完成: 静态浅灰文字 */
.compressing-done {
  font-size: 13px;
  color: #909399;
  animation: none;
}

/* 淡入淡出 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

@media (max-width: 768px) {
  .compressing-bar {
    padding: 6px 12px;
  }
  .compressing-text,
  .compressing-done {
    font-size: 12px;
  }
}
</style>