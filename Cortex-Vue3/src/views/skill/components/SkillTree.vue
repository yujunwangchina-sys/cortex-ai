<template>
  <div class="skill-tree-container" @contextmenu.prevent="handleEmptyContextMenu">
    <!-- 顶部过滤栏 -->
    <div class="filter-bar">
      <el-segmented v-model="filterType" :options="filterOptions" size="default" />
    </div>
    
    <!-- 树形结构 -->
    <div class="tree-content">
      <el-tree
        ref="treeRef"
        :data="treeData"
        :props="treeProps"
        node-key="id"
        :expand-on-click-node="false"
        :default-expand-all="false"
        :highlight-current="true"
        :indent="20"
        draggable
        @node-click="handleNodeClick"
        @node-contextmenu="handleContextMenu"
        @node-drop="handleNodeDrop"
      >
        <template #default="{ node, data }">
          <div class="custom-tree-node">
            <!-- 文件夹图标 -->
            <el-icon v-if="data.isDirectory" class="node-icon" :class="getIconClass(data)">
              <component :is="getFolderIcon(node.expanded, data)" />
            </el-icon>
            <!-- 文件图标 -->
            <el-icon v-else class="node-icon" :class="getIconClass(data)">
              <component :is="getFileIcon(data)" />
            </el-icon>
            <!-- 文件/文件夹名称 -->
            <span class="node-label" :title="data.name">{{ data.name }}</span>
            <!-- 技能包类型标签 -->
            <el-tag
              v-if="data.nodeType === 'skill_package'"
              :type="getSkillPackageTagType(data)"
              size="small"
              class="package-tag"
            >
              {{ getSkillPackageLabel(data) }}
            </el-tag>
          </div>
        </template>
      </el-tree>
    </div>
    
    <!-- 右键菜单 -->
    <ContextMenu
      v-model:visible="contextMenuVisible"
      :x="contextMenuX"
      :y="contextMenuY"
      :node="contextMenuNode"
      @create-folder="handleCreateFolder"
      @create-file="handleCreateFile"
      @create-skill="handleCreateSkill"
      @create-skill-package="handleCreateRootSkillPackage"
      @create-description="handleCreateDescription"
      @rename="handleRename"
      @delete="handleDelete"
      @upload-skill-package="handleUploadSkillPackage"
      @upload-skill="handleUploadSkill"
    />
    
    <!-- 新建/重命名对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="520px"
      @close="resetDialog"
      :close-on-click-modal="false"
    >
      <el-form :model="dialogForm" :rules="dialogRules" ref="dialogFormRef" label-width="100px">
        <el-form-item label="名称" prop="name">
          <el-input
            v-model="dialogForm.name"
            placeholder="请输入名称"
            @keyup.enter="submitDialog"
            clearable
          />
        </el-form-item>
        <el-form-item v-if="dialogType === 'createFile'" label="文件类型" prop="fileType">
          <el-select v-model="dialogForm.fileType" placeholder="请选择文件类型" style="width: 100%">
            <el-option label="Markdown文件 (.md)" value=".md" />
            <el-option label="JavaScript文件 (.js)" value=".js" />
            <el-option label="Python文件 (.py)" value=".py" />
            <el-option label="JSON文件 (.json)" value=".json" />
            <el-option label="文本文件 (.txt)" value=".txt" />
            <el-option label="其他" value="" />
          </el-select>
        </el-form-item>
        <!-- 技能包类型选择 -->
        <el-form-item v-if="dialogType === 'createSkillPackage'" label="技能包类型" prop="skillScope" class="skill-type-form-item">
          <div class="skill-type-selector">
            <div 
              :class="['skill-type-card', { active: dialogForm.skillScope === 'global' }]"
              @click="dialogForm.skillScope = 'global'"
            >
              <div class="card-header">
                <el-tag type="success" size="small">全局</el-tag>
                <el-icon v-if="dialogForm.skillScope === 'global'" class="check-icon"><CircleCheck /></el-icon>
              </div>
              <div class="card-desc">所有Agent可用</div>
            </div>
            <div 
              :class="['skill-type-card', { active: dialogForm.skillScope === 'personal' }]"
              @click="dialogForm.skillScope = 'personal'"
            >
              <div class="card-header">
                <el-tag type="info" size="small">个人</el-tag>
                <el-icon v-if="dialogForm.skillScope === 'personal'" class="check-icon"><CircleCheck /></el-icon>
              </div>
              <div class="card-desc">个人可用（需指定业务系统+用户）</div>
            </div>
          </div>
        </el-form-item>
        <!-- 业务系统选择（仅个人类型需要） -->
        <el-form-item v-if="dialogType === 'createSkillPackage' && dialogForm.skillScope === 'personal'" label="业务系统" prop="businessSystem">
          <el-select v-model="dialogForm.businessSystem" placeholder="请选择业务系统" style="width: 100%" clearable>
            <el-option
              v-for="dict in businessSystemOptions"
              :key="dict.value"
              :label="dict.label"
              :value="dict.value"
            />
          </el-select>
        </el-form-item>
        <!-- 登录账号（仅个人类型） -->
        <el-form-item v-if="dialogType === 'createSkillPackage' && dialogForm.skillScope === 'personal'" label="登录账号" prop="ownerUser">
          <el-input v-model="dialogForm.ownerUser" placeholder="请输入登录账号" clearable />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitDialog">确定</el-button>
      </template>
    </el-dialog>

    <!-- 上传技能包对话框 -->
    <el-dialog
      v-model="uploadDialogVisible"
      title="上传技能包"
      width="600px"
      :close-on-click-modal="false"
      @close="resetUploadDialog"
    >
      <el-form :model="uploadForm" :rules="uploadRules" ref="uploadFormRef" label-width="100px">
        <el-form-item label="压缩包" prop="file">
          <el-upload
            ref="uploadRef"
            action="#"
            :limit="1"
            :auto-upload="false"
            :on-change="handleFileChange"
            :on-remove="handleFileRemove"
            :file-list="uploadFileList"
            accept=".zip"
            drag
          >
            <el-icon class="el-icon--upload"><upload-filled /></el-icon>
            <div class="el-upload__text">
              拖拽文件到此处或 <em>点击上传</em>
            </div>
            <template #tip>
              <div class="el-upload__tip">
                只支持 .zip 格式，文件仅允许包含 .md, .py, .js, .txt, .json
              </div>
            </template>
          </el-upload>
        </el-form-item>

        <el-form-item label="技能包类型" prop="skillScope">
          <el-radio-group v-model="uploadForm.skillScope">
            <div class="upload-type-options">
              <el-radio value="global" class="type-option">
                <div class="type-content">
                  <el-tag type="success" size="small">全局</el-tag>
                  <span class="type-desc">所有Agent可用</span>
                </div>
              </el-radio>
              <el-radio value="personal" class="type-option">
                <div class="type-content">
                  <el-tag type="info" size="small">个人</el-tag>
                  <span class="type-desc">个人可用（需指定业务系统+用户）</span>
                </div>
              </el-radio>
            </div>
          </el-radio-group>
        </el-form-item>

        <el-form-item 
          v-if="uploadForm.skillScope === 'personal'" 
          label="业务系统" 
          prop="businessSystem"
        >
          <el-select v-model="uploadForm.businessSystem" placeholder="请选择业务系统" style="width: 100%" clearable>
            <el-option
              v-for="dict in businessSystemOptions"
              :key="dict.value"
              :label="dict.label"
              :value="dict.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item v-if="uploadForm.skillScope === 'personal'" label="所有者" prop="ownerUser">
          <el-input v-model="uploadForm.ownerUser" placeholder="默认当前用户" clearable />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="uploadDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="submitUpload">
          <el-icon v-if="!uploading"><Upload /></el-icon>
          {{ uploading ? '上传中...' : '上传' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 上传技能对话框 -->
    <el-dialog
      v-model="uploadSkillDialogVisible"
      title="上传技能"
      width="600px"
      @close="resetUploadSkillDialog"
      :close-on-click-modal="false"
    >
      <el-alert
        title="上传单个技能的ZIP压缩包，会添加到当前技能包下"
        type="info"
        :closable="false"
        style="margin-bottom: 16px;"
      />
      
      <el-form ref="uploadSkillFormRef" :model="uploadSkillForm" label-width="100px">
        <el-form-item label="技能包" prop="parentName">
          <el-input v-model="uploadSkillForm.parentName" disabled />
        </el-form-item>
        
        <el-form-item label="选择文件" prop="file" required>
          <el-upload
            ref="uploadSkillRef"
            :auto-upload="false"
            :limit="1"
            accept=".zip"
            :on-change="handleSkillFileChange"
            :file-list="uploadSkillFileList"
            drag
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">
              拖拽文件到此处，或<em>点击选择</em>
            </div>
            <template #tip>
              <div class="el-upload__tip">
                只支持 .zip 格式的压缩包
              </div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="uploadSkillDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploadingSkill" @click="submitUploadSkill">
          <el-icon v-if="!uploadingSkill"><Upload /></el-icon>
          {{ uploadingSkill ? '上传中...' : '上传' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed, watch, getCurrentInstance } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CircleCheck, Upload, UploadFilled } from '@element-plus/icons-vue'
import { getSkillTree, createFolder, createFile, deleteNode, renameNode, moveNode, uploadSkillPackage, uploadSingleSkill } from '@/api/skill/skill'
import ContextMenu from './ContextMenu.vue'
import useUserStore from '@/store/modules/user'

const emit = defineEmits(['node-click', 'create-folder', 'create-file', 'delete-node', 'rename-node'])
const userStore = useUserStore()
const { proxy } = getCurrentInstance()

// 业务系统字典选项
const { sys_b_system } = proxy.useDict('sys_b_system')
const businessSystemOptions = computed(() => sys_b_system.value || [])

const treeRef = ref(null)
const rawTreeData = ref([]) // 原始树数据
const filterType = ref('global') // 过滤类型：global/personal
const filterOptions = [
  { label: '全局技能', value: 'global' },
  { label: '个人技能', value: 'personal' }
]

// 过滤后的树数据
const treeData = computed(() => {
  return rawTreeData.value.filter(node => {
    if (node.nodeType !== 'skill_package') return true
    
    const skillScope = node.skillScope || 'global'
    
    if (filterType.value === 'global') {
      return skillScope === 'global'
    } else if (filterType.value === 'personal') {
      return skillScope === 'personal'
    }
    return false
  })
})

const treeProps = {
  children: 'children',
  label: 'name',
  isLeaf: (data) => !data.isDirectory
}

// 右键菜单相关
const contextMenuVisible = ref(false)
const contextMenuX = ref(0)
const contextMenuY = ref(0)
const contextMenuNode = ref(null)

// 对话框相关
const dialogVisible = ref(false)
const dialogTitle = ref('')
const dialogType = ref('') // createFolder, createFile, createSkillPackage, rename
const dialogForm = reactive({
  name: '',
  fileType: '.md',
  skillScope: 'global', // global(全局), personal(个人)
  businessSystem: '', // 业务系统标识（个人技能需要）
  ownerUser: '' // 个人技能的所有者
})
const dialogFormRef = ref(null)
const dialogRules = {
  name: [
    { required: true, message: '请输入名称', trigger: 'blur' },
    { 
      pattern: /^[^\\/:*?"<>|]+$/, 
      message: '名称不能包含特殊字符 \\ / : * ? " < > |', 
      trigger: 'blur' 
    }
  ],
  businessSystem: [
    { required: true, message: '请选择业务系统', trigger: 'change' }
  ],
  ownerUser: [
    { required: true, message: '请输入登录账号', trigger: 'blur' }
  ]
}

const currentParentNode = ref(null)
const currentEditNode = ref(null)

/** 获取技能包标签类型 */
function getSkillPackageTagType(data) {
  const skillScope = data.skillScope || 'global'
  
  if (skillScope === 'global') {
    return 'success' // 全局
  } else if (skillScope === 'personal') {
    return 'info' // 个人
  }
  return ''
}

/** 获取技能包标签文本 */
function getSkillPackageLabel(data) {
  const skillScope = data.skillScope || 'global'
  
  if (skillScope === 'global') {
    return '全局'
  } else if (skillScope === 'personal') {
    const bizSys = data.businessSystem || ''
    const owner = data.ownerUser || ''
    return `个人${bizSys ? `(${bizSys})` : ''}${owner ? `@${owner}` : ''}`
  }
  return ''
}

/** 获取文件夹图标 */
function getFolderIcon(expanded, data) {
  if (data.nodeType === 'skill_package') {
    return expanded ? 'FolderOpened' : 'Box'
  }
  if (data.nodeType === 'skill') {
    return expanded ? 'FolderOpened' : 'Folder'
  }
  return expanded ? 'FolderOpened' : 'Folder'
}

/** 获取文件图标 */
function getFileIcon(data) {
  const ext = data.fileExtension?.toLowerCase()
  
  // 根据文件扩展名返回不同图标
  if (ext === '.md') return 'Memo'
  if (['.js', '.ts', '.jsx', '.tsx'].includes(ext)) return 'DocumentCopy'
  if (['.py', '.java', '.c', '.cpp'].includes(ext)) return 'Cpu'
  if (['.json', '.xml', '.yaml', '.yml'].includes(ext)) return 'Document'
  if (['.png', '.jpg', '.jpeg', '.gif', '.svg'].includes(ext)) return 'Picture'
  
  return 'Document'
}

/** 获取图标样式类 */
function getIconClass(data) {
  if (data.isDirectory) {
    if (data.nodeType === 'skill_package') return 'icon-skill-package'
    if (data.nodeType === 'skill') return 'icon-skill'
    return 'icon-folder'
  }
  
  const ext = data.fileExtension?.toLowerCase()
  if (ext === '.md') return 'icon-markdown'
  if (['.js', '.ts', '.jsx', '.tsx'].includes(ext)) return 'icon-javascript'
  if (['.py'].includes(ext)) return 'icon-python'
  if (['.java'].includes(ext)) return 'icon-java'
  
  return 'icon-file'
}

/** 加载树数据 */
function loadTree() {
  getSkillTree().then(response => {
    rawTreeData.value = response.data || []
  }).catch(() => {
    ElMessage.error('加载技能树失败')
  })
}

/** 空白区域右键菜单 */
function handleEmptyContextMenu(event) {
  // 检查是否点击在空白区域（不是树节点上）
  const target = event.target
  if (!target.closest('.el-tree-node')) {
    contextMenuVisible.value = true
    contextMenuX.value = event.clientX
    contextMenuY.value = event.clientY
    contextMenuNode.value = { data: { isEmpty: true } } // 标记为空白区域
  }
}

/** 创建根级技能包 */
function handleCreateRootSkillPackage() {
  dialogType.value = 'createSkillPackage'
  dialogTitle.value = '新建技能包'
  currentParentNode.value = null
  dialogForm.name = ''
  dialogForm.skillScope = 'global'
  dialogForm.businessSystem = ''
  dialogVisible.value = true
}

/** 节点点击 */
function handleNodeClick(data, node) {
  // 设置当前选中节点
  treeRef.value.setCurrentKey(data.id)
  // 触发事件
  emit('node-click', data)
}

/** 右键菜单 */
function handleContextMenu(event, data, node) {
  event.preventDefault()
  
  // 如果是文件节点（有扩展名），只显示重命名和删除
  // 如果是目录节点，显示完整菜单
  contextMenuVisible.value = true
  contextMenuX.value = event.clientX
  contextMenuY.value = event.clientY
  contextMenuNode.value = { data, node }
}

/** 创建根文件夹 */
function handleCreateRootFolder() {
  showDialog('createFolder', '新建文件夹', null)
}

/** 创建根文件 */
function handleCreateRootFile() {
  showDialog('createFile', '新建文件', null)
}

/** 创建文件夹（右键） */
function handleCreateFolder(nodeData) {
  showDialog('createFolder', '新建文件夹', nodeData)
}

/** 创建文件（右键） */
function handleCreateFile(nodeData) {
  showDialog('createFile', '新建文件', nodeData)
}

/** 创建技能（右键 - 在技能包下）*/
function handleCreateSkill(nodeData) {
  ElMessageBox.prompt('请输入技能名称（例如：apple-notes）', '新建技能', {
    confirmButtonText: '创建',
    cancelButtonText: '取消',
    inputPattern: /^[a-zA-Z0-9_-]+$/,
    inputErrorMessage: '技能名称只能包含字母、数字、下划线和连字符'
  }).then(({ value }) => {
    const data = {
      name: value,
      parentId: nodeData.id,
      isDirectory: true,
      nodeType: 'skill'
    }
    createFolder(data).then((response) => {
      ElMessage.success('技能创建成功')
      
      // 检查响应数据
      if (!response || !response.data || !response.data.id) {
        ElMessage.error('创建技能成功，但无法自动创建 SKILL.md（响应数据异常）')
        loadTree()
        return
      }
      
      // 自动创建SKILL.md文件
      const author = userStore.user?.nickName || userStore.user?.userName || 'Unknown'
      const skillMdData = {
        name: 'SKILL.md',
        parentId: response.data.id,
        content: `---
name: ${value}
description: "${value}技能描述"
version: 1.0.0
author: ${author}
license: MIT
platforms: []
metadata:
  hermes:
    tags: []
prerequisites:
  commands: []
---

# ${value} Skill

技能说明文档

## 功能特性

- 功能1
- 功能2

## 使用方法

\`\`\`bash
# 示例代码
\`\`\`

## 支持的平台

- 平台1

## 相关引用

参考其他技能: @file[文件名](路径)
使用插件: @plugin[插件名](插件名)
`
      }
      
      createFile(skillMdData).then(() => {
        ElMessage.success('SKILL.md 已自动创建')
        loadTree()
      })
    })
  }).catch(() => {})
}

/** 创建DESCRIPTION.md（右键 - 在技能包下）*/
function handleCreateDescription(nodeData) {
  const data = {
    name: 'DESCRIPTION.md',
    parentId: nodeData.id,
    content: `# ${nodeData.name} Skills Package

技能包描述

## 包含的技能

- **技能1**: 功能说明
- **技能2**: 功能说明

## 作者



## 许可证

MIT
`
  }
  
  createFile(data).then(() => {
    ElMessage.success('DESCRIPTION.md 创建成功')
    loadTree()
  }).catch(err => {
    ElMessage.error(err.message || '创建失败')
  })
}

/** 重命名（右键） */
function handleRename(nodeData) {
  currentEditNode.value = nodeData
  dialogForm.name = nodeData.name.replace(/\.[^.]+$/, '') // 去除扩展名
  showDialog('rename', '重命名', null)
}

/** 删除（右键） */
function handleDelete(nodeData) {
  ElMessageBox.confirm(
    `确定要删除 "${nodeData.name}" 吗？${nodeData.isDirectory ? '此操作将删除文件夹及其所有内容。' : ''}`,
    '警告',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(() => {
    deleteNode(nodeData.id).then(() => {
      ElMessage.success('删除成功')
      loadTree()
      emit('delete-node', nodeData)
    })
  }).catch(() => {})
}

/** 显示对话框 */
function showDialog(type, title, parentNode) {
  dialogType.value = type
  dialogTitle.value = title
  currentParentNode.value = parentNode
  dialogForm.name = ''
  dialogForm.fileType = '.md'
  dialogVisible.value = true
}

/** 提交对话框 */
function submitDialog() {
  dialogFormRef.value.validate(valid => {
    if (valid) {
      if (dialogType.value === 'createFolder') {
        doCreateFolder()
      } else if (dialogType.value === 'createFile') {
        doCreateFile()
      } else if (dialogType.value === 'createSkillPackage') {
        doCreateSkillPackage()
      } else if (dialogType.value === 'rename') {
        doRename()
      }
    }
  })
}

/** 执行创建技能包 */
function doCreateSkillPackage() {
  const data = {
    name: dialogForm.name,
    parentId: null,
    isDirectory: true,
    nodeType: 'skill_package',
    skillScope: dialogForm.skillScope,
    businessSystem: dialogForm.skillScope === 'personal' ? dialogForm.businessSystem : null,
    ownerUser: dialogForm.skillScope === 'personal' ? dialogForm.ownerUser : null
  }
  
  createFolder(data).then(() => {
    ElMessage.success('技能包创建成功')
    dialogVisible.value = false
    loadTree()
  }).catch(() => {
    ElMessage.error('创建失败')
  })
}

/** 执行创建文件夹 */
function doCreateFolder() {
  const data = {
    name: dialogForm.name,
    parentId: currentParentNode.value?.id || null
  }
  createFolder(data).then(() => {
    ElMessage.success('创建成功')
    dialogVisible.value = false
    loadTree()
    emit('create-folder', currentParentNode.value)
  })
}

/** 执行创建文件 */
function doCreateFile() {
  let fileName = dialogForm.name
  // 如果选择了文件类型且名称不包含扩展名，自动添加
  if (dialogForm.fileType && !fileName.includes('.')) {
    fileName += dialogForm.fileType
  }
  
  // 根据文件类型自动填充内容模板
  let defaultContent = ''
  
  // 如果是Markdown文件，自动添加YAML frontmatter
  if (fileName.toLowerCase().endsWith('.md')) {
    const fileNameWithoutExt = fileName.replace(/\.md$/i, '')
    const author = userStore.user?.nickName || userStore.user?.userName || 'Unknown'
    defaultContent = `---
name: ${fileNameWithoutExt}
description: "${fileNameWithoutExt}文档说明"
version: 1.0.0
author: ${author}
date: ${new Date().toISOString().split('T')[0]}
---

# ${fileNameWithoutExt}

文档内容

## 简介

在此填写文档内容...

## 详细说明

### 小节1

内容...

### 小节2

内容...

## 参考资料

- 参考链接1
- 参考链接2
`
  }
  
  const data = {
    name: fileName,
    parentId: currentParentNode.value?.id || null,
    content: defaultContent
  }
  createFile(data).then(() => {
    ElMessage.success('创建成功')
    dialogVisible.value = false
    loadTree()
    emit('create-file', currentParentNode.value)
  })
}

/** 执行重命名 */
function doRename() {
  let newName = dialogForm.name
  // 保留原扩展名
  const oldExt = currentEditNode.value.name.match(/\.[^.]+$/)
  if (oldExt && !newName.includes('.')) {
    newName += oldExt[0]
  }
  
  const data = {
    id: currentEditNode.value.id,
    name: newName
  }
  renameNode(data).then(() => {
    ElMessage.success('重命名成功')
    dialogVisible.value = false
    loadTree()
    emit('rename-node', { ...currentEditNode.value, name: newName })
  })
}

/** 重置对话框 */
function resetDialog() {
  dialogForm.name = ''
  dialogForm.fileType = '.md'
  dialogForm.skillScope = 'global'
  dialogForm.businessSystem = ''
  dialogForm.ownerUser = ''
  currentParentNode.value = null
  currentEditNode.value = null
}

/** 节点拖拽 */
function handleNodeDrop(draggingNode, dropNode, dropType) {
  // dropType: 'before' | 'after' | 'inner'
  const data = {
    id: draggingNode.data.id,
    targetId: dropNode.data.id,
    dropType: dropType
  }
  moveNode(data).then(() => {
    ElMessage.success('移动成功')
    loadTree()
  }).catch(() => {
    ElMessage.error('移动失败')
    loadTree() // 重新加载以恢复原状态
  })
}

onMounted(() => {
  loadTree()
})

// ==================== 上传技能包相关 ====================
// 上传对话框相关
const uploadDialogVisible = ref(false)
const uploadFileList = ref([])
const uploading = ref(false)
const uploadRef = ref(null)
const uploadFormRef = ref(null)
const uploadForm = reactive({
  file: null,
  skillScope: 'personal',
  businessSystem: '',
  ownerUser: ''
})

const uploadRules = {
  businessSystem: [
    { required: true, message: '请选择业务系统', trigger: 'change' }
  ],
  ownerUser: [
    { required: true, message: '请输入所有者', trigger: 'blur' }
  ]
}

/** 打开上传对话框 */
function handleUploadSkillPackage() {
  uploadForm.file = null
  uploadForm.skillScope = 'personal'
  uploadForm.businessSystem = ''
  uploadForm.ownerUser = userStore.user?.userName || ''
  uploadFileList.value = []
  uploadDialogVisible.value = true
}

/** 文件选择变化 */
function handleFileChange(file, fileList) {
  uploadForm.file = file.raw
  uploadFileList.value = fileList
}

/** 文件移除 */
function handleFileRemove() {
  uploadForm.file = null
  uploadFileList.value = []
}

/** 提交上传 */
async function submitUpload() {
  if (!uploadForm.file) {
    ElMessage.warning('请选择要上传的技能包')
    return
  }

  if (!uploadForm.file.name.toLowerCase().endsWith('.zip')) {
    ElMessage.error('只支持 .zip 格式的压缩包')
    return
  }

  // 校验表单
  try {
    await uploadFormRef.value.validate()
  } catch {
    return
  }

  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', uploadForm.file)
    
    // skillScope直接使用，不再需要转换
    formData.append('skillScope', uploadForm.skillScope)
    
    // 业务系统（personal类型需要）
    if (uploadForm.skillScope === 'personal') {
      if (uploadForm.businessSystem) {
        formData.append('businessSystem', uploadForm.businessSystem)
      }
    }
    
    // 所有者（仅personal类型）
    if (uploadForm.skillScope === 'personal') {
      const owner = uploadForm.ownerUser || userStore.user?.userName || ''
      formData.append('ownerUser', owner)
    }

    await uploadSkillPackage(formData)
    ElMessage.success('技能包上传成功')
    uploadDialogVisible.value = false
    await loadTree() // 刷新树
  } catch (error) {
    console.error('上传失败:', error)
    ElMessage.error(error.msg || error.message || '上传失败')
  } finally {
    uploading.value = false
  }
}

/** 重置上传对话框 */
function resetUploadDialog() {
  uploadForm.file = null
  uploadForm.skillScope = 'personal'
  uploadForm.businessSystem = ''
  uploadForm.ownerUser = ''
  uploadFileList.value = []
  if (uploadFormRef.value) {
    uploadFormRef.value.resetFields()
  }
}

// ==================== 上传技能功能 ====================

const uploadSkillDialogVisible = ref(false)
const uploadSkillFormRef = ref(null)
const uploadSkillRef = ref(null)
const uploadingSkill = ref(false)
const uploadSkillFileList = ref([])
const uploadSkillForm = reactive({
  file: null,
  parentId: null,
  parentName: ''
})

/** 处理上传技能 */
function handleUploadSkill(nodeData) {
  currentParentNode.value = nodeData
  uploadSkillForm.parentId = nodeData.id
  uploadSkillForm.parentName = nodeData.name
  uploadSkillForm.file = null
  uploadSkillFileList.value = []
  uploadSkillDialogVisible.value = true
}

/** 技能文件选择变化 */
function handleSkillFileChange(file) {
  uploadSkillForm.file = file.raw
}

/** 提交上传技能 */
async function submitUploadSkill() {
  if (!uploadSkillForm.file) {
    ElMessage.error('请选择要上传的文件')
    return
  }

  if (!uploadSkillForm.file.name.toLowerCase().endsWith('.zip')) {
    ElMessage.error('只支持 .zip 格式的压缩包')
    return
  }

  uploadingSkill.value = true
  try {
    const formData = new FormData()
    formData.append('file', uploadSkillForm.file)
    formData.append('parentId', uploadSkillForm.parentId)

    // 调用上传技能API（需要新建）
    await uploadSingleSkill(formData)
    ElMessage.success('技能上传成功')
    uploadSkillDialogVisible.value = false
    await loadTree() // 刷新树
  } catch (error) {
    console.error('上传失败:', error)
    ElMessage.error(error.msg || error.message || '上传失败')
  } finally {
    uploadingSkill.value = false
  }
}

/** 重置上传技能对话框 */
function resetUploadSkillDialog() {
  uploadSkillForm.file = null
  uploadSkillForm.parentId = null
  uploadSkillForm.parentName = ''
  uploadSkillFileList.value = []
  if (uploadSkillFormRef.value) {
    uploadSkillFormRef.value.resetFields()
  }
}

// 暴露刷新方法给父组件
defineExpose({
  refresh: loadTree
})
</script>

<style lang="scss" scoped>
.skill-tree-container {
  height: 100%;
  display: flex;
  flex-direction: column;
  
  .filter-bar {
    padding: 12px;
    border-bottom: 1px solid #e4e7ed;
    background: #fafafa;
    
    :deep(.el-segmented) {
      width: 100%;
      --el-segmented-item-selected-bg-color: #1890ff;
      --el-segmented-item-selected-color: #ffffff;
      
      .el-segmented__item {
        flex: 1;
        min-width: 100px;
        justify-content: center;
      }
    }
  }
  
  .tree-content {
    flex: 1;
    overflow: auto;
    padding: 8px;
    background: #ffffff;
    
    :deep(.el-tree) {
      background: transparent;
      
      .el-tree-node {
        &:focus > .el-tree-node__content {
          background-color: transparent;
        }
      }
      
      .el-tree-node__content {
        height: 28px;
        border-radius: 4px;
        padding: 2px 4px;
        margin-bottom: 1px;
        transition: all 0.15s;
        
        &:hover {
          background-color: #f0f2f5;
        }
      }
      
      .el-tree-node.is-current > .el-tree-node__content {
        background-color: #e6f4ff !important;
        color: #096dd9;
        
        &:hover {
          background-color: #d6ebff !important;
        }
      }
      
      .el-tree-node__expand-icon {
        font-size: 12px;
        color: #8c8c8c;
        
        &.is-leaf {
          color: transparent;
        }
      }
    }
  }
  
  .custom-tree-node {
    display: flex;
    align-items: center;
    gap: 6px;
    flex: 1;
    
    .node-icon {
      flex-shrink: 0;
      font-size: 16px;
      
      // 技能包图标
      &.icon-skill-package {
        color: #d46b08;
      }
      
      // 技能图标
      &.icon-skill {
        color: #389e0d;
      }
      
      // 普通文件夹
      &.icon-folder {
        color: #d48806;
      }
      
      // Markdown文件
      &.icon-markdown {
        color: #0958d9;
      }
      
      // JavaScript/TypeScript
      &.icon-javascript {
        color: #d4b106;
      }
      
      // Python
      &.icon-python {
        color: #1765ad;
      }
      
      // Java
      &.icon-java {
        color: #0958d9;
      }
      
      // 普通文件
      &.icon-file {
        color: #595959;
      }
    }
    
    .node-label {
      flex: 1;
      font-size: 13px;
      line-height: 1.4;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      color: #262626;
      user-select: none;
    }
    
    .package-tag {
      margin-left: auto;
      font-size: 11px;
      padding: 0 6px;
      height: 18px;
      line-height: 18px;
      border-radius: 3px;
    }
  }
  
  // 拖拽时的样式
  :deep(.el-tree-node.is-drop-inner > .el-tree-node__content) {
    background-color: #e6f7ff;
    border: 1px dashed #1890ff;
  }
}

// 技能包类型选择器样式
.skill-type-form-item {
  :deep(.el-form-item__content) {
    line-height: normal;
  }
}

.skill-type-selector {
  display: flex;
  gap: 12px;
  margin-top: 4px;
}

.skill-type-card {
  flex: 1;
  padding: 16px 12px;
  border: 2px solid #e4e7ed;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s;
  background: #fafafa;
  
  &:hover {
    border-color: #c0c4cc;
    background: #f5f7fa;
    transform: translateY(-2px);
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  }
  
  &.active {
    border-color: #409eff;
    background: #ecf5ff;
    box-shadow: 0 2px 12px rgba(64, 158, 255, 0.2);
  }
  
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 8px;
    
    .check-icon {
      font-size: 18px;
      color: #67c23a;
    }
  }
  
  .card-desc {
    font-size: 12px;
    color: #909399;
    line-height: 1.5;
  }
}

// 上传对话框样式
.upload-type-options {
  display: flex;
  flex-direction: column;
  gap: 8px;
  
  .type-option {
    width: 100%;
    
    :deep(.el-radio__label) {
      width: 100%;
    }
    
    .type-content {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 4px 0;
      
      .type-desc {
        font-size: 12px;
        color: #909399;
      }
    }
  }
}
</style>
