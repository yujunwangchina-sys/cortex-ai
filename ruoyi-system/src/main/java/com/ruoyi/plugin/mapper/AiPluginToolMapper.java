package com.ruoyi.plugin.mapper;

import java.util.List;
import com.ruoyi.plugin.domain.AiPluginTool;

/**
 * AI插件工具Mapper接口
 * 
 * @author ruoyi
 */
public interface AiPluginToolMapper 
{
    /**
     * 查询AI插件工具
     * 
     * @param toolId AI插件工具主键
     * @return AI插件工具
     */
    public AiPluginTool selectAiPluginToolByToolId(Long toolId);

    /**
     * 查询AI插件工具列表
     * 
     * @param aiPluginTool AI插件工具
     * @return AI插件工具集合
     */
    public List<AiPluginTool> selectAiPluginToolList(AiPluginTool aiPluginTool);

    /**
     * 根据插件ID查询工具列表
     * 
     * @param pluginId 插件ID
     * @return AI插件工具集合
     */
    public List<AiPluginTool> selectAiPluginToolListByPluginId(Long pluginId);

    /**
     * 根据插件名称查询工具列表
     * 
     * @param pluginName 插件名称
     * @return AI插件工具集合
     */
    public List<AiPluginTool> selectAiPluginToolListByPluginName(String pluginName);

    /**
     * 新增AI插件工具
     * 
     * @param aiPluginTool AI插件工具
     * @return 结果
     */
    public int insertAiPluginTool(AiPluginTool aiPluginTool);

    /**
     * 修改AI插件工具
     * 
     * @param aiPluginTool AI插件工具
     * @return 结果
     */
    public int updateAiPluginTool(AiPluginTool aiPluginTool);

    /**
     * 删除AI插件工具
     * 
     * @param toolId AI插件工具主键
     * @return 结果
     */
    public int deleteAiPluginToolByToolId(Long toolId);

    /**
     * 批量删除AI插件工具
     * 
     * @param toolIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteAiPluginToolByToolIds(Long[] toolIds);

    /**
     * 根据插件ID删除工具
     * 
     * @param pluginId 插件ID
     * @return 结果
     */
    public int deleteAiPluginToolByPluginId(Long pluginId);
}
