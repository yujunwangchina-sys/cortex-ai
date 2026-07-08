package com.cortex.agent.mapper;

import java.util.List;
import com.cortex.agent.domain.AiAgentPlugin;

/**
 * Agent-插件关联Mapper接口
 * 
 * @author cortex
 */
public interface AiAgentPluginMapper 
{
    /**
     * 查询Agent关联的插件ID列表
     * 
     * @param agentId Agent ID
     * @return 插件ID列表
     */
    public List<Long> selectPluginIdsByAgentId(Long agentId);

    /**
     * 批量新增Agent-插件关联
     * 
     * @param list 关联列表
     * @return 结果
     */
    public int batchInsertAgentPlugin(List<AiAgentPlugin> list);

    /**
     * 删除Agent的所有插件关联
     * 
     * @param agentId Agent ID
     * @return 结果
     */
    public int deleteAgentPluginByAgentId(Long agentId);
}
