package com.ruoyi.plugin.service;

import java.util.List;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.plugin.domain.AiPluginTool;

/**
 * AI插件工具Service接口
 * 
 * @author ruoyi
 */
public interface IAiPluginToolService 
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
     * 根据工具名称查询工具
     * @param pluginName
     * @return
     */
    public List<AiPluginTool> selectAiPluginToolListByPluginName(String pluginName);

    /**
     * 根据插件ID查询工具列表
     * 
     * @param pluginId 插件ID
     * @return AI插件工具集合
     */
    public List<AiPluginTool> selectAiPluginToolListByPluginId(Long pluginId);

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
     * 批量删除AI插件工具
     * 
     * @param toolIds 需要删除的AI插件工具主键集合
     * @return 结果
     */
    public int deleteAiPluginToolByToolIds(Long[] toolIds);

    /**
     * 删除AI插件工具信息
     * 
     * @param toolId AI插件工具主键
     * @return 结果
     */
    public int deleteAiPluginToolByToolId(Long toolId);

    /**
     * 执行工具（核心方法）
     * 
     * @param sessionId 会话ID
     * @param pluginName 插件名称
     * @param toolName 工具名称
     * @param params 参数
     * @return 执行结果
     */
    public JSONObject executeTool(String sessionId, String pluginName, String toolName, JSONObject params);
}
