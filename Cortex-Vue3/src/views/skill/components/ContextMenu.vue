<template>
  <Teleport to="body">
    <div
      v-show="visible"
      class="context-menu"
      :style="{ left: x + 'px', top: y + 'px' }"
      @click.stop
    >
      <!-- 空白区域：创建根级技能包 -->
      <template v-if="isEmpty">
        <div class="menu-item" @click="handleCreateSkillPackage">
          <el-icon><Box /></el-icon>
          <span>新建技能包</span>
        </div>
        <div class="menu-item" @click="handleUploadSkillPackage">
          <el-icon><Upload /></el-icon>
          <span>上传技能包</span>
        </div>
      </template>
      
      <!-- 文件节点：只能重命名和删除 -->
      <template v-else-if="isFile">
        <div class="menu-item" @click="handleRename">
          <el-icon><Edit /></el-icon>
          <span>重命名</span>
        </div>
        <div class="menu-item danger" @click="handleDelete">
          <el-icon><Delete /></el-icon>
          <span>删除</span>
        </div>
      </template>
      
      <!-- 技能包节点：可以创建技能、DESCRIPTION.md、上传技能 -->
      <template v-else-if="isSkillPackage">
        <div class="menu-item" @click="handleCreateSkill">
          <el-icon><Folder /></el-icon>
          <span>新建技能</span>
        </div>
        <div class="menu-item" @click="handleUploadSkill">
          <el-icon><Upload /></el-icon>
          <span>上传技能</span>
        </div>
        <div class="menu-item" @click="handleCreateDescription">
          <el-icon><Document /></el-icon>
          <span>新建DESCRIPTION.md</span>
        </div>
        <div class="menu-divider"></div>
        <div class="menu-item" @click="handleRename">
          <el-icon><Edit /></el-icon>
          <span>重命名</span>
        </div>
        <div class="menu-item danger" @click="handleDelete">
          <el-icon><Delete /></el-icon>
          <span>删除</span>
        </div>
      </template>
      
      <!-- 文件夹节点（包括技能节点、普通文件夹）：可以创建文件夹和文件 -->
      <template v-else>
        <div class="menu-item" @click="handleCreateFolder">
          <el-icon><FolderAdd /></el-icon>
          <span>新建文件夹</span>
        </div>
        <div class="menu-item" @click="handleCreateFile">
          <el-icon><DocumentAdd /></el-icon>
          <span>新建文件</span>
        </div>
        <div class="menu-divider"></div>
        <div class="menu-item" @click="handleRename">
          <el-icon><Edit /></el-icon>
          <span>重命名</span>
        </div>
        <div class="menu-item danger" @click="handleDelete">
          <el-icon><Delete /></el-icon>
          <span>删除</span>
        </div>
      </template>
    </div>
  </Teleport>
</template>

<script setup>
import { watch, computed } from 'vue'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  x: {
    type: Number,
    default: 0
  },
  y: {
    type: Number,
    default: 0
  },
  node: {
    type: Object,
    default: null
  }
})

const emit = defineEmits([
  'update:visible', 
  'create-folder', 
  'create-file', 
  'create-skill', 
  'create-skill-package',
  'create-description', 
  'rename', 
  'delete',
  'upload-skill-package',
  'upload-skill'
])

// 判断节点类型 - 关键：先判断是否是文件
const isEmpty = computed(() => props.node?.data?.isEmpty === true)

// 判断是否是文件：isDirectory为false 或者 有fileExtension
const isFile = computed(() => {
  const data = props.node?.data
  if (!data) return false
  // 如果明确标记为非目录，就是文件
  if (data.isDirectory === false) return true
  // 如果有扩展名，也认为是文件
  if (data.fileExtension) return true
  return false
})

// 只有明确标记为目录才是文件夹
const isDirectory = computed(() => props.node?.data?.isDirectory === true)

// 特殊类型的文件夹
const isSkillPackage = computed(() => props.node?.data?.nodeType === 'skill_package')

function handleCreateFolder() {
  emit('create-folder', props.node?.data)
  emit('update:visible', false)
}

function handleCreateFile() {
  emit('create-file', props.node?.data)
  emit('update:visible', false)
}

function handleCreateSkill() {
  emit('create-skill', props.node?.data)
  emit('update:visible', false)
}

function handleCreateSkillPackage() {
  emit('create-skill-package')
  emit('update:visible', false)
}

function handleUploadSkillPackage() {
  emit('upload-skill-package')
  emit('update:visible', false)
}

function handleUploadSkill() {
  emit('upload-skill', props.node?.data)
  emit('update:visible', false)
}

function handleCreateDescription() {
  emit('create-description', props.node?.data)
  emit('update:visible', false)
}

function handleRename() {
  emit('rename', props.node?.data)
  emit('update:visible', false)
}

function handleDelete() {
  emit('delete', props.node?.data)
  emit('update:visible', false)
}

// 点击外部关闭菜单
watch(() => props.visible, (newVal) => {
  if (newVal) {
    const closeMenu = () => {
      emit('update:visible', false)
      document.removeEventListener('click', closeMenu)
    }
    // 延迟添加监听，避免立即触发
    setTimeout(() => {
      document.addEventListener('click', closeMenu)
    }, 100)
  }
})
</script>

<style lang="scss" scoped>
.context-menu {
  position: fixed;
  background: white;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
  padding: 6px 0;
  z-index: 9999;
  min-width: 160px;
  
  .menu-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 16px;
    cursor: pointer;
    font-size: 14px;
    transition: background-color 0.2s;
    
    &:hover {
      background-color: #f5f7fa;
    }
    
    &.danger {
      color: #f56c6c;
      
      &:hover {
        background-color: #fef0f0;
      }
    }
    
    .el-icon {
      font-size: 16px;
    }
  }
  
  .menu-divider {
    height: 1px;
    background-color: #e4e7ed;
    margin: 6px 0;
  }
}
</style>
