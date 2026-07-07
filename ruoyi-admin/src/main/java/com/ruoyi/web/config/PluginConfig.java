package com.ruoyi.web.config;

import com.ruoyi.plugin.domain.AiPlugin;
import com.ruoyi.plugin.mcp.McpProcessManager;
import com.ruoyi.plugin.service.IAiPluginService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 插件系统配置类
 * 
 * @author ruoyi
 */
@Configuration
@EnableScheduling
public class PluginConfig implements ApplicationRunner {
    
    private static final Logger log = LoggerFactory.getLogger(PluginConfig.class);
    
    @Autowired
    private IAiPluginService pluginService;
    
    @Autowired
    private McpProcessManager processManager;
    
    /**
     * 应用启动后执行 - 并行预加载所有启用的MCP插件
     */
    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("========== 开始预加载MCP插件 ==========");
        
        try {
            // 查询所有启用的MCP插件
            List<AiPlugin> plugins = pluginService.selectEnabledPluginList();
            
            // 筛选MCP插件
            List<AiPlugin> mcpPlugins = plugins.stream()
                .filter(plugin -> "mcp".equals(plugin.getPluginType()))
                .toList();
            
            if (mcpPlugins.isEmpty()) {
                log.info("没有启用的MCP插件需要加载");
                return;
            }
            
            int mcpCount = mcpPlugins.size();
            log.info("发现 {} 个启用的MCP插件，开始并行加载...", mcpCount);
            
            // 创建线程池用于并行加载（最多同时启动5个，避免资源争抢）
            ExecutorService executor = Executors.newFixedThreadPool(
                Math.min(5, mcpCount),
                new ThreadFactory() {
                    private final AtomicInteger threadNumber = new AtomicInteger(1);
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "MCP-Loader-" + threadNumber.getAndIncrement());
                        t.setDaemon(false); // 非守护线程，确保完成加载
                        return t;
                    }
                }
            );
            
            // 统计计数器
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);
            
            // 提交所有插件启动任务
            List<CompletableFuture<Void>> futures = mcpPlugins.stream()
                .map(plugin -> CompletableFuture.runAsync(() -> {
                    String pluginName = plugin.getPluginName();
                    try {
                        log.info("预加载MCP插件: {} [{}]", plugin.getPluginName(), pluginName);
                        
                        // 启动MCP进程（不重定向stdout，保持可用于MCP通信）
                        processManager.startMcpProcess(plugin, false);
                        
                        successCount.incrementAndGet();
                        log.info("✅ MCP插件启动成功: {}", pluginName);
                        
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                        log.error("❌ MCP插件启动失败: {} - {}", pluginName, e.getMessage());
                        // 继续加载其他插件，不中断
                    }
                }, executor))
                .toList();
            
            // 等待所有任务完成（最多等待60秒）
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(60, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                log.warn("部分插件加载超时，继续启动应用");
            } catch (Exception e) {
                log.error("等待插件加载完成时出错", e);
            }
            
            // 关闭线程池
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            
            log.info("========== MCP插件预加载完成 ==========");
            log.info("总计: {} 个MCP插件, 成功: {}, 失败: {}", 
                mcpCount, successCount.get(), failCount.get());
            
        } catch (Exception e) {
            log.error("预加载MCP插件失败", e);
        }
    }
}
