# MCP测试连接修复总结

## 修复内容

修复了MCP插件测试连接时的"Stream Closed"错误。

## 修改的文件

### 1. McpProcessManager.java

**文件路径**: `e:\java\RuoYi-Vue\ruoyi-system\src\main\java\com\ruoyi\plugin\mcp\McpProcessManager.java`

**修改位置**: `startMcpProcess()` 方法（约80-140行）

**主要改动**:
- 将测试模式和长期运行模式的进程启动逻辑完全分离
- 测试模式使用 `ProcessBuilder` 替代 `ProcessExecutor`
- 保持stdin/stdout流完全打开供MCP JSON-RPC通信
- 使用独立的守护线程异步读取stderr，避免缓冲区阻塞

**关键代码**:
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
    // 测试模式：使用ProcessBuilder保持stdin/stdout流打开
    ProcessBuilder pb = new ProcessBuilder(command);
    pb.environment().putAll(env);
    pb.redirectErrorStream(false);
    process = pb.start();
    
    // 异步读取stderr
    Thread stderrReader = new Thread(() -> {
        // 读取stderr避免缓冲区满
    });
    stderrReader.setDaemon(true);
    stderrReader.start();
}
```

## 问题原因

原代码在测试模式下使用 `ProcessExecutor.redirectError()` 会干扰stdin/stdout流：

```java
// ❌ 有问题的代码
executor.redirectErrorStream(false)
    .redirectError(new LogOutputStream() { ... });
StartedProcess startedProcess = executor.start();
Process process = startedProcess.getProcess();
```

虽然意图是只重定向stderr，但ProcessExecutor的内部实现会影响所有流的可用性。

## 解决方案

**测试模式**:
- 使用Java原生 `ProcessBuilder`
- 不重定向stdin/stdout，保持它们用于MCP通信
- 启动守护线程异步读取stderr

**长期运行模式**:
- 继续使用 `ProcessExecutor`
- 重定向所有输出到日志系统
- 不需要stdin/stdout通信

## 测试方法

1. 重启后端服务
2. 打开插件管理页面
3. 选择任意MCP插件（如mcp-echarts）
4. 点击"测试启动"按钮
5. 应该看到成功消息：`✅ 连接成功！发现 X 个工具`

## 相关文档

- [MCP连接测试Stream Closed问题修复说明.md](./MCP连接测试Stream%20Closed问题修复说明.md) - 详细技术分析
- [MCP插件Windows环境依赖问题.md](./MCP插件Windows环境依赖问题.md) - Windows环境配置

## 注意事项

1. 测试连接创建的进程是临时的，测试完成后会自动销毁
2. 长期运行的进程保存在进程池中，可以通过"停止"按钮关闭
3. stderr读取线程是守护线程，会随进程结束自动清理
4. 确保系统已安装相应的运行时环境（npx/uvx/python等）

## 技术要点

### MCP通信原理
- MCP使用stdio模式进行JSON-RPC通信
- **stdin**: 客户端 → 服务器（发送请求）
- **stdout**: 服务器 → 客户端（返回响应）
- **stderr**: 日志输出（不影响通信）

### 为什么需要分离两种模式

| 特性 | 测试模式 | 长期运行模式 |
|------|----------|--------------|
| 进程生命周期 | 短暂（几秒） | 长期（直到手动停止） |
| stdin/stdout | 必须保持打开 | 不需要 |
| 日志输出 | 手动处理stderr | 自动重定向所有输出 |
| 进程管理 | 不保存到进程池 | 保存到进程池 |
| 使用场景 | 验证连接、获取工具列表 | 实际运行MCP服务 |

## 修复时间

2026年6月30日
