package com.cortex.agent.runtime.tool;

import com.cortex.agent.runtime.context.RuntimeContext;
import com.cortex.agent.runtime.context.RuntimeContextHolder;
import com.cortex.plugin.domain.AiPlugin;
import com.cortex.plugin.domain.AiPluginTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.cortex.plugin.builtin.IBuiltinPlugin;
import com.cortex.plugin.mcp.McpSessionManager;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具调度器 — 路由工具调用到 MCP 或内置插件
 *
 * @author cortex
 */
@Component
public class ToolDispatcher
{
    private static final Logger log = LoggerFactory.getLogger(ToolDispatcher.class);

    @Autowired
    private McpSessionManager mcpSessionManager;

    @Autowired
    private ApplicationContext applicationContext;
    
    @Autowired
    private com.cortex.plugin.mapper.AiPluginExecutionLogMapper executionLogMapper;
    
    /**
     * 设置当前执行上下文（存储到ThreadLocal）
     */
    public void setRuntimeContext(String sessionId, Long agentId, String agentCode, String businessSystem, String userLoginName)
    {
        RuntimeContext context = new RuntimeContext(sessionId, agentCode, businessSystem, userLoginName);
        context.setAgentId(agentId);
        RuntimeContextHolder.setContext(context);
    }
    
    /**
     * 清除执行上下文
     */
    public void clearRuntimeContext()
    {
        RuntimeContextHolder.clearContext();
    }

    /**
     * 调度执行单个工具
     *
     * @param tool         工具定义
     * @param plugin       所属插件
     * @param sessionId    会话ID
     * @param toolCallId   工具调用ID
     * @param arguments    参数JSON字符串
     * @return 执行结果(JSON字符串)
     */
    public String dispatch(AiPluginTool tool, AiPlugin plugin, String sessionId,
                           String toolCallId, String arguments)
    {
        String pluginType = plugin.getPluginType();
        long startTime = System.currentTimeMillis();
        
        // 创建执行日志对象
        com.cortex.plugin.domain.AiPluginExecutionLog executionLog = new com.cortex.plugin.domain.AiPluginExecutionLog();
        executionLog.setSessionId(sessionId);
        executionLog.setPluginName(plugin.getPluginName());
        executionLog.setToolName(tool.getToolCode());
        executionLog.setInputParams(arguments);
        executionLog.setCreateTime(new java.util.Date());
        
        // 尝试获取用户ID
        try {
            executionLog.setUserId(com.cortex.common.utils.SecurityUtils.getUserId());
        } catch (Exception e) {
            // 获取用户ID失败时忽略
        }

        try
        {
            String result;
            if ("mcp".equals(pluginType))
            {
                result = dispatchMcp(plugin, sessionId, toolCallId, tool.getToolCode(), arguments);
            }
            else if ("builtin".equals(pluginType))
            {
                result = dispatchBuiltin(plugin, tool.getToolCode(), arguments);
            }
            else
            {
                result = errorJson("不支持的插件类型: " + pluginType);
            }
            
            // 记录成功日志
            long duration = System.currentTimeMillis() - startTime;
            executionLog.setExecutionTime((int) duration);
            executionLog.setStatus("0");
            executionLog.setOutputResult(result != null && result.length() > 10000 ? result.substring(0, 10000) : result);
            recordLog(executionLog);
            
            return result;
        }
        catch (Exception e)
        {
            // 记录失败日志
            long duration = System.currentTimeMillis() - startTime;
            executionLog.setExecutionTime((int) duration);
            executionLog.setStatus("1");
            executionLog.setErrorMessage(e.getMessage());
            recordLog(executionLog);
            
            log.error("工具调度失败 [plugin={}, tool={}]", plugin.getPluginName(), tool.getToolCode(), e);
            return errorJson("执行失败: " + e.getMessage());
        }
    }
    
    /**
     * 异步记录执行日志
     */
    private void recordLog(com.cortex.plugin.domain.AiPluginExecutionLog logEntry) {
        try {
            executionLogMapper.insertAiPluginExecutionLog(logEntry);
        } catch (Exception e) {
            // 记录日志失败不影响主流程
            log.error("记录执行日志失败", e);
        }
    }

    /**
     * MCP 插件调度
     */
    private String dispatchMcp(AiPlugin plugin, String sessionId, String toolCallId,
                               String toolName, String arguments) throws Exception
    {
        String mcpSessionId = "agent-" + sessionId;
        var client = mcpSessionManager.getClient(mcpSessionId, plugin.getPluginName());

        JSONObject args = JSON.parseObject(arguments);
        if (args == null)
        {
            args = new JSONObject();
        }

        JSONObject result = client.callTool(toolName, args);
        log.debug("MCP工具调用结果 [plugin={}, tool={}, result={}]", plugin.getPluginName(), toolName, result);
        
        // 解析MCP返回结果
        // MCP协议返回格式: { "content": [ { "type": "text", "text": "..." } or { "type": "image", "data": "base64...", "mimeType": "image/png" } ] }
        return parseMcpResult(result);
    }
    
    /**
     * 解析MCP工具调用结果
     * 将MCP协议的content数组转换为前端可以渲染的格式
     * 检测到图片时返回结构化的JSON（类似FileOperationPlugin）
     */
    private String parseMcpResult(JSONObject result) {
        if (result == null) {
            return "{}";
        }
        
        // 获取content数组
        Object contentObj = result.get("content");
        if (contentObj == null) {
            return result.toJSONString();
        }
        
        if (!(contentObj instanceof java.util.List)) {
            return result.toJSONString();
        }
        
        @SuppressWarnings("unchecked")
        java.util.List<Object> contentList = (java.util.List<Object>) contentObj;
        
        if (contentList.isEmpty()) {
            return result.toJSONString();
        }
        
        // 检查是否包含图片
        boolean hasImage = false;
        for (Object item : contentList) {
            if (item instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> contentItem = (java.util.Map<String, Object>) item;
                String type = (String) contentItem.get("type");
                if ("image".equals(type)) {
                    hasImage = true;
                    break;
                }
            }
        }
        
        // 如果包含图片，返回原始格式，让ImageStorageService后续处理
        // 这样可以保留base64数据用于保存
        StringBuilder markdown = new StringBuilder();
        boolean hasImageContent = false;
        boolean hasTextContent = false;
        
        for (Object item : contentList) {
            if (!(item instanceof java.util.Map)) {
                continue;
            }
            
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> contentItem = (java.util.Map<String, Object>) item;
            String type = (String) contentItem.get("type");
            
            if ("text".equals(type)) {
                // 文本内容
                String text = (String) contentItem.get("text");
                if (text != null && !text.isEmpty()) {
                    markdown.append(text).append("\n\n");
                    hasTextContent = true;
                }
            } else if ("image".equals(type)) {
                // 图片内容 - 转换为markdown图片格式
                String data = (String) contentItem.get("data");
                String mimeType = (String) contentItem.get("mimeType");
                
                if (data != null && !data.isEmpty()) {
                    hasImageContent = true;
                    // 生成markdown图片语法：![alt](data:image/png;base64,...)
                    if (mimeType == null || mimeType.isEmpty()) {
                        mimeType = "image/png"; // 默认PNG
                    }
                    markdown.append("![Generated Image](data:")
                            .append(mimeType)
                            .append(";base64,")
                            .append(data)
                            .append(")\n\n");
                }
            } else if ("resource".equals(type)) {
                // 资源引用
                Object resource = contentItem.get("resource");
                if (resource != null) {
                    markdown.append("```json\n")
                            .append(JSON.toJSONString(resource, String.valueOf(true)))
                            .append("\n```\n\n");
                }
            }
        }
        
        // 如果只有图片没有文本，在开头添加提示（这会在ImageStorageService处理后变成友好的提示）
        if (hasImageContent && !hasTextContent) {
            markdown.insert(0, "✅ 图表已生成！\n\n");
        }
        
        String resultText = markdown.toString().trim();
        return resultText.isEmpty() ? result.toJSONString() : resultText;
    }

    /**
     * 内置插件调度
     */
    @SuppressWarnings("unchecked")
    private String dispatchBuiltin(AiPlugin plugin, String toolName, String arguments) throws Exception
    {
        if (plugin.getBuiltinClass() == null || plugin.getBuiltinClass().isEmpty())
        {
            return errorJson("内置插件未配置类名");
        }

        Class<?> pluginClass = Class.forName(plugin.getBuiltinClass());
        IBuiltinPlugin builtinPlugin = (IBuiltinPlugin) applicationContext.getBean(pluginClass);

        // 注入环境变量(如果有)
        if (plugin.getEnvVars() != null && !plugin.getEnvVars().isEmpty())
        {
            try
            {
                Map<String, String> envVars = JSON.parseObject(plugin.getEnvVars(), Map.class);
                // 通过反射调用 setEnvVars (如果存在)
                try
                {
                    pluginClass.getMethod("setEnvVars", Map.class).invoke(builtinPlugin, envVars);
                }
                catch (NoSuchMethodException ignored) {}
            }
            catch (Exception e)
            {
                log.warn("注入环境变量失败 [plugin={}]", plugin.getPluginName(), e);
            }
        }
        
        // 调用初始化方法（确保插件资源正确初始化）
        try
        {
            builtinPlugin.initialize();
        }
        catch (Exception e)
        {
            log.warn("插件初始化失败 [plugin={}]", plugin.getPluginName(), e);
        }
        
        // 注意：运行时上下文已通过 RuntimeContextHolder 设置
        // 插件可以通过 RuntimeContextHolder 直接获取上下文，无需在此处注入

        Map<String, Object> args = JSON.parseObject(arguments, Map.class);
        if (args == null)
        {
            args = new HashMap<>();
        }

        String result = builtinPlugin.executeTool(toolName, args);
        
        return result;
    }

    private String errorJson(String message)
    {
        JSONObject obj = new JSONObject();
        obj.put("error", message);
        return obj.toJSONString();
    }
}
