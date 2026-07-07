package com.ruoyi.agent.mapper;

import java.util.List;
import com.ruoyi.agent.domain.AiAgentSkill;

/**
 * Agent-Skill关联Mapper接口
 * 
 * @author ruoyi
 */
public interface AiAgentSkillMapper 
{
    /**
     * 查询Agent关联的Skill ID列表
     * 
     * @param agentId Agent ID
     * @return Skill ID列表
     */
    public List<Long> selectSkillIdsByAgentId(Long agentId);

    /**
     * 批量新增Agent-Skill关联
     * 
     * @param list 关联列表
     * @return 结果
     */
    public int batchInsertAgentSkill(List<AiAgentSkill> list);

    /**
     * 删除Agent的所有Skill关联
     * 
     * @param agentId Agent ID
     * @return 结果
     */
    public int deleteAgentSkillByAgentId(Long agentId);
}
