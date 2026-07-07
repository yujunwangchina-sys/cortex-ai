# MCP连接测试修复说明

## 问题描述
在测试MCP插件连接时，出现JSON解析错误：
```
❌ 连接失败：syntax error, offset 1, character u, line 1, column 1, fastjson-version 2.0.61 uvx mcp-alchemy (耗时: 1ms)
```

错误信息显示，系统尝试将"uvx"这样的文本作为JSON解析，导致失败。

## 根本原因

### 原因1: 输出重定向问题
`McpProcessManager.startMcpProcess()`方法使用了`redirectOutput()`和`redirectError()`，导致：
- 进程的stdout被重定向到日志输出器
- `McpClient`无法从`process.getInputStream()`读取任何数据
- 或者读取到的是stderr的内容（uvx的安装/下载信息）

### 原因2: 非JSON输出未过滤
MCP进程启动时，可能输出：
- uvx的包下载/安装信息："Resolved 1 package...", "Downloaded 1 package...", "Installed..."
- 其他诊断信息
- 这些输出出现在实际的JSON-RPC响应之前

`McpClient`的响应监听器没有跳过这些非JSON行，直接尝试解析，导致错误。

## 修复方案

### 修复1: McpClient.java - 过滤非JSON输出

修改`startResponseListener()`方法：
```java
// 修改前：直接解析所有行
while (running && (line = reader.readLine()) != null) {
    try {
        JSONObject response = JSON.parseObject(line);
        // ...
    } catch (Exception e) {
        log.error("解析MCP响应失败: {}", line, e);
    }
}

// 修改后：跳过非JSON行
while (running && (line = reader.readLine()) != null) {
    // 跳过空行
    if (line.trim().isEmpty()) {
        continue;
    }
    
    // 只处理JSON行（以{开头）
    if (!line.trim().startsWith("{")) {
        log.debug("跳过非JSON输出 [session={}]: {}", sessionId, line);
        continue;
    }
    
    try {
        JSONObject response = JSON.parseObject(line);
        // ...
    } catch (Exception e) {
        log.warn("解析MCP响应失败 [session={}]: {}", sessionId, line, e);
    }
}
```

**改进点**：
- 跳过空行
- 只解析以`{`开头的JSON行
- 将uvx等工具的输出信息记录到debug日志
- 降低解析失败的日志级别为warn

### 修复2: McpProcessManager.java - 区分测试和长期运行

新增重载方法，支持控制输出重定向：
```java
// 默认方法（向后兼容，用于测试）
public Process startMcpProcess(AiPlugin plugin) throws Exception {
    return startMcpProcess(plugin, false);
}

// 新方法，支持控制输出重定向
public Process startMcpProcess(AiPlugin plugin, boolean redirectOutput) throws Exception {
    // ...
    
    if (redirectOutput) {
        // 长期运行的进程：重定向stdout和stderr到日志
        executor.redirectOutput(...).redirectError(...);
    } else {
        // 测试连接：只重定向stderr，保留stdout给McpClient
        executor.redirectErrorStream(false)
            .redirectError(new LogOutputStream() {
                @Override
                protected void processLine(String line) {
                    // 过滤uvx的常见信息到debug级别
                    if (line.contains("uv") || line.contains("Resolved") || 
                        line.contains("Downloaded") || line.contains("Installed")) {
                        log.debug("[MCP-{} stderr] {}", pluginName, line);
                    } else if (!line.trim().isEmpty()) {
                        log.warn("[MCP-{} stderr] {}", pluginName, line);
                    }
                }
            });
    }
    // ...
}
```

**改进点**：
- 测试连接时不重定向stdout，让`McpClient`可以读取JSON-RPC响应
- 仍然重定向stderr，将uvx等工具的信息输出到日志
- 对常见的无害信息（uvx下载/安装）使用debug级别
- 长期运行的插件可以使用`redirectOutput=true`保持原有行为

## MCP通信流程

```
┌─────────────┐                  ┌──────────────┐
│  测试请求   │                  │   MCP进程    │
└──────┬──────┘                  └───────┬──────┘
       │                                 │
       │ 1. startMcpProcess(false)       │
       ├────────────────────────────────>│
       │                                 │
       │                          2. stderr (uvx info)
       │                                 │ "Resolved 1 package..."
       │                                 │ "Downloaded..."
       │                                 │ "Installed..."
       │                                 │
       │                          3. stdout (JSON-RPC)
       │ <────────────────────────────── │ {"jsonrpc":"2.0",...}
       │                                 │
       │ 4. McpClient读取stdout          │
       │    - 跳过非JSON行               │
       │    - 解析JSON响应               │
       │                                 │
       │ 5. initialize请求               │
       ├────────────────────────────────>│
       │ <────────────────────────────── │ 初始化响应
       │                                 │
       │ 6. listTools请求                │
       ├────────────────────────────────>│
       │ <────────────────────────────── │ 工具列表
       │                                 │
       │ 7. 关闭连接                     │
       ├────────────────────────────────>│
       │                                 │
```

## 测试步骤

1. 重新编译项目
2. 重启后端服务
3. 在插件管理页面，点击某个MCP插件的"测试"按钮
4. 预期结果：
   ```
   ✅ 连接成功！发现 X 个工具 (耗时: XXXms)
   ```

## 相关文件

- `ruoyi-system\src\main\java\com\ruoyi\plugin\mcp\McpClient.java`
- `ruoyi-system\src\main\java\com\ruoyi\plugin\mcp\McpProcessManager.java`
- `ruoyi-system\src\main\java\com\ruoyi\plugin\service\impl\AiPluginServiceImpl.java`

## 注意事项

1. **长期运行的插件**: 未来如果需要启动长期运行的MCP插件服务，应该调用 `startMcpProcess(plugin, true)` 来重定向输出
2. **stderr处理**: uvx和npm等包管理工具会在stderr输出安装信息，这是正常的，不应该作为错误处理
3. **JSON-RPC规范**: MCP使用JSON-RPC 2.0协议，每行一个完整的JSON对象，以换行符分隔
4. **超时时间**: 初始化超时30秒，工具列表超时10秒，如果网络慢或包较大可能需要调整

## 已知限制

1. 首次运行某个MCP包时，uvx需要下载和安装，可能需要较长时间
2. 如果网络不通或pypi/npm仓库访问失败，测试会超时
3. 某些MCP包可能有特殊的环境要求（如API密钥），需要配置环境变量后才能测试成功
