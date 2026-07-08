<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="90px">
      <el-form-item label="供应商名称" prop="supplierName">
        <el-input
          v-model="queryParams.supplierName"
          placeholder="请输入供应商名称"
          clearable
          style="width: 240px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="供应商编码" prop="supplierCode">
        <el-input
          v-model="queryParams.supplierCode"
          placeholder="请输入供应商编码"
          clearable
          style="width: 240px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="供应商状态" clearable style="width: 240px">
          <el-option
            v-for="dict in sys_normal_disable"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="Plus"
          @click="handleAdd"
          v-hasPermi="['supplier:supplier:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="Edit"
          :disabled="single"
          @click="handleUpdate"
          v-hasPermi="['supplier:supplier:edit']"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="Delete"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['supplier:supplier:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="Download"
          @click="handleExport"
          v-hasPermi="['supplier:supplier:export']"
        >导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="supplierList" @selection-change="handleSelectionChange" border stripe highlight-current-row>
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="供应商名称" align="center" prop="supplierName" width="150" :show-overflow-tooltip="true" />
      <el-table-column label="供应商编码" align="center" prop="supplierCode" width="120" :show-overflow-tooltip="true" />
      <el-table-column label="API基础地址" align="center" prop="apiBaseUrl" width="300" :show-overflow-tooltip="true" />
      <el-table-column label="描述" align="center" prop="description" :show-overflow-tooltip="true" />
      <el-table-column label="状态" align="center" prop="status" width="80">
        <template #default="scope">
          <dict-tag :options="sys_normal_disable" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="排序" align="center" prop="sortOrder" width="80" />
      <el-table-column label="操作" align="center" width="280" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleModels(scope.row)">模型</el-button>
          <el-button link type="primary" icon="Connection" @click="handleTest(scope.row)" v-hasPermi="['supplier:supplier:test']">测试</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['supplier:supplier:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['supplier:supplier:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />

    <!-- 添加或修改供应商对话框 -->
    <el-dialog :title="title" v-model="open" width="600px" append-to-body>
      <el-form ref="supplierRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="供应商名称" prop="supplierName">
          <el-input v-model="form.supplierName" placeholder="请输入供应商名称" />
        </el-form-item>
        <el-form-item label="供应商编码" prop="supplierCode">
          <el-input v-model="form.supplierCode" placeholder="请输入供应商编码" />
        </el-form-item>
        <el-form-item label="API地址" prop="apiBaseUrl">
          <el-input v-model="form.apiBaseUrl" placeholder="请输入API基础地址，如：https://api.openai.com/v1" />
        </el-form-item>
        <el-form-item label="API密钥" prop="apiKey">
          <el-input 
            v-model="form.apiKey" 
            type="password" 
            placeholder="请输入API密钥"
            show-password
          />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" placeholder="请输入描述" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio
              v-for="dict in sys_normal_disable"
              :key="dict.value"
              :value="dict.value"
            >{{ dict.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="form.sortOrder" controls-position="right" :min="0" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 模型管理对话框 -->
    <el-dialog title="模型管理" v-model="modelOpen" width="1200px" append-to-body>
      <el-row :gutter="10" class="mb8">
        <el-col :span="1.5">
          <el-button
            type="primary"
            plain
            icon="Plus"
            size="small"
            @click="handleAddModel"
            v-hasPermi="['supplier:model:add']"
          >新增模型</el-button>
        </el-col>
      </el-row>

      <el-table v-loading="modelLoading" :data="modelList" border stripe highlight-current-row>
        <el-table-column label="模型ID" align="center" prop="modelId" width="80" />
        <el-table-column label="模型名称" align="center" prop="modelName" :show-overflow-tooltip="true" />
        <el-table-column label="模型编码" align="center" prop="modelCode" :show-overflow-tooltip="true" />
        <el-table-column label="模型类型" align="center" prop="modelType" width="110">
          <template #default="scope">
            <el-tag v-if="scope.row.modelType === 'chat'" type="success">聊天</el-tag>
            <el-tag v-else-if="scope.row.modelType === 'multimodal'" type="danger">全模态</el-tag>
            <el-tag v-else-if="scope.row.modelType === 'vision'" type="warning">图像识别</el-tag>
            <el-tag v-else-if="scope.row.modelType === 'embedding'" type="info">嵌入</el-tag>
            <el-tag v-else-if="scope.row.modelType === 'image'" type="primary">图像生成</el-tag>
            <el-tag v-else-if="scope.row.modelType === 'audio'" type="info">语音</el-tag>
            <el-tag v-else-if="scope.row.modelType === 'rerank'" type="primary">重排序</el-tag>
            <el-tag v-else>{{ scope.row.modelType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="上下文长度" align="center" prop="contextLength" width="110" />
        <el-table-column label="最大Token" align="center" prop="maxTokens" width="100" />
        <el-table-column label="温度" align="center" prop="temperature" width="80" />
        <el-table-column label="Top P" align="center" prop="topP" width="80" />
        <el-table-column label="状态" align="center" prop="status" width="80">
          <template #default="scope">
            <dict-tag :options="sys_normal_disable" :value="scope.row.status" />
          </template>
        </el-table-column>
        <el-table-column label="排序" align="center" prop="sortOrder" width="70" />
        <el-table-column label="操作" align="center" width="150" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-button link type="primary" icon="Edit" @click="handleUpdateModel(scope.row)" v-hasPermi="['supplier:model:edit']">修改</el-button>
            <el-button link type="primary" icon="Delete" @click="handleDeleteModel(scope.row)" v-hasPermi="['supplier:model:remove']">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 添加或修改模型对话框 -->
    <el-dialog :title="modelTitle" v-model="modelFormOpen" width="600px" append-to-body>
      <el-form ref="modelRef" :model="modelForm" :rules="modelRules" label-width="120px">
        <el-form-item label="模型名称" prop="modelName">
          <el-input v-model="modelForm.modelName" placeholder="请输入模型名称" />
        </el-form-item>
        <el-form-item label="模型编码" prop="modelCode">
          <el-input v-model="modelForm.modelCode" placeholder="请输入模型编码，如：gpt-4o" />
        </el-form-item>
        <el-form-item label="模型类型" prop="modelType">
          <el-select v-model="modelForm.modelType" placeholder="请选择模型类型">
            <el-option label="聊天模型" value="chat" />
            <el-option label="全模态模型" value="multimodal" />
            <el-option label="图像识别模型" value="vision" />
            <el-option label="嵌入模型" value="embedding" />
            <el-option label="图像生成模型" value="image" />
            <el-option label="语音模型" value="audio" />
            <el-option label="重排序模型" value="rerank" />
          </el-select>
        </el-form-item>
        <el-form-item label="上下文长度" prop="contextLength">
          <el-input-number v-model="modelForm.contextLength" controls-position="right" :min="0" />
        </el-form-item>
        <el-form-item label="最大Token数" prop="maxTokens">
          <el-input-number v-model="modelForm.maxTokens" controls-position="right" :min="0" />
        </el-form-item>
        <el-form-item label="温度参数" prop="temperature">
          <el-input-number v-model="modelForm.temperature" controls-position="right" :min="0" :max="2" :step="0.1" :precision="2" />
        </el-form-item>
        <el-form-item label="Top P参数" prop="topP">
          <el-input-number v-model="modelForm.topP" controls-position="right" :min="0" :max="1" :step="0.1" :precision="2" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="modelForm.status">
            <el-radio
              v-for="dict in sys_normal_disable"
              :key="dict.value"
              :value="dict.value"
            >{{ dict.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="modelForm.sortOrder" controls-position="right" :min="0" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="modelForm.remark" type="textarea" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitModelForm">确 定</el-button>
          <el-button @click="cancelModel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="AiSupplier">
import { listSupplier, getSupplier, delSupplier, addSupplier, updateSupplier, testConnection } from "@/api/supplier/supplier"
import { listModel, getModel, delModel, addModel, updateModel } from "@/api/supplier/model"

const { proxy } = getCurrentInstance()
const { sys_normal_disable } = useDict("sys_normal_disable")

const supplierList = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref("")

// 模型相关
const modelOpen = ref(false)
const modelFormOpen = ref(false)
const modelLoading = ref(false)
const modelList = ref([])
const modelTitle = ref("")
const currentSupplierId = ref(null)

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    supplierName: undefined,
    supplierCode: undefined,
    status: undefined
  },
  rules: {
    supplierName: [{ required: true, message: "供应商名称不能为空", trigger: "blur" }],
    supplierCode: [{ required: true, message: "供应商编码不能为空", trigger: "blur" }],
    apiBaseUrl: [{ required: true, message: "API基础地址不能为空", trigger: "blur" }],
    status: [{ required: true, message: "状态不能为空", trigger: "change" }]
  },
  modelForm: {},
  modelRules: {
    modelName: [{ required: true, message: "模型名称不能为空", trigger: "blur" }],
    modelCode: [{ required: true, message: "模型编码不能为空", trigger: "blur" }],
    modelType: [{ required: true, message: "模型类型不能为空", trigger: "change" }],
    status: [{ required: true, message: "状态不能为空", trigger: "change" }]
  }
})

const { queryParams, form, rules, modelForm, modelRules } = toRefs(data)

/** 查询供应商列表 */
function getList() {
  loading.value = true
  listSupplier(proxy.addDateRange(queryParams.value)).then(response => {
    supplierList.value = response.rows
    total.value = response.total
    loading.value = false
  })
}

/** 取消按钮 */
function cancel() {
  open.value = false
  reset()
}

/** 表单重置 */
function reset() {
  form.value = {
    supplierId: undefined,
    supplierName: undefined,
    supplierCode: undefined,
    apiBaseUrl: undefined,
    apiKey: undefined,
    description: undefined,
    status: "0",
    sortOrder: 0,
    remark: undefined
  }
  proxy.resetForm("supplierRef")
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm("queryRef")
  handleQuery()
}

/** 多选框选中数据 */
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.supplierId)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  open.value = true
  title.value = "添加AI供应商"
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset()
  const supplierId = row.supplierId || ids.value
  getSupplier(supplierId).then(response => {
    form.value = response.data
    open.value = true
    title.value = "修改AI供应商"
  })
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["supplierRef"].validate(valid => {
    if (valid) {
      if (form.value.supplierId != undefined) {
        updateSupplier(form.value).then(response => {
          proxy.$modal.msgSuccess("修改成功")
          open.value = false
          getList()
        })
      } else {
        addSupplier(form.value).then(response => {
          proxy.$modal.msgSuccess("新增成功")
          open.value = false
          getList()
        })
      }
    }
  })
}

/** 删除按钮操作 */
function handleDelete(row) {
  const supplierIds = row.supplierId || ids.value
  proxy.$modal.confirm('是否确认删除供应商编号为"' + supplierIds + '"的数据项？').then(function () {
    return delSupplier(supplierIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download("supplier/export", {
    ...queryParams.value
  }, `supplier_${new Date().getTime()}.xlsx`)
}

/** 测试连接 */
function handleTest(row) {
  proxy.$modal.loading("正在测试连接，请稍候...")
  testConnection(row).then(response => {
    proxy.$modal.closeLoading()
    proxy.$modal.msgSuccess(response.msg)
  }).catch(() => {
    proxy.$modal.closeLoading()
  })
}

/** 模型管理 */
function handleModels(row) {
  currentSupplierId.value = row.supplierId
  modelOpen.value = true
  getModelList()
}

/** 查询模型列表 */
function getModelList() {
  modelLoading.value = true
  listModel({ supplierId: currentSupplierId.value }).then(response => {
    modelList.value = response.rows
    modelLoading.value = false
  })
}

/** 新增模型 */
function handleAddModel() {
  resetModel()
  modelForm.value.supplierId = currentSupplierId.value
  modelFormOpen.value = true
  modelTitle.value = "添加模型"
}

/** 修改模型 */
function handleUpdateModel(row) {
  resetModel()
  const modelId = row.modelId
  getModel(modelId).then(response => {
    modelForm.value = response.data
    modelFormOpen.value = true
    modelTitle.value = "修改模型"
  })
}

/** 提交模型表单 */
function submitModelForm() {
  proxy.$refs["modelRef"].validate(valid => {
    if (valid) {
      if (modelForm.value.modelId != undefined) {
        updateModel(modelForm.value).then(response => {
          proxy.$modal.msgSuccess("修改成功")
          modelFormOpen.value = false
          getModelList()
        })
      } else {
        addModel(modelForm.value).then(response => {
          proxy.$modal.msgSuccess("新增成功")
          modelFormOpen.value = false
          getModelList()
        })
      }
    }
  })
}

/** 删除模型 */
function handleDeleteModel(row) {
  const modelIds = row.modelId
  proxy.$modal.confirm('是否确认删除该模型？').then(function () {
    return delModel(modelIds)
  }).then(() => {
    getModelList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

/** 取消模型 */
function cancelModel() {
  modelFormOpen.value = false
  resetModel()
}

/** 重置模型表单 */
function resetModel() {
  modelForm.value = {
    modelId: undefined,
    supplierId: undefined,
    modelName: undefined,
    modelCode: undefined,
    modelType: "chat",
    contextLength: 4096,
    maxTokens: 2048,
    temperature: 0.70,
    topP: 1.00,
    status: "0",
    sortOrder: 0,
    remark: undefined
  }
  proxy.resetForm("modelRef")
}

getList()
</script>
