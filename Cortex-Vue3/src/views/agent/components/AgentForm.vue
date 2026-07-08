<template>
  <el-dialog
    v-model="dialogVisible"
    :title="title"
    width="700px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
      <el-row>
        <el-col :span="12">
          <el-form-item label="Agent名称" prop="agentName">
            <el-input v-model="form.agentName" placeholder="请输入Agent名称" maxlength="100" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Agent编码" prop="agentCode">
            <el-input 
              v-model="form.agentCode" 
              placeholder="请输入Agent编码" 
              maxlength="100"
              :disabled="!!agentId"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row>
        <el-col :span="12">
          <el-form-item label="状态" prop="status">
            <el-radio-group v-model="form.status">
              <el-radio label="0">启用</el-radio>
              <el-radio label="1">禁用</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="排序" prop="sortOrder">
            <el-input-number v-model="form.sortOrder" :min="0" :max="9999" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row>
        <el-col :span="12">
          <el-form-item label="所属业务系统" prop="businessSystem">
            <el-select 
              v-model="form.businessSystem" 
              placeholder="请选择业务系统，留空表示通用"
              clearable
              style="width: 100%"
            >
              <el-option
                v-for="dict in sys_b_system"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="API密钥" prop="apiKey">
            <el-input 
              v-model="form.apiKey" 
              placeholder="业务系统调用时使用，留空则自动生成"
              maxlength="100"
            >
              <template #append>
                <el-button @click="generateApiKey" icon="Refresh">生成</el-button>
              </template>
            </el-input>
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="默认聊天模型">
        <el-select
          v-model="selectedDefaultModelId"
          placeholder="选择默认聊天模型，留空使用系统默认"
          clearable
          filterable
          style="width: 100%"
        >
          <el-option
            v-for="m in chatModels"
            :key="m.modelId"
            :label="m.modelName"
            :value="m.modelId"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="描述">
        <el-input
          v-model="form.description"
          type="textarea"
          placeholder="请输入描述"
          :rows="3"
          maxlength="500"
        />
      </el-form-item>

      <el-form-item label="备注">
        <el-input
          v-model="form.remark"
          type="textarea"
          placeholder="请输入备注"
          :rows="2"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取 消</el-button>
      <el-button type="primary" @click="submitForm" :loading="loading">确 定</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch, getCurrentInstance } from 'vue'
import { getAgent, addAgent, updateAgent } from '@/api/agent/agent'
import { listModel } from '@/api/supplier/model'

const { proxy } = getCurrentInstance()

// 加载业务系统字典
const { sys_b_system } = proxy.useDict('sys_b_system')

const props = defineProps({
  visible: Boolean,
  agentId: [Number, String]
})

const emit = defineEmits(['update:visible', 'success'])

const dialogVisible = ref(false)
const loading = ref(false)
const title = ref('')
const formRef = ref()

const chatModels = ref([])
const selectedDefaultModelId = ref(null)
const form = ref({
  id: null,
  agentName: null,
  agentCode: null,
  description: null,
  status: '0',
  sortOrder: 0,
  businessSystem: null,
  apiKey: null,
  modelPreference: null,
  remark: null
})

const rules = ref({
  agentName: [
    { required: true, message: 'Agent名称不能为空', trigger: 'blur' }
  ],
  agentCode: [
    { required: true, message: 'Agent编码不能为空', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_-]+$/, message: 'Agent编码只能包含字母、数字、下划线和中划线', trigger: 'blur' }
  ]
})

watch(() => props.visible, (val) => {
  dialogVisible.value = val
  if (val) {
    resetForm()
    loadChatModels()
    if (props.agentId) {
      title.value = '修改Agent'
      loadAgentData()
    } else {
      title.value = '新增Agent'
    }
  }
})

watch(dialogVisible, (val) => {
  if (!val) {
    emit('update:visible', false)
  }
})

/** 加载Agent数据 */
function loadAgentData() {
  loading.value = true
  getAgent(props.agentId).then(response => {
    form.value = response.data
    // 解析模型偏好，回显默认聊天模型
    if (form.value.modelPreference) {
      try {
        const pref = JSON.parse(form.value.modelPreference)
        selectedDefaultModelId.value = pref.chat || null
      } catch (e) {
        selectedDefaultModelId.value = null
      }
    } else {
      selectedDefaultModelId.value = null
    }
    loading.value = false
  })
}

/** 重置表单 */
function resetForm() {
  form.value = {
    id: null,
    agentName: null,
    agentCode: null,
    description: null,
    status: '0',
    sortOrder: 0,
    businessSystem: null,
    apiKey: null,
    modelPreference: null,
    remark: null
  }
  selectedDefaultModelId.value = null
  proxy.resetForm('formRef')
}

/** 生成API密钥 */
function generateApiKey() {
  // 生成32位随机密钥
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'
  let apiKey = 'ak_'
  for (let i = 0; i < 32; i++) {
    apiKey += chars.charAt(Math.floor(Math.random() * chars.length))
  }
  form.value.apiKey = apiKey
}

/** 加载聊天模型列表 */
function loadChatModels() {
  listModel({ modelType: 'chat', status: '0', pageNum: 1, pageSize: 999 }).then(res => {
    chatModels.value = res.rows || []
  })
}

/** 提交表单 */
function submitForm() {
  formRef.value.validate(valid => {
    if (valid) {
      loading.value = true
      // 序列化模型偏好
      let modelPref = {}
      if (form.value.modelPreference) {
        try { modelPref = JSON.parse(form.value.modelPreference) } catch (e) {}
      }
      if (selectedDefaultModelId.value) {
        modelPref.chat = selectedDefaultModelId.value
      } else {
        delete modelPref.chat
      }
      form.value.modelPreference = Object.keys(modelPref).length > 0 ? JSON.stringify(modelPref) : null
      if (form.value.id) {
        updateAgent(form.value).then(() => {
          proxy.$modal.msgSuccess('修改成功')
          emit('success')
          handleClose()
        }).finally(() => {
          loading.value = false
        })
      } else {
        addAgent(form.value).then(() => {
          proxy.$modal.msgSuccess('新增成功')
          emit('success')
          handleClose()
        }).finally(() => {
          loading.value = false
        })
      }
    }
  })
}

/** 关闭对话框 */
function handleClose() {
  dialogVisible.value = false
}
</script>

<style lang="scss" scoped>
:deep(.el-dialog__body) {
  max-height: 70vh;
  overflow-y: auto;
}
</style>
