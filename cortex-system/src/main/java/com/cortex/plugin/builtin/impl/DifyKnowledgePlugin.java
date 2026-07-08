package com.cortex.plugin.builtin.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.cortex.plugin.builtin.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Dify知识库插件
 * 
 * 对接Dify知识库检索API，支持两种模式：
 * 1. 指定 dataset_id → 只搜索该知识库
 * 2. 不指定 ID → 自动获取所有知识库列表，Agent可选择搜索哪个
 * 
 * 配置方式：在插件的环境变量中设置（JSON格式）：
 * {
 *   "DIFY_API_URL": "http://your-dify-host/v1",
 *   "DIFY_API_KEY": "dataset-xxxxxxxx",
 *   "DIFY_DATASET_ID": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"  (可选)
 * }
 * 
 * @author cortex
 */
@Component
public class DifyKnowledgePlugin implements IBuiltinPlugin {
    
    private static final Logger log = LoggerFactory.getLogger(DifyKnowledgePlugin.class);
    
    private HttpClient httpClient;
    
    // 环境变量配置
    private Map<String, String> envVars = new HashMap<>();
    
    /**
     * 设置环境变量（由插件管理器调用）
     */
    public void setEnvVars(Map<String, String> envVars) {
        this.envVars = envVars != null ? envVars : new HashMap<>();
    }
    
    /**
     * 获取环境变量值
     */
    private String getEnv(String key) {
        return envVars.get(key);
    }
    
    @Override
    public PluginInfo getPluginInfo() {
        PluginInfo info = new PluginInfo(
            "Dify知识库",
            "dify-knowledge",
            "对接Dify知识库进行语义检索，查找相关文档片段"
        );
        info.setVersion("1.0.0");
        info.setAuthor("Cortex");
        info.setCategory("web_search");
        info.setEmoji("📚");
        info.setRequireApproval(false);
        return info;
    }
    
    @Override
    public List<ToolDefinition> getTools() {
        List<ToolDefinition> tools = new ArrayList<>();
        tools.add(createKnowledgeSearchTool());
        tools.add(createKnowledgeListTool());
        return tools;
    }
    
    @Override
    public String executeTool(String toolName, Map<String, Object> arguments) {
        try {
            switch (toolName) {
                case "dify_knowledge_search":
                    return knowledgeSearch(arguments);
                case "dify_knowledge_list":
                    return knowledgeList(arguments);
                default:
                    return ToolResult.error("未知工具: " + toolName).toJson();
            }
        } catch (Exception e) {
            log.error("工具执行失败: " + toolName, e);
            return ToolResult.error("执行失败: " + e.getMessage()).toJson();
        }
    }
    
    @Override
    public void initialize() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
        
        log.info("Dify知识库插件初始化完成");
    }
    
    @Override
    public boolean isAvailable() {
        String apiUrl = getEnv("DIFY_API_URL");
        String apiKey = getEnv("DIFY_API_KEY");
        return apiUrl != null && !apiUrl.isEmpty() 
            && apiKey != null && !apiKey.isEmpty();
    }
    
    /**
     * 创建知识库搜索工具定义
     */
    private ToolDefinition createKnowledgeSearchTool() {
        ToolDefinition tool = new ToolDefinition();
        tool.setName("dify_knowledge_search");
        tool.setDescription(
            "在知识库中进行语义检索，查找与问题最相关的文档片段。" +
            "适用于查询操作规范、SOP文档、技术标准、管理制度等非结构化知识。"
        );
        
        // 输入参数Schema
        Map<String, Object> inputSchema = new HashMap<>();
        inputSchema.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        properties.put("query", Map.of(
            "type", "string",
            "description", "搜索查询内容，用自然语言描述你要查找的信息"
        ));
        properties.put("dataset_id", Map.of(
            "type", "string",
            "description", "知识库ID（可选，不填则使用默认配置或自动选择）"
        ));
        properties.put("top_k", Map.of(
            "type", "integer",
            "description", "返回结果数量，默认5条",
            "default", 5
        ));
        properties.put("score_threshold", Map.of(
            "type", "number",
            "description", "相关度阈值(0-1)，低于此分数的结果不返回，默认0.5",
            "default", 0.5
        ));
        
        inputSchema.put("properties", properties);
        inputSchema.put("required", List.of("query"));
        
        tool.setInputSchema(inputSchema);
        
        // 示例
        tool.setExampleInput("{\"query\": \"如何处理客户投诉\", \"top_k\": 3}");
        tool.setExampleOutput("{\"success\": true, \"total\": 3, \"results\": [...]}");
        
        return tool;
    }
    
    /**
     * 创建知识库列表工具定义
     */
    private ToolDefinition createKnowledgeListTool() {
        ToolDefinition tool = new ToolDefinition();
        tool.setName("dify_knowledge_list");
        tool.setDescription("列出所有可用的知识库及其基本信息（名称、文档数、描述等）");
        
        Map<String, Object> inputSchema = new HashMap<>();
        inputSchema.put("type", "object");
        inputSchema.put("properties", new HashMap<>());
        inputSchema.put("required", List.of());
        
        tool.setInputSchema(inputSchema);
        tool.setExampleOutput("{\"success\": true, \"total\": 2, \"datasets\": [...]}");
        
        return tool;
    }
    
    /**
     * 知识库搜索
     */
    private String knowledgeSearch(Map<String, Object> args) {
        // 获取配置
        String apiUrl = getEnv("DIFY_API_URL");
        String apiKey = getEnv("DIFY_API_KEY");
        String defaultDatasetId = getEnv("DIFY_DATASET_ID");
        
        // 参数验证
        if (apiUrl == null || apiUrl.isEmpty() || apiKey == null || apiKey.isEmpty()) {
            return ToolResult.error("未配置Dify API，请在插件环境变量中设置DIFY_API_URL和DIFY_API_KEY").toJson();
        }
        
        String query = (String) args.get("query");
        if (query == null || query.trim().isEmpty()) {
            return ToolResult.error("query参数不能为空").toJson();
        }
        
        String datasetId = (String) args.getOrDefault("dataset_id", defaultDatasetId);
        int topK = (int) args.getOrDefault("top_k", 5);
        double scoreThreshold = ((Number) args.getOrDefault("score_threshold", 0.5)).doubleValue();
        
        try {
            // 如果没有指定datasetId，尝试获取列表
            if (datasetId == null || datasetId.isEmpty()) {
                JSONObject datasetListResult = listDatasets(apiUrl, apiKey);
                JSONArray datasets = datasetListResult.getJSONArray("data");
                
                if (datasets == null || datasets.isEmpty()) {
                    return ToolResult.error("未找到任何可用知识库").toJson();
                }
                
                if (datasets.size() == 1) {
                    // 只有一个知识库，直接使用
                    datasetId = datasets.getJSONObject(0).getString("id");
                } else {
                    // 多个知识库，返回列表让用户选择
                    List<Map<String, Object>> datasetList = new ArrayList<>();
                    for (int i = 0; i < datasets.size(); i++) {
                        JSONObject ds = datasets.getJSONObject(i);
                        Map<String, Object> item = new HashMap<>();
                        item.put("id", ds.getString("id"));
                        item.put("name", ds.getString("name"));
                        item.put("description", ds.getString("description"));
                        item.put("document_count", ds.getInteger("document_count"));
                        datasetList.add(item);
                    }
                    
                    return ToolResult.success("有多个知识库可用，请指定dataset_id参数选择要搜索的知识库")
                        .addData("available_datasets", datasetList)
                        .toJson();
                }
            }
            
            // 执行检索
            JSONObject result = retrieve(apiUrl, apiKey, datasetId, query, topK, scoreThreshold);
            
            if (result.containsKey("error")) {
                return ToolResult.error(result.getString("error")).toJson();
            }
            
            JSONArray records = result.getJSONArray("records");
            
            if (records == null || records.isEmpty()) {
                return ToolResult.success("未找到相关文档")
                    .addData("results", List.of())
                    .addData("query", query)
                    .addData("dataset_id", datasetId)
                    .toJson();
            }
            
            // 处理结果
            List<Map<String, Object>> results = new ArrayList<>();
            for (int i = 0; i < records.size(); i++) {
                JSONObject record = records.getJSONObject(i);
                JSONObject segment = record.getJSONObject("segment");
                JSONObject doc = segment.getJSONObject("document");
                
                Map<String, Object> item = new HashMap<>();
                item.put("ref_index", i + 1);
                item.put("content", segment.getString("content"));
                item.put("score", Math.round(record.getDoubleValue("score") * 1000.0) / 1000.0);
                item.put("document_name", doc.getString("name"));
                item.put("segment_position", segment.getInteger("position"));
                item.put("word_count", segment.getInteger("word_count"));
                item.put("keywords", segment.getList("keywords", String.class));
                
                results.add(item);
            }
            
            // 构造引用清单
            List<Map<String, Object>> citations = new ArrayList<>();
            for (Map<String, Object> r : results) {
                Map<String, Object> citation = new HashMap<>();
                citation.put("ref_index", r.get("ref_index"));
                citation.put("document", r.get("document_name"));
                citation.put("score", r.get("score"));
                citations.add(citation);
            }
            
            return ToolResult.success("检索成功")
                .addData("query", query)
                .addData("dataset_id", datasetId)
                .addData("total", results.size())
                .addData("results", results)
                .addData("citations", citations)
                .addData("citation_instruction", 
                    "请在回答中标注每段信息的来源，格式：[^N] 其中N对应results里的ref_index。" +
                    "回答末尾用以下格式列出引用清单：\n**参考来源**\n- [^1] 《文档名》（相关度 0.xx）")
                .toJson();
                
        } catch (Exception e) {
            log.error("知识库搜索失败", e);
            return ToolResult.error("搜索失败: " + e.getMessage()).toJson();
        }
    }
    
    /**
     * 列出知识库
     */
    private String knowledgeList(Map<String, Object> args) {
        String apiUrl = getEnv("DIFY_API_URL");
        String apiKey = getEnv("DIFY_API_KEY");
        String defaultDatasetId = getEnv("DIFY_DATASET_ID");
        
        if (apiUrl == null || apiUrl.isEmpty() || apiKey == null || apiKey.isEmpty()) {
            return ToolResult.error("未配置Dify API").toJson();
        }
        
        try {
            JSONObject result = listDatasets(apiUrl, apiKey);
            JSONArray data = result.getJSONArray("data");
            
            List<Map<String, Object>> datasets = new ArrayList<>();
            if (data != null) {
                for (int i = 0; i < data.size(); i++) {
                    JSONObject ds = data.getJSONObject(i);
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", ds.getString("id"));
                    item.put("name", ds.getString("name"));
                    item.put("description", ds.getString("description"));
                    item.put("document_count", ds.getInteger("document_count"));
                    item.put("word_count", ds.getInteger("word_count"));
                    item.put("embedding_model", ds.getString("embedding_model"));
                    datasets.add(item);
                }
            }
            
            return ToolResult.success("获取成功")
                .addData("total", datasets.size())
                .addData("datasets", datasets)
                .addData("configured_default", defaultDatasetId != null ? defaultDatasetId : null)
                .toJson();
                
        } catch (Exception e) {
            log.error("获取知识库列表失败", e);
            return ToolResult.error("获取失败: " + e.getMessage()).toJson();
        }
    }
    
    /**
     * 获取知识库列表
     */
    private JSONObject listDatasets(String apiUrl, String apiKey) throws Exception {
        String url = apiUrl.replaceAll("/+$", "") + "/datasets?page=1&limit=100";
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .GET()
            .timeout(Duration.ofSeconds(15))
            .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
        }
        
        return JSON.parseObject(response.body());
    }
    
    /**
     * 执行检索
     */
    private JSONObject retrieve(String apiUrl, String apiKey, String datasetId, String query, int topK, double scoreThreshold) throws Exception {
        String url = apiUrl.replaceAll("/+$", "") + "/datasets/" + datasetId + "/retrieve";
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("query", query);
        payload.put("external_retrieval_model", Map.of(
            "top_k", topK,
            "score_threshold", scoreThreshold,
            "score_threshold_enabled", true
        ));
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(JSON.toJSONString(payload)))
            .timeout(Duration.ofSeconds(30))
            .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            JSONObject error = new JSONObject();
            error.put("error", "HTTP " + response.statusCode() + ": " + response.body().substring(0, Math.min(300, response.body().length())));
            return error;
        }
        
        return JSON.parseObject(response.body());
    }
}
