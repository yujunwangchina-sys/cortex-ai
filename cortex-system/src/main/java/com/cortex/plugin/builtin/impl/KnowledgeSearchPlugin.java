package com.cortex.plugin.builtin.impl;

import com.alibaba.fastjson2.JSON;
import com.cortex.agent.runtime.context.RuntimeContextHolder;
import com.cortex.knowledge.rag.KnowledgeSearchService;
import com.cortex.knowledge.rag.SearchResult;
import com.cortex.plugin.builtin.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 知识库检索内置插件（tool模式）
 * 
 * 允许 Agent 主动调用知识库检索，而不是自动注入。
 * 适用场景：
 * - Agent 需要根据对话上下文决定是否检索
 * - 需要针对性地检索特定主题
 * - 减少无关知识库内容的注入
 * 
 * @author cortex
 */
@Component
public class KnowledgeSearchPlugin implements IBuiltinPlugin
{
    private static final Logger log = LoggerFactory.getLogger(KnowledgeSearchPlugin.class);

    @Autowired
    private KnowledgeSearchService searchService;

    @Override
    public PluginInfo getPluginInfo()
    {
        PluginInfo info = new PluginInfo(
            "知识库检索",
            "knowledge-search",
            "在已授权的知识库中检索相关文档片段，获取专业知识和参考资料"
        );
        info.setVersion("1.0.0");
        info.setAuthor("CORTEX");
        info.setCategory("knowledge");
        info.setEmoji("📚");
        info.setRequireApproval(false);
        return info;
    }

    @Override
    public List<ToolDefinition> getTools()
    {
        List<ToolDefinition> tools = new ArrayList<>();

        ToolDefinition search = new ToolDefinition();
        search.setName("knowledge_search");
        search.setDescription(
            "在知识库中搜索与问题相关的文档片段。适用于：\n" +
            "- 查询操作规范、SOP文档、技术标准\n" +
            "- 获取专业领域知识（如财务制度、法规政策）\n" +
            "- 查找历史案例和最佳实践\n" +
            "- 获取产品文档和用户手册内容\n\n" +
            "注意：只能检索已授权给当前Agent的知识库。"
        );

        // 输入参数Schema
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new HashMap<>();
        
        properties.put("query", Map.of(
            "type", "string",
            "description", "搜索查询内容，用清晰的自然语言描述要查找的信息"
        ));
        
        properties.put("kb_id", Map.of(
            "type", "integer",
            "description", "指定要搜索的知识库ID（可选）。不指定则在所有授权知识库中搜索"
        ));
        
        properties.put("top_k", Map.of(
            "type", "integer",
            "description", "返回结果数量，默认5条，最多10条",
            "default", 5
        ));
        
        properties.put("min_score", Map.of(
            "type", "number",
            "description", "最小相似度阈值(0-1)，默认0.5。低于此分数的结果不返回",
            "default", 0.5
        ));

        schema.put("properties", properties);
        schema.put("required", List.of("query"));

        search.setInputSchema(schema);

        // 示例
        search.setExampleInput("{\"query\": \"如何处理客户投诉\", \"top_k\": 3}");
        search.setExampleOutput("{\"success\": true, \"total\": 3, \"results\": [...]}");

        tools.add(search);
        return tools;
    }

    @Override
    public String executeTool(String toolName, Map<String, Object> arguments)
    {
        try
        {
            if ("knowledge_search".equals(toolName))
            {
                return knowledgeSearch(arguments);
            }
            return ToolResult.error("未知工具: " + toolName).toJson();
        }
        catch (Exception e)
        {
            log.error("知识库检索失败", e);
            return ToolResult.error("检索失败: " + e.getMessage()).toJson();
        }
    }

    /**
     * 执行知识库检索
     */
    private String knowledgeSearch(Map<String, Object> args)
    {
        // 获取当前 Agent ID
        Long agentId = RuntimeContextHolder.getAgentId();
        if (agentId == null)
        {
            return ToolResult.error("无法获取Agent信息").toJson();
        }

        // 解析参数
        String query = (String) args.get("query");
        if (query == null || query.trim().isEmpty())
        {
            return ToolResult.error("query参数不能为空").toJson();
        }

        Object kbIdObj = args.get("kb_id");
        Long kbId = kbIdObj != null ? toLong(kbIdObj) : null;
        
        int topK = toInt(args.getOrDefault("top_k", 5));
        if (topK > 10) topK = 10; // 限制最大数量
        
        double minScore = toDouble(args.getOrDefault("min_score", 0.5));

        try
        {
            List<SearchResult> results;

            if (kbId != null)
            {
                // 检查权限
                if (!searchService.isAgentAuthorized(agentId, kbId))
                {
                    return ToolResult.error("无权访问指定的知识库 (kb_id=" + kbId + ")").toJson();
                }

                // 搜索指定知识库
                results = searchService.search(kbId, query, topK, minScore, null);
            }
            else
            {
                // 搜索所有授权的tool模式知识库
                results = searchService.searchForAgent(agentId, query, "tool");
                
                // 限制数量
                if (results.size() > topK)
                {
                    results = results.subList(0, topK);
                }
            }

            if (results == null || results.isEmpty())
            {
                return ToolResult.success("未找到相关文档")
                    .addData("query", query)
                    .addData("total", 0)
                    .addData("results", List.of())
                    .toJson();
            }

            // 构建结果
            List<Map<String, Object>> resultList = new ArrayList<>();
            for (int i = 0; i < results.size(); i++)
            {
                SearchResult sr = results.get(i);
                Map<String, Object> item = new HashMap<>();
                item.put("index", i + 1);
                String displayContent = sr.getContent();
                if (sr.getImagePath() != null)
                {
                    try
                    {
                        List<String> imgPaths = JSON.parseArray(sr.getImagePath(), String.class);
                        List<String> imgUrls = new ArrayList<>();
                        for (String p : imgPaths)
                        {
                            imgUrls.add("/profile/" + p);
                        }
                        item.put("imagePaths", imgPaths);
                        item.put("imageUrls", imgUrls);
                        displayContent = displayContent.replaceAll("\\n\\[img\\d+:desc\\][^\\n]*", "");
                        for (int j = 0; j < imgPaths.size(); j++)
                        {
                            String marker = "[img" + String.format("%03d", j + 1) + "]";
                            String markdownImg = "![图片" + (j + 1) + "](/profile/" + imgPaths.get(j) + ")";
                            displayContent = displayContent.replace(marker, markdownImg);
                        }
                    }
                    catch (Exception e)
                    {
                        List<String> singlePath = new ArrayList<>();
                        singlePath.add(sr.getImagePath());
                        item.put("imagePaths", singlePath);
                        item.put("imageUrls", List.of("/profile/" + sr.getImagePath()));
                    }
                }
                item.put("content", displayContent);
                item.put("score", sr.getScore());
                item.put("document_name", sr.getDocumentName());
                item.put("doc_category", sr.getDocCategory());
                item.put("doc_tags", sr.getDocTags());
                item.put("chunk_index", sr.getChunkIndex());
                resultList.add(item);
            }

            // 构建引用说明
            StringBuilder citations = new StringBuilder();
            citations.append("\n\n**参考来源**：\n");
            for (int i = 0; i < results.size(); i++)
            {
                SearchResult sr = results.get(i);
                citations.append("- [").append(i + 1).append("] ");
                if (sr.getDocumentName() != null)
                {
                    citations.append("《").append(sr.getDocumentName()).append("》");
                }
                if (sr.getDocCategory() != null)
                {
                    citations.append("（").append(sr.getDocCategory()).append("）");
                }
                citations.append(" 相关度: ").append(sr.getScore());
                citations.append("\n");
            }

            log.info("知识库检索成功 [agentId={}, query='{}', results={}]",
                    agentId, query.length() > 30 ? query.substring(0, 30) + "..." : query, results.size());

            return ToolResult.success("检索成功，找到 " + results.size() + " 条相关文档")
                .addData("query", query)
                .addData("total", results.size())
                .addData("results", resultList)
                .addData("citation_guide", citations.toString())
                .addData("usage_tip", "请基于以上检索结果回答用户问题，并在回答中标注引用来源，如 [1] [2]。如果检索结果包含图片，请在回答中直接使用markdown图片语法引用相关图片。")
                .toJson();
        }
        catch (Exception e)
        {
            log.error("知识库检索异常 [agentId={}, query={}]", agentId, query, e);
            return ToolResult.error("检索异常: " + e.getMessage()).toJson();
        }
    }

    // ==================== 辅助方法 ====================

    private Long toLong(Object obj)
    {
        if (obj == null) return null;
        if (obj instanceof Number) return ((Number) obj).longValue();
        try { return Long.parseLong(obj.toString()); } catch (Exception e) { return null; }
    }

    private int toInt(Object obj)
    {
        if (obj == null) return 0;
        if (obj instanceof Number) return ((Number) obj).intValue();
        try { return Integer.parseInt(obj.toString()); } catch (Exception e) { return 0; }
    }

    private double toDouble(Object obj)
    {
        if (obj == null) return 0.0;
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        try { return Double.parseDouble(obj.toString()); } catch (Exception e) { return 0.0; }
    }
}
