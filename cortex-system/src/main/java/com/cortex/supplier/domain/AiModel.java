package com.cortex.supplier.domain;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.cortex.common.annotation.Excel;
import com.cortex.common.core.domain.BaseEntity;

/**
 * AI模型对象 ai_model
 * 
 * @author cortex
 */
public class AiModel extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 模型ID */
    private Long modelId;

    /** 供应商ID */
    @Excel(name = "供应商ID")
    private Long supplierId;

    /** 模型名称 */
    @Excel(name = "模型名称")
    private String modelName;

    /** 模型编码 */
    @Excel(name = "模型编码")
    private String modelCode;

    /** 模型类型 */
    @Excel(name = "模型类型", readConverterExp = "chat=聊天,multimodal=全模态,vision=图像识别,embedding=嵌入,image=图像生成,audio=语音,rerank=重排序")
    private String modelType;

    /** 上下文长度 */
    @Excel(name = "上下文长度")
    private Integer contextLength;

    /** 最大输出token数 */
    @Excel(name = "最大输出token数")
    private Integer maxTokens;

    /** 温度参数 */
    @Excel(name = "温度参数")
    private BigDecimal temperature;

    /** Top P参数 */
    @Excel(name = "Top P参数")
    private BigDecimal topP;

    /** 状态（0正常 1停用） */
    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    private String status;

    /** 排序 */
    @Excel(name = "排序")
    private Integer sortOrder;

    /** 供应商名称（关联查询字段） */
    private String supplierName;

    public void setModelId(Long modelId) 
    {
        this.modelId = modelId;
    }

    public Long getModelId() 
    {
        return modelId;
    }

    @NotNull(message = "供应商不能为空")
    public Long getSupplierId() 
    {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) 
    {
        this.supplierId = supplierId;
    }

    @NotBlank(message = "模型名称不能为空")
    @Size(min = 0, max = 100, message = "模型名称不能超过100个字符")
    public String getModelName() 
    {
        return modelName;
    }

    public void setModelName(String modelName) 
    {
        this.modelName = modelName;
    }

    @NotBlank(message = "模型编码不能为空")
    @Size(min = 0, max = 100, message = "模型编码不能超过100个字符")
    public String getModelCode() 
    {
        return modelCode;
    }

    public void setModelCode(String modelCode) 
    {
        this.modelCode = modelCode;
    }

    public String getModelType() 
    {
        return modelType;
    }

    public void setModelType(String modelType) 
    {
        this.modelType = modelType;
    }

    public Integer getContextLength() 
    {
        return contextLength;
    }

    public void setContextLength(Integer contextLength) 
    {
        this.contextLength = contextLength;
    }

    public Integer getMaxTokens() 
    {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) 
    {
        this.maxTokens = maxTokens;
    }

    public BigDecimal getTemperature() 
    {
        return temperature;
    }

    public void setTemperature(BigDecimal temperature) 
    {
        this.temperature = temperature;
    }

    public BigDecimal getTopP() 
    {
        return topP;
    }

    public void setTopP(BigDecimal topP) 
    {
        this.topP = topP;
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

    public String getSupplierName() 
    {
        return supplierName;
    }

    public void setSupplierName(String supplierName) 
    {
        this.supplierName = supplierName;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("modelId", getModelId())
            .append("supplierId", getSupplierId())
            .append("modelName", getModelName())
            .append("modelCode", getModelCode())
            .append("modelType", getModelType())
            .append("contextLength", getContextLength())
            .append("maxTokens", getMaxTokens())
            .append("temperature", getTemperature())
            .append("topP", getTopP())
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
