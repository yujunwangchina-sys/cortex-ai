package com.cortex.plugin.builtin;

/**
 * 插件元数据信息
 * 
 * @author cortex
 */
public class PluginInfo {
    
    /** 插件名称 */
    private String name;
    
    /** 插件名称（唯一标识） */
    private String code;
    
    /** 插件描述 */
    private String description;
    
    /** 版本号 */
    private String version;
    
    /** 作者 */
    private String author;
    
    /** 分类 */
    private String category;
    
    /** 图标emoji */
    private String emoji;
    
    /** 是否需要审批 */
    private boolean requireApproval;
    
    public PluginInfo() {
    }
    
    public PluginInfo(String name, String code, String description) {
        this.name = name;
        this.code = code;
        this.description = description;
        this.version = "1.0.0";
        this.category = "utility";
        this.requireApproval = false;
    }
    
    // Getters and Setters
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getCode() {
        return code;
    }
    
    public void setCode(String code) {
        this.code = code;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    public String getAuthor() {
        return author;
    }
    
    public void setAuthor(String author) {
        this.author = author;
    }
    
    public String getCategory() {
        return category;
    }
    
    public void setCategory(String category) {
        this.category = category;
    }
    
    public String getEmoji() {
        return emoji;
    }
    
    public void setEmoji(String emoji) {
        this.emoji = emoji;
    }
    
    public boolean isRequireApproval() {
        return requireApproval;
    }
    
    public void setRequireApproval(boolean requireApproval) {
        this.requireApproval = requireApproval;
    }
}
