package com.ruoyi.agent.mapper;

import com.ruoyi.agent.domain.AiAgentMessage;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AI Agent Message Mapper - per-message persistence.
 *
 * @author ruoyi
 */
public interface AiAgentMessageMapper
{
    int insertMessage(AiAgentMessage message);

    int batchInsertMessages(@Param("messages") List<AiAgentMessage> messages);

    List<AiAgentMessage> selectBySessionId(@Param("sessionId") String sessionId);

    /** Count messages in a session */
    int countBySessionId(@Param("sessionId") String sessionId);

    /** Get messages after a given seqNum (for incremental loading) */
    List<AiAgentMessage> selectAfterSeqNum(@Param("sessionId") String sessionId,
                                            @Param("seqNum") int seqNum);

    int deleteBySessionId(@Param("sessionId") String sessionId);
}