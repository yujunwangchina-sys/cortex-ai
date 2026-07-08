package com.cortex.plugin.service;

import java.util.List;
import com.cortex.plugin.domain.AiPlugin;

/**
 * AI插件Service接口
 * 
 * @author cortex
 */
public interface IAiPluginService 
{
    /**
     * 查询AI插件
     * 
     * @param pluginId AI插件主键
     * @return AI插件
     */
    public AiPlugin selectAiPluginByPluginId(Long pluginId);

    /**
     * 根据插件名称查询AI插件
     * 
     * @param pluginName 插件名称
     * @return AI插件
     */
    public AiPlugin selectAiPluginByPluginName(String pluginName);

    /**
     * 查询AI插件列表
     * 
     * @param aiPlugin AI插件
     * @return AI插件集合
     */
    public List<AiPlugin> selectAiPluginList(AiPlugin aiPlugin);

    /**
     * 查询已启用的插件列表
     * 
     * @return AI插件集合
     */
    public List<AiPlugin> selectEnabledPluginList();

    /**
     * 新增AI插件
     * 
     * @param aiPlugin AI插件
     * @return 结果
     */
    public int insertAiPlugin(AiPlugin aiPlugin);

    /**
     * 修改AI插件
     * 
     * @param aiPlugin AI插件
     * @return 结果
     */
    public int updateAiPlugin(AiPlugin aiPlugin);

    /**
     * 批量删除AI插件
     * 
     * @param pluginIds 需要删除的AI插件主键集合
     * @return 结果
     */
    public int deleteAiPluginByPluginIds(Long[] pluginIds);

    /**
     * 删除AI插件信息
     * 
     * @param pluginId AI插件主键
     * @return 结果
     */
    public int deleteAiPluginByPluginId(Long pluginId);

    /**
     * 测试插件连接
     * 
     * @param aiPlugin AI插件
     * @return 测试结果
     */
    public String testConnection(AiPlugin aiPlugin);

    /**
     * 启动MCP插件
     * 
     * @param pluginName 插件名称
     * @return 结果
     */
    public String startMcpPlugin(String pluginName);

    /**
     * 停止MCP插件
     * 
     * @param pluginName 插件名称
     * @return 结果
     */
    public String stopMcpPlugin(String pluginName);

    /**
     * 同步MCP插件工具列表
     * 
     * @param pluginName 插件名称
     * @return 结果
     */
    public String syncPluginTools(String pluginName);

    /**
     * 获取MCP插件运行日志
     * 
     * @param pluginName 插件名称
     * @param maxLines 最大行数
     * @return 日志列表
     */
    public List<String> getMcpPluginLogs(String pluginName, int maxLines);
}
