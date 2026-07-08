<template>
  <div class="app-container skill-manager">
    <el-container class="skill-container">
      <!-- 左侧技能树 -->
      <el-aside width="300px" class="skill-tree-aside">
        <SkillTree
          @node-click="handleNodeClick"
          @create-folder="handleCreateFolder"
          @create-file="handleCreateFile"
          @delete-node="handleDeleteNode"
          @rename-node="handleRenameNode"
        />
      </el-aside>
      
      <!-- 右侧编辑器 -->
      <el-main class="skill-editor-main">
        <SkillEditor
          :current-file="currentFile"
          @save="handleSave"
          @close="handleClose"
        />
      </el-main>
    </el-container>
  </div>
</template>

<script setup name="SkillManager">
import SkillTree from './components/SkillTree.vue'
import SkillEditor from './components/SkillEditor.vue'
import { ElMessage } from 'element-plus'

const currentFile = ref(null)

/** 节点点击 */
function handleNodeClick(node) {
  // 只有文件才能打开编辑
  if (!node.isDirectory) {
    currentFile.value = node
  }
}

/** 创建文件夹 */
function handleCreateFolder(parentNode) {
  // 事件由SkillTree内部处理
}

/** 创建文件 */
function handleCreateFile(parentNode) {
  // 事件由SkillTree内部处理
}

/** 删除节点 */
function handleDeleteNode(node) {
  // 如果删除的是当前打开的文件，关闭编辑器
  if (currentFile.value && currentFile.value.id === node.id) {
    currentFile.value = null
  }
}

/** 重命名节点 */
function handleRenameNode(node) {
  // 如果重命名的是当前打开的文件，更新引用
  if (currentFile.value && currentFile.value.id === node.id) {
    currentFile.value = { ...node }
  }
}

/** 保存文件 */
function handleSave(file) {
  ElMessage.success('保存成功')
  // 更新当前文件
  currentFile.value = { ...file }
}

/** 关闭文件 */
function handleClose() {
  currentFile.value = null
}
</script>

<style lang="scss" scoped>
.skill-manager {
  height: calc(100vh - 84px);
  padding: 0;
  
  .skill-container {
    height: 100%;
    
    .skill-tree-aside {
      border-right: 1px solid #e4e7ed;
      background-color: #fff;
      overflow: hidden;
    }
    
    .skill-editor-main {
      padding: 0;
      background-color: #f5f7fa;
      overflow: hidden;
    }
  }
}
</style>
