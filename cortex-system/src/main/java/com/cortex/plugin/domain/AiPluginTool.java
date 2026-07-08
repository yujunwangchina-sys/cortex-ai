package com.cortex.plugin.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.cortex.common.annotation.Excel;
import com.cortex.common.core.domain.BaseEntity;

/**
 * AI插件工具对象 ai_plugin_tool
 * 
 * @author cortex
 */
public class AiPluginTool extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 工具ID */
    private Long toolId;

    /** 插件ID */
    @Excel(name = "插件ID")
    private Long pluginId;

    /** 工具名称 */
    @Excel(name = "工具名称")
    private String toolName;

    /** 工具编码 */
    @Excel(name = "工具编码")
    private String toolCode;

    /** 工具描述 */
    @Excel(name = "描述")
    private String description;

    /** 输入参数schema */
    private String inputSchema;

    /** 输出参数schema */
    private String outputSchema;

    /** 示例输入 */
    private String exampleInput;

    /** 示例输出 */
    private String exampleOutput;

    /** 状态（0启用 1禁用） */
    @Excel(name = "状态", readConverterExp = "0=启用,1=禁用")
    private String status;

    /** 排序 */
    private Integer sortOrder;

    /** 插件名称（关联查询） */
    private String pluginName;

    /** 插件类型（关联查询，用于过滤） */
    private String pluginType;

    public void setToolId(Long toolId) 
    {
        this.toolId = toolId;
    }

    public Long getToolId() 
    {
        return toolId;
    }

    @NotNull(message = "插件ID不能为空")
    public Long getPluginId() 
    {
        return pluginId;
    }

    public void setPluginId(Long pluginId) 
    {
        this.pluginId = pluginId;
    }

    @NotBlank(message = "工具名称不能为空")
    @Size(min = 0, max = 100, message = "工具名称长度不能超过100个字符")
    public String getToolName() 
    {
        return toolName;
    }

    public void setToolName(String toolName) 
    {
        this.toolName = toolName;
    }

    @NotBlank(message = "工具编码不能为空")
    @Size(min = 0, max = 100, message = "工具编码长度不能超过100个字符")
    public String getToolCode() 
    {
        return toolCode;
    }

    public void setToolCode(String toolCode) 
    {
        this.toolCode = toolCode;
    }

    public String getDescription() 
    {
        return description;
    }

    public void setDescription(String description) 
    {
        this.description = description;
    }

    public String getInputSchema() 
    {
        return inputSchema;
    }

    public void setInputSchema(String inputSchema) 
    {
        this.inputSchema = inputSchema;
    }

    public String getOutputSchema() 
    {
        return outputSchema;
    }

    public void setOutputSchema(String outputSchema) 
    {
        this.outputSchema = outputSchema;
    }

    public String getExampleInput() 
    {
        return exampleInput;
    }

    public void setExampleInput(String exampleInput) 
    {
        this.exampleInput = exampleInput;
    }

    public String getExampleOutput() 
    {
        return exampleOutput;
    }

    public void setExampleOutput(String exampleOutput) 
    {
        this.exampleOutput = exampleOutput;
    }

    public String getStatus() 
    {
        return status;
    }

    public void setStatus(String status) 
    {
        this.status = status;
    }

    public Integer getSortOrder() 
    {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) 
    {
        this.sortOrder = sortOrder;
    }

    public String getPluginName() 
    {
        return pluginName;
    }

    public void setPluginName(String pluginName) 
    {
        this.pluginName = pluginName;
    }

    public String getPluginType() 
    {
        return pluginType;
    }

    public void setPluginType(String pluginType) 
    {
        this.pluginType = pluginType;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("toolId", getToolId())
            .append("pluginId", getPluginId())
            .append("toolName", getToolName())
            .append("toolCode", getToolCode())
            .append("description", getDescription())
            .append("inputSchema", getInputSchema())
            .append("outputSchema", getOutputSchema())
            .append("exampleInput", getExampleInput())
            .append("exampleOutput", getExampleOutput())
            .append("status", getStatus())
            .append("sortOrder", getSortOrder())
            .append("createTime", getCreateTime())
            .append("updateTime", getUpdateTime())
            .toString();
    }
}
