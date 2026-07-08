package com.cortex.knowledge.mapper;

import java.util.List;
import com.cortex.knowledge.domain.AiAgentKnowledge;

/**
 * Agent知识库授权Mapper接口
 *
 * @author cortex
 */
public interface AiAgentKnowledgeMapper
{
    public List<AiAgentKnowledge> selectByAgentId(Long agentId);

    public List<AiAgentKnowledge> selectActiveByAgentId(Long agentId);

    public AiAgentKnowledge selectByAgentIdAndKbId(Long agentId, Long kbId);

    public int insertAiAgentKnowledge(AiAgentKnowledge aiAgentKnowledge);

    public int deleteByAgentId(Long agentId);

    public int deleteByAgentIdAndKbId(Long agentId, Long kbId);

    public int batchInsertGrants(List<AiAgentKnowledge> grants);

    public List<Long> selectKbIdsByAgentId(Long agentId);
}