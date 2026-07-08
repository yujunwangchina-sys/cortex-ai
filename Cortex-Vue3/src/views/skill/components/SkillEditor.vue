<template>
  <div class="skill-editor">
    <!-- 空状态 -->
    <div v-if="!currentFile" class="empty-state">
      <el-empty description="请从左侧选择一个文件进行编辑">
        <template #image>
          <el-icon :size="100" color="#909399">
            <Document />
          </el-icon>
        </template>
      </el-empty>
    </div>
    
    <!-- 编辑器 -->
    <div v-else class="editor-container">
      <!-- 工具栏 -->
      <div class="editor-toolbar">
        <div class="file-info">
          <el-icon class="file-icon" :style="{ color: getFileIconColor() }">
            <component :is="getFileIcon()" />
          </el-icon>
          <span class="file-name">{{ currentFile.name }}</span>
          <el-tag v-if="isModified" type="warning" size="small" effect="dark">
            未保存
          </el-tag>
          <el-tag :type="getFileTypeTagType()" size="small" effect="plain">{{ getFileTypeLabel() }}</el-tag>
        </div>
        <div class="toolbar-actions">
          <el-button
            type="success"
            size="default"
            :icon="saving ? '' : 'CircleCheck'"
            :loading="saving"
            :disabled="!isModified"
            @click="handleSave"
          >
            {{ saving ? '保存中' : '保存' }}
          </el-button>
          <el-button
            size="default"
            :icon="CloseBold"
            @click="handleClose"
          >
            关闭
          </el-button>
        </div>
      </div>
      
      <!-- 编辑区域 -->
      <div class="editor-content">
        <!-- Markdown文件 - 使用md-editor-v3 -->
        <MarkdownEditor
          v-if="isMarkdownFile"
          v-model="fileContent"
          @change="handleContentChange"
          @save="handleSave"
          @insert-reference="handleInsertReference"
        />
        
        <!-- 代码文件 - 使用Monaco Editor -->
        <CodeEditor
          v-else-if="isCodeFile"
          v-model="fileContent"
          :language="getFileLanguage()"
          :theme="editorTheme"
          @change="handleContentChange"
        />
        
        <!-- 普通文本文件 -->
        <div v-else class="plain-editor">
          <el-input
            v-model="fileContent"
            type="textarea"
            :rows="30"
            placeholder="请输入文件内容"
            @input="handleContentChange"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getFileContent, saveFileContent } from '@/api/skill/skill'
import MarkdownEditor from './SimpleMarkdownEditor.vue'
import CodeEditor from './CodeEditor.vue'
import { 
  Document, Memo, DocumentCopy, 
  Cpu, Connection, Files,
  EditPen, CircleCheck, CloseBold
} from '@element-plus/icons-vue'

const props = defineProps({
  currentFile: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['save', 'close'])

const fileContent = ref('')
const originalContent = ref('')
const isModified = ref(false)
const saving = ref(false)
const editorTheme = ref('vs-dark') // vs, vs-dark, hc-black

// 文件类型判断
const isMarkdownFile = computed(() => {
  return props.currentFile?.name?.endsWith('.md')
})

// 代码文件扩展名列表
const CODE_FILE_EXTENSIONS = [
  '.js', '.jsx', '.ts', '.tsx',
  '.py', '.java', '.c', '.cpp', '.h', '.hpp',
  '.cs', '.go', '.rs', '.php',
  '.rb', '.swift', '.kt', '.scala',
  '.html', '.css', '.scss', '.sass', '.less',
  '.json', '.xml', '.yaml', '.yml',
  '.sql', '.sh', '.bat', '.ps1',
  '.r', '.m', '.lua'
]

const isCodeFile = computed(() => {
  if (!props.currentFile?.name) return false
  const ext = props.currentFile.name.substring(props.currentFile.name.lastIndexOf('.')).toLowerCase()
  return CODE_FILE_EXTENSIONS.includes(ext)
})

// 获取文件语言类型（用于Monaco Editor）
function getFileLanguage() {
  if (!props.currentFile?.name) return 'plaintext'
  
  const ext = props.currentFile.name.substring(props.currentFile.name.lastIndexOf('.')).toLowerCase()
  
  const languageMap = {
    '.js': 'javascript',
    '.jsx': 'javascript',
    '.ts': 'typescript',
    '.tsx': 'typescript',
    '.py': 'python',
    '.java': 'java',
    '.c': 'c',
    '.cpp': 'cpp',
    '.h': 'cpp',
    '.hpp': 'cpp',
    '.cs': 'csharp',
    '.go': 'go',
    '.rs': 'rust',
    '.php': 'php',
    '.rb': 'ruby',
    '.swift': 'swift',
    '.kt': 'kotlin',
    '.scala': 'scala',
    '.html': 'html',
    '.css': 'css',
    '.scss': 'scss',
    '.sass': 'sass',
    '.less': 'less',
    '.json': 'json',
    '.xml': 'xml',
    '.yaml': 'yaml',
    '.yml': 'yaml',
    '.sql': 'sql',
    '.sh': 'shell',
    '.bat': 'bat',
    '.ps1': 'powershell',
    '.r': 'r',
    '.m': 'objective-c',
    '.lua': 'lua'
  }
  
  return languageMap[ext] || 'plaintext'
}

// 获取文件图标
function getFileIcon() {
  if (!props.currentFile?.name) return Document
  
  const name = props.currentFile.name.toLowerCase()
  
  if (name.endsWith('.md')) return Memo
  if (name.endsWith('.json') || name.endsWith('.xml') || name.endsWith('.yaml') || name.endsWith('.yml')) return DocumentCopy
  if (name.endsWith('.js') || name.endsWith('.ts') || name.endsWith('.py') || name.endsWith('.java')) return Cpu
  if (name === 'skill.md' || name === 'description.md') return Connection
  
  return Files
}

// 获取文件图标颜色
function getFileIconColor() {
  if (!props.currentFile?.name) return '#909399'
  
  const name = props.currentFile.name.toLowerCase()
  
  if (name.endsWith('.md')) return '#1890ff'
  if (name.endsWith('.js') || name.endsWith('.ts')) return '#f7df1e'
  if (name.endsWith('.py')) return '#3776ab'
  if (name.endsWith('.java')) return '#007396'
  if (name.endsWith('.json')) return '#52c41a'
  
  return '#606266'
}

// 获取文件类型标签
function getFileTypeLabel() {
  if (isMarkdownFile.value) return 'Markdown'
  if (isCodeFile.value) {
    const lang = getFileLanguage()
    return lang.toUpperCase()
  }
  return 'Text'
}

function getFileTypeTagType() {
  if (isMarkdownFile.value) return 'primary'
  if (isCodeFile.value) return 'success'
  return 'info'
}

/** 加载文件内容 */
function loadFileContent() {
  if (!props.currentFile) return
  
  getFileContent(props.currentFile.path).then(response => {
    fileContent.value = response.data?.content || ''
    originalContent.value = fileContent.value
    isModified.value = false
  }).catch(() => {
    ElMessage.error('加载文件内容失败')
  })
}

/** 内容变化 */
function handleContentChange() {
  isModified.value = fileContent.value !== originalContent.value
}

/** 保存文件 */
function handleSave() {
  saving.value = true
  const data = {
    id: props.currentFile.id,
    path: props.currentFile.path,
    content: fileContent.value
  }
  
  saveFileContent(data).then(() => {
    ElMessage.success('保存成功')
    originalContent.value = fileContent.value
    isModified.value = false
    emit('save', props.currentFile)
  }).finally(() => {
    saving.value = false
  })
}

/** 关闭文件 */
function handleClose() {
  if (isModified.value) {
    ElMessageBox.confirm(
      '文件已修改，是否保存？',
      '提示',
      {
        confirmButtonText: '保存',
        cancelButtonText: '不保存',
        distinguishCancelAndClose: true,
        type: 'warning'
      }
    ).then(() => {
      // 保存后关闭
      handleSave()
      emit('close')
    }).catch(action => {
      if (action === 'cancel') {
        // 不保存直接关闭
        emit('close')
      }
      // 点击右上角关闭不做任何操作
    })
  } else {
    emit('close')
  }
}

/** 插入引用 */
function handleInsertReference(reference) {
  // 在光标位置插入引用
  fileContent.value += reference
  handleContentChange()
}

// 监听文件变化
watch(() => props.currentFile, (newFile, oldFile) => {
  if (newFile && newFile.id !== oldFile?.id) {
    // 如果旧文件有未保存的修改，提示保存
    if (isModified.value && oldFile) {
      ElMessageBox.confirm(
        `文件 "${oldFile.name}" 已修改，是否保存？`,
        '提示',
        {
          confirmButtonText: '保存',
          cancelButtonText: '不保存',
          distinguishCancelAndClose: true,
          type: 'warning'
        }
      ).then(() => {
        // 保存旧文件
        const data = {
          id: oldFile.id,
          path: oldFile.path,
          content: fileContent.value
        }
        saveFileContent(data).then(() => {
          ElMessage.success('保存成功')
          loadFileContent()
        })
      }).catch(action => {
        if (action === 'cancel') {
          loadFileContent()
        }
      })
    } else {
      loadFileContent()
    }
  }
}, { immediate: true })
</script>

<style lang="scss" scoped>
.skill-editor {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: white;
  
  .empty-state {
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
  }
  
  .editor-container {
    height: 100%;
    display: flex;
    flex-direction: column;
    
    .editor-toolbar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 12px 16px;
      border-bottom: 1px solid #e4e7ed;
      background: white;
      flex-shrink: 0;
      
      .file-info {
        display: flex;
        align-items: center;
        gap: 8px;
        
        .file-icon {
          font-size: 20px;
        }
        
        .file-name {
          font-size: 14px;
          font-weight: 500;
          color: #303133;
        }
      }
      
      .toolbar-actions {
        display: flex;
        gap: 8px;
      }
    }
    
    .editor-content {
      flex: 1;
      overflow: hidden;
      
      .plain-editor {
        height: 100%;
        padding: 16px;
        
        :deep(.el-textarea__inner) {
          font-family: 'Courier New', monospace;
          font-size: 14px;
          line-height: 1.6;
          height: 100%;
        }
      }
    }
  }
}
</style>
