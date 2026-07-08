package com.cortex.plugin.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.cortex.plugin.mapper.AiPluginExecutionLogMapper;
import com.cortex.plugin.domain.AiPluginExecutionLog;
import com.cortex.plugin.service.IAiPluginExecutionLogService;

/**
 * AI插件执行日志Service业务层处理
 * 
 * @author cortex
 */
@Service
public class AiPluginExecutionLogServiceImpl implements IAiPluginExecutionLogService 
{
    @Autowired
    private AiPluginExecutionLogMapper aiPluginExecutionLogMapper;

    @Override
    public AiPluginExecutionLog selectAiPluginExecutionLogByLogId(Long logId)
    {
        return aiPluginExecutionLogMapper.selectAiPluginExecutionLogByLogId(logId);
    }

    @Override
    public List<AiPluginExecutionLog> selectAiPluginExecutionLogList(AiPluginExecutionLog aiPluginExecutionLog)
    {
        return aiPluginExecutionLogMapper.selectAiPluginExecutionLogList(aiPluginExecutionLog);
    }

    @Override
    public List<AiPluginExecutionLog> selectLogListBySessionId(String sessionId)
    {
        return aiPluginExecutionLogMapper.selectLogListBySessionId(sessionId);
    }

    @Override
    public int deleteAiPluginExecutionLogByLogId(Long logId)
    {
        return aiPluginExecutionLogMapper.deleteAiPluginExecutionLogByLogId(logId);
    }

    @Override
    public int deleteAiPluginExecutionLogByLogIds(Long[] logIds)
    {
        return aiPluginExecutionLogMapper.deleteAiPluginExecutionLogByLogIds(logIds);
    }

    @Override
    public int cleanOldLogs(int days)
    {
        return aiPluginExecutionLogMapper.cleanOldLogs(days);
    }
}
