# Skill 三层权限体系实现总结

**实现日期**: 2026-07-01  
**功能描述**: 实现技能包的三层权限隔离体系（全局/业务系统/个人）

---

## 一、功能概述

### 1.1 权限架构

```
┌─────────────────────────────────────────┐
│          AI中台 - Skill管理              │
├─────────────────────────────────────────┤
│  ┌───────────────────────────────────┐  │
│  │  全局技能包（所有Agent可用）        │  │
│  │  skillScope=system                 │  │
│  │  businessSystem=null               │  │
│  └───────────────────────────────────┘  │
│                                         │
│  ┌───────────────────────────────────┐  │
│  │  业务系统技能包（业务系统Agent可用）│  │
│  │  skillScope=system                 │  │
│  │  businessSystem=ERP/CRM/OA         │  │
│  └───────────────────────────────────┘  │
│                                         │
│  ┌───────────────────────────────────┐  │
│  │  个人技能包（个人Agent可用）        │  │
│  │  skillScope=user                   │  │
│  │  ownerUser=张三                    │  │
│  └───────────────────────────────────┘  │
└─────────────────────────────────────────┘
```

### 1.2 三层权限说明

| 权限层级 | skillScope | businessSystem | ownerUser | 可用范围 |
|---------|-----------|----------------|-----------|---------|
| **全局** | `system` | `null` | `null` | 所有业务系统的所有Agent |
| **业务系统** | `system` | `ERP/CRM/OA` | `null` | 指定业务系统的所有Agent |
| **个人** | `user` | 任意 | `张三` | 指定用户的Agent |

### 1.3 自学习机制

- Agent 自学习生成的技能固定为 **个人级别**（`skillScope=user`）
- 用户可通过 Skill 管理界面手动创建三种类型的技能包
- 业务系统管理员可将优秀的个人技能提升为业务系统技能

---

## 二、数据库变更

### 2.1 skill_node 表新增字段

| 字段名 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `skill_scope` | VARCHAR(20) | `system` | 技能范围：system(系统级) / user(用户级) |
| `skill_type` | VARCHAR(20) | `general` | 技能类型：general(通用) / dedicated(专属) |
| `learned_from_session` | VARCHAR(100) | `null` | 来源会话ID（自学习技能的来源） |
| `business_system` | VARCHAR(50) | `null` | 业务系统标识（系统级技能的业务系统归属） |
| `owner_user` | VARCHAR(50) | `null` | 技能所有者（用户级技能的创建者登录名） |

### 2.2 执行迁移脚本

```bash
# 执行 Skill 权限迁移
mysql -u root -p your_database < sql/skill_permission_migration.sql
```

**脚本功能**：
- 添加 5 个新字段
- 创建索引（`idx_skill_scope`, `idx_business_system`, `idx_owner_user`, `idx_node_type`）
- 提供数据初始化建议

---

## 三、后端实现

### 3.1 Domain 层 (SkillNode.java)

新增字段：
```java
/** 技能范围: system(系统内置,只读) / user(用户自学习) */
private String skillScope;

/** 技能类型: general(通用) / dedicated(专属) */
private String skillType;

/** 来源会话ID */
private String learnedFromSession;

/** 业务系统标识(系统技能按业务系统隔离) */
private String businessSystem;

/** 技能所有者(user级技能的创建者) */
private String ownerUser;

/** 子节点数量（非数据库字段，用于前端展示） */
private Long childCount;
```

### 3.2 Mapper 层 (SkillNodeMapper.xml)

**更新 selectSkillNodeVo**:
```xml
<sql id="selectSkillNodeVo">
    SELECT id, name, file_extension, path, is_directory, node_type, 
           skill_scope, skill_type, learned_from_session, business_system, owner_user,
           parent_id, sort_order, file_size, mime_type, 
           create_by, create_time, update_by, update_time, remark
    FROM skill_node
</sql>
```

**新增权限过滤查询**:
```xml
<!-- 三层隔离查询: 全局 + 业务系统 + 个人技能 -->
<select id="selectVisibleSkills" resultMap="SkillNodeResult">
    <include refid="selectSkillNodeVo"/>
    where node_type = 'skill'
    and (
        (skill_scope = 'system' and business_system IS NULL)
        or
        (skill_scope = 'system' and business_system = #{businessSystem})
        or
        (skill_scope = 'user' and owner_user = #{userLoginName})
    )
    order by sort_order asc, create_time desc
</select>
```

**insert/update 语句已更新**，支持新字段的保存。

### 3.3 Service 层 (ISkillNodeService.java)

新增方法：
```java
/**
 * 获取技能包列表（仅第一层，带子节点数量）
 */
List<SkillNode> getSkillPackages();

/**
 * 获取可用的技能包列表（根据业务系统和用户过滤）
 */
List<SkillNode> getAvailableSkillPackages(String businessSystem, String ownerUser);
```

**实现逻辑** (SkillNodeServiceImpl.java):

1. **getSkillPackages()**: 
   - 查询 `parent_id = null` 且 `node_type = 'skill_package'`
   - 计算每个技能包的子节点数量（`childCount`）

2. **getAvailableSkillPackages()**:
   - 过滤全局技能包：`skillScope=system` + `businessSystem=null`
   - 过滤业务系统技能包：`skillScope=system` + `businessSystem` 匹配
   - 过滤个人技能包：`skillScope=user` + `ownerUser` 匹配

### 3.4 Controller 层 (SkillNodeController.java)

新增接口：
```java
/**
 * 获取技能包列表（仅第一层）
 * GET /skill/packages
 */
@GetMapping("/packages")
public AjaxResult getSkillPackages()

/**
 * 获取可用的技能包列表（根据业务系统和用户过滤）
 * GET /skill/packages/available?businessSystem=ERP&ownerUser=zhangsan
 */
@GetMapping("/packages/available")
public AjaxResult getAvailableSkillPackages(
    @RequestParam(required = false) String businessSystem,
    @RequestParam(required = false) String ownerUser)
```

### 3.5 自学习插件 (SkillManagerPlugin.java)

**创建技能逻辑**（第 175-206 行）：
```java
node.setSkillScope("user");              // 固定为用户级别
node.setSkillType(skillType);            // general 或 dedicated
node.setLearnedFromSession(sessionId);   // 记录来源会话
node.setBusinessSystem(businessSystem);  // 设置业务系统（用于查询权限）
node.setOwnerUser(userLoginName);        // 设置所有者
```

**权限检查**（编辑技能时）：
- 系统技能（`skillScope=system`）只读，不可修改
- 个人技能（`skillScope=user`）只能所有者本人修改

---

## 四、前端实现

### 4.1 技能树过滤栏 (SkillTree.vue)

**顶部分类切换**：
```vue
<div class="filter-bar">
  <el-segmented v-model="filterType" :options="filterOptions" size="small" />
</div>
```

**过滤选项**：
- 全局
- 业务系统
- 个人

**过滤逻辑**：
```javascript
const treeData = computed(() => {
  return rawTreeData.value.filter(node => {
    if (node.nodeType !== 'skill_package') return true
    
    const skillScope = node.skillScope || 'system'
    const hasBusinessSystem = !!node.businessSystem
    
    if (filterType.value === 'global') {
      return skillScope === 'system' && !hasBusinessSystem
    } else if (filterType.value === 'business') {
      return skillScope === 'system' && hasBusinessSystem
    } else if (filterType.value === 'personal') {
      return skillScope === 'user'
    }
    return false
  })
})
```

### 4.2 技能包标签显示

**标签组件**：
```vue
<el-tag
  v-if="data.nodeType === 'skill_package'"
  :type="getSkillPackageTagType(data)"
  size="small"
  class="package-tag"
>
  {{ getSkillPackageLabel(data) }}
</el-tag>
```

**标签逻辑**：
```javascript
// 标签类型
function getSkillPackageTagType(data) {
  const skillScope = data.skillScope || 'system'
  const hasBusinessSystem = !!data.businessSystem
  
  if (skillScope === 'system' && !hasBusinessSystem) {
    return 'success' // 全局 - 绿色
  } else if (skillScope === 'system' && hasBusinessSystem) {
    return 'warning' // 业务系统 - 橙色
  } else if (skillScope === 'user') {
    return 'info' // 个人 - 灰色
  }
}

// 标签文本
function getSkillPackageLabel(data) {
  const skillScope = data.skillScope || 'system'
  const hasBusinessSystem = !!data.businessSystem
  
  if (skillScope === 'system' && !hasBusinessSystem) {
    return '全局'
  } else if (skillScope === 'system' && hasBusinessSystem) {
    return data.businessSystem || '业务' // 显示业务系统名称
  } else if (skillScope === 'user') {
    return '个人'
  }
}
```

### 4.3 新建技能包对话框

**表单字段**：
```vue
<!-- 技能包类型选择 -->
<el-form-item label="技能包类型" prop="skillScope">
  <el-radio-group v-model="dialogForm.skillScope">
    <el-radio value="system">
      <el-tag type="success" size="small">全局</el-tag>
      <span>所有Agent可用</span>
    </el-radio>
    <el-radio value="business">
      <el-tag type="warning" size="small">业务系统</el-tag>
      <span>指定业务系统Agent可用</span>
    </el-radio>
    <el-radio value="user">
      <el-tag type="info" size="small">个人</el-tag>
      <span>仅个人Agent可用</span>
    </el-radio>
  </el-radio-group>
</el-form-item>

<!-- 业务系统输入（仅业务系统类型显示） -->
<el-form-item v-if="dialogForm.skillScope === 'business'" label="业务系统" prop="businessSystem">
  <el-input v-model="dialogForm.businessSystem" placeholder="例如：ERP、CRM、OA" />
</el-form-item>
```

**创建逻辑**：
```javascript
function doCreateSkillPackage() {
  const data = {
    name: dialogForm.name,
    parentId: null,
    isDirectory: true,
    nodeType: 'skill_package',
    skillScope: dialogForm.skillScope,
    businessSystem: dialogForm.skillScope === 'business' ? dialogForm.businessSystem : null,
    ownerUser: dialogForm.skillScope === 'user' ? userStore.user.userName : null
  }
  
  createFolder(data).then(() => {
    ElMessage.success('技能包创建成功')
    dialogVisible.value = false
    loadTree()
  })
}
```

### 4.4 Agent 权限配置 (AgentPermission.vue)

**接口调用**：
```javascript
// 使用新的可用技能包接口，根据 Agent 的业务系统过滤
const loadAvailableSkills = () => {
  loading.value = true
  getAvailableSkillPackages({
    businessSystem: props.agent.businessSystem,
    ownerUser: userStore.user.userName
  }).then(response => {
    allSkills.value = response.data || []
    loading.value = false
  })
}
```

**展示结果**：
- Agent 绑定 `businessSystem=ERP` 时，只能看到：
  - 全局技能包
  - ERP 业务系统技能包
  - 当前用户的个人技能包

---

## 五、使用场景

### 5.1 场景1：全局通用技能

**用例**: 创建一个所有 Agent 都能使用的 Python 工具技能包

**操作**：
1. 点击"新建技能包"
2. 选择"全局"类型
3. 输入名称：`python-tools`
4. 创建后显示绿色"全局"标签

**效果**：
- 所有业务系统的所有 Agent 都能使用此技能包
- 自学习的 Agent 可以调用这些技能

### 5.2 场景2：业务系统专属技能

**用例**: 创建 ERP 系统专用的订单处理技能包

**操作**：
1. 点击"新建技能包"
2. 选择"业务系统"类型
3. 输入业务系统：`ERP`
4. 输入名称：`erp-order-tools`
5. 创建后显示橙色"ERP"标签

**效果**：
- 只有 `businessSystem=ERP` 的 Agent 能使用
- CRM、OA 等其他业务系统的 Agent 看不到此技能包

### 5.3 场景3：个人自学习技能

**用例**: 张三的 Agent 在对话中自动学习了新技能

**操作**：
- Agent 自动执行 `skill_create` 工具
- 自动设置：
  - `skillScope=user`
  - `ownerUser=zhangsan`
  - `learnedFromSession=sess-xxx`

**效果**：
- 只有张三的 Agent 能使用此技能
- 李四、王五等其他用户看不到

**提升为业务技能**：
- 管理员发现张三的技能很有价值
- 可修改该技能包的 `skillScope=system` + `businessSystem=ERP`
- 变为 ERP 业务系统技能，ERP 的所有用户都能使用

---

## 六、权限验证流程

### 6.1 Agent 权限配置时

```
用户选择技能包
    ↓
前端调用 /skill/packages/available
    ↓
传入 businessSystem + ownerUser
    ↓
后端过滤：
  ✓ 全局技能包（所有人可见）
  ✓ 匹配的业务系统技能包
  ✓ 用户自己的个人技能包
    ↓
返回过滤后的技能包列表
```

### 6.2 Agent 运行时

```
Agent 执行对话
    ↓
调用 skills_list 工具
    ↓
SkillManagerPlugin 根据 _businessSystem + _userLoginName 过滤
    ↓
返回可用技能列表
    ↓
Agent 使用技能完成任务
```

---

## 七、测试清单

### 7.1 数据库测试
- [ ] 执行迁移脚本成功
- [ ] 新字段可正常读写
- [ ] 索引创建成功
- [ ] 默认值正确

### 7.2 后端测试
- [ ] 创建全局技能包
- [ ] 创建业务系统技能包（带 businessSystem）
- [ ] 创建个人技能包（带 ownerUser）
- [ ] `/skill/packages` 接口返回所有技能包
- [ ] `/skill/packages/available` 正确过滤技能包
- [ ] Agent 自学习创建个人技能
- [ ] 编辑系统技能被拒绝
- [ ] 编辑他人个人技能被拒绝

### 7.3 前端测试
- [ ] 技能树顶部过滤栏显示正常
- [ ] 切换"全局/业务系统/个人"过滤正常
- [ ] 技能包节点显示正确的标签（全局/ERP/个人）
- [ ] 新建技能包对话框显示类型选择
- [ ] 选择"业务系统"类型时显示业务系统输入框
- [ ] 创建全局技能包成功（绿色标签）
- [ ] 创建业务系统技能包成功（橙色标签显示业务系统名）
- [ ] 创建个人技能包成功（灰色标签）
- [ ] Agent 权限配置只显示可用技能包

### 7.4 集成测试
- [ ] Agent A（ERP）配置技能时只看到全局+ERP+个人技能
- [ ] Agent B（CRM）配置技能时只看到全局+CRM+个人技能
- [ ] Agent 运行时调用 skills_list 返回正确的技能列表
- [ ] Agent 自学习创建的技能只有创建者可见
- [ ] 切换过滤类型后技能树正确更新

---

## 八、相关文档

- **Agent API Key 授权**: `doc/Agent-API-Key授权机制实现总结.md`
- **Agent 嵌入指南**: `doc/Agent嵌入使用指南.md`
- **数据库迁移脚本**: 
  - `sql/agent_api_key_migration.sql`
  - `sql/skill_permission_migration.sql`

---

## 九、后续优化

### 9.1 短期优化
- [ ] 技能包批量导入/导出
- [ ] 技能包共享功能（个人 → 业务系统 → 全局）
- [ ] 技能使用统计（哪些技能最常用）
- [ ] 技能版本管理

### 9.2 长期规划
- [ ] 技能市场（用户可发布/订阅技能包）
- [ ] 技能推荐系统（根据使用场景推荐技能）
- [ ] 技能质量评分
- [ ] 技能依赖管理（技能A依赖技能B）

---

**实现状态**: ✅ 完成  
**测试状态**: ⏳ 待测试  
**文档状态**: ✅ 已完成
