package com.cortex.plugin.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.cortex.common.annotation.Excel;
import com.cortex.common.core.domain.BaseEntity;

/**
 * AI插件执行日志对象 ai_plugin_execution_log
 * 
 * @author cortex
 */
public class AiPluginExecutionLog extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 日志ID */
    private Long logId;

    /** 会话ID */
    @Excel(name = "会话ID")
    private String sessionId;

    /** 插件名称 */
    @Excel(name = "插件名称")
    private String pluginName;

    /** 工具名称 */
    @Excel(name = "工具名称")
    private String toolName;

    /** 用户ID */
    @Excel(name = "用户ID")
    private Long userId;

    /** 输入参数 */
    private String inputParams;

    /** 输出结果 */
    private String outputResult;

    /** 执行时长（毫秒） */
    @Excel(name = "执行时长(ms)")
    private Integer executionTime;

    /** 执行状态（0成功 1失败） */
    @Excel(name = "状态", readConverterExp = "0=成功,1=失败")
    private String status;

    /** 错误信息 */
    private String errorMessage;

    /** 用户名称 */
    private String userName;


    public void setLogId(Long logId) 
    {
        this.logId = logId;
    }

    public Long getLogId() 
    {
        return logId;
    }

    public String getSessionId() 
    {
        return sessionId;
    }

    public void setSessionId(String sessionId) 
    {
        this.sessionId = sessionId;
    }

    public String getToolName() 
    {
        return toolName;
    }

    public void setToolName(String toolName) 
    {
        this.toolName = toolName;
    }

    public Long getUserId() 
    {
        return userId;
    }

    public void setUserId(Long userId) 
    {
        this.userId = userId;
    }

    public String getInputParams() 
    {
        return inputParams;
    }

    public void setInputParams(String inputParams) 
    {
        this.inputParams = inputParams;
    }

    public String getOutputResult() 
    {
        return outputResult;
    }

    public void setOutputResult(String outputResult) 
    {
        this.outputResult = outputResult;
    }

    public Integer getExecutionTime() 
    {
        return executionTime;
    }

    public void setExecutionTime(Integer executionTime) 
    {
        this.executionTime = executionTime;
    }

    public String getStatus() 
    {
        return status;
    }

    public void setStatus(String status) 
    {
        this.status = status;
    }

    public String getErrorMessage() 
    {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) 
    {
        this.errorMessage = errorMessage;
    }

    public String getUserName() 
    {
        return userName;
    }

    public void setUserName(String userName) 
    {
        this.userName = userName;
    }

    public String getPluginName() 
    {
        return pluginName;
    }

    public void setPluginName(String pluginName) 
    {
        this.pluginName = pluginName;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("logId", getLogId())
            .append("sessionId", getSessionId())
            .append("pluginName", getPluginName())
            .append("toolName", getToolName())
            .append("userId", getUserId())
            .append("inputParams", getInputParams())
            .append("outputResult", getOutputResult())
            .append("executionTime", getExecutionTime())
            .append("status", getStatus())
            .append("errorMessage", getErrorMessage())
            .append("createTime", getCreateTime())
            .toString();
    }
}
