# MCP集成重构方案

## 🎯 重构目标

使用**LangChain4j官方MCP包** (`langchain4j-mcp`) 替代自己实现的MCP客户端，提升稳定性和可维护性。

## 📦 官方包说明

### 1. **LangChain4j MCP**
- **Maven坐标**: `dev.langchain4j:langchain4j-mcp:0.36.2`
- **功能**: 完整的MCP客户端实现
- **支持**: STDIO、SSE、WebSocket传输
- **优势**: 
  - 官方维护，持续更新
  - 完整的协议实现
  - 成熟的错误处理
  - 社区支持

### 2. **Spring AI MCP**
- **版本**: Spring AI 1.1.0-M2+
- **功能**: Spring Boot集成
- **优势**: 
  - 自动配置
  - Spring生态集成
  - Boot Starter支持

## 🔄 重构对比

### **当前实现（手写）**
```java
// 自己实现的McpClient
public class McpClient {
    private final Process process;
    private final BufferedReader reader;
    private final BufferedWriter writer;
    
    public void initialize() {
        // 发送initialize请求
        // 手动处理JSON-RPC协议
    }
    
    public String callTool(String toolName, Map<String, Object> arguments) {
        // 构造JSON-RPC请求
        // 发送请求
        // 解析响应
    }
}
```

**问题**：
- ❌ 需要手动处理JSON-RPC协议
- ❌ 错误处理不完善
- ❌ 需要自己维护协议版本
- ❌ 缺少超时、重试等机制

### **官方实现（LangChain4j）**
```java
// 使用LangChain4j官方客户端
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;

// 创建客户端
McpClient client = McpClient.builder()
    .transport(StdioMcpTransport.builder()
        .command("uvx", "mcp-server-sqlite")
        .build())
    .build();

// 初始化
client.initialize();

// 列出工具
List<Tool> tools = client.listTools();

// 调用工具
ToolExecutionResult result = client.executeTool(
    toolName, 
    arguments
);
```

**优势**：
- ✅ 完整的JSON-RPC实现
- ✅ 完善的错误处理
- ✅ 自动协议协商
- ✅ 内置超时、重试机制
- ✅ 支持多种传输方式（STDIO、SSE、WebSocket）

## 📋 重构步骤

### **第1步：更新依赖** ✅
已完成：在`cortex-system/pom.xml`中添加：
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-mcp</artifactId>
</dependency>
```

### **第2步：重构McpClient**
保留现有的`McpClient`类名，但内部使用LangChain4j的实现：

```java
@Component
public class McpClient {
    private final dev.langchain4j.mcp.client.McpClient delegate;
    
    public McpClient(Process process) {
        // 使用官方客户端，传入已启动的进程
        this.delegate = dev.langchain4j.mcp.client.McpClient.builder()
            .transport(StdioMcpTransport.builder()
                .process(process)  // 复用已启动的进程
                .build())
            .build();
    }
    
    public void initialize() {
        delegate.initialize();
    }
    
    public List<ToolInfo> listTools() {
        return delegate.listTools().stream()
            .map(this::convertToToolInfo)
            .collect(Collectors.toList());
    }
    
    public String executeTool(String toolName, Map<String, Object> arguments) {
        ToolExecutionResult result = delegate.executeTool(toolName, arguments);
        return result.getContent();
    }
}
```

### **第3步：保持接口不变**
对外接口保持不变，确保Service层和Controller层无需修改：
- `McpSessionManager` - 不变
- `McpProcessManager` - 不变
- `IAiPluginService` - 不变
- Controller层 - 不变

### **第4步：测试验证**
1. 测试插件启动
2. 测试工具调用
3. 测试会话隔离
4. 测试并发调用

## 🎯 架构优势

### **保留的优势**
1. ✅ 系统启动预加载（PluginConfig）
2. ✅ 全局进程池（McpProcessManager）
3. ✅ 会话隔离（McpSessionManager）
4. ✅ 自动管理（定时清理、优雅关闭）

### **新增的优势**
1. ✅ 使用官方协议实现（更稳定）
2. ✅ 完善的错误处理
3. ✅ 自动版本协商
4. ✅ 社区持续维护

## 📊 最终架构

```
系统启动
  ↓
PluginConfig (预加载)
  ↓
McpProcessManager (进程池 - 全局单例)
  ├─ mcp-sqlite  → Process
  ├─ mcp-filesystem → Process
  └─ mcp-git → Process
  
Agent调用
  ↓
McpSessionManager (会话管理)
  ↓
McpClient (封装LangChain4j官方实现)
  ↓
LangChain4j MCP Client
  ↓
STDIO Transport
  ↓
MCP Server Process (复用进程池)
```

## 🔧 配置说明

### **application.yml**
无需额外配置，继续使用现有的数据库配置。

### **数据库表**
无需修改，继续使用`ai_plugin`、`ai_plugin_tool`等表。

## ✅ 验证清单

- [x] 依赖添加完成
- [ ] McpClient重构
- [ ] 单元测试
- [ ] 集成测试
- [ ] 性能测试
- [ ] 文档更新

## 📚 参考资料

1. **LangChain4j MCP文档**: https://docs.langchain4j.dev/tutorials/mcp
2. **MCP协议规范**: https://modelcontextprotocol.io
3. **LangChain4j GitHub**: https://github.com/langchain4j/langchain4j
4. **Spring AI MCP**: https://docs.spring.io/spring-ai/reference/api/mcp/

## 💡 总结

使用官方的`langchain4j-mcp`包是最佳实践：
- **不需要重新发明轮子**
- **获得社区支持和持续更新**
- **减少维护成本**
- **提升系统稳定性**

同时保留我们自己设计的：
- **预加载机制**（系统启动时自动加载）
- **进程池复用**（节省资源）
- **会话隔离**（并发安全）

这是**最优雅的解决方案**！🎉
