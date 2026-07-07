# Agent 嵌入使用指南

## 📋 概述

本文档说明如何在业务系统中嵌入 AI Agent 聊天功能。

## 🔑 授权配置

### 1. 在 AI 中台创建 Agent

1. 登录 AI 中台系统
2. 进入「Agent 管理」
3. 点击「新增」创建 Agent
4. 填写必要信息：
   - **Agent名称**：如"客服助手"
   - **Agent编码**：如"customer-service"（用于API调用）
   - **所属业务系统**：如"ERP"、"CRM"（业务系统标识）
   - **API密钥**：点击"生成"按钮自动生成，如 `ak_abc123...`
5. 配置 Agent 的 Skill 和 Plugin 权限
6. 保存并启用

### 2. 记录授权信息

创建完成后，记录以下信息用于业务系统调用：

```javascript
const agentConfig = {
  agentCode: 'customer-service',      // Agent编码
  apiKey: 'ak_abc123...',             // API密钥
  businessSystem: 'ERP',              // 业务系统标识
  apiBaseUrl: 'http://localhost:8080' // AI中台地址
}
```

## 🚀 JavaScript SDK 使用

### 方式一：通过 iframe 嵌入

```html
<!DOCTYPE html>
<html>
<head>
  <title>业务系统 - 内嵌 AI 助手</title>
  <style>
    #ai-chat-container {
      position: fixed;
      bottom: 20px;
      right: 20px;
      width: 400px;
      height: 600px;
      border: 1px solid #ddd;
      border-radius: 8px;
      box-shadow: 0 4px 12px rgba(0,0,0,0.15);
      overflow: hidden;
      z-index: 1000;
    }
    
    #ai-chat-frame {
      width: 100%;
      height: 100%;
      border: none;
    }
    
    #ai-chat-toggle {
      position: fixed;
      bottom: 20px;
      right: 20px;
      width: 60px;
      height: 60px;
      border-radius: 50%;
      background: #409eff;
      color: white;
      border: none;
      cursor: pointer;
      font-size: 24px;
      box-shadow: 0 4px 12px rgba(0,0,0,0.15);
      z-index: 999;
    }
  </style>
</head>
<body>
  <!-- 业务系统内容 -->
  <h1>我的业务系统</h1>
  
  <!-- AI 助手按钮 -->
  <button id="ai-chat-toggle" onclick="toggleChat()">💬</button>
  
  <!-- AI 助手容器 -->
  <div id="ai-chat-container" style="display: none;">
    <iframe 
      id="ai-chat-frame"
      src=""
    ></iframe>
  </div>

  <script>
    // 配置信息
    const AI_CONFIG = {
      agentCode: 'customer-service',
      apiKey: 'ak_abc123...',
      businessSystem: 'ERP',
      apiBaseUrl: 'http://localhost:8080'
    }
    
    // 初始化
    const chatFrame = document.getElementById('ai-chat-frame')
    const chatUrl = `${AI_CONFIG.apiBaseUrl}/agent-chat?` + 
      `agentCode=${AI_CONFIG.agentCode}&` +
      `businessSystem=${AI_CONFIG.businessSystem}&` +
      `apiKey=${encodeURIComponent(AI_CONFIG.apiKey)}&` +
      `userLoginName=${getCurrentUser()}`
    
    chatFrame.src = chatUrl
    
    // 切换显示
    function toggleChat() {
      const container = document.getElementById('ai-chat-container')
      const button = document.getElementById('ai-chat-toggle')
      
      if (container.style.display === 'none') {
        container.style.display = 'block'
        button.style.display = 'none'
      } else {
        container.style.display = 'none'
        button.style.display = 'block'
      }
    }
    
    // 获取当前用户（从业务系统会话中获取）
    function getCurrentUser() {
      // TODO: 从业务系统获取当前登录用户
      return 'zhangsan'
    }
  </script>
</body>
</html>
```

### 方式二：通过 API 调用

```javascript
// ai-agent-client.js
class AIAgentClient {
  constructor(config) {
    this.agentCode = config.agentCode
    this.apiKey = config.apiKey
    this.businessSystem = config.businessSystem
    this.apiBaseUrl = config.apiBaseUrl
    this.sessionId = null
  }
  
  /**
   * 发送消息（非流式）
   */
  async chat(message, userLoginName) {
    const response = await fetch(`${this.apiBaseUrl}/agent/api/chat`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Business-System': this.businessSystem,
        'X-API-Key': this.apiKey
      },
      body: JSON.stringify({
        agentCode: this.agentCode,
        message: message,
        userLoginName: userLoginName,
        sessionId: this.sessionId,
        apiKey: this.apiKey,
        businessSystem: this.businessSystem
      })
    })
    
    const result = await response.json()
    
    if (result.code === 200) {
      const data = result.data
      this.sessionId = data.sessionId // 保存会话ID
      return {
        success: true,
        message: data.finalAnswer,
        sessionId: data.sessionId
      }
    } else {
      return {
        success: false,
        error: result.msg
      }
    }
  }
  
  /**
   * 发送消息（流式）
   */
  async chatStream(message, userLoginName, onMessage, onComplete, onError) {
    const response = await fetch(`${this.apiBaseUrl}/agent/api/chat/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Business-System': this.businessSystem,
        'X-API-Key': this.apiKey
      },
      body: JSON.stringify({
        agentCode: this.agentCode,
        message: message,
        userLoginName: userLoginName,
        sessionId: this.sessionId,
        apiKey: this.apiKey,
        businessSystem: this.businessSystem
      })
    })
    
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      
      const text = decoder.decode(value)
      const lines = text.split('\n')
      
      for (const line of lines) {
        if (line.startsWith('data: ')) {
          const data = line.substring(6)
          try {
            const event = JSON.parse(data)
            
            if (event.type === 'session_start') {
              this.sessionId = event.data.sessionId
            } else if (event.type === 'chunk') {
              onMessage(event.data.text)
            } else if (event.type === 'final') {
              onComplete(event.data.finalAnswer)
            } else if (event.type === 'error') {
              onError(event.data.message)
            }
          } catch (e) {
            console.error('解析SSE事件失败:', e)
          }
        }
      }
    }
  }
}

// 使用示例
const aiClient = new AIAgentClient({
  agentCode: 'customer-service',
  apiKey: 'ak_abc123...',
  businessSystem: 'ERP',
  apiBaseUrl: 'http://localhost:8080'
})

// 非流式调用
const result = await aiClient.chat('你好，请帮我查询订单', 'zhangsan')
console.log(result.message)

// 流式调用
await aiClient.chatStream(
  '你好，请帮我查询订单',
  'zhangsan',
  (chunk) => {
    console.log('收到片段:', chunk)
    // 更新UI显示
  },
  (finalAnswer) => {
    console.log('完成:', finalAnswer)
  },
  (error) => {
    console.error('错误:', error)
  }
)
```

## 🔐 安全说明

### API Key 保护

1. **不要在前端代码中硬编码 API Key**
2. **推荐做法**：
   ```javascript
   // 在业务系统后端中转
   // 前端调用业务系统API
   const response = await fetch('/api/ai-chat', {
     method: 'POST',
     body: JSON.stringify({ message: '...' })
   })
   
   // 业务系统后端调用AI中台
   app.post('/api/ai-chat', async (req, res) => {
     const result = await fetch('http://ai-platform/agent/api/chat', {
       headers: {
         'X-API-Key': process.env.AI_AGENT_API_KEY  // 从环境变量读取
       },
       body: JSON.stringify({
         agentCode: 'customer-service',
         message: req.body.message,
         userLoginName: req.session.user.username,
         businessSystem: 'ERP',
         apiKey: process.env.AI_AGENT_API_KEY
       })
     })
     res.json(result)
   })
   ```

### 权限验证流程

```
业务系统请求
   ↓
传入: agentCode + apiKey + businessSystem + userLoginName
   ↓
AI中台验证:
   1. Agent是否存在？
   2. API Key是否匹配？
   3. Agent的businessSystem是否匹配？
   4. Agent是否启用？
   ↓
验证通过 → 执行对话
验证失败 → 返回401错误
```

## 📊 三层 Skill 权限

Agent 执行时自动根据业务系统和用户加载可用的 Skill：

| Skill 类型 | 可见范围 |
|-----------|---------|
| 🌐 全局 Skill (system + 无businessSystem) | 所有 Agent 可用 |
| 🏢 业务系统 Skill (system + businessSystem=ERP) | ERP 系统的 Agent 可用 |
| 👤 个人 Skill (user + ownerUser=张三) | 张三的会话可用 |

## 🎯 完整示例

详见项目中的示例文件：
- `examples/agent-embed-simple.html` - iframe 嵌入示例
- `examples/agent-embed-api.html` - API 调用示例
- `examples/ai-agent-client.js` - JavaScript SDK

## ❓ 常见问题

### Q: API Key 验证失败？
A: 检查：
1. API Key 是否正确
2. Agent 的 businessSystem 是否匹配请求头中的 X-Business-System
3. Agent 是否启用

### Q: 如何实现多租户隔离？
A: 通过 `businessSystem` + `userLoginName` 组合实现：
- 不同业务系统的会话互相隔离
- 同一业务系统内，不同用户的会话隔离

### Q: 如何自定义 UI？
A: 可以通过 API 调用方式完全自定义聊天界面，参考 `agent-embed-api.html` 示例。
