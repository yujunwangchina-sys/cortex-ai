package com.cortex.agent.domain;

import com.cortex.common.core.domain.BaseEntity;

/**
 * AI Agent会话对象 ai_agent_session
 *
 * @author cortex
 */
public class AiAgentSession extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 会话唯一ID(雪花算法) */
    private String sessionId;

    /** Agent ID */
    private Long agentId;

    /** Agent编码 */
    private String agentCode;

    /** 用户登录名 */
    private String userLoginName;

    /** 业务系统标识 */
    private String businessSystem;

    /** 消息历史JSON(表上字段已废弃) */
    private String messages;

    /** 累计token数 */
    private Integer tokenUsage;

    /** 累计迭代次数 */
    private Integer iterationCount;

    /** 状态(0活跃 1已结束 2异常中断) */
    private String status;

    /** 最后消息时间 */
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private java.util.Date lastMessageTime;

    /** Agent名称(关联查询) */
    private String agentName;

    /** 会话标题(AI意图分析) */
    private String title;

    /** 当前使用的模型ID（记录最后一次使用的模型） */
    private Long modelId;
    
    /** 模型切换历史（JSON格式：[{turnId:1,fromModelId:10,toModelId:12}]） */
    private String modelSwitchHistory;
    
    /** 压缩后的工作上下文（JSON格式，用于LLM，可能被压缩过） */
    private String compressedContext;

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

    public void setAgentCode(String agentCode)
    {
        this.agentCode = agentCode;
    }

    public String getAgentCode()
    {
        return agentCode;
    }

    public void setUserLoginName(String userLoginName)
    {
        this.userLoginName = userLoginName;
    }

    public String getUserLoginName()
    {
        return userLoginName;
    }

    public void setBusinessSystem(String businessSystem)
    {
        this.businessSystem = businessSystem;
    }

    public String getBusinessSystem()
    {
        return businessSystem;
    }

    public void setMessages(String messages)
    {
        this.messages = messages;
    }

    public String getMessages()
    {
        return messages;
    }

    public void setTokenUsage(Integer tokenUsage)
    {
        this.tokenUsage = tokenUsage;
    }

    public Integer getTokenUsage()
    {
        return tokenUsage;
    }

    public void setIterationCount(Integer iterationCount)
    {
        this.iterationCount = iterationCount;
    }

    public Integer getIterationCount()
    {
        return iterationCount;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getStatus()
    {
        return status;
    }

    public java.util.Date getLastMessageTime()
    {
        return lastMessageTime;
    }

    public void setLastMessageTime(java.util.Date lastMessageTime)
    {
        this.lastMessageTime = lastMessageTime;
    }

    public String getAgentName()
    {
        return agentName;
    }

    public void setAgentName(String agentName)
    {
        this.agentName = agentName;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public Long getModelId()
    {
        return modelId;
    }

    public void setModelId(Long modelId)
    {
        this.modelId = modelId;
    }
    
    public String getModelSwitchHistory()
    {
        return modelSwitchHistory;
    }

    public void setModelSwitchHistory(String modelSwitchHistory)
    {
        this.modelSwitchHistory = modelSwitchHistory;
    }
    
    public String getCompressedContext()
    {
        return compressedContext;
    }

    public void setCompressedContext(String compressedContext)
    {
        this.compressedContext = compressedContext;
    }
}
