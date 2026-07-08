<template>
  <el-dialog
    v-model="dialogVisible"
    title="Agent权限配置"
    width="800px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <el-tabs v-model="activeTab" v-loading="loading">
      <!-- Skill权限 -->
      <el-tab-pane label="Skill权限" name="skill">
        <div class="permission-container">
          <el-alert
            title="选择该Agent可以使用的全局Skill技能包（个人Skill由Agent自学习自动生成，无需手动分配）"
            type="info"
            :closable="false"
            style="margin-bottom: 16px;"
          />
          
          <div style="margin-bottom: 12px; display: flex; justify-content: space-between; align-items: center;">
            <span style="color: #606266; font-size: 14px;">
              已选择 <span style="color: #409eff; font-weight: bold;">{{ selectedSkillIds.length }}</span> 项
            </span>
            <el-button 
              v-if="selectedSkillIds.length > 0" 
              size="small" 
              type="warning" 
              link
              @click="clearSkillSelection"
            >
              清空选择
            </el-button>
          </div>
          
          <el-table
            ref="skillTableRef"
            :data="filteredSkillList"
            style="width: 100%"
            row-key="id"
            @selection-change="handleSkillSelectionChange"
          >
            <el-table-column type="selection" width="55" reserve-selection />
            <el-table-column prop="name" label="技能包名称" min-width="180">
              <template #default="{ row }">
                <div style="display: flex; align-items: center; gap: 8px;">
                  <el-icon color="#409eff"><FolderOpened /></el-icon>
                  {{ row.name }}
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="path" label="路径" min-width="200" show-overflow-tooltip />
            <el-table-column prop="childCount" label="技能数" width="100" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.childCount" size="small" type="info">{{ row.childCount }}</el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
          </el-table>
          
          <el-pagination
            v-model:current-page="skillPageNum"
            v-model:page-size="skillPageSize"
            :total="skillList.length"
            :page-sizes="[5, 10, 20, 50]"
            layout="total, sizes, prev, pager, next, jumper"
            style="margin-top: 16px; display: flex; justify-content: flex-end;"
            @size-change="handleSkillSizeChange"
            @current-change="handleSkillPageChange"
          />
        </div>
      </el-tab-pane>

      <!-- 插件权限 -->
      <el-tab-pane label="工具权限" name="plugin">
        <div class="permission-container">
          <el-alert
            title="选择该Agent可以使用的工具(插件)"
            type="info"
            :closable="false"
            style="margin-bottom: 16px;"
          />
          
          <div style="margin-bottom: 12px; display: flex; justify-content: space-between; align-items: center;">
            <span style="color: #606266; font-size: 14px;">
              已选择 <span style="color: #409eff; font-weight: bold;">{{ selectedPluginIds.length }}</span> 项
            </span>
            <el-button 
              v-if="selectedPluginIds.length > 0" 
              size="small" 
              type="warning" 
              link
              @click="clearPluginSelection"
            >
              清空选择
            </el-button>
          </div>
          
          <el-table
            ref="pluginTableRef"
            :data="filteredPluginList"
            style="width: 100%"
            row-key="id"
            @selection-change="handlePluginSelectionChange"
          >
            <el-table-column type="selection" width="55" reserve-selection />
            <el-table-column prop="pluginName" label="插件名称" min-width="150">
              <template #default="{ row }">
                <div style="display: flex; align-items: center; gap: 8px;">
                  <el-icon color="#409eff"><Connection /></el-icon>
                  {{ row.pluginName }}
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="description" label="描述" min-width="250" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.description || '暂无描述' }}
              </template>
            </el-table-column>
            <el-table-column prop="pluginType" label="类型" width="80" align="center">
              <template #default="{ row }">
                <el-tag 
                  v-if="row.pluginType" 
                  size="small" 
                  :type="row.pluginType === 'mcp' ? 'success' : 'info'"
                >
                  {{ row.pluginType === 'mcp' ? 'MCP' : '内置' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
          
          <el-pagination
            v-model:current-page="pluginPageNum"
            v-model:page-size="pluginPageSize"
            :total="pluginList.length"
            :page-sizes="[5, 10, 20, 50]"
            layout="total, sizes, prev, pager, next, jumper"
            style="margin-top: 16px; display: flex; justify-content: flex-end;"
            @size-change="handlePluginSizeChange"
            @current-change="handlePluginPageChange"
          />
        </div>
      </el-tab-pane>

      <!-- Agent授权 -->
      <el-tab-pane label="Agent授权" name="delegation">
        <div class="permission-container">
          <el-alert
            title="选择该Agent可以调用(委派任务)的其他Agent。被授权的Agent可作为专家Agent处理子任务。"
            type="info"
            :closable="false"
            style="margin-bottom: 16px;"
          />

          <div style="margin-bottom: 12px; display: flex; justify-content: space-between; align-items: center;">
            <span style="color: #606266; font-size: 14px;">
              已选择 <span style="color: #409eff; font-weight: bold;">{{ selectedDelegateAgentIds.length }}</span> 项
            </span>
            <el-button
              v-if="selectedDelegateAgentIds.length > 0"
              size="small"
              type="warning"
              link
              @click="clearAgentSelection"
            >
              清空选择
            </el-button>
          </div>

          <el-table
            ref="agentTableRef"
            :data="filteredAgentList"
            style="width: 100%"
            row-key="id"
            @selection-change="handleAgentSelectionChange"
          >
            <el-table-column type="selection" width="55" reserve-selection />
            <el-table-column prop="agentName" label="Agent名称" min-width="150">
              <template #default="{ row }">
                <div style="display: flex; align-items: center; gap: 8px;">
                  <el-icon color="#409eff"><User /></el-icon>
                  {{ row.agentName }}
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="agentCode" label="编码" width="150" show-overflow-tooltip />
            <el-table-column prop="description" label="描述" min-width="250" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.description || '暂无描述' }}
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-model:current-page="agentPageNum"
            v-model:page-size="agentPageSize"
            :total="agentList.length"
            :page-sizes="[5, 10, 20, 50]"
            layout="total, sizes, prev, pager, next, jumper"
            style="margin-top: 16px; display: flex; justify-content: flex-end;"
            @size-change="handleAgentSizeChange"
            @current-change="handleAgentPageChange"
          />
        </div>
      </el-tab-pane>

      <!-- 知识库权限 -->
      <el-tab-pane label="知识库权限" name="knowledge">
        <div class="permission-container">
          <el-alert
            title="选择该Agent可以访问的知识库，并配置检索模式和元数据过滤。"
            type="info"
            :closable="false"
            style="margin-bottom: 16px;"
          />
          <div style="margin-bottom: 12px; color: #606266; font-size: 14px;">
            已选择 <span style="color: #409eff; font-weight: bold;">{{ selectedKbIds.length }}</span> 项
          </div>
          <el-table
            ref="kbTableRef"
            :data="kbList"
            style="width: 100%"
            row-key="id"
            @selection-change="(sel) => { selectedKbIds = sel.map(item => item.id) }"
          >
            <el-table-column type="selection" width="55" reserve-selection />
            <el-table-column prop="kbName" label="知识库名称" min-width="150" />
            <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
            <el-table-column label="检索模式" width="140">
              <template #default="{ row }">
                <el-select v-model="getKbGrant(row.id).retrievalMode" size="small" style="width: 100%">
                  <el-option label="自动注入" value="auto" />
                  <el-option label="工具模式" value="tool" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="元数据过滤" width="200">
              <template #default="{ row }">
                <el-input v-model="getKbGrant(row.id).metadataFilter" size="small" placeholder="{&quot;doc_category&quot;:&quot;财务&quot;}" />
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>

    <template #footer>
      <el-button @click="handleClose">取 消</el-button>
      <el-button type="primary" @click="submitPermission" :loading="saving">保 存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch, nextTick, getCurrentInstance, computed } from 'vue'
import { getAgent, saveAgentSkills, saveAgentPlugins, getAgentDelegations, saveAgentDelegations, listAgent } from '@/api/agent/agent'
import { getAvailableSkillPackages } from '@/api/skill/skill'
import { listPlugin } from '@/api/plugin/plugin'
import { availableKnowledgeBase, getAgentGrants, saveAgentGrants } from '@/api/knowledge/knowledge'
import { Connection, FolderOpened, User } from '@element-plus/icons-vue'

const { proxy } = getCurrentInstance()

const props = defineProps({
  visible: Boolean,
  agentId: [Number, String]
})

const emit = defineEmits(['update:visible'])

const dialogVisible = ref(false)
const loading = ref(false)
const saving = ref(false)
const activeTab = ref('skill')

// Skill相关
const skillList = ref([])
const filteredSkillList = ref([])
const selectedSkillIds = ref([])
const skillPageNum = ref(1)
const skillPageSize = ref(5)

// 插件相关
const pluginList = ref([])
const filteredPluginList = ref([])
const selectedPluginIds = ref([])
const pluginPageNum = ref(1)
const pluginPageSize = ref(5)

// Agent委派授权相关
const agentList = ref([])
const filteredAgentList = ref([])
const selectedDelegateAgentIds = ref([])
const agentPageNum = ref(1)
const agentPageSize = ref(5)

// 表格引用
const skillTableRef = ref()
const pluginTableRef = ref()
const agentTableRef = ref()

// 知识库授权相关
const kbList = ref([])
const selectedKbIds = ref([])
const kbGrants = ref({})
const kbTableRef = ref()

// 获取或初始化知识库授权配置
function getKbGrant(kbId) {
  if (!kbGrants.value[kbId]) {
    kbGrants.value[kbId] = { 
      retrievalMode: 'auto', 
      metadataFilter: '' 
    }
  }
  return kbGrants.value[kbId]
}

watch(() => props.visible, (val) => {
  dialogVisible.value = val
  if (val && props.agentId) {
    loadPermissionData()
  }
})

watch(dialogVisible, (val) => {
  if (!val) {
    emit('update:visible', false)
  }
})

/** 加载权限数据 */
function loadPermissionData() {
  loading.value = true
  
  // 先重置状态
  selectedSkillIds.value = []
  selectedPluginIds.value = []
  selectedDelegateAgentIds.value = []
  selectedKbIds.value = []
  kbGrants.value = {}
  skillPageNum.value = 1
  pluginPageNum.value = 1
  agentPageNum.value = 1
  
  Promise.all([
    getAgent(props.agentId),
    getAvailableSkillPackages({
      businessSystem: null,  // 从 Agent 数据中获取
      ownerUser: null        // 从当前登录用户获取
    }),
    listPlugin({ status: '0', pageNum: 1, pageSize: 999 }),
    listAgent({ status: '0', pageNum: 1, pageSize: 999 }),
    getAgentDelegations(props.agentId),
    availableKnowledgeBase(),
    getAgentGrants(props.agentId)
  ]).then(([agentRes, skillRes, pluginRes, agentListRes, delegationRes, kbRes, grantsRes]) => {
    console.log('Agent响应数据:', agentRes)
    
    // 获取 Agent 数据
    const agentData = agentRes.data || agentRes
    
    // 重新加载技能包（使用 Agent 的业务系统和当前用户）
    const businessSystem = agentData.businessSystem
    const ownerUser = agentData.createBy  // 或从 SecurityUtils 获取当前用户
    
    // 重新加载可用的技能包
    getAvailableSkillPackages({ businessSystem, ownerUser }).then(res => {
      skillList.value = (res.data || []).map(node => ({
        id: node.id,
        name: node.name,
        path: node.path,
        childCount: node.childCount || 0,
        skillScope: node.skillScope,
        businessSystem: node.businessSystem,
        ownerUser: node.ownerUser
      }))
      
      updateSkillList()
      
      // 延迟设置技能包选中状态
      nextTick(() => {
        setTimeout(() => {
          if (skillTableRef.value && Array.isArray(preSelectedSkillIds)) {
            skillList.value.forEach(skill => {
              if (preSelectedSkillIds.includes(skill.id)) {
                skillTableRef.value.toggleRowSelection(skill, true)
              }
            })
          }
        }, 100)
      })
    })
    
    // 加载插件列表
    pluginList.value = (pluginRes.rows || []).map(plugin => ({
      id: plugin.pluginId || plugin.id,  // 兼容 pluginId 和 id
      pluginName: plugin.pluginName,
      description: plugin.description,
      pluginType: plugin.pluginType
    }))
    
    console.log('技能包列表:', skillList.value)
    console.log('插件列表:', pluginList.value)
    
    // 加载Agent列表(排除自身)
    agentList.value = (agentListRes.rows || []).filter(a => String(a.id) !== String(props.agentId)).map(a => ({
      id: a.id,
      agentName: a.agentName,
      agentCode: a.agentCode,
      description: a.description
    }))
    updateAgentList()
    
    // 获取已授权的委派Agent IDs
    const preSelectedDelegationIds = delegationRes.data || delegationRes || []
    

    // 加载知识库列表
    kbList.value = (kbRes.data || []).map(kb => ({
      id: kb.id,
      kbName: kb.kbName,
      kbCode: kb.kbCode,
      description: kb.description
    }))
    
    // 加载已授权的知识库
    selectedKbIds.value = []
    kbGrants.value = {}
    const grants = grantsRes.data || []
    if (Array.isArray(grants)) {
      grants.forEach(g => {
        if (g.status === "0") {
          selectedKbIds.value.push(g.kbId)
          kbGrants.value[g.kbId] = { retrievalMode: g.retrievalMode || "auto", metadataFilter: g.metadataFilter || "" }
        }
      })
    }
    nextTick(() => {
      if (kbTableRef.value) {
        kbList.value.forEach(kb => {
          if (selectedKbIds.value.includes(kb.id)) {
            kbTableRef.value.toggleRowSelection(kb, true)
          }
        })
      }
    })
    // 加载已选择的权限 - 兼容多种返回格式
    let preSelectedSkillIds = []
    let preSelectedPluginIds = []
    
    // 尝试从不同位置获取
    if (agentRes.skillIds) {
      preSelectedSkillIds = agentRes.skillIds
    } else if (agentRes.data && agentRes.data.skillIds) {
      preSelectedSkillIds = agentRes.data.skillIds
    }
    
    if (agentRes.pluginIds) {
      preSelectedPluginIds = agentRes.pluginIds
    } else if (agentRes.data && agentRes.data.pluginIds) {
      preSelectedPluginIds = agentRes.data.pluginIds
    }
    
    console.log('预选的Skill IDs:', preSelectedSkillIds)
    console.log('预选的Plugin IDs:', preSelectedPluginIds)
    
    // 更新分页列表
    updateSkillList()
    updatePluginList()
    
    // 延迟设置选中状态
    nextTick(() => {
      setTimeout(() => {
        // 设置技能包选中
        if (skillTableRef.value && Array.isArray(preSelectedSkillIds)) {
          skillList.value.forEach(skill => {
            if (preSelectedSkillIds.includes(skill.id)) {
              skillTableRef.value.toggleRowSelection(skill, true)
            }
          })
        }
        
        // 设置插件选中
        if (pluginTableRef.value && Array.isArray(preSelectedPluginIds)) {
          pluginList.value.forEach(plugin => {
            if (preSelectedPluginIds.includes(plugin.id)) {
              pluginTableRef.value.toggleRowSelection(plugin, true)
            }
          })
        }
        
        // 设置Agent授权选中
        if (agentTableRef.value && Array.isArray(preSelectedDelegationIds)) {
          agentList.value.forEach(a => {
            if (preSelectedDelegationIds.includes(a.id)) {
              agentTableRef.value.toggleRowSelection(a, true)
            }
          })
        }
      }, 100)
    })
    
    loading.value = false
  }).catch((error) => {
    console.error('加载权限数据失败:', error)
    loading.value = false
  })
}

/** 更新技能包列表（分页） */
function updateSkillList() {
  const start = (skillPageNum.value - 1) * skillPageSize.value
  const end = start + skillPageSize.value
  filteredSkillList.value = skillList.value.slice(start, end)
}

/** 更新插件列表（分页） */
function updatePluginList() {
  const start = (pluginPageNum.value - 1) * pluginPageSize.value
  const end = start + pluginPageSize.value
  filteredPluginList.value = pluginList.value.slice(start, end)
}

/** Skill选择变化 */
function handleSkillSelectionChange(selection) {
  selectedSkillIds.value = selection.map(item => item.id)
}

/** 插件选择变化 */
function handlePluginSelectionChange(selection) {
  selectedPluginIds.value = selection.map(item => item.id)
}

/** Skill分页变化 */
function handleSkillPageChange() {
  updateSkillList()
}

function handleSkillSizeChange() {
  skillPageNum.value = 1
  updateSkillList()
}

/** 插件分页变化 */
function handlePluginPageChange() {
  updatePluginList()
}

function handlePluginSizeChange() {
  pluginPageNum.value = 1
  updatePluginList()
}

/** 清空技能包选择 */
function clearSkillSelection() {
  if (skillTableRef.value) {
    skillTableRef.value.clearSelection()
  }
  selectedSkillIds.value = []
}

/** 清空插件选择 */
function clearPluginSelection() {
  if (pluginTableRef.value) {
    pluginTableRef.value.clearSelection()
  }
  selectedPluginIds.value = []
}

/** 更新Agent列表（分页） */
function updateAgentList() {
  const start = (agentPageNum.value - 1) * agentPageSize.value
  const end = start + agentPageSize.value
  filteredAgentList.value = agentList.value.slice(start, end)
}

/** Agent授权选择变化 */
function handleAgentSelectionChange(selection) {
  selectedDelegateAgentIds.value = selection.map(item => item.id)
}

/** Agent分页变化 */
function handleAgentPageChange() {
  updateAgentList()
}

function handleAgentSizeChange() {
  agentPageNum.value = 1
  updateAgentList()
}

/** 清空Agent授权选择 */
function clearAgentSelection() {
  if (agentTableRef.value) {
    agentTableRef.value.clearSelection()
  }
  selectedDelegateAgentIds.value = []
}

/** 提交权限 */
function submitPermission() {
  if (!props.agentId) { proxy.$modal.msgError('Agent ID不存在，无法保存'); return }
  saving.value = true
  
  Promise.all([
    saveAgentSkills(props.agentId, selectedSkillIds.value),
    saveAgentPlugins(props.agentId, selectedPluginIds.value),
    saveAgentDelegations(props.agentId, selectedDelegateAgentIds.value),
    saveAgentGrants(props.agentId, selectedKbIds.value.map(id => ({ kbId: id, retrievalMode: (kbGrants.value[id] || {}).retrievalMode || 'auto', metadataFilter: (kbGrants.value[id] || {}).metadataFilter || '' })))
  ]).then(() => {
    proxy.$modal.msgSuccess('权限配置成功')
    handleClose()
  }).finally(() => {
    saving.value = false
  })
}

/** 关闭对话框 */
function handleClose() {
  activeTab.value = 'skill'
  skillPageNum.value = 1
  pluginPageNum.value = 1
  agentPageNum.value = 1
  dialogVisible.value = false
}
</script>

<style lang="scss" scoped>
.permission-container {
  min-height: 400px;
  max-height: 600px;
  overflow: visible;
}
</style>
