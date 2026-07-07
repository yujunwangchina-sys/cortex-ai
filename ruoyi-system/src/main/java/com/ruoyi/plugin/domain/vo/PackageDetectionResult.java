package com.ruoyi.plugin.domain.vo;

import java.util.Map;

/**
 * 包检测结果
 * 
 * @author ruoyi
 */
public class PackageDetectionResult {
    
    /** 是否检测成功 */
    private boolean success;
    
    /** 包名 */
    private String packageName;
    
    /** 运行时类型（pip/npm） */
    private String runtimeType;
    
    /** 版本号 */
    private String version;
    
    /** 启动命令（JSON数组格式） */
    private String startCommand;
    
    /** 环境变量（JSON对象格式） */
    private String envVars;
    
    /** 描述信息 */
    private String description;
    
    /** 是否是官方包 */
    private String isOfficial;
    
    /** 插件名称（自动生成） */
    private String pluginName;
    
    /** 分类 */
    private String category;
    
    /** 错误消息 */
    private String errorMessage;
    
    /** 检测详情（哪些环境检查了） */
    private String detectionDetails;
    
    /** 是否已下载 */
    private boolean downloaded;
    
    /** 额外元数据 */
    private Map<String, Object> metadata;

    // Getters and Setters
    
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getRuntimeType() {
        return runtimeType;
    }

    public void setRuntimeType(String runtimeType) {
        this.runtimeType = runtimeType;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getStartCommand() {
        return startCommand;
    }

    public void setStartCommand(String startCommand) {
        this.startCommand = startCommand;
    }

    public String getEnvVars() {
        return envVars;
    }

    public void setEnvVars(String envVars) {
        this.envVars = envVars;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getIsOfficial() {
        return isOfficial;
    }

    public void setIsOfficial(String isOfficial) {
        this.isOfficial = isOfficial;
    }

    public String getPluginName() {
        return pluginName;
    }

    public void setPluginName(String pluginName) {
        this.pluginName = pluginName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getDetectionDetails() {
        return detectionDetails;
    }

    public void setDetectionDetails(String detectionDetails) {
        this.detectionDetails = detectionDetails;
    }

    public boolean isDownloaded() {
        return downloaded;
    }

    public void setDownloaded(boolean downloaded) {
        this.downloaded = downloaded;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
