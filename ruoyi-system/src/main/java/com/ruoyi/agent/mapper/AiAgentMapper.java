package com.ruoyi.agent.mapper;

import java.util.List;
import com.ruoyi.agent.domain.AiAgent;

/**
 * AI AgentMapper接口
 * 
 * @author ruoyi
 */
public interface AiAgentMapper 
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
     * 删除AI Agent
     * 
     * @param id AI Agent主键
     * @return 结果
     */
    public int deleteAiAgentById(Long id);

    /**
     * 批量删除AI Agent
     * 
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteAiAgentByIds(Long[] ids);

    /**
     * 检查Agent编码是否唯一
     * 
     * @param agentCode Agent编码
     * @return 结果
     */
    public AiAgent checkAgentCodeUnique(String agentCode);

    /**
     * 根据编码查询AI Agent
     * 
     * @param agentCode Agent编码
     * @return AI Agent
     */
    public AiAgent selectAiAgentByCode(String agentCode);


    /**
     * 获取Agent信息
     * @param apiKey
     * @return
     */
    AiAgent selectAiAgentByApiKey(String apiKey);
}
