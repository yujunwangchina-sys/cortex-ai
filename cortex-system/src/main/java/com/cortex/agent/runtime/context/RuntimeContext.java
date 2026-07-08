package com.cortex.agent.runtime.context;

/**
 * Agent运行时上下文
 * 存储会话级别的运行时信息，供插件使用
 * 
 * @author cortex
 */
public class RuntimeContext
{
    private String sessionId;
    private Long agentId;
    private String agentCode;
    private String businessSystem;
    private String userLoginName;
    
    public RuntimeContext()
    {
    }
    
    public RuntimeContext(String sessionId, String agentCode, String businessSystem, String userLoginName)
    {
        this.sessionId = sessionId;
        this.agentCode = agentCode;
        this.businessSystem = businessSystem;
        this.userLoginName = userLoginName;
    }
    
    public String getSessionId()
    {
        return sessionId;
    }
    
    public void setSessionId(String sessionId)
    {
        this.sessionId = sessionId;
    }
    
    public Long getAgentId()
    {
        return agentId;
    }
    
    public void setAgentId(Long agentId)
    {
        this.agentId = agentId;
    }
    
    public String getAgentCode()
    {
        return agentCode;
    }
    
    public void setAgentCode(String agentCode)
    {
        this.agentCode = agentCode;
    }
    
    public String getBusinessSystem()
    {
        return businessSystem;
    }
    
    public void setBusinessSystem(String businessSystem)
    {
        this.businessSystem = businessSystem;
    }
    
    public String getUserLoginName()
    {
        return userLoginName;
    }
    
    public void setUserLoginName(String userLoginName)
    {
        this.userLoginName = userLoginName;
    }
    
    @Override
    public String toString()
    {
        return "RuntimeContext{" +
                "sessionId='" + sessionId + '\'' +
                ", agentId=" + agentId +
                ", agentCode='" + agentCode + '\'' +
                ", businessSystem='" + businessSystem + '\'' +
                ", userLoginName='" + userLoginName + '\'' +
                '}';
    }
}
