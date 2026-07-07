package com.ruoyi.knowledge.rag;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.knowledge.domain.AiAgentKnowledge;
import com.ruoyi.knowledge.mapper.AiAgentKnowledgeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 知识库上下文注入器
 * 在对话开始前，自动检索授权知识库(auto模式)，将结果注入到system prompt。
 *
 * @author ruoyi
 */
@Component
public class KnowledgeContextInjector
{
    private static final Logger log = LoggerFactory.getLogger(KnowledgeContextInjector.class);

    @Autowired
    private KnowledgeSearchService searchService;

    @Autowired
    private AiAgentKnowledgeMapper agentKbMapper;

    /**
     * 为Agent构建知识库检索上下文
     *
     * @param agentId     Agent ID
     * @param userMessage 用户消息
     * @return 检索上下文文本(null=无知识库或无结果)
     */
    public String inject(Long agentId, String userMessage)
    {
        if (agentId == null || userMessage == null || userMessage.trim().isEmpty())
        {
            return null;
        }

        try
        {
            // 检查是否有auto模式的知识库授权
            List<AiAgentKnowledge> grants = agentKbMapper.selectActiveByAgentId(agentId);
            if (grants == null || grants.isEmpty())
            {
                return null;
            }

            boolean hasAutoMode = false;
            for (AiAgentKnowledge g : grants)
            {
                if ("auto".equals(g.getRetrievalMode()))
                {
                    hasAutoMode = true;
                    break;
                }
            }

            if (!hasAutoMode)
            {
                return null;
            }

            // 检索
            List<SearchResult> results = searchService.searchForAgent(agentId, userMessage, "auto");
            if (results == null || results.isEmpty())
            {
                log.info("知识库检索无结果 [agentId={}]", agentId);
                return null;
            }

            // 构建上下文
            StringBuilder sb = new StringBuilder();
            sb.append("\n\n---\n\n## 知识库检索结果\n\n");
            sb.append("以下是从知识库检索到的与用户问题最相关的资料，");
            sb.append("请在回答时参考这些内容，并在回答末尾标注引用来源：\n\n");

            for (int i = 0; i < results.size(); i++)
            {
                SearchResult sr = results.get(i);
                sb.append("[").append(i + 1).append("] ");
                if (sr.getDocumentName() != null)
                {
                    sb.append("来源: ").append(sr.getDocumentName());
                    if (sr.getDocCategory() != null)
                    {
                        sb.append(" (").append(sr.getDocCategory()).append(")");
                    }
                    sb.append("\n");
                }
                String displayContent = sr.getContent();
                if (sr.getImagePath() != null)
                {
                    try
                    {
                        List<String> imgPaths = JSON.parseArray(sr.getImagePath(), String.class);
                        if (imgPaths != null)
                        {
                            displayContent = displayContent.replaceAll("\\n\\[img\\d+:desc\\][^\\n]*", "");
                            for (int pi = 0; pi < imgPaths.size(); pi++)
                            {
                                String marker = "[img" + String.format("%03d", pi + 1) + "]";
                                String markdownImg = "![图片" + (pi + 1) + "](/profile/" + imgPaths.get(pi) + ")";
                                displayContent = displayContent.replace(marker, markdownImg);
                            }
                        }
                    }
                    catch (Exception e)
                    {
                        // fallback: keep original content
                    }
                }
                sb.append(displayContent).append("\n\n");
            }

            sb.append("> 请在回答中使用 [1] [2] 等标注引用来源。\n");

            log.info("知识库上下文注入 [agentId={}, results={}]", agentId, results.size());
            return sb.toString();
        }
        catch (Exception e)
        {
            log.error("知识库上下文注入失败 [agentId={}]", agentId, e);
            // 失败时返回null，不影响对话继续
            return null;
        }
    }
}
