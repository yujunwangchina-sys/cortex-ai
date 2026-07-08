package com.cortex.plugin.service;

import com.cortex.plugin.domain.vo.BuiltinPluginInfo;

import java.util.List;

/**
 * 内置插件扫描服务接口
 * 
 * @author cortex
 */
public interface IBuiltinPluginScanService {
    
    /**
     * 扫描所有内置插件
     * 
     * @return 内置插件信息列表
     */
    List<BuiltinPluginInfo> scanBuiltinPlugins();
    
    /**
     * 加载内置插件到插件列表
     * 
     * @param builtinClass 插件类名
     * @param pluginName 插件名称（可选，用于自定义名称）
     * @return 插件ID
     */
    Long loadBuiltinPlugin(String builtinClass, String pluginName);
    
    /**
     * 卸载内置插件（从插件列表移除）
     * 
     * @param builtinClass 插件类名
     * @return 是否成功
     */
    boolean unloadBuiltinPlugin(String builtinClass);
}
