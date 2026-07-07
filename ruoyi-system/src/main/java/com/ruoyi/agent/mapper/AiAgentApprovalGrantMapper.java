package com.ruoyi.agent.mapper;

import java.util.List;
import com.ruoyi.agent.domain.AiAgentApprovalGrant;

/**
 * AI Agent审批授权Mapper接口
 *
 * @author ruoyi
 */
public interface AiAgentApprovalGrantMapper
{
    public AiAgentApprovalGrant selectAiAgentApprovalGrantByGrantId(Long grantId);

    public List<AiAgentApprovalGrant> selectAiAgentApprovalGrantList(AiAgentApprovalGrant aiAgentApprovalGrant);

    public List<AiAgentApprovalGrant> selectPendingBySessionId(String sessionId);

    public AiAgentApprovalGrant selectBySessionAndPlugin(String sessionId, Long pluginId);

    public int insertAiAgentApprovalGrant(AiAgentApprovalGrant aiAgentApprovalGrant);

    public int updateAiAgentApprovalGrant(AiAgentApprovalGrant aiAgentApprovalGrant);

    public int deleteAiAgentApprovalGrantByGrantId(Long grantId);

    public int deleteBySessionId(String sessionId);

    public int cleanupExpired();
}
