package com.ruoyi.plugin.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 运行时环境配置对象 runtime_config
 * 
 * @author ruoyi
 */
public class RuntimeConfig extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** ID */
    private Long id;

    /** 运行时类型（python/node） */
    private String runtimeType;

    /** 可执行文件路径 */
    private String executablePath;

    /** 是否启用自定义路径 */
    private Boolean customPathEnabled;

    /** 版本信息 */
    private String version;

    /** 状态（0=可用 1=不可用） */
    private String status;

    public void setId(Long id) 
    {
        this.id = id;
    }

    public Long getId() 
    {
        return id;
    }

    public void setRuntimeType(String runtimeType) 
    {
        this.runtimeType = runtimeType;
    }

    public String getRuntimeType() 
    {
        return runtimeType;
    }

    public void setExecutablePath(String executablePath) 
    {
        this.executablePath = executablePath;
    }

    public String getExecutablePath() 
    {
        return executablePath;
    }

    public void setCustomPathEnabled(Boolean customPathEnabled) 
    {
        this.customPathEnabled = customPathEnabled;
    }

    public Boolean getCustomPathEnabled() 
    {
        return customPathEnabled;
    }

    public void setVersion(String version) 
    {
        this.version = version;
    }

    public String getVersion() 
    {
        return version;
    }

    public void setStatus(String status) 
    {
        this.status = status;
    }

    public String getStatus() 
    {
        return status;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("runtimeType", getRuntimeType())
            .append("executablePath", getExecutablePath())
            .append("customPathEnabled", getCustomPathEnabled())
            .append("version", getVersion())
            .append("status", getStatus())
            .append("createTime", getCreateTime())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
