package com.cortex.agent.domain;

import com.cortex.common.core.domain.BaseEntity;

/**
 * Agent-插件关联对象 ai_agent_plugin
 * 
 * @author cortex
 */
public class AiAgentPlugin extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** ID */
    private Long id;

    /** Agent ID */
    private Long agentId;

    /** 插件ID */
    private Long pluginId;

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

    public void setPluginId(Long pluginId) 
    {
        this.pluginId = pluginId;
    }

    public Long getPluginId() 
    {
        return pluginId;
    }
}
