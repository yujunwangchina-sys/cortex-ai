package com.ruoyi.plugin.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * AI插件对象 ai_plugin
 * 
 * @author ruoyi
 */
public class AiPlugin extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 插件ID */
    private Long pluginId;

    /** 插件名称（唯一） */
    @Excel(name = "插件名称")
    private String pluginName;

    /** 插件类型（mcp/builtin） */
    @Excel(name = "插件类型", readConverterExp = "mcp=MCP插件,builtin=内置插件")
    private String pluginType;

    /** 插件分类 */
    @Excel(name = "插件分类")
    private String category;

    /** 插件描述 */
    @Excel(name = "描述")
    private String description;

    /** 插件图标 */
    private String icon;

    /** 版本号 */
    @Excel(name = "版本")
    private String version;

    /** 作者 */
    @Excel(name = "作者")
    private String author;

    /** 运行时类型（pip/venv/npm） */
    @Excel(name = "运行时", readConverterExp = "pip=Python(pip),venv=Python(venv),npm=Node(npm)")
    private String runtimeType;

    /** 包名 */
    @Excel(name = "包名")
    private String packageName;

    /** 启动命令（JSON数组） */
    private String startCommand;

    /** 环境变量（JSON对象） */
    private String envVars;

    /** 内置插件类名 */
    private String builtinClass;

    /** 状态（0启用 1禁用） */
    @Excel(name = "状态", readConverterExp = "0=启用,1=禁用")
    private String status;

    /** 是否需要审批（0否 1是） */
    @Excel(name = "需要审批", readConverterExp = "0=否,1=是")
    private String requireApproval;

    /** 是否官方插件 */
    @Excel(name = "官方插件", readConverterExp = "0=否,1=是")
    private String isOfficial;

    /** 安装来源 */
    @Excel(name = "来源")
    private String installSource;

    /** 排序 */
    private Integer sortOrder;

    /** 进程运行状态（非持久化字段，仅用于查询展示） */
    private Boolean isRunning;

    public void setPluginId(Long pluginId) 
    {
        this.pluginId = pluginId;
    }

    public Long getPluginId() 
    {
        return pluginId;
    }

    @NotBlank(message = "插件名称不能为空")
    @Size(min = 0, max = 100, message = "插件名称长度不能超过100个字符")
    public String getPluginName() 
    {
        return pluginName;
    }

    public void setPluginName(String pluginName) 
    {
        this.pluginName = pluginName;
    }

    @NotBlank(message = "插件类型不能为空")
    public String getPluginType() 
    {
        return pluginType;
    }

    public void setPluginType(String pluginType) 
    {
        this.pluginType = pluginType;
    }

    public String getCategory() 
    {
        return category;
    }

    public void setCategory(String category) 
    {
        this.category = category;
    }

    public String getDescription() 
    {
        return description;
    }

    public void setDescription(String description) 
    {
        this.description = description;
    }

    public String getIcon() 
    {
        return icon;
    }

    public void setIcon(String icon) 
    {
        this.icon = icon;
    }

    public String getVersion() 
    {
        return version;
    }

    public void setVersion(String version) 
    {
        this.version = version;
    }

    public String getAuthor() 
    {
        return author;
    }

    public void setAuthor(String author) 
    {
        this.author = author;
    }

    public String getRuntimeType() 
    {
        return runtimeType;
    }

    public void setRuntimeType(String runtimeType) 
    {
        this.runtimeType = runtimeType;
    }

    public String getPackageName() 
    {
        return packageName;
    }

    public void setPackageName(String packageName) 
    {
        this.packageName = packageName;
    }

    public String getStartCommand() 
    {
        return startCommand;
    }

    public void setStartCommand(String startCommand) 
    {
        this.startCommand = startCommand;
    }

    public String getEnvVars() 
    {
        return envVars;
    }

    public void setEnvVars(String envVars) 
    {
        this.envVars = envVars;
    }

    public String getBuiltinClass() 
    {
        return builtinClass;
    }

    public void setBuiltinClass(String builtinClass) 
    {
        this.builtinClass = builtinClass;
    }

    public String getStatus() 
    {
        return status;
    }

    public void setStatus(String status) 
    {
        this.status = status;
    }

    public String getRequireApproval() 
    {
        return requireApproval;
    }

    public void setRequireApproval(String requireApproval) 
    {
        this.requireApproval = requireApproval;
    }

    public String getIsOfficial() 
    {
        return isOfficial;
    }

    public void setIsOfficial(String isOfficial) 
    {
        this.isOfficial = isOfficial;
    }

    public String getInstallSource() 
    {
        return installSource;
    }

    public void setInstallSource(String installSource) 
    {
        this.installSource = installSource;
    }

    public Integer getSortOrder() 
    {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) 
    {
        this.sortOrder = sortOrder;
    }

    public Boolean getIsRunning() 
    {
        return isRunning;
    }

    public void setIsRunning(Boolean isRunning) 
    {
        this.isRunning = isRunning;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("pluginId", getPluginId())
            .append("pluginName", getPluginName())
            .append("pluginType", getPluginType())
            .append("category", getCategory())
            .append("description", getDescription())
            .append("icon", getIcon())
            .append("version", getVersion())
            .append("author", getAuthor())
            .append("runtimeType", getRuntimeType())
            .append("packageName", getPackageName())
            .append("startCommand", getStartCommand())
            .append("envVars", getEnvVars())
            .append("builtinClass", getBuiltinClass())
            .append("status", getStatus())
            .append("isOfficial", getIsOfficial())
            .append("installSource", getInstallSource())
            .append("sortOrder", getSortOrder())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
