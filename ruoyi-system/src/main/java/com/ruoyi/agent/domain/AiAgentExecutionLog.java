package com.ruoyi.agent.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import java.util.Date;

/**
 * AI Agent执行日志对象 ai_agent_execution_log
 *
 * @author ruoyi
 */
public class AiAgentExecutionLog extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 日志ID */
    private Long logId;

    /** 会话ID */
    private String sessionId;

    /** Agent ID */
    private Long agentId;

    /** 轮次ID */
    private String turnId;

    /** 第几次迭代 */
    private Integer iteration;

    /** 事件类型 */
    private String eventType;

    /** 插件名 */
    private String pluginName;

    /** 工具名 */
    private String toolName;

    /** 输入参数JSON */
    private String inputParams;

    /** 输出结果JSON */
    private String outputResult;

    /** 执行耗时(毫秒) */
    private Integer durationMs;

    /** 输入token数 */
    private Integer tokenInput;

    /** 输出token数 */
    private Integer tokenOutput;

    /** 状态(0成功 1失败) */
    private String status;

    /** 错误信息 */
    private String errorMessage;

    /** 业务系统 */
    private String businessSystem;

    /** 用户登录名 */
    private String userLoginName;

    public void setLogId(Long logId)
    {
        this.logId = logId;
    }

    public Long getLogId()
    {
        return logId;
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

    public void setTurnId(String turnId)
    {
        this.turnId = turnId;
    }

    public String getTurnId()
    {
        return turnId;
    }

    public void setIteration(Integer iteration)
    {
        this.iteration = iteration;
    }

    public Integer getIteration()
    {
        return iteration;
    }

    public void setEventType(String eventType)
    {
        this.eventType = eventType;
    }

    public String getEventType()
    {
        return eventType;
    }

    public void setPluginName(String pluginName)
    {
        this.pluginName = pluginName;
    }

    public String getPluginName()
    {
        return pluginName;
    }

    public void setToolName(String toolName)
    {
        this.toolName = toolName;
    }

    public String getToolName()
    {
        return toolName;
    }

    public void setInputParams(String inputParams)
    {
        this.inputParams = inputParams;
    }

    public String getInputParams()
    {
        return inputParams;
    }

    public void setOutputResult(String outputResult)
    {
        this.outputResult = outputResult;
    }

    public String getOutputResult()
    {
        return outputResult;
    }

    public void setDurationMs(Integer durationMs)
    {
        this.durationMs = durationMs;
    }

    public Integer getDurationMs()
    {
        return durationMs;
    }

    public void setTokenInput(Integer tokenInput)
    {
        this.tokenInput = tokenInput;
    }

    public Integer getTokenInput()
    {
        return tokenInput;
    }

    public void setTokenOutput(Integer tokenOutput)
    {
        this.tokenOutput = tokenOutput;
    }

    public Integer getTokenOutput()
    {
        return tokenOutput;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getStatus()
    {
        return status;
    }

    public void setErrorMessage(String errorMessage)
    {
        this.errorMessage = errorMessage;
    }

    public String getErrorMessage()
    {
        return errorMessage;
    }

    public void setBusinessSystem(String businessSystem)
    {
        this.businessSystem = businessSystem;
    }

    public String getBusinessSystem()
    {
        return businessSystem;
    }

    public void setUserLoginName(String userLoginName)
    {
        this.userLoginName = userLoginName;
    }

    public String getUserLoginName()
    {
        return userLoginName;
    }
}
