# 会话标题AI生成功能说明

## 功能概述

在新建会话并发送第一条消息后，系统会自动调用AI分析用户意图，生成一个不超过20字的简洁标题，并显示在左侧会话列表中。

## 实现方案

### 1. 数据库变更

新增 `title` 字段到 `ai_agent_session` 表：

```sql
ALTER TABLE ai_agent_session 
ADD COLUMN title VARCHAR(100) COMMENT '会话标题(AI意图分析,不超过20字)' AFTER last_message_time;
```

**执行SQL脚本：** `add-session-title-field.sql`

### 2. 后端实现

#### 2.1 实体类更新

在 `AiAgentSession.java` 中添加：
- 新增 `title` 字段及其 getter/setter 方法

#### 2.2 Mapper XML 更新

在 `AiAgentSessionMapper.xml` 中：
- ResultMap 中添加 `title` 字段映射
- selectAiAgentSessionVo 查询中包含 `s.title`
- updateAiAgentSession 方法中支持更新 `title`

#### 2.3 会话管理器增强

在 `SessionManager.java` 中实现标题生成逻辑：

**关键方法：**

1. **`persistSession(AgentSessionContext ctx)`** - 增强
   - 检测第一条用户消息（消息数=2：1条user + 1条assistant）
   - 触发异步标题生成

2. **`generateAndUpdateTitle(AgentSessionContext ctx)`** - 新增
   - 异步执行，不阻塞主流程
   - 调用 LLM 生成标题
   - 更新数据库

3. **`generateTitle(String userMessage)`** - 新增
   - 从数据库获取可用的 chat 模型配置
   - 构建标题生成提示词
   - 调用 OpenAI 兼容 API
   - 清理标题格式（移除引号、标点，限制长度）

**标题生成提示词：**
```
System: 你是一个会话标题生成助手。根据用户的第一条消息，生成一个简洁的标题，不超过20个字，直接输出标题文本，不要有任何解释或标点符号。

User: 用户消息：{实际用户消息}
```

**标题清理规则：**
- 移除首尾引号和括号：`"'《「【` 和 `"'》」】`
- 移除尾部标点：`。！？.,!?`
- 限制长度：最多 20 个字符

### 3. 前端展示

会话列表组件 `ChatSidebar.vue` 已支持显示 `title` 字段：

```vue
<span class="session-title">{{ session.title || '新会话' }}</span>
```

- 如果 `title` 为空，显示默认值"新会话"
- 标题过长时自动截断（CSS：`text-overflow: ellipsis`）

## 执行流程

```
用户发送第一条消息
    ↓
AgentRuntime 执行对话
    ↓
SessionManager.persistSession()
    ↓
检测：messages.size() == 2 && messages[0].role == 'user'
    ↓
异步执行 generateAndUpdateTitle()
    ↓
获取默认 chat 模型配置
    ↓
调用 LLM API 生成标题
    ↓
清理和格式化标题
    ↓
更新数据库 ai_agent_session.title
    ↓
前端刷新会话列表时显示新标题
```

## 特性

### 异步执行
- 标题生成不阻塞用户对话流程
- 即使生成失败也不影响主功能

### 智能触发
- 仅在第一条用户消息后触发
- 避免重复生成

### 配置灵活
- 自动从数据库获取可用的 chat 模型
- 支持多种 OpenAI 兼容的 LLM

### 格式规范
- 严格限制 20 字以内
- 自动清理多余符号
- 确保显示整洁

## 依赖模块

- **LLM Client**: `OpenAiCompatibleClient.java`
- **模型配置**: `AiModelMapper` / `AiSupplierMapper`
- **会话存储**: `AiAgentSessionMapper`

## 配置要求

系统中需要至少配置一个：
- **模型类型**: `chat`
- **状态**: 启用（status = '0'）
- **供应商**: 包含有效的 `baseUrl` 和 `apiKey`

## 错误处理

标题生成失败时：
- 记录警告日志
- 不影响会话功能
- 会话列表显示默认值"新会话"

常见失败原因：
- 未配置可用的 chat 模型
- LLM API 调用超时或失败
- 网络连接问题

## 后续优化建议

1. **可配置化**
   - 标题长度限制可配置
   - 提示词模板可自定义

2. **重试机制**
   - LLM 调用失败后自动重试
   - 指数退避策略

3. **缓存优化**
   - 缓存模型配置，减少数据库查询

4. **用户自定义**
   - 允许用户手动修改标题
   - 提供标题重新生成按钮

## 测试要点

1. **数据库迁移**: 执行 SQL 脚本，验证字段添加成功
2. **首次消息**: 发送第一条消息，等待几秒，刷新会话列表
3. **标题显示**: 验证标题是否正确显示且不超过 20 字
4. **异常处理**: 断开网络，验证生成失败不影响对话
5. **后续消息**: 发送第二条消息，验证不会重复生成标题

## 更新日期

2026-07-03
