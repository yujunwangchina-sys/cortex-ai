package com.cortex.agent.service.impl;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cortex.agent.mapper.AiAgentMapper;
import com.cortex.agent.mapper.AiAgentSkillMapper;
import com.cortex.agent.mapper.AiAgentPluginMapper;
import com.cortex.agent.mapper.AiAgentDelegationMapper;
import com.cortex.agent.domain.AiAgentDelegation;
import com.cortex.agent.domain.AiAgent;
import com.cortex.agent.domain.AiAgentSkill;
import com.cortex.agent.domain.AiAgentPlugin;
import com.cortex.agent.service.IAiAgentService;
import com.cortex.common.utils.StringUtils;

/**
 * AI AgentService业务层处理
 * 
 * @author cortex
 */
@Service
public class AiAgentServiceImpl implements IAiAgentService 
{
    @Autowired
    private AiAgentMapper aiAgentMapper;

    @Autowired
    private AiAgentSkillMapper aiAgentSkillMapper;

    @Autowired
    private AiAgentPluginMapper aiAgentPluginMapper;

    @Autowired
    private AiAgentDelegationMapper aiAgentDelegationMapper;

    /**
     * 查询AI Agent
     * 
     * @param id AI Agent主键
     * @return AI Agent
     */
    @Override
    public AiAgent selectAiAgentById(Long id)
    {
        return aiAgentMapper.selectAiAgentById(id);
    }

    /**
     * 查询AI Agent列表
     * 
     * @param aiAgent AI Agent
     * @return AI Agent
     */
    @Override
    public List<AiAgent> selectAiAgentList(AiAgent aiAgent)
    {
        return aiAgentMapper.selectAiAgentList(aiAgent);
    }

    /**
     * 新增AI Agent
     * 
     * @param aiAgent AI Agent
     * @return 结果
     */
    @Override
    @Transactional
    public int insertAiAgent(AiAgent aiAgent)
    {
        return aiAgentMapper.insertAiAgent(aiAgent);
    }

    /**
     * 修改AI Agent
     * 
     * @param aiAgent AI Agent
     * @return 结果
     */
    @Override
    @Transactional
    public int updateAiAgent(AiAgent aiAgent)
    {
        return aiAgentMapper.updateAiAgent(aiAgent);
    }

    /**
     * 批量删除AI Agent
     * 
     * @param ids 需要删除的AI Agent主键
     * @return 结果
     */
    @Override
    @Transactional
    public int deleteAiAgentByIds(Long[] ids)
    {
        // 删除关联的skill和plugin
        for (Long id : ids)
        {
            aiAgentSkillMapper.deleteAgentSkillByAgentId(id);
            aiAgentPluginMapper.deleteAgentPluginByAgentId(id);
        }
        return aiAgentMapper.deleteAiAgentByIds(ids);
    }

    /**
     * 删除AI Agent信息
     * 
     * @param id AI Agent主键
     * @return 结果
     */
    @Override
    @Transactional
    public int deleteAiAgentById(Long id)
    {
        // 删除关联的skill和plugin
        aiAgentSkillMapper.deleteAgentSkillByAgentId(id);
        aiAgentPluginMapper.deleteAgentPluginByAgentId(id);
        return aiAgentMapper.deleteAiAgentById(id);
    }

    /**
     * 检查Agent编码是否唯一
     * 
     * @param agentCode Agent编码
     * @return true唯一 false不唯一
     */
    @Override
    public boolean checkAgentCodeUnique(String agentCode)
    {
        AiAgent agent = aiAgentMapper.checkAgentCodeUnique(agentCode);
        return agent == null;
    }

    /**
     * 保存Agent的Skill权限
     * 
     * @param agentId Agent ID
     * @param skillIds Skill ID列表
     * @return 结果
     */
    @Override
    @Transactional
    public int saveAgentSkills(Long agentId, List<Long> skillIds)
    {
        // 先删除旧的关联
        aiAgentSkillMapper.deleteAgentSkillByAgentId(agentId);
        
        // 插入新的关联
        if (skillIds != null && !skillIds.isEmpty())
        {
            List<AiAgentSkill> list = new ArrayList<>();
            for (Long skillId : skillIds)
            {
                AiAgentSkill agentSkill = new AiAgentSkill();
                agentSkill.setAgentId(agentId);
                agentSkill.setSkillId(skillId);
                list.add(agentSkill);
            }
            return aiAgentSkillMapper.batchInsertAgentSkill(list);
        }
        // 如果没有技能要关联，删除操作已成功，返回1表示操作成功
        return 1;
    }

    /**
     * 保存Agent的插件权限
     * 
     * @param agentId Agent ID
     * @param pluginIds 插件ID列表
     * @return 结果
     */
    @Override
    @Transactional
    public int saveAgentPlugins(Long agentId, List<Long> pluginIds)
    {
        // 先删除旧的关联
        aiAgentPluginMapper.deleteAgentPluginByAgentId(agentId);
        
        // 插入新的关联
        if (pluginIds != null && !pluginIds.isEmpty())
        {
            List<AiAgentPlugin> list = new ArrayList<>();
            for (Long pluginId : pluginIds)
            {
                AiAgentPlugin agentPlugin = new AiAgentPlugin();
                agentPlugin.setAgentId(agentId);
                agentPlugin.setPluginId(pluginId);
                list.add(agentPlugin);
            }
            return aiAgentPluginMapper.batchInsertAgentPlugin(list);
        }
        // 如果没有插件要关联，删除操作已成功，返回1表示操作成功
        return 1;
    }

    @Override
    public int saveAgentDelegations(Long agentId, List<Long> delegateAgentIds)
    {
        aiAgentDelegationMapper.deleteAgentDelegationByAgentId(agentId);
        if (delegateAgentIds != null && !delegateAgentIds.isEmpty())
        {
            List<AiAgentDelegation> list = new ArrayList<>();
            for (Long delegateId : delegateAgentIds)
            {
                AiAgentDelegation delegation = new AiAgentDelegation();
                delegation.setAgentId(agentId);
                delegation.setDelegateAgentId(delegateId);
                list.add(delegation);
            }
            return aiAgentDelegationMapper.batchInsertAgentDelegation(list);
        }
        return 1;
    }

    @Override
    public List<Long> selectDelegateAgentIds(Long agentId)
    {
        return aiAgentDelegationMapper.selectDelegateAgentIdsByAgentId(agentId);
    }

    /**
     * 获取Agent的Skill ID列表
     * 
     * @param agentId Agent ID
     * @return Skill ID列表
     */
    @Override
    public List<Long> getAgentSkillIds(Long agentId)
    {
        return aiAgentSkillMapper.selectSkillIdsByAgentId(agentId);
    }

    /**
     * 获取Agent的插件ID列表
     * 
     * @param agentId Agent ID
     * @return 插件ID列表
     */
    @Override
    public List<Long> getAgentPluginIds(Long agentId)
    {
        return aiAgentPluginMapper.selectPluginIdsByAgentId(agentId);
    }

    /**
     * 验证 Agent API Key
     * 
     * @param agentCode Agent编码
     * @param apiKey API密钥
     * @param businessSystem 业务系统标识
     * @return 验证是否通过
     */
    @Override
    public boolean validateApiKey(String agentCode, String apiKey, String businessSystem)
    {
        if (StringUtils.isEmpty(agentCode) || StringUtils.isEmpty(apiKey))
        {
            return false;
        }
        
        AiAgent agent = aiAgentMapper.selectAiAgentByCode(agentCode);
        if (agent == null)
        {
            return false;
        }
        
        // 验证 API Key
        if (!apiKey.equals(agent.getApiKey()))
        {
            return false;
        }
        
        // 验证业务系统（如果 Agent 指定了业务系统，则必须匹配）
        if (StringUtils.isNotEmpty(agent.getBusinessSystem()) && 
            !agent.getBusinessSystem().equals(businessSystem))
        {
            return false;
        }
        
        // 验证状态
        if (!"0".equals(agent.getStatus()))
        {
            return false;
        }
        
        return true;
    }

    /**
     * 验证key
     * @param apiKey API密钥
     * @return
     */
    @Override
    public AiAgent validateApiKey(String apiKey) {
        return aiAgentMapper.selectAiAgentByApiKey(apiKey);
    }

    /**
     * 根据编码查询 Agent
     * 
     * @param agentCode Agent编码
     * @return Agent
     */
    @Override
    public AiAgent selectAiAgentByCode(String agentCode)
    {
        return aiAgentMapper.selectAiAgentByCode(agentCode);
    }
}
