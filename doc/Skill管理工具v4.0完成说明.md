# Skill 管理工具 v4.0 完成说明

## 📋 更新概述

完成了 `SkillManagerPlugin` v4.0 升级，借鉴 Hermes-Agent 的优秀设计，实现了统一的技能管理接口。

**升级时间**: 2026-07-03  
**版本**: 3.0.0 → 4.0.0  
**核心特性**: 统一管理接口 `skill_manage` + 精确 patch 功能

---

## ✅ 完成的功能

### 1. **skill_manage 统一管理工具** ⭐ 新增

取代原有的分散接口，提供统一的技能管理入口：

```java
skill_manage(
    action: "create" | "patch" | "edit" | "delete",
    name: string,
    content?: string,
    old_string?: string,
    new_string?: string
)
```

**支持的操作**：
- `create`: 创建新技能（需要 name, content）
- `patch`: 精确更新部分内容（需要 name, old_string, new_string）⭐ 推荐
- `edit`: 全量重写 SKILL.md（需要 name, content）
- `delete`: 删除技能（需要 name）

**设计理念（借鉴 Hermes）**：
- **patch 优先**: 小改用 patch，大改才用 edit
- **唯一性验证**: patch 必须确保 old_string 在文件中唯一
- **模糊匹配**: 自动处理空白字符差异，提升匹配容错性
- **安全防护**: 验证 YAML frontmatter 完整性，防止破坏结构

---

### 2. **patchSkill 精确更新** ⭐ 核心实现

实现了类似 Hermes 的 `fuzzy_find_and_replace` 机制：

**特性**：
1. **唯一性检查**: 自动验证 old_string 必须唯一
2. **模糊匹配**: 标准化空白字符，处理格式差异
3. **错误提示**: 匹配失败时返回文件预览和诊断信息
4. **结构验证**: 确保 patch 后 YAML frontmatter 仍然完整

**工作流程**：
```
1. 查找技能 → 2. 权限检查 → 3. 唯一性验证 → 4. 执行替换 → 5. 结构验证 → 6. 保存
```

**错误处理**：
- 未找到匹配 → 返回文件预览 + 提示查看完整内容
- 找到多个匹配 → 要求增加更多上下文
- 破坏结构 → 拒绝保存，提示重新检查

---

### 3. **deleteSkill 安全删除**

实现了递归删除机制，符合 Java 数据库操作规范：

**特性**：
- 权限验证（只能删除自己的技能）
- 递归删除（目录 + 所有子文件）
- 事务安全（使用 Mapper 操作数据库）

**实现逻辑**：
```java
deleteNodeRecursive(nodeId):
1. 获取所有子节点
2. 递归删除每个子节点
3. 删除当前节点
```

---

### 4. **模糊匹配辅助方法**

实现了三个核心辅助方法：

#### `normalizeWhitespace(text)`
标准化空白字符，用于模糊匹配：
```java
"hello    world\n\n" → "hello world"
```

#### `countOccurrences(text, substring)`
计算子串出现次数，用于唯一性验证：
```java
countOccurrences(content, oldString) == 1  // 必须唯一
```

#### `fuzzyReplace(content, oldString, newString)`
模糊替换，处理空白字符差异：
```java
// 尝试直接替换
if (content.contains(oldString)) return replace...

// 失败则按行匹配（标准化空白字符）
match lines with normalized whitespace...
```

---

## 📊 工具清单

### v4.0 完整工具列表

| 工具名 | 类型 | 说明 | 状态 |
|--------|------|------|------|
| `skills_list` | 查询 | 列出所有技能包（全局+个人） | ✅ 已有 |
| `skill_view` | 查询 | 查看完整 SKILL.md 内容 | ✅ 已有 |
| `skill_tree` | 查询 | 查看技能包文件树结构 | ✅ v3.0 |
| `skill_read_file` | 查询 | 读取技能包内任意文件 | ✅ v3.0 |
| `skill_create` | 管理 | 创建个人技能（遗留接口） | ✅ 已有 |
| `skill_edit` | 管理 | 全量编辑技能（遗留接口） | ✅ 已有 |
| **`skill_manage`** | **统一管理** | **推荐使用的统一接口** | ✅ **v4.0 新增** |

### 推荐工作流

#### 查询流程（渐进式披露）
```
skills_list() 
  → 看到技能包列表
  → skill_tree(skillId=X) 
      → 看到文件树
      → skill_view(skillId=X) 或 skill_read_file(skillId=X, filePath="...")
```

#### 管理流程（统一接口）
```
// 创建
skill_manage(action="create", name="my-skill", content="---\n...")

// 修复（推荐）
skill_manage(action="patch", name="my-skill", old_string="...", new_string="...")

// 大改
skill_manage(action="edit", name="my-skill", content="---\n...")

// 删除
skill_manage(action="delete", name="my-skill")
```

---

## 🎯 设计亮点

### 1. **借鉴 Hermes 的提示词引导**

在 `PromptBuilder.java` 中已更新 `SKILLS_LEARNING_GUIDANCE`：

```java
何时创建技能：
- 复杂任务完成（5+ 工具调用）
- 克服困难或错误
- 用户纠正的方法有效
- 发现非平凡工作流程

何时更新技能：
- 指令过时/错误
- OS 特定失败
- 使用中发现缺少步骤

主动提议保存为技能！
```

### 2. **patch 优于 edit 的理念**

**patch 的优势**：
- 精确定位变更位置
- 保留其他内容不变
- 减少意外破坏风险
- 更符合"外科手术式修改"

**何时用 edit**：
- 全面重构技能结构
- 多处分散的大量修改
- patch 难以表达的复杂变更

### 3. **渐进式披露一致性**

工具链保持一致：
1. **列表层** → `skills_list()` 看概览
2. **结构层** → `skill_tree()` 看文件
3. **内容层** → `skill_view()` 或 `skill_read_file()` 看详情

---

## 🔧 技术实现细节

### Java 实现 vs Python 原版

| 特性 | Hermes (Python) | CORTEX System (Java) |
|------|----------------|------------------|
| 文件系统 | `pathlib.Path` + 文件操作 | 数据库 `SkillNode` 表 |
| 事务安全 | `atomic_replace()` | MyBatis Mapper |
| 模糊匹配 | `fuzzy_find_and_replace()` 库 | 自实现 `fuzzyReplace()` |
| 递归删除 | `shutil.rmtree()` | 自实现 `deleteNodeRecursive()` |
| 安全扫描 | `skills_guard.scan_skill()` | 暂无（可扩展） |
| Profile 隔离 | `~/.hermes/profiles/*/skills` | `business_system` + `owner_user` |

### 关键设计决策

**为什么用数据库而不是文件系统？**
- ✅ 支持多租户隔离（business_system + owner_user）
- ✅ 支持细粒度权限控制
- ✅ 支持版本跟踪和审计
- ✅ 更容易实现搜索和索引

**为什么自实现模糊匹配？**
- Python 的 `fuzzy_match` 库功能强大但复杂
- Java 环境下实现简化版已足够
- 核心需求：处理空白字符差异 + 唯一性检查

---

## 📝 使用示例

### 示例 1: 创建技能

```javascript
skill_manage({
  action: "create",
  name: "fix-maven-encoding",
  content: `---
name: 修复 Maven 编码问题
description: 解决 Maven 项目中文乱码问题
keywords: [maven, encoding, 中文, 乱码]
---

# 修复 Maven 编码问题

## 触发条件
- Maven 构建时出现中文乱码
- 编译警告 "unmappable character for encoding"

## 解决步骤
1. 在 pom.xml 添加编码配置
2. 设置 IDEA 文件编码为 UTF-8
3. 重新编译项目

## 陷阱
- 必须同时设置项目和 IDE 编码
- 已编译的 class 文件需要清理
`
})
```

### 示例 2: 精确修复（patch）

```javascript
skill_manage({
  action: "patch",
  name: "fix-maven-encoding",
  old_string: `## 解决步骤
1. 在 pom.xml 添加编码配置
2. 设置 IDEA 文件编码为 UTF-8`,
  new_string: `## 解决步骤
1. 在 pom.xml 添加编码配置：
   <properties>
     <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
   </properties>
2. 设置 IDEA 文件编码为 UTF-8（Settings > Editor > File Encodings）
3. 清理并重新编译：mvn clean compile`
})
```

### 示例 3: 删除技能

```javascript
skill_manage({
  action: "delete",
  name: "fix-maven-encoding"
})
```

---

## 🔍 测试清单

### 功能测试

- [ ] `skill_manage(action="create")` 创建新技能
- [ ] `skill_manage(action="patch")` 精确更新内容
  - [ ] 唯一匹配成功
  - [ ] 未找到匹配报错
  - [ ] 多个匹配报错
  - [ ] 空白字符差异容错
- [ ] `skill_manage(action="edit")` 全量重写
- [ ] `skill_manage(action="delete")` 删除技能
- [ ] YAML frontmatter 结构验证
- [ ] 权限检查（只能操作自己的技能）

### 集成测试

- [ ] 与提示词 `SKILLS_LEARNING_GUIDANCE` 配合
- [ ] AI Agent 自动创建技能
- [ ] AI Agent 自动 patch 技能
- [ ] 错误提示能帮助 AI 自我纠正

---

## 📚 参考文档

- `hermes-agent-2026.6.19/tools/skill_manager_tool.py` - Hermes 原实现
- `doc/Skill自学习机制启用说明.md` - 自学习需求背景
- `doc/Skill渐进式披露机制详解.md` - 渐进式披露设计
- `cortex-system/src/main/java/com/cortex/agent/runtime/prompt/PromptBuilder.java` - 提示词配置

---

## 🎉 总结

**v4.0 的核心价值**：
1. ✅ **统一接口**: `skill_manage` 替代分散的 create/edit 接口
2. ✅ **精确更新**: `patch` 功能让 AI 能做"外科手术式"修改
3. ✅ **容错设计**: 模糊匹配降低 AI 使用门槛
4. ✅ **完整借鉴**: 吸收 Hermes 的优秀设计理念

**下一步建议**：
- 测试 AI Agent 的实际使用效果
- 根据使用日志优化提示词引导
- 考虑添加技能版本历史功能
- 可选：实现类似 Hermes 的安全扫描机制

---

**更新人**: CORTEX System  
**参考项目**: Hermes-Agent  
**完成状态**: ✅ 实现完成，等待测试验证
