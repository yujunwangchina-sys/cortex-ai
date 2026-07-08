package com.cortex.plugin.service.impl;

import java.util.Date;
import java.util.List;
import com.alibaba.fastjson2.JSONObject;
import com.cortex.common.utils.SecurityUtils;
import com.cortex.plugin.domain.AiPluginExecutionLog;
import com.cortex.plugin.domain.AiPluginTool;
import com.cortex.plugin.mapper.AiPluginExecutionLogMapper;
import com.cortex.plugin.mapper.AiPluginToolMapper;
import com.cortex.plugin.mcp.McpClient;
import com.cortex.plugin.mcp.McpSessionManager;
import com.cortex.plugin.service.IAiPluginToolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * AI插件工具Service业务层处理
 * 
 * @author cortex
 */
@Service
public class AiPluginToolServiceImpl implements IAiPluginToolService 
{
    private static final Logger log = LoggerFactory.getLogger(AiPluginToolServiceImpl.class);
    
    @Autowired
    private AiPluginToolMapper aiPluginToolMapper;

    @Autowired
    private AiPluginExecutionLogMapper executionLogMapper;

    @Autowired
    private McpSessionManager sessionManager;

    @Override
    public AiPluginTool selectAiPluginToolByToolId(Long toolId)
    {
        return aiPluginToolMapper.selectAiPluginToolByToolId(toolId);
    }

    @Override
    public List<AiPluginTool> selectAiPluginToolList(AiPluginTool aiPluginTool)
    {
        return aiPluginToolMapper.selectAiPluginToolList(aiPluginTool);
    }

    @Override
    public List<AiPluginTool> selectAiPluginToolListByPluginName(String pluginName) {
        return aiPluginToolMapper.selectAiPluginToolListByPluginName(pluginName);
    }

    @Override
    public List<AiPluginTool> selectAiPluginToolListByPluginId(Long pluginId)
    {
        return aiPluginToolMapper.selectAiPluginToolListByPluginId(pluginId);
    }


    @Override
    public int insertAiPluginTool(AiPluginTool aiPluginTool)
    {
        aiPluginTool.setCreateTime(new Date());
        return aiPluginToolMapper.insertAiPluginTool(aiPluginTool);
    }

    @Override
    public int updateAiPluginTool(AiPluginTool aiPluginTool)
    {
        aiPluginTool.setUpdateTime(new Date());
        return aiPluginToolMapper.updateAiPluginTool(aiPluginTool);
    }

    @Override
    public int deleteAiPluginToolByToolIds(Long[] toolIds)
    {
        return aiPluginToolMapper.deleteAiPluginToolByToolIds(toolIds);
    }

    @Override
    public int deleteAiPluginToolByToolId(Long toolId)
    {
        return aiPluginToolMapper.deleteAiPluginToolByToolId(toolId);
    }

    /**
     * 执行工具（核心方法 - 支持多线程并发）
     */
    @Override
    public JSONObject executeTool(String sessionId, String pluginName, String toolName, JSONObject params)
    {
        long startTime = System.currentTimeMillis();
        
        AiPluginExecutionLog executionLog = new AiPluginExecutionLog();
        executionLog.setSessionId(sessionId);
        executionLog.setPluginName(pluginName);
        executionLog.setToolName(toolName);
        executionLog.setInputParams(params == null ? "{}" : params.toJSONString());
        executionLog.setCreateTime(new Date());
        
        try {
            executionLog.setUserId(SecurityUtils.getUserId());
        } catch (Exception e) {
            // 获取用户ID失败时忽略
        }
        
        try {
            log.info("执行工具 [session={}, plugin={}, tool={}]", sessionId, pluginName, toolName);
            
            // 获取会话的MCP客户端（线程安全）
            McpClient client = sessionManager.getClient(sessionId, pluginName);
            
            // 调用工具
            JSONObject result = client.callTool(toolName, params);
            
            // 记录执行时长
            long duration = System.currentTimeMillis() - startTime;
            executionLog.setExecutionTime((int) duration);
            executionLog.setStatus("0");
            executionLog.setOutputResult(result.toJSONString());
            
            log.info("工具执行成功 [session={}, plugin={}, tool={}, duration={}ms]", 
                sessionId, pluginName, toolName, duration);
            
            // 异步记录日志
            recordLog(executionLog);
            
            return result;
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            executionLog.setExecutionTime((int) duration);
            executionLog.setStatus("1");
            executionLog.setErrorMessage(e.getMessage());
            
            log.error("工具执行失败 [session={}, plugin={}, tool={}]", 
                sessionId, pluginName, toolName, e);
            
            // 异步记录日志
            recordLog(executionLog);
            
            throw new RuntimeException("工具执行失败: " + e.getMessage());
        }
    }

    /**
     * 异步记录执行日志
     */
    private void recordLog(AiPluginExecutionLog log) {
        try {
            executionLogMapper.insertAiPluginExecutionLog(log);
        } catch (Exception e) {
            // 记录日志失败不影响主流程
            this.log.error("记录执行日志失败", e);
        }
    }
}
