package com.cortex.plugin.mcp;

import com.cortex.plugin.domain.AiPlugin;
import com.cortex.plugin.mapper.AiPluginMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP会话管理器
 * 负责管理会话级别的MCP客户端连接
 * 实现会话隔离和并发调用
 * 
 * @author cortex
 */
@Component
public class McpSessionManager {
    
    private static final Logger log = LoggerFactory.getLogger(McpSessionManager.class);
    
    @Autowired
    private McpProcessManager processManager;
    
    @Autowired
    private AiPluginMapper pluginMapper;
    
    /**
     * 会话上下文映射
     * key: sessionId
     * value: SessionContext
     */
    private final Map<String, SessionContext> sessions = new ConcurrentHashMap<>();
    
    /**
     * 会话超时时间（30分钟）
     */
    private static final long SESSION_TIMEOUT = 30 * 60 * 1000;
    
    /**
     * 获取或创建会话的MCP客户端
     * 
     * 重要：MCP使用stdio通信，每个进程只能有一个客户端连接。
     * 因此这里改为插件级别单例，而不是会话级别。
     * 
     * @param sessionId 会话ID
     * @param pluginName 插件名称
     * @return MCP客户端
     */
    public McpClient getClient(String sessionId, String pluginName) {
        // 更新会话最后访问时间（用于清理）
        SessionContext session = sessions.computeIfAbsent(sessionId, k -> {
            log.debug("记录会话 [session={}]", sessionId);
            return new SessionContext(sessionId);
        });
        session.updateLastAccessTime();
        
        // 获取或创建插件级别的MCP客户端（全局单例）
        // 注意：使用"global"作为sessionId，因为客户端是跨会话共享的
        return getOrCreateGlobalClient(pluginName);
    }
    
    /**
     * 获取或创建插件级别的全局客户端（跨会话共享）
     */
    private McpClient getOrCreateGlobalClient(String pluginName) {
        // 使用全局会话上下文存储客户端
        SessionContext globalSession = sessions.computeIfAbsent("__global__", k -> {
            log.info("创建全局客户端上下文");
            return new SessionContext("__global__");
        });
        
        // 先检查是否已存在
        McpClient existing = globalSession.getExistingClient(pluginName);
        if (existing != null) {
            log.debug("复用已有全局客户端 [plugin={}]", pluginName);
            return existing;
        }
        
        log.info("创建新的全局客户端 [plugin={}]", pluginName);
        return globalSession.getOrCreateClient(pluginName, (sid, pname) -> 
            createMcpClient("__global__", pname)
        );
    }
    
    /**
     * 获取已存在的MCP客户端（不创建新的）
     * 用于测试连接等场景
     * 
     * @param sessionId 会话ID（忽略，因为客户端是全局的）
     * @param pluginName 插件名称
     * @return 已存在的客户端，如果不存在返回null
     */
    public McpClient getExistingClient(String sessionId, String pluginName) {
        SessionContext globalSession = sessions.get("__global__");
        if (globalSession == null) {
            return null;
        }
        
        return globalSession.getExistingClient(pluginName);
    }
    
    /**
     * 创建MCP客户端
     */
    private McpClient createMcpClient(String sessionId, String pluginName) {
        try {
            // 查询插件配置
            AiPlugin plugin = pluginMapper.selectAiPluginByPluginName(pluginName);
            if (plugin == null) {
                throw new RuntimeException("插件不存在: " + pluginName);
            }
            
            if (!"0".equals(plugin.getStatus())) {
                throw new RuntimeException("插件未启用: " + pluginName);
            }
            
            if (!"mcp".equals(plugin.getPluginType())) {
                throw new RuntimeException("不是MCP插件: " + pluginName);
            }
            
            // 获取或启动MCP进程（全局共享）
            Process process = getOrStartProcess(plugin);
            
            // 创建客户端
            McpClient client = new McpClient(sessionId, process);
            
            // 初始化连接
            client.initialize();
            
            log.info("创建MCP客户端成功 [session={}, plugin={}]", sessionId, pluginName);
            
            return client;
            
        } catch (Exception e) {
            log.error("创建MCP客户端失败 [session={}, plugin={}]", sessionId, pluginName, e);
            throw new RuntimeException("创建MCP客户端失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取或启动MCP进程（全局单例）
     * MCP使用stdio通信，必须保持stdin/stdout可用于JSON-RPC
     */
    private synchronized Process getOrStartProcess(AiPlugin plugin) throws Exception {
        String pluginName = plugin.getPluginName();
        
        // 检查进程是否已存在且存活
        if (processManager.isProcessAlive(pluginName)) {
            Process existingProcess = processManager.getProcessInfo(pluginName).getProcess();
            
            // 检查进程的启动时间，判断是否是旧进程
            // 如果进程启动时间早于应用启动时间，说明是旧进程，需要重启
            long processUptime = processManager.getProcessInfo(pluginName).getUptime();
            if (processUptime > 600000) { // 超过10分钟的进程可能是旧模式启动的
                log.warn("检测到长时间运行的MCP进程，可能是旧模式启动，将重启 [plugin={}, uptime={}ms]", 
                    pluginName, processUptime);
                processManager.stopMcpProcess(pluginName);
                // 继续下面的启动流程
            } else {
                log.debug("复用现有MCP进程 [plugin={}]", pluginName);
                return existingProcess;
            }
        }
        
        // 启动新进程（不重定向输出，让客户端直接通信）
        log.info("启动新MCP进程 [plugin={}]", pluginName);
        return processManager.startMcpProcess(plugin, false);
    }
    
    /**
     * 移除会话（仅清理会话记录，不关闭全局客户端）
     */
    public void removeSession(String sessionId) {
        // 跳过全局会话
        if ("__global__".equals(sessionId)) {
            return;
        }
        
        SessionContext session = sessions.remove(sessionId);
        if (session != null) {
            // 不关闭客户端，因为它们是全局共享的
            log.info("移除会话记录 [session={}]", sessionId);
        }
    }
    
    /**
     * 移除插件的全局客户端
     * 当插件进程被停止或重启时调用
     * 
     * @param pluginName 插件名称
     */
    public void removeGlobalClient(String pluginName) {
        SessionContext globalSession = sessions.get("__global__");
        if (globalSession == null) {
            return;
        }
        
        McpClient client = globalSession.getExistingClient(pluginName);
        if (client != null) {
            log.info("关闭全局MCP客户端 [plugin={}]", pluginName);
            client.close();
            // 从map中移除
            globalSession.removeClient(pluginName);
        }
    }
    
    /**
     * 清理空闲会话（定时任务：每5分钟执行一次）
     * 注意：只清理普通会话，不清理全局客户端
     */
    @Scheduled(fixedRate = 300000)
    public void cleanupIdleSessions() {
        long now = System.currentTimeMillis();
        
        sessions.entrySet().removeIf(entry -> {
            String sessionId = entry.getKey();
            
            // 不清理全局会话
            if ("__global__".equals(sessionId)) {
                return false;
            }
            
            SessionContext session = entry.getValue();
            long idle = now - session.getLastAccessTime();
            
            if (idle > SESSION_TIMEOUT) {
                log.info("清理空闲会话 [session={}, idle={}ms]", sessionId, idle);
                // 不调用close()，因为没有客户端需要关闭
                return true;
            }
            return false;
        });
    }
    
    /**
     * 关闭所有会话
     */
    @PreDestroy
    public void shutdown() {
        log.info("关闭所有MCP客户端和会话，共{}个", sessions.size());
        
        // 关闭全局客户端
        SessionContext globalSession = sessions.get("__global__");
        if (globalSession != null) {
            log.info("关闭全局MCP客户端");
            globalSession.close();
        }
        
        sessions.clear();
        
        // 停止所有MCP进程
        processManager.stopAllProcesses();
    }
    
    /**
     * 获取会话数量
     */
    public int getSessionCount() {
        return sessions.size();
    }
    
    /**
     * 会话上下文
     */
    private static class SessionContext {
        private final String sessionId;
        private final Map<String, McpClient> clients;
        private long createTime;
        private long lastAccessTime;
        
        public SessionContext(String sessionId) {
            this.sessionId = sessionId;
            this.clients = new ConcurrentHashMap<>();
            this.createTime = System.currentTimeMillis();
            this.lastAccessTime = this.createTime;
        }
        
        public McpClient getOrCreateClient(String pluginName, ClientFactory factory) {
            return clients.computeIfAbsent(pluginName, k -> 
                factory.create(sessionId, pluginName)
            );
        }
        
        public McpClient getExistingClient(String pluginName) {
            return clients.get(pluginName);
        }
        
        public void removeClient(String pluginName) {
            clients.remove(pluginName);
        }
        
        public void updateLastAccessTime() {
            this.lastAccessTime = System.currentTimeMillis();
        }
        
        public long getLastAccessTime() {
            return lastAccessTime;
        }
        
        public void close() {
            clients.values().forEach(McpClient::close);
            clients.clear();
        }
    }
    
    /**
     * 客户端工厂函数式接口
     */
    @FunctionalInterface
    private interface ClientFactory {
        McpClient create(String sessionId, String pluginName);
    }
}
