package com.ruoyi.supplier.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * AI供应商对象 ai_supplier
 * 
 * @author ruoyi
 */
public class AiSupplier extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 供应商ID */
    private Long supplierId;

    /** 供应商名称 */
    @Excel(name = "供应商名称")
    private String supplierName;

    /** 供应商编码 */
    @Excel(name = "供应商编码")
    private String supplierCode;

    /** API基础地址 */
    @Excel(name = "API基础地址")
    private String apiBaseUrl;

    /** API密钥 */
    private String apiKey;

    /** 描述 */
    @Excel(name = "描述")
    private String description;

    /** 状态（0正常 1停用） */
    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    private String status;

    /** 排序 */
    @Excel(name = "排序")
    private Integer sortOrder;

    public void setSupplierId(Long supplierId) 
    {
        this.supplierId = supplierId;
    }

    public Long getSupplierId() 
    {
        return supplierId;
    }

    @NotBlank(message = "供应商名称不能为空")
    @Size(min = 0, max = 100, message = "供应商名称不能超过100个字符")
    public String getSupplierName() 
    {
        return supplierName;
    }

    public void setSupplierName(String supplierName) 
    {
        this.supplierName = supplierName;
    }

    @NotBlank(message = "供应商编码不能为空")
    @Size(min = 0, max = 50, message = "供应商编码不能超过50个字符")
    public String getSupplierCode() 
    {
        return supplierCode;
    }

    public void setSupplierCode(String supplierCode) 
    {
        this.supplierCode = supplierCode;
    }

    @NotBlank(message = "API基础地址不能为空")
    @Size(min = 0, max = 500, message = "API基础地址不能超过500个字符")
    public String getApiBaseUrl() 
    {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) 
    {
        this.apiBaseUrl = apiBaseUrl;
    }

    public String getApiKey() 
    {
        return apiKey;
    }

    public void setApiKey(String apiKey) 
    {
        this.apiKey = apiKey;
    }

    public String getDescription() 
    {
        return description;
    }

    public void setDescription(String description) 
    {
        this.description = description;
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

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("supplierId", getSupplierId())
            .append("supplierName", getSupplierName())
            .append("supplierCode", getSupplierCode())
            .append("apiBaseUrl", getApiBaseUrl())
            .append("apiKey", getApiKey())
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
