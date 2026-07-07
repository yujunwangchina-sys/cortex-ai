package com.ruoyi.agent.mapper;

import java.util.List;
import com.ruoyi.agent.domain.AiAgentDelegation;

/**
 * Agent委派授权Mapper接口
 *
 * @author ruoyi
 */
public interface AiAgentDelegationMapper
{
    /**
     * 查询Agent可委派的Agent ID列表
     */
    public List<Long> selectDelegateAgentIdsByAgentId(Long agentId);

    /**
     * 批量新增Agent委派授权
     */
    public int batchInsertAgentDelegation(List<AiAgentDelegation> list);

    /**
     * 删除Agent的所有委派授权
     */
    public int deleteAgentDelegationByAgentId(Long agentId);
}