package com.ruoyi.agent.domain;

import com.ruoyi.common.core.domain.BaseEntity;

/**
 * Agent委派授权对象 ai_agent_delegation
 *
 * @author ruoyi
 */
public class AiAgentDelegation extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long id;

    /** 授权Agent ID (调用方) */
    private Long agentId;

    /** 被调用Agent ID (被委派方) */
    private Long delegateAgentId;

    public void setId(Long id) { this.id = id; }
    public Long getId() { return id; }

    public void setAgentId(Long agentId) { this.agentId = agentId; }
    public Long getAgentId() { return agentId; }

    public void setDelegateAgentId(Long delegateAgentId) { this.delegateAgentId = delegateAgentId; }
    public Long getDelegateAgentId() { return delegateAgentId; }
}