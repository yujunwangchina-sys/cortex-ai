package com.ruoyi.agent.mapper;

import java.util.List;
import com.ruoyi.agent.domain.AiAgentSession;

/**
 * AI Agent会话Mapper接口
 *
 * @author ruoyi
 */
public interface AiAgentSessionMapper
{
    public AiAgentSession selectAiAgentSessionBySessionId(String sessionId);

    public List<AiAgentSession> selectAiAgentSessionList(AiAgentSession aiAgentSession);

    public List<AiAgentSession> selectSessionsByBusinessSystem(String businessSystem);

    public List<String> selectDistinctUserLoginNames(String businessSystem);

    public int insertAiAgentSession(AiAgentSession aiAgentSession);

    public int updateAiAgentSession(AiAgentSession aiAgentSession);

    public int deleteAiAgentSessionBySessionId(String sessionId);

    public int deleteAiAgentSessionBySessionIds(String[] sessionIds);

    /**
     * Update session stats (token_usage, iteration_count, last_message_time) without touching messages column.
     */
    public int updateSessionStats(com.ruoyi.agent.domain.AiAgentSession aiAgentSession);

    public int cleanupIdleSessions(@org.apache.ibatis.annotations.Param("cutoffTime") java.util.Date cutoffTime);

    /**
     * 查询所有业务系统标识(去重)
     */
    public List<String> selectAllBusinessSystems();

    /**
     * 查询所有业务系统与用户对(去重)
     */
    public java.util.List<java.util.Map<String, Object>> selectSessionTree();
    
    /**
     * 统计会话总数
     */
    public int countSessions();
    
    /**
     * 统计上一周期的会话数
     */
    public int countSessionsLastPeriod();
    
    /**
     * 按日期统计会话数
     */
    public int countSessionsByDate(String date);
}
