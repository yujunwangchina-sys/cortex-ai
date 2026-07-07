# MCP请求超时问题修复说明

## 问题描述

使用MCP插件时出现以下错误：

```
java.lang.RuntimeException: MCP请求超时
at com.ruoyi.plugin.mcp.McpClient.sendRequest(McpClient.java:135)
```

## 根本原因

MCP（Model Context Protocol）使用**stdio（标准输入输出）**进行JSON-RPC通信，这意味着：

1. **stdin/stdout是独占的**：只能有一个客户端通过stdin/stdout与进程通信
2. **不能重定向stdout**：如果stdout被日志收集器占用，MCP客户端就无法读取响应
3. **一个进程只能服务一个客户端**：这是stdio模式的限制

### 错误的实现

之前的代码存在以下问题：

```java
// ❌ 错误：以redirectOutput=true模式启动，启动了stdout日志收集器
processManager.startMcpProcess(plugin, true);

// ❌ 错误：尝试为每个会话创建独立的客户端
session.getOrCreateClient(pluginName, this::createMcpClient);

// ❌ 错误：日志收集器占用stdout
Thread stdoutCollector = new Thread(() -> {
    BufferedReader reader = new BufferedReader(
        new InputStreamReader(process.getInputStream()));
    // 读取stdout导致MCP客户端无法读取
});
```

当日志收集器读取stdout时，MCP客户端的`reader.readLine()`会永远等不到响应，导致超时。

## 修复方案

### 1. 客户端改为全局单例

**修改前**：每个会话创建独立的McpClient（错误）
```java
// 会话级别的客户端
session.getOrCreateClient(pluginName, this::createMcpClient);
```

**修改后**：所有会话共享一个全局客户端
```java
// 插件级别的全局单例客户端
private McpClient getOrCreateGlobalClient(String pluginName) {
    SessionContext globalSession = sessions.computeIfAbsent("__global__", ...);
    return globalSession.getOrCreateClient(pluginName, ...);
}
```

### 2. 取消stdout日志收集

**修改前**：启动stdout和stderr日志收集器
```java
public Process startMcpProcess(AiPlugin plugin, boolean redirectOutput) {
    if (redirectOutput) {
        // 启动stdout收集器（错误！）
        startLogCollector(process, processInfo, pluginName);
    }
}
```

**修改后**：只收集stderr，保持stdout可用
```java
public Process startMcpProcess(AiPlugin plugin, boolean logStderr) {
    // stdout必须保持可用用于MCP通信，不能重定向
    // 只收集stderr日志
    if (logStderr) {
        startStderrCollector(process, processInfo, pluginName);
    } else {
        startStderrDrain(process, pluginName); // 至少要消费stderr避免缓冲区满
    }
}
```

### 3. 统一使用非重定向模式

将所有调用改为`startMcpProcess(plugin, false)`：

```java
// AiPluginServiceImpl.java
mcpProcessManager.startMcpProcess(plugin, false);

// PluginConfig.java  
processManager.startMcpProcess(plugin, false);

// McpProcessManager.java
return startMcpProcess(plugin, false);
```

### 4. 增加超时时间和错误提示

```java
// 初始化超时从30秒增加到90秒（应对npx首次下载）
return sendRequest("initialize", params, 90000);

// 超时时提供详细提示
if ("initialize".equals(method)) {
    timeoutMsg += "\n提示：如果使用npx启动，首次运行需要下载包可能较慢。" +
                 "建议预先安装：npm install -g 包名";
}
```

## 架构说明

### MCP通信模型

```
┌─────────────┐
│ MCP Client  │
│  (Java)     │
└──────┬──────┘
       │ JSON-RPC over stdio
       │ (stdin/stdout独占)
┌──────▼──────┐
│ MCP Process │
│  (Node.js)  │
└─────────────┘
```

### 修复后的架构

```
┌──────────┐  ┌──────────┐  ┌──────────┐
│ Session1 │  │ Session2 │  │ Session3 │
└────┬─────┘  └────┬─────┘  └────┬─────┘
     │             │             │
     └─────────────┼─────────────┘
                   │
             ┌─────▼─────┐
             │  Global   │
             │ McpClient │ (单例)
             └─────┬─────┘
                   │ stdio
             ┌─────▼─────┐
             │    MCP    │
             │  Process  │
             └───────────┘
                   │
                   └──> stderr → 日志收集
```

## 为什么要全局单例？

MCP的stdio模式决定了：
- **一个进程只能有一个stdin/stdout**
- **同一时刻只能有一个客户端通信**

因此必须：
1. 每个插件只启动一个进程
2. 每个进程只创建一个客户端
3. 所有会话共享这个客户端

这类似于数据库连接池的概念，但更严格——只能有一个连接。

## 性能影响

**Q: 所有会话共享客户端会有并发问题吗？**

A: 不会。MCP客户端内部使用：
- `ConcurrentHashMap<Long, CompletableFuture>` 存储待处理请求
- 每个请求有唯一ID
- 响应监听器根据ID分发到对应的Future
- 支持并发调用，不会混淆

```java
// 支持并发
long requestId = requestIdCounter.incrementAndGet(); // 原子操作
CompletableFuture<JSONObject> future = new CompletableFuture<>();
pendingRequests.put(requestId, future); // 线程安全
```

## 重启说明

修复后需要**重启应用**，原因：
1. 已启动的进程是以重定向模式运行的
2. stdout已被日志收集器占用
3. 必须停止进程并重新启动

重启步骤：
```bash
# 1. 停止应用
# 2. 应用会自动清理所有MCP进程（@PreDestroy）
# 3. 重启应用
# 4. 新进程以正确模式启动
```

或手动重启插件：
1. 在插件管理页面停用插件
2. 再次启用插件
3. 新进程会以正确模式启动

## 验证方法

启动后观察日志：

```
✅ 正确的日志：
INFO  c.r.p.mcp.McpClient - MCP客户端已创建 [session=__global__]
DEBUG c.r.p.mcp.McpClient - 发送MCP请求 [method=initialize, id=1]
INFO  c.r.p.m.McpSessionManager - 创建MCP客户端成功 [session=__global__, plugin=mcp-echarts]

❌ 错误的日志（如果还看到这些，说明进程是旧模式启动的）：
DEBUG c.r.p.m.McpProcessManager - [MCP-mcp-echarts stdout] {"result":...}
ERROR c.r.p.m.McpSessionManager - 创建MCP客户端失败: MCP请求超时
```

## 后续优化建议

1. **连接池模式**：如果MCP支持TCP模式，可以实现真正的连接池
2. **请求队列**：添加请求队列避免过多并发请求
3. **健康检查**：定期ping客户端确保连接正常
4. **自动重连**：检测到连接断开时自动重启进程

## 相关文件

- `McpClient.java` - MCP客户端实现
- `McpSessionManager.java` - 会话管理器（改为全局单例）
- `McpProcessManager.java` - 进程管理器（取消stdout重定向）
- `AiPluginServiceImpl.java` - 插件服务（启动参数修改）
- `PluginConfig.java` - 应用启动配置（启动参数修改）

## 参考资料

- [Model Context Protocol Specification](https://modelcontextprotocol.io)
- [JSON-RPC 2.0 Specification](https://www.jsonrpc.org/specification)
- [Process I/O in Java](https://docs.oracle.com/javase/tutorial/essential/io/)
