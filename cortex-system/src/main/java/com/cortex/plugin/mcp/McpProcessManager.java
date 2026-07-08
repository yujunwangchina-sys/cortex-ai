package com.cortex.plugin.mcp;

import com.alibaba.fastjson2.JSON;
import com.cortex.common.utils.platform.PlatformUtils;
import com.cortex.common.utils.platform.CommandLocator;
import com.cortex.plugin.domain.AiPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.StartedProcess;
import org.zeroturnaround.exec.stream.LogOutputStream;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * MCP进程管理器
 * 负责启动、停止和监控MCP进程
 *
 * @author cortex
 */
@Component
public class McpProcessManager {

    private static final Logger log = LoggerFactory.getLogger(McpProcessManager.class);

    @org.springframework.beans.factory.annotation.Value("${env.path}")
    private String envBasePath;

    /**
     * 全局MCP进程池（插件级别单例）
     * key: pluginName
     * value: MCP进程信息
     */
    private final Map<String, McpProcessInfo> processes = new ConcurrentHashMap<>();

    /**
     * Scheduled heartbeat: ping all MCP processes every 60s, restart dead ones
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 60000)
    public void heartbeat() {
        for (Map.Entry<String, McpProcessInfo> entry : processes.entrySet()) {
            String pluginName = entry.getKey();
            McpProcessInfo info = entry.getValue();
            Process process = info.getProcess();
            if (process == null || !process.isAlive()) {
                log.warn("MCP process dead [plugin={}], removing from registry", pluginName);
                processes.remove(pluginName);
                continue;
            }
            // Check if process is responsive via stdin/stdout
            // If the process has been alive but not producing output for too long, consider it stale
            try {
                // Lightweight liveness check: is the process alive and can we write to stdin?
                if (!process.getInputStream().getClass().getMethod("available").invoke(process.getInputStream()).equals(0)) {
                    // Data available - process is active
                }
            } catch (Exception ignored) {
                // Process stream closed - mark as dead
                log.warn("MCP process stream closed [plugin={}], removing", pluginName);
                processes.remove(pluginName);
            }
        }
    }

    /**
     * Get or restart a process if it has died
     */
    public Process ensureProcess(AiPlugin plugin) throws Exception {
        String pluginName = plugin.getPluginName();
        McpProcessInfo info = processes.get(pluginName);
        if (info != null && info.getProcess() != null && info.getProcess().isAlive()) {
            return info.getProcess();
        }
        // Process died - remove and restart
        if (info != null) {
            log.warn("MCP process restart [plugin={}]", pluginName);
            processes.remove(pluginName);
        }
        return startMcpProcess(plugin, false); // MCP必须保持stdout可用于通信
    }

    /**
     * 启动MCP进程（用于测试连接，不重定向输出）
     */
    public Process startMcpProcess(AiPlugin plugin) throws Exception {
        return startMcpProcess(plugin, false);
    }

    /**
     * 启动MCP进程
     * <p>
     * 重要说明：
     * MCP协议使用stdio（标准输入输出）进行JSON-RPC通信，因此：
     * 1. stdin/stdout必须保持可用，不能关闭或重定向
     * 2. 只有一个客户端可以连接（stdin/stdout是独占的）
     * 3. stderr可以重定向用于日志收集
     *
     * @param plugin    插件信息
     * @param logStderr 是否收集stderr日志（stdout始终不能重定向）
     */
    public Process startMcpProcess(AiPlugin plugin, boolean logStderr) throws Exception {
        String pluginName = plugin.getPluginName();

        // 检查是否已经启动
        McpProcessInfo existing = processes.get(pluginName);
        if (existing != null && existing.getProcess().isAlive()) {
            log.info("MCP进程已存在 [plugin={}]", pluginName);
            return existing.getProcess();
        }

        log.info("启动MCP进程 [plugin={}, type={}, runtime={}, logStderr={}]",
                pluginName, plugin.getPluginType(), plugin.getRuntimeType(), logStderr);

        // 构建启动命令
        List<String> command = buildStartCommand(plugin);

        // 构建环境变量
        Map<String, String> env = buildEnvironment(plugin);

        // 启动进程（stdout/stdin必须保持可用用于MCP通信）
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.environment().putAll(env);
        pb.redirectErrorStream(false); // stderr独立处理

        Process process = pb.start();

        // 保存进程信息
        McpProcessInfo processInfo = new McpProcessInfo(pluginName, process);
        processes.put(pluginName, processInfo);

        // 启动stderr日志收集（stdout不能收集，它用于MCP通信）
        if (logStderr) {
            startStderrCollector(process, processInfo, pluginName);
        } else {
            // 即使不收集，也要异步消费stderr避免缓冲区满
            startStderrDrain(process, pluginName);
        }

        // 监控进程退出
        startExitMonitor(process, processInfo, pluginName);

        // 给进程时间初始化
        TimeUnit.MILLISECONDS.sleep(1000);

        // 检查进程是否还活着
        if (!process.isAlive()) {
            try {
                int exitCode = process.exitValue();
                throw new RuntimeException(
                        String.format("MCP进程启动后立即退出 [exitCode=%d]。" +
                                        "可能是命令错误或依赖缺失。命令: %s",
                                exitCode, String.join(" ", command))
                );
            } catch (IllegalThreadStateException e) {
                // 进程还在运行，继续
            }
        }

        log.info("MCP进程启动成功 [plugin={}, pid={}, command={}]", pluginName, process.pid(), command);

        return process;
    }

    /**
     * 启动stderr日志收集器（详细模式）
     */
    private void startStderrCollector(Process process, McpProcessInfo processInfo, String pluginName) {
        Thread stderrCollector = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    processInfo.addLog("[ERR] " + line);
                    if (!line.trim().startsWith("{") && !line.contains("uv") &&
                            !line.contains("Resolved") && !line.contains("Downloaded")) {
                        log.warn("[MCP-{} stderr] {}", pluginName, line);
                    } else {
                        log.debug("[MCP-{} stderr] {}", pluginName, line);
                    }
                }
                log.info("[MCP-{}] stderr流已关闭", pluginName);
            } catch (IOException e) {
                log.debug("[MCP-{}] stderr收集结束: {}", pluginName, e.getMessage());
            }
        }, "MCP-stderr-" + pluginName);
        stderrCollector.setDaemon(true);
        stderrCollector.start();
    }

    /**
     * 启动stderr消费器（简单模式，避免缓冲区满）
     */
    private void startStderrDrain(Process process, String pluginName) {
        Thread stderrDrain = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // 只记录警告和错误
                    if (line.contains("error") || line.contains("Error") ||
                            line.contains("warn") || line.contains("Warning")) {
                        log.warn("[MCP-{} stderr] {}", pluginName, line);
                    } else {
                        log.debug("[MCP-{} stderr] {}", pluginName, line);
                    }
                }
            } catch (IOException e) {
                log.debug("[MCP-{}] stderr消费结束: {}", pluginName, e.getMessage());
            }
        }, "MCP-stderr-drain-" + pluginName);
        stderrDrain.setDaemon(true);
        stderrDrain.start();
    }

    /**
     * 启动进程退出监控
     */
    private void startExitMonitor(Process process, McpProcessInfo processInfo, String pluginName) {
        Thread exitMonitor = new Thread(() -> {
            try {
                int exitCode = process.waitFor();
                log.error("[MCP-{}] 进程异常退出 [pid={}, exitCode={}, uptime={}ms]",
                        pluginName,
                        process.pid(),
                        exitCode,
                        processInfo.getUptime());

                // 记录退出原因到日志
                String exitReason = switch (exitCode) {
                    case 0 -> "正常退出";
                    case 1 -> "一般错误";
                    case 2 -> "误用Shell命令";
                    case 126 -> "命令无法执行";
                    case 127 -> "命令未找到";
                    case 130 -> "被Ctrl+C中断";
                    case 137 -> "被SIGKILL杀死";
                    case 143 -> "被SIGTERM终止";
                    default -> "未知原因";
                };

                processInfo.addLog(String.format("[EXIT] 进程退出 exitCode=%d (%s)", exitCode, exitReason));
                log.warn("[MCP-{}] 退出原因: {}", pluginName, exitReason);

            } catch (InterruptedException e) {
                log.debug("[MCP-{}] 进程监控被中断", pluginName);
                Thread.currentThread().interrupt();
            }
        }, "MCP-exit-" + pluginName);
        exitMonitor.setDaemon(true);
        exitMonitor.start();
    }

    /**
     * 启动日志收集器（用于长期运行的进程）
     *
     * @deprecated 不应使用，因为会占用stdout导致MCP通信失败
     */
    @Deprecated
    private void startLogCollector(Process process, McpProcessInfo processInfo, String pluginName) {
        // stdout收集器 - 这会导致MCP客户端无法读取响应！
        Thread stdoutCollector = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    processInfo.addLog("[OUT] " + line);
                    log.debug("[MCP-{} stdout] {}", pluginName, line);
                }
                log.info("[MCP-{}] stdout流已关闭", pluginName);
            } catch (IOException e) {
                log.debug("[MCP-{}] stdout收集结束: {}", pluginName, e.getMessage());
            }
        }, "MCP-stdout-" + pluginName);
        stdoutCollector.setDaemon(true);
        stdoutCollector.start();

        // stderr收集器
        Thread stderrCollector = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    processInfo.addLog("[ERR] " + line);
                    if (!line.trim().startsWith("{") && !line.contains("uv") &&
                            !line.contains("Resolved") && !line.contains("Downloaded")) {
                        log.warn("[MCP-{} stderr] {}", pluginName, line);
                    } else {
                        log.debug("[MCP-{} stderr] {}", pluginName, line);
                    }
                }
                log.info("[MCP-{}] stderr流已关闭", pluginName);
            } catch (IOException e) {
                log.debug("[MCP-{}] stderr收集结束: {}", pluginName, e.getMessage());
            }
        }, "MCP-stderr-" + pluginName);
        stderrCollector.setDaemon(true);
        stderrCollector.start();

        // 监控进程退出
        Thread exitMonitor = new Thread(() -> {
            try {
                int exitCode = process.waitFor();
                log.error("[MCP-{}] 进程异常退出 [pid={}, exitCode={}, uptime={}ms]",
                        pluginName,
                        process.pid(),
                        exitCode,
                        processInfo.getUptime());

                // 记录退出原因到日志
                String exitReason = switch (exitCode) {
                    case 0 -> "正常退出";
                    case 1 -> "一般错误";
                    case 2 -> "误用Shell命令";
                    case 126 -> "命令无法执行";
                    case 127 -> "命令未找到";
                    case 130 -> "被Ctrl+C中断";
                    case 137 -> "被SIGKILL杀死";
                    case 143 -> "被SIGTERM终止";
                    default -> "未知原因";
                };

                processInfo.addLog(String.format("[EXIT] 进程退出 exitCode=%d (%s)", exitCode, exitReason));
                log.warn("[MCP-{}] 退出原因: {}", pluginName, exitReason);

            } catch (InterruptedException e) {
                log.debug("[MCP-{}] 进程监控被中断", pluginName);
                Thread.currentThread().interrupt();
            }
        }, "MCP-exit-" + pluginName);
        exitMonitor.setDaemon(true);
        exitMonitor.start();
    }

    /**
     * 停止MCP进程
     */
    public void stopMcpProcess(String pluginName) {
        McpProcessInfo processInfo = processes.remove(pluginName);

        if (processInfo == null) {
            log.warn("MCP进程不存在 [plugin={}]", pluginName);
            return;
        }

        Process process = processInfo.getProcess();

        if (process.isAlive()) {
            log.info("停止MCP进程 [plugin={}, pid={}]", pluginName, process.pid());

            // 优雅关闭
            process.destroy();

            try {
                boolean exited = process.waitFor(5, TimeUnit.SECONDS);
                if (!exited) {
                    // 强制杀死
                    process.destroyForcibly();
                    process.waitFor(2, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        log.info("MCP进程已停止 [plugin={}]", pluginName);
    }

    /**
     * 获取进程信息
     */
    public McpProcessInfo getProcessInfo(String pluginName) {
        return processes.get(pluginName);
    }

    /**
     * 检查进程是否存活
     */
    public boolean isProcessAlive(String pluginName) {
        McpProcessInfo info = processes.get(pluginName);

        if (info == null) {
            log.debug("检查进程状态 [plugin={}, hasInfo=false]", pluginName);
            return false;
        }

        Process process = info.getProcess();
        boolean alive = process.isAlive();

        log.debug("检查进程状态 [plugin={}, hasInfo=true, isAlive={}]",
                pluginName, alive);

        if (!alive) {
            // 进程已退出，记录退出码
            try {
                int exitCode = process.exitValue();
                log.warn("进程已退出 [plugin={}, pid={}, uptime={}ms, exitCode={}]",
                        pluginName,
                        process.pid(),
                        info.getUptime(),
                        exitCode);
            } catch (IllegalThreadStateException e) {
                // 进程还在运行（理论上不会到这里）
                log.debug("进程详情 [plugin={}, pid={}, uptime={}ms]",
                        pluginName,
                        process.pid(),
                        info.getUptime());
            }
        } else {
            log.debug("进程运行中 [plugin={}, pid={}, uptime={}ms]",
                    pluginName,
                    process.pid(),
                    info.getUptime());
        }

        return alive;
    }

    /**
     * 构建启动命令
     */
    private List<String> buildStartCommand(AiPlugin plugin) {
        List<String> command = new ArrayList<>();

        // 从startCommand解析
        if (plugin.getStartCommand() != null && !plugin.getStartCommand().isEmpty()) {
            String startCommand = plugin.getStartCommand().trim();

            // 尝试作为JSON数组解析
            if (startCommand.startsWith("[")) {
                try {
                    List<String> cmdList = JSON.parseArray(startCommand, String.class);
                    // 定位第一个命令的完整路径
                    if (!cmdList.isEmpty()) {
                        cmdList.set(0, CommandLocator.locateCommand(cmdList.get(0)));
                    }
                    command.addAll(cmdList);
                    return command;
                } catch (Exception e) {
                    log.warn("解析JSON格式命令失败，尝试空格分割: {}", startCommand, e);
                }
            }

            // 作为空格分隔的字符串处理
            String[] parts = startCommand.split("\\s+");
            for (int i = 0; i < parts.length; i++) {
                if (!parts[i].isEmpty()) {
                    // 第一个命令需要定位完整路径
                    if (i == 0) {
                        command.add(CommandLocator.locateCommand(parts[i]));
                    } else {
                        command.add(parts[i]);
                    }
                }
            }
        } else {
            // 根据运行时类型构建默认命令
            String runtimeType = plugin.getRuntimeType();
            String packageName = plugin.getPackageName();

            if (packageName == null || packageName.isEmpty()) {
                throw new IllegalArgumentException("包名不能为空");
            }

            switch (runtimeType) {
                case "pip":
                    // 使用MCP Python虚拟环境
                    String pythonExe = getMcpPythonExecutable();
                    if (pythonExe == null) {
                        throw new IllegalStateException("MCP Python虚拟环境不存在，请先创建");
                    }
                    command.add(pythonExe);
                    command.add("-m");
                    command.add(packageName.replace("-", "_"));
                    break;
                case "npm":
                    // 使用MCP Node环境
                    String nodeExe = CommandLocator.locateCommand("node");
                    command.add(nodeExe);

                    String nodeEnvPath = getMcpNodeEnvPath();
                    // MCP包通常在dist/index.js或根目录有入口文件
                    // 先尝试标准的MCP服务器入口点
                    String[] possibleEntries = {
                            "dist" + File.separator + "index.js",
                            "build" + File.separator + "index.js",
                            "index.js",
                            "lib" + File.separator + "index.js"
                    };

                    String entryPath = null;
                    String packageDir = nodeEnvPath + File.separator + "node_modules" + File.separator + packageName;

                    for (String entry : possibleEntries) {
                        File entryFile = new File(packageDir, entry);
                        if (entryFile.exists()) {
                            entryPath = entryFile.getAbsolutePath();
                            log.debug("找到npm包入口文件: {}", entryPath);
                            break;
                        }
                    }

                    if (entryPath == null) {
                        // 如果都找不到，使用包目录让Node.js自己解析
                        entryPath = packageDir;
                        log.warn("未找到标准入口文件，使用包目录: {}", entryPath);
                    }

                    command.add(entryPath);
                    break;
                case "venv":
                    // 兼容旧版venv（逐步淘汰）
                    command.add(CommandLocator.locateCommand("python"));
                    command.add("-m");
                    command.add(packageName.replace("-", "_"));
                    break;
                default:
                    throw new IllegalArgumentException("不支持的运行时类型: " + runtimeType + "，请使用 pip 或 npm");
            }
        }

        log.debug("MCP启动命令: {}", command);
        return command;
    }

    /**
     * 获取MCP Python虚拟环境的python可执行文件路径
     */
    private String getMcpPythonExecutable() {
        String envPath = envBasePath + File.separator + "python-env";
        File venvDir = new File(envPath);

        // Windows
        File windowsPython = new File(venvDir, "Scripts/python.exe");
        if (windowsPython.exists()) {
            return windowsPython.getAbsolutePath();
        }
        // Linux/Mac
        File unixPython = new File(venvDir, "bin/python");
        if (unixPython.exists()) {
            return unixPython.getAbsolutePath();
        }
        return null;
    }

    /**
     * 获取MCP Node环境路径
     */
    private String getMcpNodeEnvPath() {
        return envBasePath + File.separator + "node-env";
    }

    /**
     * 构建环境变量
     */
    private Map<String, String> buildEnvironment(AiPlugin plugin) {
        Map<String, String> env = new ConcurrentHashMap<>(System.getenv());

        // 添加插件自定义环境变量
        if (plugin.getEnvVars() != null && !plugin.getEnvVars().isEmpty()) {
            try {
                Map<String, String> customEnv = JSON.parseObject(
                        plugin.getEnvVars(),
                        Map.class
                );
                env.putAll(customEnv);
            } catch (Exception e) {
                log.error("解析环境变量失败", e);
            }
        }

        return env;
    }

    /**
     * 停止所有进程（应用关闭时调用）
     */
    public void stopAllProcesses() {
        log.info("停止所有MCP进程，共{}个", processes.size());

        processes.keySet().forEach(this::stopMcpProcess);
        processes.clear();
    }

    /**
     * 提取关键错误信息
     */
    private String extractKeyError(String errorOutput) {
        // 限制长度
        if (errorOutput.length() > 300) {
            // 尝试找到最后一个错误行
            String[] lines = errorOutput.split("\n");
            for (int i = lines.length - 1; i >= 0; i--) {
                String line = lines[i].trim();
                if (line.contains("error") || line.contains("Error") || line.contains("failed") || line.contains("Failed")) {
                    return line.length() > 200 ? line.substring(0, 200) + "..." : line;
                }
            }
            // 如果没找到错误行，返回最后几行
            return errorOutput.substring(Math.max(0, errorOutput.length() - 200));
        }
        return errorOutput;
    }

    /**
     * 获取进程日志
     */
    public List<String> getProcessLogs(String pluginName, int maxLines) {
        McpProcessInfo info = processes.get(pluginName);
        if (info == null) {
            return new ArrayList<>();
        }
        return info.getRecentLogs(maxLines);
    }

    /**
     * 获取所有进程状态（用于调试）
     */
    public Map<String, String> getAllProcessStatus() {
        Map<String, String> status = new ConcurrentHashMap<>();

        for (Map.Entry<String, McpProcessInfo> entry : processes.entrySet()) {
            String pluginName = entry.getKey();
            McpProcessInfo info = entry.getValue();
            Process process = info.getProcess();

            String statusStr = String.format(
                    "PID=%d, Alive=%s, Uptime=%dms",
                    process.pid(),
                    process.isAlive(),
                    info.getUptime()
            );

            status.put(pluginName, statusStr);
        }

        log.info("当前进程池状态: {}", status);
        return status;
    }

    /**
     * MCP进程信息
     */
    public static class McpProcessInfo {
        private final String pluginName;
        private final Process process;
        private final long startTime;
        // 循环缓冲区，保存最近的日志
        private final List<String> logs = new java.util.concurrent.CopyOnWriteArrayList<>();
        private static final int MAX_LOG_SIZE = 1000; // 最多保存1000行日志

        public McpProcessInfo(String pluginName, Process process) {
            this.pluginName = pluginName;
            this.process = process;
            this.startTime = System.currentTimeMillis();
        }

        public String getPluginName() {
            return pluginName;
        }

        public Process getProcess() {
            return process;
        }

        public long getStartTime() {
            return startTime;
        }

        public long getUptime() {
            return System.currentTimeMillis() - startTime;
        }

        /**
         * 添加日志行
         */
        public void addLog(String line) {
            if (logs.size() >= MAX_LOG_SIZE) {
                // 删除最旧的日志
                logs.remove(0);
            }
            String timestamp = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            logs.add(String.format("[%s] %s", timestamp, line));
        }

        /**
         * 获取最近的日志
         */
        public List<String> getRecentLogs(int maxLines) {
            int size = logs.size();
            if (size <= maxLines) {
                return new ArrayList<>(logs);
            }
            return new ArrayList<>(logs.subList(size - maxLines, size));
        }

        /**
         * 获取所有日志
         */
        public List<String> getAllLogs() {
            return new ArrayList<>(logs);
        }
    }
}
