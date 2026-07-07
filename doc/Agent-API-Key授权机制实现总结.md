# Agent API Key 授权机制实现总结

**实现日期**: 2026-07-01  
**功能描述**: 实现业务系统通过 API Key 安全调用 Agent 的授权机制

---

## 一、功能概述

### 1.1 业务场景
AI中台作为中心平台，Agent 可授权给不同业务系统使用。业务系统通过以下方式嵌入 AI 能力：
- **前端嵌入**: 使用 JS SDK 嵌入聊天界面到业务系统
- **后端调用**: 通过 REST API 直接调用 Agent 进行对话

### 1.2 核心特性
- ✅ **业务系统隔离**: Agent 可指定授权给特定业务系统
- ✅ **API Key 验证**: 32位随机密钥，确保调用安全性
- ✅ **双重验证模式**: 
  - 内部调用：使用登录用户身份
  - 外部调用：使用 API Key + businessSystem 验证
- ✅ **状态检查**: 自动验证 Agent 是否启用

---

## 二、数据库变更

### 2.1 ai_agent 表新增字段

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `business_system` | VARCHAR(50) | 所属业务系统标识（如：ERP、CRM、OA） |
| `api_key` | VARCHAR(100) | API授权密钥（32位随机字符串） |

### 2.2 执行迁移脚本

```bash
# 执行 Agent API Key 迁移
mysql -u root -p your_database < sql/agent_api_key_migration.sql
```

**脚本内容**：
- 添加 `business_system` 字段
- 添加 `api_key` 字段
- 创建索引（`idx_agent_code`, `idx_business_system`）

---

## 三、后端实现

### 3.1 Domain 层 (AiAgent.java)

新增字段：
```java
/** 所属业务系统 */
private String businessSystem;

/** API授权密钥 */
private String apiKey;
```

### 3.2 Service 层 (IAiAgentService.java)

新增方法：
```java
/**
 * 验证 Agent API Key
 */
boolean validateApiKey(String agentCode, String apiKey, String businessSystem);

/**
 * 根据编码查询 Agent
 */
AiAgent selectAiAgentByCode(String agentCode);
```

**实现逻辑** (AiAgentServiceImpl.java):
1. 验证参数非空
2. 根据 `agentCode` 查询 Agent
3. 验证 API Key 匹配
4. 验证业务系统匹配（如果 Agent 指定了 businessSystem）
5. 验证 Agent 状态为启用

### 3.3 Mapper 层 (AiAgentMapper.xml)

新增查询：
```xml
<select id="selectAiAgentByCode" parameterType="String" resultMap="AiAgentResult">
    <include refid="selectAiAgentVo"/>
    where agent_code = #{agentCode}
</select>
```

更新 `selectAiAgentVo`:
- 包含 `business_system`, `api_key` 字段

更新 `insert`/`update` 语句:
- 支持 `businessSystem`, `apiKey` 字段的保存和更新

### 3.4 Controller 层 (AgentRuntimeController.java)

**API 接口**: `/agent/api/chat` 和 `/agent/api/chat/stream`

**验证流程**：
```java
// 1. 从 Header 或 Body 获取 API Key
String apiKey = request.getApiKey() != null ? request.getApiKey() : headerApiKey;

// 2. 外部调用验证
if (apiKey != null && !apiKey.isEmpty()) {
    // 验证 API Key
    if (!aiAgentService.validateApiKey(request.getAgentCode(), apiKey, businessSystem)) {
        return AjaxResult.error("API Key 验证失败或 Agent 未授权给该业务系统");
    }
    // 必须提供 userLoginName
    if (request.getUserLoginName() == null || request.getUserLoginName().isEmpty()) {
        return AjaxResult.error("外部调用必须提供 userLoginName 参数");
    }
}
// 3. 内部调用使用当前登录用户
else {
    if (request.getUserLoginName() == null || request.getUserLoginName().isEmpty()) {
        request.setUserLoginName(getUsername());
    }
}
```

**支持参数传递方式**：
- **Header**: `X-Business-System`, `X-API-Key`
- **Body**: `apiKey`, `businessSystem`, `userLoginName`

---

## 四、前端实现

### 4.1 Agent 表单 (AgentForm.vue)

新增表单字段：
```vue
<!-- 业务系统 -->
<el-form-item label="业务系统" prop="businessSystem">
  <el-input v-model="form.businessSystem" placeholder="留空表示全局Agent" />
</el-form-item>

<!-- API密钥 -->
<el-form-item label="API密钥" prop="apiKey">
  <el-input v-model="form.apiKey" type="password" show-password>
    <template #append>
      <el-button @click="generateApiKey" icon="Refresh">生成</el-button>
    </template>
  </el-input>
</el-form-item>
```

**API Key 生成逻辑**：
```javascript
const generateApiKey = () => {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'
  let key = ''
  for (let i = 0; i < 32; i++) {
    key += chars.charAt(Math.floor(Math.random() * chars.length))
  }
  form.value.apiKey = key
}
```

### 4.2 Agent 列表 (index.vue)

新增列显示：
```vue
<!-- 业务系统 -->
<el-table-column label="业务系统" prop="businessSystem" width="120">
  <template #default="{ row }">
    <span>{{ row.businessSystem || '全局' }}</span>
  </template>
</el-table-column>

<!-- API密钥（脱敏显示） -->
<el-table-column label="API密钥" prop="apiKey" width="150">
  <template #default="{ row }">
    <span v-if="row.apiKey">{{ row.apiKey.substring(0, 8) }}****</span>
    <span v-else style="color: #909399">未设置</span>
  </template>
</el-table-column>
```

---

## 五、API 调用示例

### 5.1 非流式对话

**请求示例**：
```javascript
fetch('http://your-domain/agent/api/chat', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'X-Business-System': 'ERP',
    'X-API-Key': 'your-32-char-api-key-here'
  },
  body: JSON.stringify({
    agentCode: 'customer-service',
    userMessage: '你好，我想查询订单状态',
    userLoginName: 'zhangsan',
    sessionId: 'optional-session-id'
  })
})
```

**响应示例**：
```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "sessionId": "sess-20260701-001",
    "assistantMessage": "您好！请提供订单号，我帮您查询订单状态。",
    "status": "completed"
  }
}
```

### 5.2 SSE 流式对话

**请求示例**：
```javascript
const eventSource = new EventSource(
  'http://your-domain/agent/api/chat/stream?' + 
  new URLSearchParams({
    agentCode: 'customer-service',
    userMessage: '你好，我想查询订单状态',
    userLoginName: 'zhangsan',
    businessSystem: 'ERP',
    apiKey: 'your-32-char-api-key-here'
  })
)

eventSource.onmessage = (event) => {
  const data = JSON.parse(event.data)
  console.log('收到消息:', data)
}

eventSource.addEventListener('error', (error) => {
  console.error('连接错误:', error)
  eventSource.close()
})
```

### 5.3 前端 iframe 嵌入

参考文档：`doc/Agent嵌入使用指南.md`

**嵌入示例**：
```html
<iframe 
  src="http://your-domain/chat?agentCode=customer-service&businessSystem=ERP&apiKey=your-api-key" 
  width="100%" 
  height="600px"
  frameborder="0">
</iframe>
```

---

## 六、安全机制

### 6.1 验证层级
1. **API Key 验证**: 32位随机密钥，防止未授权访问
2. **业务系统验证**: Agent 绑定特定业务系统，防止跨系统调用
3. **状态验证**: 只允许调用已启用的 Agent
4. **用户标识**: 外部调用必须提供 `userLoginName`，确保可追溯

### 6.2 最佳实践
- ✅ API Key 应妥善保管，不要在前端代码中硬编码
- ✅ 定期轮换 API Key
- ✅ 为不同业务系统分配独立的 Agent
- ✅ 监控 API 调用日志，及时发现异常

### 6.3 错误处理

| 错误码 | 错误信息 | 原因 |
|--------|----------|------|
| 403 | API Key 验证失败或 Agent 未授权给该业务系统 | API Key 错误或 businessSystem 不匹配 |
| 400 | 外部调用必须提供 userLoginName 参数 | 缺少用户标识 |
| 500 | 对话失败: ... | Agent 执行异常 |

---

## 七、测试清单

### 7.1 数据库测试
- [ ] 执行迁移脚本成功
- [ ] 新字段可正常读写
- [ ] 索引创建成功

### 7.2 后端测试
- [ ] API Key 验证通过（正确的 Key）
- [ ] API Key 验证失败（错误的 Key）
- [ ] 业务系统验证通过（匹配）
- [ ] 业务系统验证失败（不匹配）
- [ ] Agent 状态验证（启用/禁用）
- [ ] 内部调用使用登录用户

### 7.3 前端测试
- [ ] Agent 表单可添加/编辑业务系统和 API Key
- [ ] API Key 生成功能正常
- [ ] Agent 列表显示业务系统和脱敏后的 API Key
- [ ] 保存后字段持久化成功

### 7.4 集成测试
- [ ] 通过 Postman/curl 测试非流式对话
- [ ] 通过 EventSource 测试流式对话
- [ ] 业务系统嵌入 iframe 测试
- [ ] 错误场景测试（无效 Key、缺少参数等）

---

## 八、相关文档

- **使用指南**: `doc/Agent嵌入使用指南.md`
- **三层权限体系**: `doc/Skill三层权限体系实现说明.md`（待创建）
- **数据库迁移**: 
  - `sql/agent_api_key_migration.sql`
  - `sql/skill_permission_migration.sql`

---

## 九、后续优化

### 9.1 短期优化
- [ ] 添加 API Key 过期时间机制
- [ ] 实现 API 调用频率限制
- [ ] 增加审计日志（记录所有 API Key 验证尝试）

### 9.2 长期规划
- [ ] 支持多 API Key（主备密钥）
- [ ] 实现 IP 白名单机制
- [ ] 添加 Agent 调用监控大屏
- [ ] 支持 OAuth2 / JWT 授权方式

---

**实现状态**: ✅ 完成  
**测试状态**: ⏳ 待测试  
**文档状态**: ✅ 已完成
