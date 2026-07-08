package com.cortex.agent.domain;

import com.cortex.common.core.domain.BaseEntity;

/**
 * Agent-Skill关联对象 ai_agent_skill
 * 
 * @author cortex
 */
public class AiAgentSkill extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** ID */
    private Long id;

    /** Agent ID */
    private Long agentId;

    /** Skill ID */
    private Long skillId;

    public void setId(Long id) 
    {
        this.id = id;
    }

    public Long getId() 
    {
        return id;
    }

    public void setAgentId(Long agentId) 
    {
        this.agentId = agentId;
    }

    public Long getAgentId() 
    {
        return agentId;
    }

    public void setSkillId(Long skillId) 
    {
        this.skillId = skillId;
    }

    public Long getSkillId() 
    {
        return skillId;
    }
}
