package com.ruoyi.plugin.builtin;

import java.util.List;
import java.util.Map;

/**
 * 内置插件接口
 * 
 * 所有内置插件必须实现此接口
 * 
 * @author ruoyi
 */
public interface IBuiltinPlugin {
    
    /**
     * 获取插件信息
     * 
     * @return 插件元数据
     */
    PluginInfo getPluginInfo();
    
    /**
     * 获取插件提供的工具列表
     * 
     * @return 工具定义列表
     */
    List<ToolDefinition> getTools();
    
    /**
     * 执行工具调用
     * 
     * @param toolName 工具名称
     * @param arguments 工具参数
     * @return 执行结果（JSON格式字符串）
     */
    String executeTool(String toolName, Map<String, Object> arguments);
    
    /**
     * 初始化插件（可选）
     * 在插件加载时调用
     */
    default void initialize() {
        // 默认不做任何操作
    }
    
    /**
     * 销毁插件（可选）
     * 在插件卸载时调用
     */
    default void destroy() {
        // 默认不做任何操作
    }
    
    /**
     * 检查插件是否可用
     * 
     * @return true表示可用，false表示不可用
     */
    default boolean isAvailable() {
        return true;
    }
    
    /**
     * 获取插件状态信息
     * 
     * @return 状态信息
     */
    default Map<String, Object> getStatus() {
        return Map.of(
            "available", isAvailable(),
            "toolCount", getTools().size()
        );
    }
}
