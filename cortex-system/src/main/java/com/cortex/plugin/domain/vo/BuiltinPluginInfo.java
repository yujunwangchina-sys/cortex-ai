package com.cortex.plugin.domain.vo;

import com.cortex.plugin.builtin.ToolDefinition;
import lombok.Data;

import java.util.List;

/**
 * 内置插件信息VO
 * 
 * @author cortex
 */
@Data
public class BuiltinPluginInfo {
    
    /** 插件类名（完整类名），对应 AiPlugin.builtinClass */
    private String builtinClass;
    
    /** 插件显示名称，对应 AiPlugin.pluginName */
    private String displayName;
    
    /** 插件名称，对应 AiPlugin.pluginName */
    private String pluginName;
    
    /** 插件分类，对应 AiPlugin.category */
    private String category;
    
    /** 插件描述，对应 AiPlugin.description */
    private String description;
    
    /** 版本号，对应 AiPlugin.version */
    private String version;
    
    /** 作者，对应 AiPlugin.author */
    private String author;
    
    /** 图标emoji，对应 AiPlugin.icon */
    private String emoji;
    
    /** 是否需要审批，对应 AiPlugin.requireApproval（0否 1是） */
    private Boolean requireApproval;
    
    /** 是否已在插件列表中 */
    private Boolean enabled;
    
    /** 插件ID（如果已加载） */
    private Long pluginId;
    
    /** 已加载的实例数量 */
    private Integer instanceCount;
    
    /** 是否可用（环境变量等配置是否完整） */
    private Boolean available;
    
    /** 工具列表（仅用于显示，不保存到数据库） */
    private List<ToolDefinition> tools;
}
