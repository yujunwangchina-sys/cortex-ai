package com.ruoyi.web.controller.knowledge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.knowledge.domain.AiAgentKnowledge;
import com.ruoyi.knowledge.mapper.AiAgentKnowledgeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * Agent知识库授权Controller
 * 管理Agent可访问的知识库及检索模式配置。
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/knowledge/agent")
public class AgentKnowledgeController extends BaseController
{
    @Autowired
    private AiAgentKnowledgeMapper agentKbMapper;

    /**
     * 获取Agent已授权的知识库列表
     */
    @GetMapping("/{agentId}/grants")
    public AjaxResult getGrants(@PathVariable("agentId") Long agentId)
    {
        List<AiAgentKnowledge> grants = agentKbMapper.selectByAgentId(agentId);
        return success(grants);
    }

    /**
     * 保存Agent知识库授权
     * 请求体: [{ "kbId": 1, "retrievalMode": "auto", "metadataFilter": "{}" }, ...]
     */
    @PostMapping("/{agentId}/grants")
    public AjaxResult saveGrants(@PathVariable("agentId") Long agentId,
                                  @RequestBody List<Map<String, Object>> grantList)
    {
        // 先清除旧授权
        agentKbMapper.deleteByAgentId(agentId);

        // 批量插入新授权
        if (grantList != null && !grantList.isEmpty())
        {
            String username = SecurityUtils.getUsername();
            List<AiAgentKnowledge> grants = new ArrayList<>();

            for (Map<String, Object> g : grantList)
            {
                AiAgentKnowledge grant = new AiAgentKnowledge();
                grant.setAgentId(agentId);
                grant.setKbId(Long.valueOf(g.get("kbId").toString()));
                grant.setRetrievalMode(g.containsKey("retrievalMode") ? g.get("retrievalMode").toString() : "auto");
                grant.setMetadataFilter(g.containsKey("metadataFilter") ? g.get("metadataFilter").toString() : null);
                grant.setStatus("0");
                grant.setGrantedBy(username);
                grants.add(grant);
            }

            if (!grants.isEmpty())
            {
                agentKbMapper.batchInsertGrants(grants);
            }
        }

        return success();
    }
}