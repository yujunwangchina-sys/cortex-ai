package com.cortex.plugin.mapper;

import java.util.List;
import com.cortex.plugin.domain.AiPlugin;

/**
 * AI插件Mapper接口
 * 
 * @author cortex
 */
public interface AiPluginMapper 
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
     * 根据插件类型查询插件列表
     * 
     * @param pluginType 插件类型
     * @return AI插件集合
     */
    public List<AiPlugin> selectPluginListByType(String pluginType);

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
     * 删除AI插件
     * 
     * @param pluginId AI插件主键
     * @return 结果
     */
    public int deleteAiPluginByPluginId(Long pluginId);

    /**
     * 批量删除AI插件
     * 
     * @param pluginIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteAiPluginByPluginIds(Long[] pluginIds);

    /**
     * 校验插件名称是否唯一
     * 
     * @param pluginName 插件名称
     * @return 插件信息
     */
    public AiPlugin checkPluginNameUnique(String pluginName);
    
    /**
     * 统计插件总数
     * 
     * @return 插件数量
     */
    public int countPlugins();
    
    /**
     * 统计上一周期的插件数
     * 
     * @return 插件数量
     */
    public int countPluginsLastPeriod();
    
    /**
     * 按日期统计插件数
     * 
     * @param date 日期
     * @return 插件数量
     */
    public int countPluginsByDate(String date);
}
