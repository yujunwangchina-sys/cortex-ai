# Skill管理系统

## 功能特性

### 1. 技能树管理
- ✅ 左侧树形结构展示所有Skill文件和文件夹
- ✅ 支持拖拽移动文件/文件夹
- ✅ 右键菜单操作：新建、重命名、删除
- ✅ 图标区分文件夹、普通文件、Markdown文件

### 2. Markdown编辑器
- ✅ 三种查看模式：编辑、预览、分屏
- ✅ 工具栏快捷操作：加粗、斜体、标题、代码、链接等
- ✅ 支持@文件引用和@插件引用
- ✅ 实时预览Markdown渲染效果

### 3. 文件引用功能
- ✅ @文件：可以引用项目中的任何文件
- ✅ @插件：可以引用已注册的插件工具
- ✅ 引用语法：
  - 文件引用：`@file[文件名](文件路径)`
  - 插件引用：`@plugin[插件名称](插件名称)`

### 4. 多文件类型支持
- ✅ Markdown文件(.md)：完整的编辑和预览功能
- ✅ 普通文本文件：基础文本编辑
- ✅ JSON、TXT等其他格式

## 组件结构

```
src/views/skill/
├── index.vue                  # 主入口页面（左右布局）
├── components/
│   ├── SkillTree.vue         # 左侧技能树组件
│   ├── SkillEditor.vue       # 右侧编辑器组件
│   ├── MarkdownViewer.vue    # Markdown预览组件
│   ├── MarkdownEditor.vue    # Markdown编辑器组件
│   ├── FileReference.vue     # 文件引用选择器组件
│   ├── PluginReference.vue   # 插件引用选择器组件
│   └── ContextMenu.vue       # 右键菜单组件
└── README.md                  # 说明文档
```

## 依赖安装

### 1. Markdown渲染库
```bash
npm install marked --save
```

### 2. HTML清理库（防止XSS攻击）
```bash
npm install dompurify --save
npm install @types/dompurify --save-dev
```

### 3. Element Plus图标（如果未安装）
```bash
npm install @element-plus/icons-vue --save
```

## 后端API接口

需要实现以下接口：

### Skill树管理
- `GET /skill/tree` - 获取技能树
- `POST /skill/folder` - 创建文件夹
- `POST /skill/file` - 创建文件
- `DELETE /skill/{id}` - 删除节点
- `PUT /skill/rename` - 重命名节点
- `PUT /skill/move` - 移动节点

### 文件内容管理
- `GET /skill/content?filePath={path}` - 获取文件内容
- `POST /skill/content` - 保存文件内容

### 引用功能
- `GET /skill/files` - 获取所有可引用的文件列表
- `GET /plugin/list/simple` - 获取简化的插件列表
- `POST /skill/parse-references` - 解析文件中的引用

## 数据结构示例

### 技能树节点
```json
{
  "id": 1,
  "name": "我的技能",
  "path": "/skills/my-skill.md",
  "isDirectory": false,
  "parentId": null,
  "children": []
}
```

### 文件内容
```json
{
  "id": 1,
  "path": "/skills/my-skill.md",
  "content": "# 我的技能\n\n这是一个示例技能..."
}
```

## 使用示例

### 创建Skill文件
1. 在左侧树中右键点击文件夹
2. 选择"新建文件"
3. 输入文件名（如：`my-skill.md`）
4. 在右侧编辑器中编写内容

### 引用其他文件
在Markdown编辑器中：
1. 点击工具栏"@文件"按钮
2. 在弹出的对话框中选择要引用的文件
3. 自动插入引用语法：`@file[文件名](文件路径)`

### 引用插件
在Markdown编辑器中：
1. 点击工具栏"@插件"按钮
2. 在弹出的对话框中选择要引用的插件
3. 自动插入引用语法：`@plugin[插件名称](插件名称)`

## 注意事项

1. **文件命名**：避免使用特殊字符 `\ / : * ? " < > |`
2. **拖拽移动**：支持文件和文件夹的拖拽排序和移动
3. **自动保存提示**：关闭文件或切换文件时，如有未保存内容会提示保存
4. **引用渲染**：在预览模式下，文件引用和插件引用会以特殊样式显示

## 路由配置

在 `src/router/index.js` 中添加路由（或通过后端菜单配置）：

```javascript
{
  path: '/skill',
  component: Layout,
  meta: { title: 'Skill管理', icon: 'skill' },
  children: [
    {
      path: 'index',
      component: () => import('@/views/skill/index'),
      name: 'SkillManage',
      meta: { title: 'Skill管理' }
    }
  ]
}
```

## 待优化功能

- [ ] 文件搜索功能
- [ ] 批量操作（批量删除、移动）
- [ ] 版本历史记录
- [ ] 协同编辑
- [ ] 导出为PDF/HTML
- [ ] 代码高亮优化
- [ ] 快捷键支持
- [ ] 文件收藏夹
