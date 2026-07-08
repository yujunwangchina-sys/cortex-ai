<template>
  <div class="markdown-editor-wrapper">
    <!-- Markdown编辑器 -->
    <MdEditor
      ref="editorRef"
      v-model="localContent"
      :preview="true"
      :toolbars="toolbars"
      :theme="editorTheme"
      :preview-theme="previewTheme"
      :code-theme="codeTheme"
      language="zh-CN"
      :placeholder="placeholder"
      @on-save="handleSave"
      @on-upload-img="handleUploadImg"
      @onChange="handleChange"
    />
    
    <!-- @提及弹窗 -->
    <teleport to="body">
      <div
        v-if="showMentionMenu"
        class="mention-menu"
        :style="{ top: menuPosition.top + 'px', left: menuPosition.left + 'px' }"
      >
        <!-- 搜索框 -->
        <div class="mention-search">
          <el-input
            ref="searchInputRef"
            v-model="mentionSearch"
            size="small"
            placeholder="搜索..."
            prefix-icon="Search"
            @input="filterMentionList"
            @keydown="handleSearchKeydown"
          />
        </div>
        
        <!-- 分类标签 -->
        <div class="mention-tabs">
          <el-radio-group v-model="mentionType" size="small" @change="switchMentionType">
            <el-radio-button label="file">文件</el-radio-button>
            <el-radio-button label="plugin">插件</el-radio-button>
          </el-radio-group>
        </div>
        
        <!-- 列表 -->
        <div class="mention-list">
          <div
            v-for="(item, index) in filteredMentionList"
            :key="index"
            class="mention-item"
            :class="{ active: selectedIndex === index }"
            @click="selectMention(item)"
            @mouseenter="selectedIndex = index"
          >
            <el-icon v-if="mentionType === 'file'" class="item-icon">
              <Folder v-if="item.isDirectory" />
              <Document v-else-if="!item.name?.endsWith('.md')" />
              <Memo v-else />
            </el-icon>
            <el-icon v-else class="item-icon">
              <Connection />
            </el-icon>
            <div class="item-content">
              <div class="item-name">{{ mentionType === 'file' ? item.name : item.pluginName }}</div>
              <div v-if="mentionType === 'file'" class="item-path">{{ item.path }}</div>
              <div v-else class="item-desc">{{ item.description }}</div>
            </div>
          </div>
          <div v-if="filteredMentionList.length === 0" class="mention-empty">
            暂无数据
          </div>
        </div>
        
        <!-- 提示 -->
        <div class="mention-footer">
          <span>↑↓ 选择</span>
          <span>←→ 切换</span>
          <span>Enter 确认</span>
          <span>Esc 取消</span>
        </div>
      </div>
      
      <!-- 遮罩层 -->
      <div v-if="showMentionMenu" class="mention-overlay" @click="closeMentionMenu"></div>
    </teleport>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { MdEditor } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import { getFileList, getPluginList } from '@/api/skill/skill'
import { Connection, Folder, Document, Memo } from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  placeholder: { type: String, default: '请输入Markdown内容...\n\n💡 提示：输入 @ 可以引用文件或插件' }
})


const emit = defineEmits(['update:modelValue', 'change', 'save', 'insert-reference'])

const editorRef = ref(null)
const searchInputRef = ref(null)
const localContent = ref(props.modelValue)
const editorTheme = ref('light')
const previewTheme = ref('default')
const codeTheme = ref('github')
const toolbars = [
  'bold', 'underline', 'italic', 'strikeThrough', '-',
  'title', 'sub', 'sup', 'quote', 'unorderedList', 'orderedList', 'task', '-',
  'codeRow', 'code', 'link', 'image', 'table', 'mermaid', 'katex', '-',
  'revoke', 'next', 'save', '=',
  'pageFullscreen', 'fullscreen', 'preview', 'htmlPreview', 'catalog'
]

const showMentionMenu = ref(false)
const menuPosition = ref({ top: 200, left: 200 })
const mentionType = ref('file')
const mentionSearch = ref('')
const selectedIndex = ref(0)
const mentionStartPos = ref(0)
const fileList = ref([])
const pluginList = ref([])
const filteredMentionList = ref([])
let lastContent = ''

watch(localContent, (newVal) => {
  emit('update:modelValue', newVal)
  emit('change', newVal)
})

watch(() => props.modelValue, (newVal) => {
  if (newVal !== localContent.value) {
    localContent.value = newVal
  }
})

// 监听内容变化
function handleChange(val) {
  nextTick(() => {
    checkAtSymbol(val)
  })
}

function checkAtSymbol(content) {
  if (!content || content === lastContent) return
  lastContent = content
  
  // 检查最后输入的字符是否是@
  const diff = content.length - (lastContent.length - 1)
  if (diff === 1 && content[content.length - 1] === '@') {
    mentionStartPos.value = content.length - 1
    showMentionMenuAtCursor()
  } else if (showMentionMenu.value) {
    // 如果菜单已打开，更新搜索词
    updateMentionSearch(content)
  }
}

function updateMentionSearch(content) {
  const textAfterAt = content.substring(mentionStartPos.value + 1)
  const spaceIndex = textAfterAt.search(/[\s\n]/)
  
  if (spaceIndex !== -1) {
    closeMentionMenu()
    return
  }
  
  mentionSearch.value = textAfterAt
  filterMentionList()
}


function showMentionMenuAtCursor() {
  nextTick(() => {
    // 简单可靠的定位：相对编辑器容器
    const editorEl = document.querySelector('.md-editor-input-wrapper') || document.querySelector('.md-editor')
    
    if (editorEl) {
      const rect = editorEl.getBoundingClientRect()
      
      // 固定在编辑器内部，靠近顶部居中偏右
      menuPosition.value = {
        top: rect.top + 60 + window.scrollY,
        left: rect.left + rect.width - 420 + window.scrollX
      }
    } else {
      // 备用：屏幕中心偏右
      menuPosition.value = {
        top: 150 + window.scrollY,
        left: window.innerWidth - 450 + window.scrollX
      }
    }
    
    showMentionMenu.value = true
    selectedIndex.value = 0
    mentionSearch.value = ''
    
    if (mentionType.value === 'file' && fileList.value.length === 0) {
      loadFileList()
    } else if (mentionType.value === 'plugin' && pluginList.value.length === 0) {
      loadPluginList()
    } else {
      filterMentionList()
    }
    
    // 聚焦搜索框
    nextTick(() => {
      searchInputRef.value?.focus()
    })
  })
}

function closeMentionMenu() {
  showMentionMenu.value = false
  mentionSearch.value = ''
  selectedIndex.value = 0
}

function switchMentionType() {
  selectedIndex.value = 0
  if (mentionType.value === 'file' && fileList.value.length === 0) {
    loadFileList()
  } else if (mentionType.value === 'plugin' && pluginList.value.length === 0) {
    loadPluginList()
  }
  filterMentionList()
}

function loadFileList() {
  console.log('加载文件列表...')
  getFileList().then(response => {
    console.log('文件API响应:', response)
    const allFiles = response.data || []
    fileList.value = allFiles.filter(f => !f.isDirectory)
    console.log('文件列表数据:', fileList.value)
    filterMentionList()
  }).catch(err => {
    console.error('加载文件列表失败:', err)
    fileList.value = []
  })
}

function loadPluginList() {
  console.log('加载插件列表...')
  getPluginList().then(response => {
    console.log('插件API响应:', response)
    // 尝试不同的数据结构
    if (response.rows) {
      pluginList.value = response.rows
    } else if (response.data) {
      pluginList.value = Array.isArray(response.data) ? response.data : []
    } else if (Array.isArray(response)) {
      pluginList.value = response
    } else {
      pluginList.value = []
    }
    console.log('插件列表数据:', pluginList.value)
    filterMentionList()
  }).catch(err => {
    console.error('加载插件列表失败:', err)
    pluginList.value = []
  })
}

function filterMentionList() {
  const list = mentionType.value === 'file' ? fileList.value : pluginList.value
  const search = mentionSearch.value.toLowerCase()
  
  if (!search) {
    filteredMentionList.value = list.slice(0, 10)
    return
  }
  
  filteredMentionList.value = list.filter(item => {
    if (mentionType.value === 'file') {
      return item.name.toLowerCase().includes(search) || item.path.toLowerCase().includes(search)
    } else {
      return item.pluginName.toLowerCase().includes(search) ||
        (item.description && item.description.toLowerCase().includes(search))
    }
  }).slice(0, 10)
  
  selectedIndex.value = 0
}


function selectMention(item) {
  let reference = ''
  if (mentionType.value === 'file') {
    reference = `file[${item.name}](${item.path})`
  } else {
    reference = `plugin[${item.pluginName}](${item.pluginName})`
  }
  
  // 替换文本（包括@符号）
  const before = localContent.value.substring(0, mentionStartPos.value)
  const after = localContent.value.substring(mentionStartPos.value + mentionSearch.value.length + 1)
  localContent.value = before + '@' + reference + ' ' + after
  lastContent = localContent.value
  
  closeMentionMenu()
  
  // 重新聚焦到编辑器
  nextTick(() => {
    const textarea = document.querySelector('.md-editor-input-wrapper textarea')
    if (textarea) {
      textarea.focus()
      // 设置光标位置到插入内容之后
      const newPos = before.length + reference.length + 2 // @ + reference + space
      textarea.setSelectionRange(newPos, newPos)
    }
  })
  
  emit('insert-reference', '@' + reference)
}

function handleSearchKeydown(e) {
  if (e.key === 'ArrowDown') {
    e.preventDefault()
    selectedIndex.value = Math.min(selectedIndex.value + 1, filteredMentionList.value.length - 1)
  } else if (e.key === 'ArrowUp') {
    e.preventDefault()
    selectedIndex.value = Math.max(selectedIndex.value - 1, 0)
  } else if (e.key === 'ArrowLeft') {
    e.preventDefault()
    mentionType.value = 'file'
    switchMentionType()
  } else if (e.key === 'ArrowRight') {
    e.preventDefault()
    mentionType.value = 'plugin'
    switchMentionType()
  } else if (e.key === 'Enter') {
    e.preventDefault()
    if (filteredMentionList.value[selectedIndex.value]) {
      selectMention(filteredMentionList.value[selectedIndex.value])
    }
  } else if (e.key === 'Escape') {
    e.preventDefault()
    closeMentionMenu()
  } else if (e.key === 'Tab') {
    e.preventDefault()
    mentionType.value = mentionType.value === 'file' ? 'plugin' : 'file'
    switchMentionType()
  }
}

function handleSave(v) {
  emit('save', v)
}

function handleUploadImg(files, callback) {
  console.log('图片上传功能暂未实现', files)
  callback([])
}

defineExpose({
  getValue: () => localContent.value,
  setValue: (value) => { localContent.value = value },
  insertValue: (value) => { localContent.value += value }
})
</script>

<style lang="scss" scoped>
.markdown-editor-wrapper {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: white;
  position: relative;
  
  :deep(.md-editor) {
    flex: 1;
    height: auto !important;
  }
}
</style>

<style lang="scss">
.mention-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 2000;
  background: transparent;
}


.mention-menu {
  position: fixed;
  z-index: 2001;
  width: 380px;
  max-height: 450px;
  background: white;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  
  .mention-search {
    padding: 12px;
    border-bottom: 1px solid #ebeef5;
  }
  
  .mention-tabs {
    padding: 8px 12px;
    border-bottom: 1px solid #ebeef5;
    background: #f5f7fa;
  }
  
  .mention-list {
    flex: 1;
    overflow-y: auto;
    max-height: 320px;
    
    /* 滚动条样式 */
    &::-webkit-scrollbar {
      width: 6px;
    }
    
    &::-webkit-scrollbar-thumb {
      background: #c0c4cc;
      border-radius: 3px;
    }
    
    &::-webkit-scrollbar-track {
      background: #f5f7fa;
    }
    
    .mention-item {
      display: flex;
      align-items: center;
      padding: 10px 12px;
      cursor: pointer;
      transition: background-color 0.15s;
      border-bottom: 1px solid #f0f0f0;
      
      &:last-child {
        border-bottom: none;
      }
      
      &:hover {
        background: #f5f7fa;
      }
      
      &.active {
        background: #ecf5ff;
        border-left: 3px solid #409eff;
        padding-left: 9px;
      }
      
      .item-icon {
        font-size: 20px;
        margin-right: 10px;
        color: #606266;
        flex-shrink: 0;
      }
      
      .item-content {
        flex: 1;
        min-width: 0;
        
        .item-name {
          font-size: 14px;
          color: #303133;
          font-weight: 500;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }
        
        .item-path,
        .item-desc {
          font-size: 12px;
          color: #909399;
          margin-top: 2px;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }
      }
    }
    
    .mention-empty {
      padding: 40px 20px;
      text-align: center;
      color: #909399;
      font-size: 14px;
    }
  }
  
  .mention-footer {
    padding: 8px 12px;
    border-top: 1px solid #ebeef5;
    background: #fafafa;
    display: flex;
    gap: 16px;
    font-size: 12px;
    color: #909399;
  }
}
</style>
