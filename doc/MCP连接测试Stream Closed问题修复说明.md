# MCP连接测试Stream Closed问题修复说明

## 问题描述

在测试MCP连接时，出现"Stream Closed"错误：

```
连接失败: Stream Closed
```

进程能够成功启动（已确认PID），但在McpClient尝试写入stdin时报错流已关闭。

## 根本原因

`McpProcessManager.startMcpProcess()` 在测试模式下使用了 `ProcessExecutor.redirectError()`，这会干扰stdin/stdout流的正常使用。

MCP服务器使用stdio模式进行JSON-RPC通信：
- **stdin**: 接收客户端请求
- **stdout**: 返回服务器响应  
- **stderr**: 输出日志和错误信息

当使用 `ProcessExecutor.redirectError()` 时，即使没有重定向stdout，内部的流处理也可能导致stdin/stdout被关闭或不可访问。

## 修复方案

### 修改前

```java
// 测试连接时，只重定向stderr，保留stdout给客户端
executor.redirectErrorStream(false)
    .redirectError(new LogOutputStream() {
        // ...
    });

StartedProcess startedProcess = executor.start();
Process process = startedProcess.getProcess();
```

问题：虽然意图是保留stdout，但ProcessExecutor对流的处理仍会影响stdin/stdout的可用性。

### 修改后

```java
if (redirectOutput) {
    // 长期运行模式：使用ProcessExecutor重定向日志
    ProcessExecutor executor = new ProcessExecutor()
        .command(command)
        .environment(env)
        .redirectOutput(...)
        .redirectError(...);
    process = executor.start().getProcess();
} else {
    // 测试模式：使用ProcessBuilder保持stdin/stdout流打开供MCP通信
    ProcessBuilder pb = new ProcessBuilder(command);
    pb.environment().putAll(env);
    pb.redirectErrorStream(false);
    process = pb.start();
    
    // 异步读取stderr避免缓冲区满
    Thread stderrReader = new Thread(() -> {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.warn("[MCP-{} stderr] {}", pluginName, line);
            }
        } catch (IOException e) {
            log.debug("[MCP-{}] stderr读取结束: {}", pluginName, e.getMessage());
        }
    }, "MCP-stderr-" + pluginName);
    stderrReader.setDaemon(true);
    stderrReader.start();
}
```

## 关键改进

1. **测试模式使用ProcessBuilder**：直接使用Java原生ProcessBuilder，完全控制流的行为
2. **保持stdin/stdout打开**：不对stdin/stdout做任何重定向，完全用于MCP通信
3. **异步读取stderr**：启动独立的守护线程读取stderr，防止缓冲区满阻塞进程
4. **分离两种模式**：
   - 测试模式：ProcessBuilder + 手动stderr处理
   - 长期运行模式：ProcessExecutor + 自动日志重定向

## 验证测试

使用mcp-echarts进行测试（已确认该包在命令行下工作正常）：

```bash
npx -y mcp-echarts --help  # ✅ 命令行正常
```

修复后应该能够：
1. 进程成功启动
2. stdin/stdout流保持打开
3. McpClient能够通过stdin发送JSON-RPC请求
4. 从stdout读取JSON-RPC响应
5. stderr输出被异步捕获到日志

## 相关文件

- `e:\java\Cortex-Vue\cortex-system\src\main\java\com\cortex\plugin\mcp\McpProcessManager.java`
- `e:\java\Cortex-Vue\cortex-system\src\main\java\com\cortex\plugin\mcp\McpClient.java`
- `e:\java\Cortex-Vue\cortex-system\src\main\java\com\cortex\plugin\service\impl\AiPluginServiceImpl.java`

## 测试步骤

1. 重新编译项目
2. 启动后端服务
3. 在插件管理页面，选择mcp-echarts
4. 点击"测试启动"按钮
5. 应该看到：`✅ 连接成功！发现 X 个工具 (耗时: XXXms)`

## 注意事项

- 测试模式的进程会在测试完成后自动销毁，不会保留在进程池中
- 长期运行模式继续使用ProcessExecutor，保持日志输出的便利性
- stderr读取线程是守护线程，会随进程结束自动退出
