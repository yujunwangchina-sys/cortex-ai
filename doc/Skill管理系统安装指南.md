# Skill管理系统 - 安装指南

## 一、前端依赖安装 ✅

前端依赖已安装完成：
- ✅ marked (Markdown渲染库)
- ✅ dompurify (HTML清理库)
- ✅ @types/dompurify (TypeScript类型定义)

## 二、数据库配置

### 1. 执行SQL脚本

在数据库中执行以下SQL文件：

```bash
sql/skill_node.sql
```

该脚本会创建：
- ✅ `skill_node` 表（技能节点表）
- ✅ 菜单权限配置
- ✅ 示例数据（可选）

### 2. 验证表创建

执行以下SQL验证表是否创建成功：

```sql
SELECT * FROM skill_node;
SELECT * FROM sys_menu WHERE menu_name LIKE '%Skill%';
```

## 三、后端代码说明

### 已创建的文件：

#### 1. Domain层（实体类）
- `cortex-system/src/main/java/com/cortex/skill/domain/SkillNode.java`

#### 2. Mapper层（数据访问）
- `cortex-system/src/main/java/com/cortex/skill/mapper/SkillNodeMapper.java`
- `cortex-system/src/main/resources/mapper/skill/SkillNodeMapper.xml`

#### 3. Service层（业务逻辑）
- `cortex-system/src/main/java/com/cortex/skill/service/ISkillNodeService.java`
- `cortex-system/src/main/java/com/cortex/skill/service/impl/SkillNodeServiceImpl.java`

#### 4. Controller层（控制器）
- `cortex-admin/src/main/java/com/cortex/web/controller/skill/SkillNodeController.java`

#### 5. 前端页面
- `Cortex-Vue3/src/views/skill/index.vue` - 主页面
- `Cortex-Vue3/src/views/skill/components/` - 组件目录
  - SkillTree.vue - 技能树
  - SkillEditor.vue - 编辑器
  - MarkdownEditor.vue - Markdown编辑器
  - MarkdownViewer.vue - Markdown预览器
  - FileReference.vue - 文件引用选择器
  - PluginReference.vue - 插件引用选择器
  - ContextMenu.vue - 右键菜单

#### 6. API接口
- `Cortex-Vue3/src/api/skill/skill.js`

## 四、启动验证

### 1. 编译后端

```bash
cd e:\java\Cortex-Vue
mvn clean install
```

### 2. 启动后端服务

运行 `CortexApplication.java` 或执行：

```bash
cd cortex-admin
mvn spring-boot:run
```

### 3. 启动前端

```bash
cd Cortex-Vue3
npm run dev
```

### 4. 访问系统

1. 打开浏览器访问：http://localhost:80
2. 使用管理员账号登录（admin/admin123）
3. 在左侧菜单中找到"Skill管理"
4. 点击进入Skill管理页面

## 五、功能测试清单

### 基础功能测试
- [ ] 左侧技能树正常显示
- [ ] 创建文件夹
- [ ] 创建文件（.md和其他类型）
- [ ] 重命名文件/文件夹
- [ ] 删除文件/文件夹
- [ ] 拖拽移动文件/文件夹

### Markdown编辑功能
- [ ] 切换编辑/预览/分屏模式
- [ ] Markdown工具栏正常使用
- [ ] 文件保存功能
- [ ] 未保存提示

### 引用功能测试
- [ ] @文件引用选择器正常弹出
- [ ] 选择文件后正确插入引用语法
- [ ] @插件引用选择器正常弹出
- [ ] 选择插件后正确插入引用语法
- [ ] 预览模式下引用样式正常显示

## 六、常见问题

### 1. 菜单不显示
**解决方案：**
- 确认数据库中菜单数据已正确插入
- 使用管理员账号登录
- 清除浏览器缓存后重新登录

### 2. 保存文件失败
**解决方案：**
- 检查数据库连接是否正常
- 确认用户有 `skill:node:edit` 权限
- 查看后端日志错误信息

### 3. Markdown预览不正常
**解决方案：**
- 确认 marked 和 dompurify 已正确安装
- 检查浏览器控制台是否有JavaScript错误
- 尝试重新安装前端依赖：`npm install`

### 4. 文件引用/插件引用不工作
**解决方案：**
- 检查插件Controller中的 `/plugin/list/simple` 接口是否正常
- 确认用户有相应的查询权限
- 查看网络请求是否返回正确数据

### 5. 拖拽移动不生效
**解决方案：**
- Element Plus的Tree组件需要设置 `draggable` 属性
- 检查后端移动接口是否正常响应
- 确认用户有 `skill:node:edit` 权限

## 七、API接口列表

### Skill管理接口

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 获取技能树 | GET | /skill/tree | 获取完整的技能树结构 |
| 创建文件夹 | POST | /skill/folder | 创建新文件夹 |
| 创建文件 | POST | /skill/file | 创建新文件 |
| 删除节点 | DELETE | /skill/{id} | 删除文件或文件夹 |
| 重命名节点 | PUT | /skill/rename | 重命名文件或文件夹 |
| 移动节点 | PUT | /skill/move | 移动文件或文件夹 |
| 获取文件内容 | GET | /skill/content | 根据路径获取文件内容 |
| 保存文件内容 | POST | /skill/content | 保存文件内容 |
| 获取文件列表 | GET | /skill/files | 获取所有文件列表（用于引用） |

### 插件接口（已扩展）

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 获取插件简化列表 | GET | /plugin/list/simple | 获取已启用的插件列表（用于引用） |

## 八、权限配置

确保相关角色拥有以下权限：

```
skill:node:list     # 查询Skill列表
skill:node:query    # 查询Skill详细
skill:node:add      # 新增Skill
skill:node:edit     # 修改Skill
skill:node:remove   # 删除Skill
plugin:list:query   # 查询插件（用于引用）
```

## 九、下一步优化建议

1. **文件搜索功能** - 添加全局搜索
2. **版本历史** - 记录文件修改历史
3. **导出功能** - 导出为PDF/HTML
4. **代码高亮** - 优化代码块显示
5. **快捷键** - 添加常用快捷键支持
6. **协同编辑** - WebSocket实时协同
7. **文件上传** - 支持上传图片/附件
8. **标签分类** - 为Skill添加标签

## 十、技术支持

如遇到问题，请查看：
1. 后端日志：`logs/sys-info.log`
2. 浏览器控制台错误信息
3. 网络请求响应数据

---

**安装完成！** 🎉

现在可以开始使用Skill管理系统了！
