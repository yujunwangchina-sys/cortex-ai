# Agent管理组件说明

## 组件列表

### 1. AgentForm.vue
Agent新增/编辑表单组件
- Agent基本信息编辑
- 系统提示词Markdown编辑器
- 表单验证

### 2. AgentPermission.vue
Agent权限配置组件
- Skill技能权限分配（树形选择）
- 插件工具权限分配（卡片选择）
- 支持搜索过滤

## 使用方式

```vue
<template>
  <div>
    <!-- Agent表单 -->
    <AgentForm
      v-model:visible="formVisible"
      :agent-id="currentAgentId"
      @success="handleSuccess"
    />

    <!-- 权限配置 -->
    <AgentPermission
      v-model:visible="permissionVisible"
      :agent-id="currentAgentId"
    />
  </div>
</template>
```

## 功能特性

- ✅ Agent CRUD操作
- ✅ Markdown编辑器（复用Skill组件）
- ✅ Skill权限树形选择
- ✅ 插件权限卡片展示
- ✅ 搜索过滤功能
- ✅ 响应式布局
