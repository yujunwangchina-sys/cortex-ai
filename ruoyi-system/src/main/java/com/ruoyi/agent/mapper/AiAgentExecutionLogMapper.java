package com.ruoyi.agent.mapper;

import java.util.List;
import com.ruoyi.agent.domain.AiAgentExecutionLog;

/**
 * AI Agent执行日志Mapper接口
 *
 * @author ruoyi
 */
public interface AiAgentExecutionLogMapper
{
    public AiAgentExecutionLog selectAiAgentExecutionLogByLogId(Long logId);

    public List<AiAgentExecutionLog> selectAiAgentExecutionLogList(AiAgentExecutionLog aiAgentExecutionLog);

    public List<AiAgentExecutionLog> selectBySessionId(String sessionId);

    public int insertAiAgentExecutionLog(AiAgentExecutionLog aiAgentExecutionLog);

    public int deleteAiAgentExecutionLogByLogId(Long logId);

    public int deleteAiAgentExecutionLogByLogIds(Long[] logIds);

    public int deleteBySessionId(String sessionId);
}
