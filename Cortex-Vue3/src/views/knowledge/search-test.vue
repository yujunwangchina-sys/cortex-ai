<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header><span>检索测试</span></template>
      <el-form :model="searchForm" label-width="100px" style="max-width: 800px;">
        <el-form-item label="知识库" required>
          <el-select v-model="searchForm.kbId" placeholder="选择知识库" filterable style="width: 100%">
            <el-option v-for="kb in kbList" :key="kb.id" :label="kb.kbName" :value="kb.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="查询问题" required>
          <el-input v-model="searchForm.query" type="textarea" :rows="3" placeholder="输入要检索的问题" />
        </el-form-item>
        <el-form-item label="元数据过滤">
          <el-input v-model="searchForm.metadataFilter" placeholder='JSON格式，如 {"doc_category":"财务"}' />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="返回数量">
              <el-input-number v-model="searchForm.topK" :min="1" :max="20" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最小相似度">
              <el-input-number v-model="searchForm.minScore" :min="0" :max="1" :step="0.05" :precision="2" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleSearch" :loading="loading">检索</el-button>
          <el-button icon="Refresh" @click="resetForm">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 检索结果 -->
    <div style="margin-top: 16px;" v-if="results.length > 0">
      <div style="margin-bottom: 12px; display: flex; align-items: center; gap: 8px;">
        <el-icon color="#409eff"><DataAnalysis /></el-icon>
        <span style="font-weight: bold;">检索结果 ({{ results.length }} 条)</span>
      </div>
      <el-card v-for="(item, idx) in results" :key="idx" shadow="hover" style="margin-bottom: 12px;">
        <div style="display: flex; justify-content: space-between; align-items: start; margin-bottom: 8px;">
          <div>
            <el-tag size="small" type="primary">[{{ idx + 1 }}]</el-tag>
            <span v-if="item.documentName" style="margin-left: 8px; font-weight: 600;">{{ item.documentName }}</span>
            <el-tag v-if="item.docCategory" size="small" type="info" style="margin-left: 8px;">{{ item.docCategory }}</el-tag>
          </div>
          <el-tag type="success" size="small">相似度: {{ formatScore(item.score) }}</el-tag>
        </div>
        <div style="color: #606266; line-height: 1.6;">
          <template v-for="(seg, si) in parseChunkSegments(item.content, item.imagePath)" :key="si">
            <span v-if="seg.type === 'text'" style="white-space: pre-wrap;">{{ seg.value }}</span>
            <el-image
              v-else
              :src="seg.url"
              :preview-src-list="[seg.url]"
              fit="contain"
              preview-teleported
              style="max-width: 300px; max-height: 200px; border-radius: 4px; border: 1px solid #e4e7ed; vertical-align: middle; margin: 4px 0;"
            />
          </template>
        </div>
      </el-card>
    </div>
    <el-empty v-if="searched && results.length === 0" description="未找到相关内容" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { DataAnalysis } from '@element-plus/icons-vue'
import { searchTest, availableKnowledgeBase } from '@/api/knowledge/knowledge'

const loading = ref(false)
const searched = ref(false)
const kbList = ref([])
const results = ref([])
const baseUrl = import.meta.env.VITE_APP_BASE_API
const searchForm = reactive({ kbId: null, query: '', metadataFilter: '', topK: 5, minScore: 0.5 })

function handleSearch() {
  if (!searchForm.kbId || !searchForm.query.trim()) return
  loading.value = true
  searched.value = true
  searchTest(searchForm).then(res => {
    results.value = res.data || []
  }).finally(() => { loading.value = false })
}

function resetForm() {
  searchForm.query = ''
  searchForm.metadataFilter = ''
  results.value = []
  searched.value = false
}

function parseChunkSegments(content, imagePath) {
  if (!content) return [{ type: 'text', value: '' }]
  let displayContent = content.replace(/\n\[img\d+:desc\][^\n]*/g, '')
  if (!imagePath) {
    displayContent = displayContent.replace(/\[img\d+\]/g, '')
    return [{ type: 'text', value: displayContent }]
  }
  let paths = []
  try { paths = JSON.parse(imagePath) } catch (e) { paths = [imagePath] }
  const segments = []
  const regex = /\[img(\d+)\]/g
  let lastIndex = 0
  let match
  while ((match = regex.exec(displayContent)) !== null) {
    if (match.index > lastIndex) {
      segments.push({ type: 'text', value: displayContent.slice(lastIndex, match.index) })
    }
    const imgNum = parseInt(match[1])
    const pathIdx = imgNum - 1
    if (pathIdx >= 0 && pathIdx < paths.length) {
      segments.push({ type: 'image', value: paths[pathIdx], url: baseUrl + '/profile/' + paths[pathIdx] })
    }
    lastIndex = regex.lastIndex
  }
  if (lastIndex < displayContent.length) {
    segments.push({ type: 'text', value: displayContent.slice(lastIndex) })
  }
  return segments
}
function formatScore(score) {
  return score != null ? Number(score).toFixed(4) : '-'
}

onMounted(() => {
  availableKnowledgeBase().then(res => { kbList.value = res.data || [] })
})
</script>
