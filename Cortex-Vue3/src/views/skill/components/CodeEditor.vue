<template>
  <div class="code-editor">
    <vue-monaco-editor
      v-model:value="localContent"
      :language="language"
      :options="editorOptions"
      :theme="theme"
      @change="handleChange"
      class="monaco-container"
    />
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { VueMonacoEditor } from '@guolao/vue-monaco-editor'

const props = defineProps({
  modelValue: {
    type: String,
    default: ''
  },
  language: {
    type: String,
    default: 'javascript'
  },
  theme: {
    type: String,
    default: 'vs-dark' // vs, vs-dark, hc-black
  },
  readOnly: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'change'])

const localContent = ref(props.modelValue)

// Monaco编辑器配置
const editorOptions = computed(() => ({
  fontSize: 14,
  lineHeight: 22,
  tabSize: 2,
  minimap: {
    enabled: true
  },
  scrollBeyondLastLine: false,
  automaticLayout: true,
  readOnly: props.readOnly,
  wordWrap: 'on',
  wrappingIndent: 'indent',
  lineNumbers: 'on',
  renderLineHighlight: 'all',
  quickSuggestions: {
    other: true,
    comments: true,
    strings: true
  },
  suggestOnTriggerCharacters: true,
  acceptSuggestionOnCommitCharacter: true,
  acceptSuggestionOnEnter: 'on',
  snippetSuggestions: 'inline',
  formatOnPaste: true,
  formatOnType: true,
  folding: true,
  foldingStrategy: 'indentation',
  showFoldingControls: 'always',
  matchBrackets: 'always',
  renderWhitespace: 'selection',
  scrollbar: {
    verticalScrollbarSize: 10,
    horizontalScrollbarSize: 10
  }
}))

function handleChange(value) {
  emit('update:modelValue', value)
  emit('change', value)
}

// 监听外部值变化
watch(() => props.modelValue, (newVal) => {
  if (newVal !== localContent.value) {
    localContent.value = newVal
  }
})
</script>

<style lang="scss" scoped>
.code-editor {
  height: 100%;
  width: 100%;
  
  .monaco-container {
    height: 100%;
    width: 100%;
  }
}
</style>
