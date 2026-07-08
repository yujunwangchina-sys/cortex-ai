package com.cortex.agent.service;

import java.util.List;
import com.cortex.agent.domain.AiAgent;

/**
 * AI AgentService接口
 * 
 * @author cortex
 */
public interface IAiAgentService 
{
    /**
     * 查询AI Agent
     * 
     * @param id AI Agent主键
     * @return AI Agent
     */
    public AiAgent selectAiAgentById(Long id);

    /**
     * 查询AI Agent列表
     * 
     * @param aiAgent AI Agent
     * @return AI Agent集合
     */
    public List<AiAgent> selectAiAgentList(AiAgent aiAgent);

    /**
     * 新增AI Agent
     * 
     * @param aiAgent AI Agent
     * @return 结果
     */
    public int insertAiAgent(AiAgent aiAgent);

    /**
     * 修改AI Agent
     * 
     * @param aiAgent AI Agent
     * @return 结果
     */
    public int updateAiAgent(AiAgent aiAgent);

    /**
     * 批量删除AI Agent
     * 
     * @param ids 需要删除的AI Agent主键集合
     * @return 结果
     */
    public int deleteAiAgentByIds(Long[] ids);

    /**
     * 删除AI Agent信息
     * 
     * @param id AI Agent主键
     * @return 结果
     */
    public int deleteAiAgentById(Long id);

    /**
     * 检查Agent编码是否唯一
     * 
     * @param agentCode Agent编码
     * @return true唯一 false不唯一
     */
    public boolean checkAgentCodeUnique(String agentCode);

    /**
     * 保存Agent的Skill权限
     * 
     * @param agentId Agent ID
     * @param skillIds Skill ID列表
     * @return 结果
     */
    public int saveAgentSkills(Long agentId, List<Long> skillIds);

    /**
     * 保存Agent的插件权限
     * 
     * @param agentId Agent ID
     * @param pluginIds 插件ID列表
     * @return 结果
     */
    public int saveAgentPlugins(Long agentId, List<Long> pluginIds);

    /** 保存Agent的委派授权(可调用哪些其他Agent) */
    public int saveAgentDelegations(Long agentId, List<Long> delegateAgentIds);

    /** 查询Agent可委派的Agent ID列表 */
    public List<Long> selectDelegateAgentIds(Long agentId);

    /**
     * 获取Agent的Skill ID列表
     * 
     * @param agentId Agent ID
     * @return Skill ID列表
     */
    public List<Long> getAgentSkillIds(Long agentId);

    /**
     * 获取Agent的插件ID列表
     * 
     * @param agentId Agent ID
     * @return 插件ID列表
     */
    public List<Long> getAgentPluginIds(Long agentId);

    /**
     * 验证 Agent API Key
     * 
     * @param agentCode Agent编码
     * @param apiKey API密钥
     * @param businessSystem 业务系统标识
     * @return 验证是否通过
     */
    public boolean validateApiKey(String agentCode, String apiKey, String businessSystem);

    /**
     * 验证 Agent API Key
     *
     * @param apiKey API密钥
     * @return 验证是否通过
     */
    public AiAgent validateApiKey(String apiKey);

    /**
     * 根据编码查询 Agent
     * 
     * @param agentCode Agent编码
     * @return Agent
     */
    public AiAgent selectAiAgentByCode(String agentCode);
}
