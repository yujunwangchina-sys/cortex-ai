package com.cortex.agent.domain;

import com.cortex.common.core.domain.BaseEntity;

/**
 * AI Agent审批授权对象 ai_agent_approval_grant
 *
 * @author cortex
 */
public class AiAgentApprovalGrant extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 授权ID */
    private Long grantId;

    /** 会话ID */
    private String sessionId;

    /** Agent ID */
    private Long agentId;

    /** 插件ID */
    private Long pluginId;

    /** 插件名称 */
    private String pluginName;

    /** 授权状态(0已授权 1已拒绝 2待决定) */
    private String grantStatus;

    /** 授权人 */
    private String grantedBy;

    /** 业务系统 */
    private String businessSystem;

    /** 授权过期时间 */
    private java.util.Date expireTime;

    /** 拒绝理由 */
    private String rejectReason;

    public void setGrantId(Long grantId)
    {
        this.grantId = grantId;
    }

    public Long getGrantId()
    {
        return grantId;
    }

    public void setSessionId(String sessionId)
    {
        this.sessionId = sessionId;
    }

    public String getSessionId()
    {
        return sessionId;
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

    public void setPluginName(String pluginName)
    {
        this.pluginName = pluginName;
    }

    public String getPluginName()
    {
        return pluginName;
    }

    public void setGrantStatus(String grantStatus)
    {
        this.grantStatus = grantStatus;
    }

    public String getGrantStatus()
    {
        return grantStatus;
    }

    public void setGrantedBy(String grantedBy)
    {
        this.grantedBy = grantedBy;
    }

    public String getGrantedBy()
    {
        return grantedBy;
    }

    public void setBusinessSystem(String businessSystem)
    {
        this.businessSystem = businessSystem;
    }

    public String getBusinessSystem()
    {
        return businessSystem;
    }

    public java.util.Date getExpireTime()
    {
        return expireTime;
    }

    public void setExpireTime(java.util.Date expireTime)
    {
        this.expireTime = expireTime;
    }

    public String getRejectReason()
    {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason)
    {
        this.rejectReason = rejectReason;
    }
}
