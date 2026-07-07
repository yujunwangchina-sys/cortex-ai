# Skill 技能包上传功能实现说明

**实现日期**: 2026-07-03  
**功能描述**: 支持上传技能包压缩包，自动解析导入到Skill管理系统

---

## 一、功能概述

### 1.1 上传功能特性

- **上传入口**: 技能树最外层右键菜单 → "上传技能包"
- **文件格式**: `.zip` 压缩包
- **允许的文件类型**: `.md`, `.py`, `.js`, `.txt`, `.json`
- **结构要求**:
  - 第一层必须是技能包名称文件夹
  - 根目录必须有 `DESCRIPTION.md`
  - 每个技能子目录必须有 `SKILL.md`
- **同名处理**: 自动覆盖同名技能包（需权限检查）
- **权限支持**: 全局/业务系统/个人三层权限

---

## 二、压缩包结构示例

```
my-skill-package.zip
└── my-skill-package/          ← 第一层：技能包名称文件夹
    ├── DESCRIPTION.md         ✅ 必须
    ├── skill-1/
    │   ├── SKILL.md          ✅ 必须
    │   ├── implementation.py  ✅ 允许
    │   ├── config.json       ✅ 允许
    │   └── helper.js         ✅ 允许
    ├── skill-2/
    │   ├── SKILL.md          ✅ 必须
    │   └── script.py         ✅ 允许
    └── README.md             ✅ 可选
```

---

## 三、后端实现（已完成）

### 3.1 Controller 层

**文件**: `SkillNodeController.java`

**新增接口**:
```java
@PostMapping("/upload")
public AjaxResult uploadSkillPackage(
    @RequestParam("file") MultipartFile file,
    @RequestParam(defaultValue = "user") String skillScope,
    @RequestParam(required = false) String businessSystem,
    @RequestParam(required = false) String ownerUser)
```

### 3.2 Service 层

**文件**: `ISkillNodeService.java` 和 `SkillNodeServiceImpl.java`

**核心方法**:
```java
String uploadSkillPackage(
    MultipartFile file,
    String skillScope,
    String businessSystem,
    String ownerUser) throws Exception
```

**实现逻辑**:
1. 解压zip到临时目录
2. 查找技能包根目录（第一层文件夹）
3. 校验 `DESCRIPTION.md` 和每个技能的 `SKILL.md`
4. 校验文件扩展名（只允许 5 种）
5. 查找同名技能包并删除（权限检查）
6. 递归导入到数据库
7. 清理临时目录

### 3.3 校验规则

**允许的文件扩展名**:
```java
private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>(Arrays.asList(
    "md", "py", "js", "txt", "json"
));
```

**必需的文件**:
- `{技能包根目录}/DESCRIPTION.md`
- `{技能包根目录}/{技能目录}/SKILL.md`

---

## 四、前端实现（待完成）

### 4.1 修改 SkillTree.vue

#### 4.1.1 添加上传对话框

在 `<template>` 部分添加：

```vue
<!-- 上传技能包对话框 -->
<el-dialog
  v-model="uploadDialogVisible"
  title="上传技能包"
  width="600px"
  :close-on-click-modal="false"
>
  <el-form :model="uploadForm" :rules="uploadRules" ref="uploadFormRef" label-width="100px">
    <el-form-item label="压缩包" prop="file">
      <el-upload
        ref="uploadRef"
        action="#"
        :limit="1"
        :auto-upload="false"
        :on-change="handleFileChange"
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
        <el-radio value="system">
          <el-tag type="success" size="small">全局</el-tag>
          所有Agent可用
        </el-radio>
        <el-radio value="business">
          <el-tag type="warning" size="small">业务系统</el-tag>
          指定业务系统Agent可用
        </el-radio>
        <el-radio value="user">
          <el-tag type="info" size="small">个人</el-tag>
          仅个人Agent可用
        </el-radio>
      </el-radio-group>
    </el-form-item>

    <el-form-item v-if="uploadForm.skillScope === 'business' || uploadForm.skillScope === 'user'" 
                  label="业务系统" prop="businessSystem">
      <el-select v-model="uploadForm.businessSystem" placeholder="请选择业务系统" style="width: 100%">
        <el-option label="ERP" value="ERP" />
        <el-option label="CRM" value="CRM" />
        <el-option label="OA" value="OA" />
      </el-select>
    </el-form-item>

    <el-form-item v-if="uploadForm.skillScope === 'user'" label="所有者" prop="ownerUser">
      <el-input v-model="uploadForm.ownerUser" placeholder="默认当前用户" />
    </el-form-item>
  </el-form>

  <template #footer>
    <el-button @click="uploadDialogVisible = false">取消</el-button>
    <el-button type="primary" :loading="uploading" @click="submitUpload">上传</el-button>
  </template>
</el-dialog>
```

#### 4.1.2 添加 script 部分

```javascript
import { ref } from 'vue'
import { uploadSkillPackage } from '@/api/skill'
import { ElMessage } from 'element-plus'

// 上传相关
const uploadDialogVisible = ref(false)
const uploadFileList = ref([])
const uploading = ref(false)
const uploadForm = ref({
  file: null,
  skillScope: 'user',
  businessSystem: '',
  ownerUser: ''
})

// 打开上传对话框
const handleUploadSkillPackage = () => {
  uploadForm.value = {
    file: null,
    skillScope: 'user',
    businessSystem: '',
    ownerUser: ''
  }
  uploadFileList.value = []
  uploadDialogVisible.value = true
}

// 文件选择变化
const handleFileChange = (file, fileList) => {
  uploadForm.value.file = file.raw
  uploadFileList.value = fileList
}

// 提交上传
const submitUpload = async () => {
  if (!uploadForm.value.file) {
    ElMessage.warning('请选择要上传的技能包')
    return
  }

  if (!uploadForm.value.file.name.toLowerCase().endsWith('.zip')) {
    ElMessage.error('只支持 .zip 格式的压缩包')
    return
  }

  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', uploadForm.value.file)
    formData.append('skillScope', uploadForm.value.skillScope === 'business' ? 'system' : uploadForm.value.skillScope)
    if (uploadForm.value.businessSystem) {
      formData.append('businessSystem', uploadForm.value.businessSystem)
    }
    if (uploadForm.value.ownerUser) {
      formData.append('ownerUser', uploadForm.value.ownerUser)
    }

    await uploadSkillPackage(formData)
    ElMessage.success('技能包上传成功')
    uploadDialogVisible.value = false
    await loadTree() // 刷新树
  } catch (error) {
    console.error('上传失败:', error)
    ElMessage.error(error.message || '上传失败')
  } finally {
    uploading.value = false
  }
}
```

#### 4.1.3 修改右键菜单

在 `ContextMenu` 组件中添加"上传技能包"选项，当右键点击空白区域时显示。

```vue
<el-menu-item @click="$emit('upload-skill-package')">
  <el-icon><Upload /></el-icon>
  <span>上传技能包</span>
</el-menu-item>
```

在 `SkillTree.vue` 中绑定事件：

```vue
<ContextMenu
  @upload-skill-package="handleUploadSkillPackage"
  ... 其他属性
/>
```

### 4.2 添加 API 接口

**文件**: `src/api/skill.js`

```javascript
/**
 * 上传技能包
 */
export function uploadSkillPackage(data) {
  return request({
    url: '/skill/upload',
    method: 'post',
    data: data,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}
```

### 4.3 修改新建文件的文件类型选项

在 `dialogForm.fileType` 的选择框中，只保留以下选项：

```vue
<el-option label="Markdown (.md)" value=".md" />
<el-option label="Python (.py)" value=".py" />
<el-option label="JavaScript (.js)" value=".js" />
<el-option label="JSON (.json)" value=".json" />
<el-option label="Text (.txt)" value=".txt" />
```

移除其他选项（如 .html, .css 等）。

---

## 五、测试清单

### 5.1 上传成功场景

- [ ] 上传符合规范的技能包（包含DESCRIPTION.md和SKILL.md）
- [ ] 上传全局类型技能包
- [ ] 上传业务系统类型技能包
- [ ] 上传个人类型技能包
- [ ] 同名技能包覆盖（相同权限）
- [ ] 上传后技能树正确显示
- [ ] 权限标签正确显示（全局/业务系统/个人）

### 5.2 上传失败场景

- [ ] 上传非.zip文件 → 提示错误
- [ ] 压缩包第一层不是单个文件夹 → 提示结构错误
- [ ] 缺少 DESCRIPTION.md → 提示缺少必需文件
- [ ] 技能目录缺少 SKILL.md → 提示缺少必需文件
- [ ] 包含不支持的文件格式(.html, .exe等) → 提示文件类型不支持
- [ ] 尝试覆盖其他用户的个人技能包 → 权限检查失败

### 5.3 文件格式限制测试

- [ ] 新建文件时只显示5种文件类型
- [ ] 上传包含 .md 文件 → 成功
- [ ] 上传包含 .py 文件 → 成功
- [ ] 上传包含 .js 文件 → 成功
- [ ] 上传包含 .txt 文件 → 成功
- [ ] 上传包含 .json 文件 → 成功
- [ ] 上传包含 .html 文件 → 失败
- [ ] 上传包含 .xml 文件 → 失败

---

## 六、后续优化建议

1. **压缩包预览**: 上传前显示压缩包结构预览
2. **批量上传**: 支持一次上传多个技能包
3. **导出功能**: 支持将技能包导出为.zip文件
4. **版本管理**: 技能包版本控制和回滚
5. **自动校验**: 上传时实时校验并给出详细错误提示
6. **进度显示**: 大文件上传时显示进度条

---

## 七、注意事项

1. **权限检查**: 覆盖技能包时必须检查权限
   - 全局技能包：需要管理员权限
   - 业务系统技能包：需要业务系统管理员权限
   - 个人技能包：只能覆盖自己的

2. **文件安全**: 
   - 防止zip slip攻击
   - 限制文件类型（只允许5种）
   - 限制压缩包大小（建议10MB以内）

3. **事务处理**: 
   - 导入失败时自动回滚数据库
   - 清理临时文件

4. **错误提示**: 
   - 详细说明哪个文件不符合规范
   - 给出修正建议

---

**实现状态**: 
- ✅ 后端实现完成
- ⏳ 前端实现待完成

**下一步**:
1. 修改前端 SkillTree.vue 添加上传对话框
2. 添加 API 接口调用
3. 修改文件类型选择框
4. 测试完整流程
