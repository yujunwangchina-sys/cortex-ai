package com.ruoyi.plugin.mcp;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MCP客户端 - JSON-RPC 2.0实现
 * 使用自己实现的协议，稳定可靠
 * 
 * @author ruoyi
 */
public class McpClient {
    
    private static final Logger log = LoggerFactory.getLogger(McpClient.class);
    
    private final String sessionId;
    private final Process process;
    private final BufferedReader reader;
    private final BufferedWriter writer;
    private final AtomicLong requestIdCounter = new AtomicLong(0);
    private final Map<Long, CompletableFuture<JSONObject>> pendingRequests = new ConcurrentHashMap<>();
    private final ExecutorService listenerExecutor = Executors.newSingleThreadExecutor();
    
    private volatile boolean running = true;
    
    public McpClient(String sessionId, Process process) {
        this.sessionId = sessionId;
        this.process = process;
        this.reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        this.writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
        
        // 启动响应监听线程
        startResponseListener();
        
        log.info("MCP客户端已创建 [session={}]", sessionId);
    }
    
    /**
     * 初始化MCP连接
     */
    public JSONObject initialize() {
        try {
            JSONObject params = new JSONObject();
            params.put("protocolVersion", "2024-11-05");
            params.put("capabilities", new JSONObject());
            params.put("clientInfo", new JSONObject()
                .fluentPut("name", "ruoyi-mcp-client")
                .fluentPut("version", "1.0.0"));
            
            // 增加超时时间到90秒，以应对npx首次下载的情况
            return sendRequest("initialize", params, 90000);
        } catch (Exception e) {
            log.error("MCP初始化失败 [session={}]", sessionId, e);
            throw new RuntimeException("MCP初始化失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取工具列表
     */
    public JSONObject listTools() {
        try {
            return sendRequest("tools/list", new JSONObject(), 10000);
        } catch (Exception e) {
            log.error("获取工具列表失败 [session={}]", sessionId, e);
            throw new RuntimeException("获取工具列表失败: " + e.getMessage());
        }
    }
    
    /**
     * 调用工具
     */
    public JSONObject callTool(String toolName, JSONObject params) {
        try {
            JSONObject callParams = new JSONObject();
            callParams.put("name", toolName);
            callParams.put("arguments", params == null ? new JSONObject() : params);
            
            return sendRequest("tools/call", callParams, 60000);
        } catch (Exception e) {
            log.error("调用工具失败 [session={}, tool={}]", sessionId, toolName, e);
            throw new RuntimeException("调用工具失败: " + e.getMessage());
        }
    }
    
    /**
     * 发送JSON-RPC请求
     */
    private JSONObject sendRequest(String method, JSONObject params, long timeoutMs) throws Exception {
        long requestId = requestIdCounter.incrementAndGet();
        
        // 构建JSON-RPC请求
        JSONObject request = new JSONObject();
        request.put("jsonrpc", "2.0");
        request.put("id", requestId);
        request.put("method", method);
        request.put("params", params);
        
        // 创建Future用于接收响应
        CompletableFuture<JSONObject> future = new CompletableFuture<>();
        pendingRequests.put(requestId, future);
        
        // 发送请求
        synchronized (writer) {
            writer.write(request.toJSONString());
            writer.newLine();
            writer.flush();
        }
        
        log.debug("发送MCP请求 [method={}, id={}]", method, requestId);
        
        // 等待响应（带超时）
        try {
            JSONObject response = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            
            // 检查错误
            if (response.containsKey("error")) {
                JSONObject error = response.getJSONObject("error");
                throw new RuntimeException(
                    String.format("MCP错误 [code=%s, message=%s]", 
                        error.getInteger("code"), 
                        error.getString("message"))
                );
            }
            
            return response.getJSONObject("result");
        } catch (TimeoutException e) {
            pendingRequests.remove(requestId);
            String timeoutMsg = String.format(
                "MCP请求超时 [method=%s, timeout=%dms, session=%s]", 
                method, timeoutMs, sessionId
            );
            
            // 针对initialize方法提供更详细的提示
            if ("initialize".equals(method)) {
                timeoutMsg += "\n提示：如果使用npx启动，首次运行需要下载包可能较慢。" +
                             "建议预先安装：npm install -g 包名，或增加超时时间。" +
                             "详见文档：doc/MCP-ECharts插件安装指南.md";
            }
            
            log.warn(timeoutMsg);
            throw new RuntimeException(timeoutMsg);
        } catch (ExecutionException e) {
            throw new RuntimeException("MCP请求执行异常: " + e.getCause().getMessage());
        }
    }
    
    /**
     * 启动响应监听线程
     */
    private void startResponseListener() {
        listenerExecutor.submit(() -> {
            try {
                String line;
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
                        Long id = response.getLong("id");
                        
                        if (id != null) {
                            CompletableFuture<JSONObject> future = pendingRequests.remove(id);
                            if (future != null) {
                                future.complete(response);
                            }
                        } else {
                            // 可能是通知消息，记录但不处理
                            log.debug("收到无ID的消息 [session={}]: {}", sessionId, line);
                        }
                    } catch (Exception e) {
                        log.warn("解析MCP响应失败 [session={}]: {}", sessionId, line, e);
                    }
                }
            } catch (IOException e) {
                if (running) {
                    log.error("MCP响应监听异常 [session={}]", sessionId, e);
                }
            } finally {
                log.info("MCP响应监听器已停止 [session={}]", sessionId);
            }
        });
    }
    
    /**
     * 关闭客户端
     */
    public void close() {
        running = false;
        
        try {
            // 取消所有待处理的请求
            pendingRequests.values().forEach(future -> 
                future.completeExceptionally(new RuntimeException("MCP客户端已关闭"))
            );
            pendingRequests.clear();
            
            // 关闭线程池（先停止监听器）
            listenerExecutor.shutdown();
            try {
                listenerExecutor.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                listenerExecutor.shutdownNow();
            }
            
            // 关闭IO流（分别捕获异常，避免一个失败影响另一个）
            try {
                if (writer != null) {
                    writer.close();
                }
            } catch (IOException e) {
                log.debug("关闭writer异常 [session={}]: {}", sessionId, e.getMessage());
            }
            
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                log.debug("关闭reader异常 [session={}]: {}", sessionId, e.getMessage());
            }
            
            log.info("MCP客户端已关闭 [session={}]", sessionId);
        } catch (Exception e) {
            log.error("关闭MCP客户端异常 [session={}]", sessionId, e);
        }
    }
    
    /**
     * 检查进程是否存活
     */
    public boolean isAlive() {
        return process != null && process.isAlive();
    }
    
    public String getSessionId() {
        return sessionId;
    }
}
