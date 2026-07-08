# Skill渐进式披露机制详解

**文档日期**: 2026-07-03  
**适用版本**: Cortex-Vue Skill System v2.0

---

## 📋 一、核心问题解答

### 1.1 DESCRIPTION.md 的作用

#### ✅ 当前状态：**已加入第一次渐进式披露**（2026-07-03更新）

**现在的工作方式**：

1. **校验阶段** ✅ 必须存在，否则上传失败
2. **第一次披露** ✅ 读取并注入到技能索引中
3. **运行时优化** ✅ 按技能包分组显示，每个技能包显示描述

**披露流程**：
```
加载技能索引时
    ↓
expandSkillPackage 方法
    ↓
查找子节点中的 DESCRIPTION.md
    ↓
懒加载其 content 字段（如果未加载）
    ↓
提取前200字符的有效内容（跳过YAML frontmatter）
    ↓
保存到技能包节点的 skillMetadata.packageDescription
    ↓
PromptBuilder 按技能包分组显示
    ↓
每个技能包标题下显示 DESCRIPTION.md 的描述
```

**注入效果**：
```markdown
## 技能索引

### Apple技能包
*Apple生产力工具集成，包括Notes笔记管理、Reminders任务管理和Calendar日历管理。*

- **apple-notes** — 管理Apple Notes笔记  [id=123]
- **apple-reminders** — 管理Apple Reminders任务  [id=124]
- **apple-calendar** — 管理Apple Calendar日历  [id=125]

### Python开发工具包
*Python开发常用工具集，包括代码检查、性能分析和测试覆盖率工具。*

- **python-lint** — Python代码质量检查  [id=201]
- **python-profiler** — Python性能分析工具  [id=202]
```

**优势**：
- ✅ AI第一次就能看到技能包的整体用途
- ✅ 只读取前200字符，Token消耗可控
- ✅ 如果没有DESCRIPTION.md，降级到只显示技能列表
- ✅ 不需要持久化，临时存储在内存中

**Token消耗**：
- 每个技能包描述约 ~50 tokens（200字符）
- 10个技能包 = ~500 tokens
- 相比完整SKILL.md（每个5K tokens），节省 **99%**

---

### 1.2 README.md 的作用

#### ❌ 当前状态：**完全不使用**

- ❌ **校验阶段**：非必需，可选文件
- ❌ **运行时**：不会被加载
- ❌ **技能索引**：不参与任何逻辑

**结论**：`README.md` 只是技能包的说明文档，供人类阅读，AI完全看不到。

---

### 1.3 Agent授权Skill体系是否生效？

#### ✅ 已完全生效！

**核心机制**：`ai_agent_skill` 关联表

```sql
CREATE TABLE `ai_agent_skill` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `agent_id` bigint(20) NOT NULL COMMENT 'Agent ID',
  `skill_id` bigint(20) NOT NULL COMMENT 'Skill ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_skill` (`agent_id`, `skill_id`)
);
```

#### 授权流程验证

**1. 前端配置界面**
```vue
<!-- AgentPermission.vue -->
<el-alert
  title="选择该Agent可以使用的全局Skill技能包（个人Skill由Agent自学习自动生成，无需手动分配）"
  type="info"
/>
```

**2. 保存授权**
```javascript
// api/agent/agent.js
export function saveAgentSkills(id, skillIds) {
  return request({
    url: `/agent/agent/${id}/skills`,
    method: 'put',
    data: skillIds
  })
}
```

**3. 后端处理**
```java
// AiAgentServiceImpl.java
public int saveAgentSkills(Long agentId, List<Long> skillIds) {
    // 删除旧的关联
    aiAgentSkillMapper.deleteAgentSkillByAgentId(agentId);
    
    // 插入新的关联
    List<AiAgentSkill> list = new ArrayList<>();
    for (Long skillId : skillIds) {
        AiAgentSkill agentSkill = new AiAgentSkill();
        agentSkill.setAgentId(agentId);
        agentSkill.setSkillId(skillId);
        list.add(agentSkill);
    }
    return aiAgentSkillMapper.batchInsertAgentSkill(list);
}
```

**4. 运行时加载**
```java
// AgentConfigLoader.java:126
public List<SkillNode> loadSkillIndex(Long agentId, String businessSystem, String userLoginName) {
    // ✅ 从关联表加载授权的Skill ID
    List<Long> skillIds = agentSkillMapper.selectSkillIdsByAgentId(agentId);
    
    List<SkillNode> result = new ArrayList<>();
    for (Long skillId : skillIds) {
        SkillNode node = skillNodeMapper.selectNodeById(skillId);
        // 权限校验 + 展开技能包
        if (!isSkillVisible(node, businessSystem, userLoginName)) continue;
        // ...
    }
    return result;
}
```

**5. 注入系统提示词**
```java
// AgentRuntime.java:82-85
List<SkillNode> skills = configLoader.loadSkillIndex(
    agent.getId(), ctx.getBusinessSystem(), ctx.getUserLoginName());
String systemPrompt = promptBuilder.buildSystemPrompt(agent, skills, memoryText);
```

---

## 📊 二、完整的渐进式披露流程（最新实现 v2.0）

```
用户发起对话
    ↓
┌─────────────────────────────────────────────┐
│ 1. Agent启动 (AgentRuntime.run)             │
└─────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────┐
│ 2. 加载授权的Skill ID                        │
│    SELECT skill_id FROM ai_agent_skill       │
│    WHERE agent_id = #{agentId}              │
│    ✅ 授权体系生效点                         │
└─────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────┐
│ 3. 根据ID加载Skill节点（轻量级）             │
│    - 只加载元数据：id, name, path            │
│    - 不加载content字段                       │
│    - 应用两层权限过滤：                      │
│      ✓ global: 所有人可见                   │
│      ✓ personal: 仅owner可见                │
└─────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────┐
│ 4. 展开技能包（expandSkillPackage）         │
│    ✅ 新增：查找 DESCRIPTION.md 文件         │
│    ✅ 新增：懒加载其 content 字段            │
│    ✅ 新增：提取前200字符作为技能包描述      │
│    ✅ 新增：保存到 skillMetadata.packageDescription │
│    - 遍历子节点，找到所有 nodeType='skill'  │
│    - 只收集实际的技能节点                    │
└─────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────┐
│ 5. 提取简短描述                              │
│    优先级：                                  │
│    1. skillMetadata.description             │
│    2. content 的第一行非注释内容             │
│    3. 空字符串                               │
│    ✅ 技能包：读取 packageDescription        │
└─────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────┐
│ 6. 构建技能索引（注入系统提示词）             │
│    ✅ 新增：按技能包分组显示                 │
│    ✅ 新增：显示技能包描述                   │
│                                              │
│    ## 技能索引                               │
│                                              │
│    ### Apple技能包                          │
│    *Apple生产力工具集成...*                 │
│                                              │
│    - **apple-notes** — 管理Apple Notes...  │
│      [id=123]                               │
│    - **apple-reminders** — 管理Reminders   │
│      [id=124]                               │
│                                              │
│    ### Python开发工具包                     │
│    *Python开发常用工具集...*                │
│                                              │
│    - **python-lint** — 代码质量检查...     │
│      [id=201]                               │
│                                              │
│    > 提示：使用 skill_view(skillId) 获取   │
└─────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────┐
│ 7. AI看到分组的技能索引，理解技能包用途      │
└─────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────┐
│ 8. AI调用 skill_view(123)                   │
└─────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────┐
│ 9. 懒加载完整内容                            │
│    SkillNode node = skillNodeMapper          │
│        .selectNodeWithContent(skillId);     │
│    // 包含完整的 SKILL.md 内容               │
└─────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────┐
│ 10. AI获得完整技能文档，执行任务             │
└─────────────────────────────────────────────┘
```

---

## 🔍 三、文件类型在系统中的实际作用

| 文件名 | 是否必需 | 校验阶段 | 加载到AI | 参与索引 | 实际作用 |
|--------|---------|---------|----------|---------|---------|
| **SKILL.md** | ✅ 必需 | ✅ 检查存在 | ✅ 按需加载 | ✅ 提取YAML frontmatter | **核心文件**，AI按需查看完整内容 |
| **DESCRIPTION.md** | ✅ 必需 | ✅ 检查存在 | ✅ 第一次加载 | ✅ 技能包描述 | **技能包描述**，第一次披露时注入 |
| **README.md** | ❌ 可选 | ❌ 不检查 | ❌ 不加载 | ❌ 不使用 | 供人类阅读，**AI看不到** |
| **其他文件** (.py, .js, .json) | ❌ 可选 | ⚠️ 检查扩展名 | ❌ 不自动加载 | ❌ 不参与 | 技能的辅助文件，AI可能通过file_view查看 |

---

## 🎯 四、SKILL.md YAML Frontmatter 机制（v2.0新增）

### 4.1 标准格式

```markdown
---
name: python-code-analyzer
description: "Python代码质量分析工具，支持静态检查、复杂度分析和代码风格检查"
version: 1.0.0
author: Hermes Agent
license: MIT
platforms: [linux, macos, windows]
metadata:
  hermes:
    tags: [python, code-quality, linting]
prerequisites:
  commands: [pylint, flake8]
---

# Python代码分析器

完整的技能文档内容...
```

### 4.2 字段说明

| 字段 | 必填 | 说明 | 用途 |
|------|------|------|------|
| `name` | ✅ | 技能唯一标识 | 技能识别 |
| `description` | ✅ | 简短描述（20-100字符） | **技能索引显示** ⭐ |
| `version` | ✅ | 语义化版本号 | 版本管理 |
| `author` | ⭐ | 作者 | 溯源 |
| `license` | ⭐ | 许可证 | 合规 |
| `platforms` | ⭐ | 支持的平台 | 平台判断 |
| `metadata` | ⭐ | 元数据（tags等） | 分类检索 |
| `prerequisites` | ⭐ | 前置依赖 | 环境检查 |

### 4.3 描述提取优先级

```
1. YAML frontmatter 的 description 字段（最高优先级） ⭐
   ↓
2. skillMetadata JSON 的 description 字段
   ↓
3. 内容的第一段文字（跳过标题和frontmatter）
   ↓
4. 空字符串（兜底）
```

### 4.4 前端自动生成

用户在前端新建技能时，系统会自动生成包含YAML frontmatter的SKILL.md模板：

```javascript
// SkillTree.vue
const skillMdData = {
  name: 'SKILL.md',
  content: `---
name: ${skillName}
description: "${skillName}技能描述"
version: 1.0.0
author: 
license: MIT
platforms: []
metadata:
  hermes:
    tags: []
prerequisites:
  commands: []
---

# ${skillName} Skill

技能说明文档
...
`
}
```

**详细说明**：参见 [SKILL.md标准格式说明](./SKILL.md标准格式说明.md)

---

## 🚨 五、授权体系验证清单

### ✅ 已验证生效的功能

1. **Agent配置界面**
   - ✅ 只显示全局Skill（`skillScope='global'`）
   - ✅ 不显示个人Skill（由AI自学习生成）
   - ✅ 多选Skill进行授权

2. **权限保存**
   - ✅ 保存到 `ai_agent_skill` 表
   - ✅ 删除旧授权，插入新授权
   - ✅ 支持空列表（清空所有授权）

3. **运行时加载**
   - ✅ 从 `ai_agent_skill` 表查询授权的Skill ID
   - ✅ 只加载授权的Skill（未授权的看不到）
   - ✅ 应用两层权限过滤（global/personal）
   - ✅ 个人Skill自动添加（不需要授权）

4. **技能索引注入**
   - ✅ 授权的技能出现在系统提示词中
   - ✅ 未授权的技能AI看不到
   - ✅ AI只能调用 `skill_view` 查看已授权的技能

### 测试验证方法

```sql
-- 1. 查看Agent的授权Skill
SELECT s.id, s.name, s.skill_scope
FROM ai_agent_skill ags
JOIN skill_node s ON ags.skill_id = s.id
WHERE ags.agent_id = 1;

-- 2. 验证授权体系
-- 假设Agent 1 只授权了 Skill 10 和 20
-- 运行时应该只能看到这两个Skill（加上个人Skill）

-- 3. 查看个人Skill（自动添加，无需授权）
SELECT * FROM skill_node
WHERE skill_scope = 'personal'
  AND business_system = 'cortex'
  AND owner_user = 'admin';
```

---

## 🚨 五、当前存在的问题

### 问题1：DESCRIPTION.md 未被使用

**现状**：
- 上传时强制要求存在
- 运行时完全不使用
- 浪费了一个很好的元数据文件

**建议改进**：
```java
// AgentConfigLoader.java:expandSkillPackage
private void expandSkillPackage(SkillNode pkgNode, ...) {
    // 建议：读取 DESCRIPTION.md 作为技能包的整体描述
    SkillNode descFile = findChildByName(pkgNode.getId(), "DESCRIPTION.md");
    if (descFile != null && descFile.getContent() != null) {
        pkgNode.setSkillMetadata(descFile.getContent()); // 或提取frontmatter
    }
    
    // 然后在技能索引中显示技能包描述
    // ## 技能索引
    // ### Apple技能包
    // Apple生产力工具集成（Notes、Reminders、Calendar）
    //
    // - **apple-notes** — 管理Apple Notes...
    // - **apple-reminders** — 管理Apple Reminders...
}
```

### 问题2：技能包作为整体的语义缺失

**现状**：
- 技能包被展开为扁平的技能列表
- AI看不到技能之间的分组关系
- 不知道某些技能属于同一个技能包

**建议改进**：
```java
// PromptBuilder.java:buildSystemPrompt
// 按技能包分组显示
for (Map.Entry<String, List<SkillNode>> entry : skillsByPackage.entrySet()) {
    sb.append("### ").append(entry.getKey()).append("\n");
    sb.append(getPackageDescription(entry.getKey())).append("\n\n");
    for (SkillNode skill : entry.getValue()) {
        sb.append("- **").append(skill.getName()).append("**");
        // ...
    }
}
```

---

## 📝 六、总结

### 当前机制总结

| 功能 | 状态 | 说明 |
|------|------|------|
| **Agent授权体系** | ✅ 已生效 | `ai_agent_skill` 表完美工作 |
| **渐进式披露** | ✅ 已生效 | 先索引后按需加载 |
| **两层权限** | ✅ 已生效 | global + personal |
| **SKILL.md** | ✅ 核心文件 | 唯一被AI看到的技能内容 |
| **DESCRIPTION.md** | ⚠️ 未使用 | 仅校验，运行时不加载 |
| **README.md** | ⚠️ 未使用 | 完全不参与系统逻辑 |

### 关键发现

1. **授权体系完美工作** ✅
   - Agent只能看到授权的Skill
   - 个人Skill自动添加（按业务系统+用户隔离）
   - 未授权的Skill完全不可见

2. **DESCRIPTION.md 是摆设** ⚠️
   - 上传时要求必须存在
   - 运行时从不读取
   - 白白浪费了技能包级别的元数据

3. **渐进式披露有效节省Token** ✅
   - 初始只注入轻量级索引（~3K tokens）
   - AI按需调用 `skill_view` 获取完整内容
   - 相比全量注入节省60-80% tokens

---

**文档状态**: ✅ 完成  
**下次更新**: 当DESCRIPTION.md机制实现后

