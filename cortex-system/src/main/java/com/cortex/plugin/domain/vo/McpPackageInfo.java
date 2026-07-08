package com.cortex.plugin.domain.vo;

import lombok.Data;

/**
 * MCP包信息VO
 *
 * @author cortex
 */
@Data
public class McpPackageInfo {
    
    /** 包名 */
    private String packageName;
    
    /** 显示名称 */
    private String displayName;
    
    /** 版本号 */
    private String version;
    
    /** 描述 */
    private String description;
    
    /** 运行时类型 (pip/npm) */
    private String runtimeType;
    
    /** 是否已启用（在ai_plugin表中且status='0'） */
    private Boolean enabled;
    
    /** 插件ID（如果已启用） */
    private Long pluginId;
    
    /** 默认启动命令 */
    private String defaultCommand;
    
    /** 环境变量（JSON字符串） */
    private String envVars;
    
    /** 是否需要审批（0否 1是） */
    private String requireApproval;
    
    /** 插件名称（用户自定义） */
    private String pluginName;
    
    /** 作者 */
    private String author;
    
    /** 主页 */
    private String homepage;
    
    /** 分类 */
    private String category;
    
    /** 是否官方推荐 */
    private Boolean official;
    
    /** 安装时间 */
    private String installedTime;
}
