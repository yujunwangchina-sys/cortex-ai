package com.ruoyi.agent.domain;

import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * AI Agent对象 ai_agent
 * 
 * @author ruoyi
 */
public class AiAgent extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** Agent ID */
    private Long id;

    /** Agent名称 */
    @Excel(name = "Agent名称")
    private String agentName;

    /** Agent编码 */
    @Excel(name = "Agent编码")
    private String agentCode;

    /** 系统级提示词 */
    private String systemPrompt;

    /** Agent描述 */
    @Excel(name = "描述")
    private String description;

    /** 状态(0启用 1禁用) */
    @Excel(name = "状态", readConverterExp = "0=启用,1=禁用")
    private String status;

    /** 排序 */
    private Integer sortOrder;

    /** 最大迭代次数(默认20) */
    private Integer maxIterations;

    /** 模型偏好(JSON) */
    private String modelPreference;

    /** 温度参数(覆盖模型默认) */
    private java.math.BigDecimal temperature;

    /** Agent所属业务系统 */
    private String businessSystem;

    /** API授权密钥（业务系统调用时使用） */
    private String apiKey;

    /** Agent头像路径 */
    private String avatar;

    public void setId(Long id) 
    {
        this.id = id;
    }

    public Long getId() 
    {
        return id;
    }

    public void setAgentName(String agentName) 
    {
        this.agentName = agentName;
    }

    public String getAgentName() 
    {
        return agentName;
    }

    public void setAgentCode(String agentCode) 
    {
        this.agentCode = agentCode;
    }

    public String getAgentCode() 
    {
        return agentCode;
    }

    public void setSystemPrompt(String systemPrompt) 
    {
        this.systemPrompt = systemPrompt;
    }

    public String getSystemPrompt() 
    {
        return systemPrompt;
    }

    public void setDescription(String description) 
    {
        this.description = description;
    }

    public String getDescription() 
    {
        return description;
    }

    public void setStatus(String status) 
    {
        this.status = status;
    }

    public String getStatus() 
    {
        return status;
    }

    public void setSortOrder(Integer sortOrder) 
    {
        this.sortOrder = sortOrder;
    }

    public Integer getSortOrder() 
    {
        return sortOrder;
    }

    public Integer getMaxIterations()
    {
        return maxIterations;
    }

    public void setMaxIterations(Integer maxIterations) 
    {
        this.maxIterations = maxIterations;
    }

    public String getModelPreference() 
    {
        return modelPreference;
    }

    public void setModelPreference(String modelPreference) 
    {
        this.modelPreference = modelPreference;
    }

    public java.math.BigDecimal getTemperature() 
    {
        return temperature;
    }

    public void setTemperature(java.math.BigDecimal temperature) 
    {
        this.temperature = temperature;
    }

    public String getBusinessSystem() 
    {
        return businessSystem;
    }

    public void setBusinessSystem(String businessSystem) 
    {
        this.businessSystem = businessSystem;
    }

    public String getApiKey() 
    {
        return apiKey;
    }

    public void setApiKey(String apiKey) 
    {
        this.apiKey = apiKey;
    }

    public String getAvatar() 
    {
        return avatar;
    }

    public void setAvatar(String avatar) 
    {
        this.avatar = avatar;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("agentName", getAgentName())
            .append("agentCode", getAgentCode())
            .append("systemPrompt", getSystemPrompt())
            .append("description", getDescription())
            .append("status", getStatus())
            .append("sortOrder", getSortOrder())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
