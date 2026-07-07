package com.ruoyi.plugin.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.plugin.builtin.IBuiltinPlugin;
import com.ruoyi.plugin.builtin.ToolDefinition;
import com.ruoyi.plugin.domain.AiPlugin;
import com.ruoyi.plugin.domain.AiPluginTool;
import com.ruoyi.plugin.mapper.AiPluginMapper;
import com.ruoyi.plugin.mapper.AiPluginToolMapper;
import com.ruoyi.plugin.mcp.McpClient;
import com.ruoyi.plugin.mcp.McpProcessManager;
import com.ruoyi.plugin.mcp.McpSessionManager;
import com.ruoyi.plugin.service.IAiPluginService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI插件Service业务层处理
 * 
 * @author ruoyi
 */
@Service
public class AiPluginServiceImpl implements IAiPluginService 
{
    private static final Logger log = LoggerFactory.getLogger(AiPluginServiceImpl.class);
    
    @Autowired
    private AiPluginMapper aiPluginMapper;

    @Autowired
    private AiPluginToolMapper aiPluginToolMapper;

    @Autowired
    private McpProcessManager mcpProcessManager;

    @Autowired
    private McpSessionManager mcpSessionManager;

    @Autowired
    private ApplicationContext applicationContext;

    @Override
    public AiPlugin selectAiPluginByPluginId(Long pluginId)
    {
        return aiPluginMapper.selectAiPluginByPluginId(pluginId);
    }

    @Override
    public AiPlugin selectAiPluginByPluginName(String pluginName)
    {
        return aiPluginMapper.selectAiPluginByPluginName(pluginName);
    }

    @Override
    public List<AiPlugin> selectAiPluginList(AiPlugin aiPlugin)
    {
        List<AiPlugin> list = aiPluginMapper.selectAiPluginList(aiPlugin);
        
        log.debug("查询到 {} 个插件", list.size());
        
        // 填充运行状态
        int mcpCount = 0;
        int runningCount = 0;
        
        for (AiPlugin plugin : list) {
            if ("mcp".equals(plugin.getPluginType())) {
                mcpCount++;
                boolean isRunning = mcpProcessManager.isProcessAlive(plugin.getPluginName());
                plugin.setIsRunning(isRunning);
                if (isRunning) {
                    runningCount++;
                }
                log.debug("插件状态 [code={}, name={}, isRunning={}]", 
                    plugin.getPluginName(), plugin.getPluginName(), isRunning);
            } else {
                // 非MCP插件不显示运行状态
                plugin.setIsRunning(null);
            }
        }
        
        log.debug("MCP插件统计: 总数={}, 运行中={}", mcpCount, runningCount);
        
        return list;
    }

    @Override
    public List<AiPlugin> selectEnabledPluginList()
    {
        return aiPluginMapper.selectEnabledPluginList();
    }

    @Override
    public int insertAiPlugin(AiPlugin aiPlugin)
    {
        return aiPluginMapper.insertAiPlugin(aiPlugin);
    }

    @Override
    public int updateAiPlugin(AiPlugin aiPlugin)
    {
        // 如果修改了插件，需要重启进程
        if ("mcp".equals(aiPlugin.getPluginType())) {
            String pluginName = aiPlugin.getPluginName();
            if (mcpProcessManager.isProcessAlive(pluginName)){
                log.info("插件配置已修改，重启MCP进程 [plugin={}]", pluginName);
                mcpProcessManager.stopMcpProcess(pluginName);
            }
        }
        
        return aiPluginMapper.updateAiPlugin(aiPlugin);
    }

    @Override
    @Transactional
    public int deleteAiPluginByPluginIds(Long[] pluginIds)
    {
        // 停止进程并删除工具
        for (Long pluginId : pluginIds)
        {
            AiPlugin plugin = aiPluginMapper.selectAiPluginByPluginId(pluginId);
            if (plugin != null && "mcp".equals(plugin.getPluginType())) {
                mcpProcessManager.stopMcpProcess(plugin.getPluginName());
            }
            aiPluginToolMapper.deleteAiPluginToolByPluginId(pluginId);
        }
        
        return aiPluginMapper.deleteAiPluginByPluginIds(pluginIds);
    }

    @Override
    @Transactional
    public int deleteAiPluginByPluginId(Long pluginId)
    {
        AiPlugin plugin = aiPluginMapper.selectAiPluginByPluginId(pluginId);
        if (plugin != null && "mcp".equals(plugin.getPluginType())) {
            mcpProcessManager.stopMcpProcess(plugin.getPluginName());
        }
        
        aiPluginToolMapper.deleteAiPluginToolByPluginId(pluginId);
        return aiPluginMapper.deleteAiPluginByPluginId(pluginId);
    }

    @Override
    public String testConnection(AiPlugin aiPlugin)
    {
        if (!"mcp".equals(aiPlugin.getPluginType())) {
            return "只支持MCP插件的连接测试";
        }
        
        long startTime = System.currentTimeMillis();
        String pluginName = aiPlugin.getPluginName();
        boolean isAlreadyRunning = mcpProcessManager.isProcessAlive(pluginName);
        Process process = null;
        McpClient testClient = null;
        
        try {
            log.info("开始测试连接: {} [pluginName={}, alreadyRunning={}]", 
                aiPlugin.getPluginName(), pluginName, isAlreadyRunning);
            
            if (isAlreadyRunning) {
                // 插件已经在运行，尝试获取现有客户端或创建测试客户端
                log.info("插件已运行，尝试连接测试");
                
                // 方案1：尝试复用现有客户端（如果有的话）
                String runningSessionId = "plugin-" + pluginName;
                McpClient existingClient = mcpSessionManager.getExistingClient(runningSessionId, pluginName);
                
                if (existingClient != null) {
                    // 有现有客户端，直接使用
                    try {
                        JSONObject result = existingClient.listTools();
                        int toolCount = 0;
                        if (result.containsKey("tools")) {
                            toolCount = result.getJSONArray("tools").size();
                        }
                        long duration = System.currentTimeMillis() - startTime;
                        return String.format("✅ 连接成功！插件运行中，发现 %d 个工具 (耗时: %dms)", toolCount, duration);
                    } catch (Exception e) {
                        long duration = System.currentTimeMillis() - startTime;
                        return String.format("❌ 连接失败：%s (耗时: %dms)", e.getMessage(), duration);
                    }
                } else {
                    // 没有现有客户端，说明进程刚启动还没被使用过
                    // 这种情况下，只要进程存活就认为连接正常
                    long duration = System.currentTimeMillis() - startTime;
                    return String.format("✅ 连接正常！插件进程运行中（PID: %d）(耗时: %dms)", 
                        mcpProcessManager.getProcessInfo(pluginName).getProcess().pid(), 
                        duration);
                }
                
            } else {
                // 插件未运行，启动临时进程测试
                log.info("插件未运行，启动临时进程测试");
                
                process = mcpProcessManager.startMcpProcess(aiPlugin, false);
                
                // 检查进程是否存活
                if (!process.isAlive()) {
                    return "❌ 连接失败：进程启动后立即退出，请检查命令是否正确";
                }
                
                log.info("临时进程已启动，PID: {}", process.pid());
                
                // 创建测试客户端
                testClient = new McpClient("test-" + System.currentTimeMillis(), process);
                
                // 初始化
                testClient.initialize();
                
                // 获取工具列表
                JSONObject result = testClient.listTools();
                
                int toolCount = 0;
                if (result.containsKey("tools")) {
                    toolCount = result.getJSONArray("tools").size();
                }
                
                long duration = System.currentTimeMillis() - startTime;
                
                return String.format("✅ 连接成功！发现 %d 个工具 (耗时: %dms)", toolCount, duration);
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            long duration = System.currentTimeMillis() - startTime;
            return String.format("❌ 连接失败：测试被中断 (耗时: %dms)", duration);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("连接测试失败 [pluginName={}, alreadyRunning={}]", pluginName, isAlreadyRunning, e);
            
            // 检查进程状态提供更好的错误信息
            String errorMsg = e.getMessage();
            if (process != null && !process.isAlive()) {
                errorMsg = "进程已退出。" + errorMsg;
                // 尝试读取错误输出
                try {
                    byte[] errorBytes = process.getErrorStream().readAllBytes();
                    if (errorBytes.length > 0) {
                        String errorOutput = new String(errorBytes).trim();
                        if (!errorOutput.isEmpty()) {
                            errorMsg += " 错误输出: " + errorOutput.substring(0, Math.min(200, errorOutput.length()));
                        }
                    }
                } catch (Exception readError) {
                    log.debug("无法读取错误输出: {}", readError.getMessage());
                }
            }
            
            return String.format("❌ 连接失败：%s (耗时: %dms)", errorMsg, duration);
        } finally {
            // 清理资源
            if (testClient != null && !isAlreadyRunning) {
                // 只有临时启动的进程才需要清理testClient
                try {
                    testClient.close();
                } catch (Exception e) {
                    log.debug("关闭测试客户端异常: {}", e.getMessage());
                }
            }
            
            if (process != null && !isAlreadyRunning && process.isAlive()) {
                // 只关闭临时启动的进程
                try {
                    process.destroy();
                    if (!process.waitFor(3, TimeUnit.SECONDS)) {
                        process.destroyForcibly();
                    }
                } catch (Exception e) {
                    log.debug("关闭测试进程异常: {}", e.getMessage());
                }
            }
        }
    }

    @Override
    public String startMcpPlugin(String pluginName)
    {
        try {
            log.info("接收到启动请求 [pluginName={}]", pluginName);
            
            AiPlugin plugin = aiPluginMapper.selectAiPluginByPluginName(pluginName);
            if (plugin == null) {
                log.warn("插件不存在 [pluginName={}]", pluginName);
                return "插件不存在";
            }
            
            if (!"mcp".equals(plugin.getPluginType())) {
                log.warn("不是MCP插件 [pluginName={}, type={}]", pluginName, plugin.getPluginType());
                return "不是MCP插件";
            }
            
            // 检查是否已经启动
            if (mcpProcessManager.isProcessAlive(pluginName)) {
                log.info("插件已经在运行中 [pluginName={}]", pluginName);
                return "插件已经在运行中";
            }
            
            log.info("开始启动MCP插件 [pluginName={}, name={}]", pluginName, plugin.getPluginName());
            mcpProcessManager.startMcpProcess(plugin, false); // MCP必须保持stdout可用于通信
            
            // 验证启动结果
            boolean isRunning = mcpProcessManager.isProcessAlive(pluginName);
            log.info("MCP插件启动完成 [pluginName={}, isRunning={}]", pluginName, isRunning);
            
            // 输出当前所有进程状态
            mcpProcessManager.getAllProcessStatus();
            
            return isRunning ? "启动成功" : "启动失败：进程未能保持运行";
            
        } catch (Exception e) {
            log.error("启动MCP插件失败 [pluginName={}]", pluginName, e);
            return "启动失败: " + e.getMessage();
        }
    }

    @Override
    public String stopMcpPlugin(String pluginName)
    {
        try {
            log.info("接收到停止请求 [pluginName={}]", pluginName);
            
            if (!mcpProcessManager.isProcessAlive(pluginName)) {
                log.info("插件未运行 [pluginName={}]", pluginName);
                return "插件未运行";
            }
            
            log.info("开始停止MCP插件 [pluginName={}]", pluginName);
            
            // 先清理全局客户端
            mcpSessionManager.removeGlobalClient(pluginName);
            
            // 再停止进程
            mcpProcessManager.stopMcpProcess(pluginName);
            
            // 验证停止结果
            boolean isRunning = mcpProcessManager.isProcessAlive(pluginName);
            log.info("MCP插件停止完成 [pluginName={}, isRunning={}]", pluginName, isRunning);
            
            // 输出当前所有进程状态
            mcpProcessManager.getAllProcessStatus();
            
            return "停止成功";
        } catch (Exception e) {
            log.error("停止MCP插件失败 [pluginName={}]", pluginName, e);
            return "停止失败: " + e.getMessage();
        }
    }

    @Override
    @Transactional
    public String syncPluginTools(String pluginName)
    {
        try {
            log.info("开始同步工具 [pluginName={}]", pluginName);
            
            AiPlugin plugin = aiPluginMapper.selectAiPluginByPluginName(pluginName);
            if (plugin == null) {
                return "插件不存在";
            }
            
            // 根据插件类型选择不同的同步方式
            if ("builtin".equals(plugin.getPluginType())) {
                return syncBuiltinPluginTools(plugin);
            } else if ("mcp".equals(plugin.getPluginType())) {
                return syncMcpPluginTools(plugin);
            } else {
                return "不支持的插件类型: " + plugin.getPluginType();
            }
            
        } catch (Exception e) {
            log.error("同步工具失败 [pluginName={}]", pluginName, e);
            return "同步失败: " + e.getMessage();
        }
    }
    
    /**
     * 同步内置插件工具
     */
    private String syncBuiltinPluginTools(AiPlugin plugin) {
        try {
            log.info("同步内置插件工具 [pluginId={}, builtinClass={}]", 
                plugin.getPluginId(), plugin.getBuiltinClass());
            
            // 加载插件实例
            Class<?> pluginClass = Class.forName(plugin.getBuiltinClass());
            IBuiltinPlugin builtinPlugin = (IBuiltinPlugin) applicationContext.getBean(pluginClass);
            
            // 获取工具列表
            List<ToolDefinition> tools = builtinPlugin.getTools();
            if (tools == null || tools.isEmpty()) {
                return "该插件没有工具";
            }
            
            // 删除旧的工具
            log.info("删除旧工具 [pluginId={}]", plugin.getPluginId());
            aiPluginToolMapper.deleteAiPluginToolByPluginId(plugin.getPluginId());
            
            // 插入新的工具
            int count = 0;
            for (ToolDefinition toolDef : tools) {
                AiPluginTool tool = new AiPluginTool();
                tool.setPluginId(plugin.getPluginId());
                tool.setToolName(toolDef.getName());
                tool.setToolCode(toolDef.getName());
                tool.setDescription(toolDef.getDescription());
                
                // 转换 inputSchema
                if (toolDef.getInputSchema() != null) {
                    tool.setInputSchema(JSON.toJSONString(toolDef.getInputSchema()));
                }
                
                tool.setStatus("0");
                tool.setSortOrder(count);
                
                aiPluginToolMapper.insertAiPluginTool(tool);
                count++;
            }
            
            log.info("内置插件工具同步完成 [pluginName={}, count={}]", 
                plugin.getPluginName(), count);
            
            return String.format("同步成功，共 %d 个工具", count);
            
        } catch (Exception e) {
            log.error("同步内置插件工具失败 [pluginId={}]", plugin.getPluginId(), e);
            return "同步失败: " + e.getMessage();
        }
    }
    
    /**
     * 同步MCP插件工具
     */
    private String syncMcpPluginTools(AiPlugin plugin) {
        Process tempProcess = null;
        McpClient tempClient = null;
        boolean wasRunning = false;
        String pluginName = plugin.getPluginName();
        
        try {
            // 检查插件是否正在运行
            wasRunning = mcpProcessManager.isProcessAlive(pluginName);
            
            if (wasRunning) {
                log.info("插件正在运行，先停止以便同步工具 [pluginName={}]", pluginName);
                mcpProcessManager.stopMcpProcess(pluginName);
                // 等待进程完全停止
                Thread.sleep(500);
            }
            
            // 启动临时进程用于同步
            log.info("启动临时进程获取工具列表 [pluginName={}]", pluginName);
            tempProcess = mcpProcessManager.startMcpProcess(plugin, false);
            
            if (!tempProcess.isAlive()) {
                return "同步失败：进程启动失败";
            }
            
            // 创建临时客户端
            tempClient = new McpClient("sync-" + System.currentTimeMillis(), tempProcess);
            
            // 初始化并获取工具列表
            tempClient.initialize();
            JSONObject result = tempClient.listTools();
            
            if (!result.containsKey("tools")) {
                return "未获取到工具列表";
            }
            
            // 删除旧的工具
            log.info("删除旧工具 [pluginId={}]", plugin.getPluginId());
            aiPluginToolMapper.deleteAiPluginToolByPluginId(plugin.getPluginId());
            
            // 插入新的工具
            int count = 0;
            for (Object toolObj : result.getJSONArray("tools")) {
                JSONObject toolJson = (JSONObject) toolObj;
                
                AiPluginTool tool = new AiPluginTool();
                tool.setPluginId(plugin.getPluginId());
                tool.setToolName(toolJson.getString("name"));
                tool.setToolCode(toolJson.getString("name"));
                tool.setDescription(toolJson.getString("description"));
                
                if (toolJson.containsKey("inputSchema")) {
                    tool.setInputSchema(toolJson.getJSONObject("inputSchema").toJSONString());
                }
                    
                tool.setStatus("0");
                tool.setSortOrder(count);
                
                aiPluginToolMapper.insertAiPluginTool(tool);
                count++;
            }
            
            log.info("工具同步完成 [pluginName={}, count={}]", pluginName, count);
            
            // 如果原来是运行状态，重新启动
            if (wasRunning) {
                log.info("重新启动插件 [pluginName={}]", pluginName);
                try {
                    mcpProcessManager.startMcpProcess(plugin, false); // MCP必须保持stdout可用于通信
                } catch (Exception e) {
                    log.error("重新启动插件失败 [pluginName={}]", pluginName, e);
                    return String.format("同步成功，共 %d 个工具。但重新启动失败: %s", count, e.getMessage());
                }
            }
            
            return String.format("同步成功，共 %d 个工具", count);
            
        } catch (Exception e) {
            log.error("同步工具失败 [pluginName={}]", pluginName, e);
            return "同步失败: " + e.getMessage();
        } finally {
            // 清理临时资源
            if (tempClient != null) {
                try {
                    tempClient.close();
                } catch (Exception e) {
                    log.debug("关闭临时客户端失败", e);
                }
            }
            
            if (tempProcess != null && tempProcess.isAlive()) {
                try {
                    tempProcess.destroy();
                    if (!tempProcess.waitFor(3, TimeUnit.SECONDS)) {
                        tempProcess.destroyForcibly();
                    }
                } catch (Exception e) {
                    log.debug("关闭临时进程失败", e);
                }
            }
        }
    }

    @Override
    public List<String> getMcpPluginLogs(String pluginName, int maxLines)
    {
        try {
            return mcpProcessManager.getProcessLogs(pluginName, maxLines);
        } catch (Exception e) {
            log.error("获取插件日志失败", e);
            List<String> errorLog = new ArrayList<>();
            errorLog.add("获取日志失败: " + e.getMessage());
            return errorLog;
        }
    }
}
