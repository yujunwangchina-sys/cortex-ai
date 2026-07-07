package com.ruoyi.knowledge.rag;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.supplier.domain.AiModel;
import com.ruoyi.supplier.domain.AiSupplier;
import com.ruoyi.supplier.mapper.AiModelMapper;
import com.ruoyi.supplier.mapper.AiSupplierMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rerank模型工厂
 * 根据ai_model表配置构建重排序模型客户端，按modelId缓存。
 * 兼容Cohere/Jina/SiliconFlow等 /v1/rerank 接口。
 *
 * @author ruoyi
 */
@Component
public class RerankModelFactory
{
    private static final Logger log = LoggerFactory.getLogger(RerankModelFactory.class);

    /** 单个文档截断长度(字符)，避免超出rerank模型token限制 */
    private static final int MAX_DOC_CHARS = 2000;

    @Autowired
    private AiModelMapper modelMapper;

    @Autowired
    private AiSupplierMapper supplierMapper;

    private final ConcurrentHashMap<Long, RerankModel> cache = new ConcurrentHashMap<>();

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 获取Rerank模型（带缓存）
     */
    public RerankModel getModel(Long modelId)
    {
        return cache.computeIfAbsent(modelId, this::buildModel);
    }

    /**
     * 清除缓存（模型配置变更时调用）
     */
    public void evict(Long modelId)
    {
        cache.remove(modelId);
    }

    private RerankModel buildModel(Long modelId)
    {
        AiModel model = modelMapper.selectAiModelByModelId(modelId);
        if (model == null)
        {
            throw new RuntimeException("Rerank模型不存在: modelId=" + modelId);
        }

        AiSupplier supplier = supplierMapper.selectAiSupplierBySupplierId(model.getSupplierId());
        if (supplier == null)
        {
            throw new RuntimeException("供应商不存在: supplierId=" + model.getSupplierId());
        }

        String baseUrl = normalizeBaseUrl(supplier.getApiBaseUrl());
        if (baseUrl == null || baseUrl.isEmpty())
        {
            throw new RuntimeException("供应商API地址未配置: supplierId=" + model.getSupplierId());
        }
        
        String endpoint = baseUrl + "/rerank";

        log.info("构建Rerank模型 [model={}, supplier={}, endpoint={}]",
                model.getModelCode(), supplier.getSupplierName(), endpoint);

        return new OpenAiCompatibleRerankModel(endpoint, supplier.getApiKey(), model.getModelCode());
    }

    private String normalizeBaseUrl(String url)
    {
        if (url == null || url.isEmpty())
        {
            return url;
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * Rerank结果
     */
    public static class RerankResult
    {
        private final int index;
        private final double score;

        public RerankResult(int index, double score)
        {
            this.index = index;
            this.score = score;
        }

        public int getIndex() { return index; }
        public double getScore() { return score; }
    }

    /**
     * Rerank模型接口
     */
    public interface RerankModel
    {
        /**
         * 对文档列表重排序
         *
         * @param query     查询文本
         * @param documents 文档列表
         * @param topN      返回前N个
         * @return 按相关性降序排列的结果
         */
        List<RerankResult> rerank(String query, List<String> documents, int topN);
    }

    /**
     * OpenAI兼容的Rerank实现(Cohere/Jina/SiliconFlow等)
     * 优先尝试 /rerank 端点，失败则回退到 /v1/chat/completions（用LLM做重排序）
     * 
     * /rerank 端点:
     * Request:  {"model":"xxx", "query":"xxx", "documents":["d1","d2"], "top_n":5}
     * Response: {"results":[{"index":0,"relevance_score":0.95}, ...]}
     * 
     * /v1/chat/completions 回退:
     * 使用LLM对文档进行相关性评分
     */
    private static class OpenAiCompatibleRerankModel implements RerankModel
    {
        private final String rerankEndpoint;
        private final String chatEndpoint;
        private final String apiKey;
        private final String modelName;
        private boolean rerankEndpointFailed = false;

        OpenAiCompatibleRerankModel(String rerankEndpoint, String apiKey, String modelName)
        {
            this.rerankEndpoint = rerankEndpoint;
            this.apiKey = apiKey;
            this.modelName = modelName;
            
            // 从 rerank 端点推导 chat completions 端点
            // 情况1: https://api.example.com/rerank -> https://api.example.com/v1/chat/completions
            // 情况2: https://api.example.com/v1/rerank -> https://api.example.com/v1/chat/completions
            // 情况3: https://api.example.com/model/v1/rerank -> https://api.example.com/model/v1/chat/completions
            if (rerankEndpoint != null && rerankEndpoint.contains("/rerank"))
            {
                String baseUrl = rerankEndpoint.substring(0, rerankEndpoint.lastIndexOf("/rerank"));
                
                // 如果baseUrl已经包含/v1，直接拼接
                if (baseUrl.endsWith("/v1") || baseUrl.contains("/v1/") || baseUrl.endsWith("/model/v1"))
                {
                    this.chatEndpoint = baseUrl + "/chat/completions";
                }
                else
                {
                    // 否则需要添加/v1
                    this.chatEndpoint = baseUrl + "/v1/chat/completions";
                }
                
                log.debug("推导Chat端点: rerank={} -> chat={}", rerankEndpoint, chatEndpoint);
            }
            else
            {
                // 如果端点格式不符合预期，设置为null，回退方案将失败并记录错误
                this.chatEndpoint = null;
                log.warn("Rerank端点格式异常，无法推导Chat Completions端点: {}", rerankEndpoint);
            }
        }

        @Override
        public List<RerankResult> rerank(String query, List<String> documents, int topN)
        {
            if (documents == null || documents.isEmpty())
            {
                return new ArrayList<>();
            }

            // 截断过长的文档
            List<String> truncatedDocs = new ArrayList<>(documents.size());
            for (String doc : documents)
            {
                if (doc != null && doc.length() > MAX_DOC_CHARS)
                {
                    truncatedDocs.add(doc.substring(0, MAX_DOC_CHARS));
                }
                else
                {
                    truncatedDocs.add(doc != null ? doc : "");
                }
            }

            // 如果rerank端点之前没失败过，先尝试
            if (!rerankEndpointFailed)
            {
                try
                {
                    return rerankWithDedicatedEndpoint(query, truncatedDocs, topN);
                }
                catch (Exception e)
                {
                    log.warn("Rerank专用端点失败，尝试回退到Chat Completions: {}", e.getMessage());
                    rerankEndpointFailed = true;
                }
            }

            // 回退到使用LLM进行重排序
            return rerankWithChatCompletion(query, truncatedDocs, topN);
        }

        /**
         * 使用专用 /rerank 端点
         */
        private List<RerankResult> rerankWithDedicatedEndpoint(String query, List<String> documents, int topN) throws Exception
        {
            JSONObject body = new JSONObject();
            body.put("model", modelName);
            body.put("query", query);
            body.put("documents", documents);
            body.put("top_n", Math.min(topN, documents.size()));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(rerankEndpoint))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 404)
            {
                throw new RuntimeException("Rerank端点不存在(404)");
            }

            if (response.statusCode() != 200)
            {
                throw new RuntimeException("Rerank API返回错误: " + response.statusCode()
                        + " " + (response.body() != null && response.body().length() > 500
                        ? response.body().substring(0, 500) : response.body()));
            }

            JSONObject resp = JSON.parseObject(response.body());
            JSONArray results = resp.getJSONArray("results");
            if (results == null)
            {
                throw new RuntimeException("Rerank API响应缺少results字段");
            }

            List<RerankResult> rerankResults = new ArrayList<>();
            for (int i = 0; i < results.size(); i++)
            {
                JSONObject r = results.getJSONObject(i);
                int index = r.getIntValue("index");
                double score = r.getDoubleValue("relevance_score");
                rerankResults.add(new RerankResult(index, score));
            }

            log.info("使用Rerank专用端点成功，返回{}个结果", rerankResults.size());
            return rerankResults;
        }

        /**
         * 回退方案：使用Chat Completions让LLM对文档进行相关性评分
         */
        private List<RerankResult> rerankWithChatCompletion(String query, List<String> documents, int topN)
        {
            if (chatEndpoint == null)
            {
                throw new RuntimeException("无法使用Chat Completions回退方案：端点未配置");
            }
            
            try
            {
                // 构建简化的prompt，要求模型只返回JSON
                // 注意：为了避免prompt过长，每个文档最多保留200字符
                StringBuilder prompt = new StringBuilder();
                prompt.append("评分任务：为文档与查询的相关性打分(0-100)。\n\n");
                prompt.append("查询: ").append(query).append("\n\n");
                prompt.append("文档:\n");
                
                int docCount = Math.min(documents.size(), 10); // 最多处理10个文档避免超长
                for (int i = 0; i < docCount; i++)
                {
                    String doc = documents.get(i);
                    if (doc.length() > 200) {
                        doc = doc.substring(0, 200) + "...";
                    }
                    prompt.append(i).append(": ").append(doc).append("\n");
                }
                
                prompt.append("\n直接返回JSON，格式:\n");
                prompt.append("{\"scores\": [{\"index\": 0, \"score\": 85}, {\"index\": 1, \"score\": 60}]}");

                JSONObject body = new JSONObject();
                body.put("model", modelName);
                
                JSONArray messages = new JSONArray();
                JSONObject systemMsg = new JSONObject();
                systemMsg.put("role", "system");
                systemMsg.put("content", "你是评分助手，只返回JSON，不解释。");
                messages.add(systemMsg);
                
                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");
                userMsg.put("content", prompt.toString());
                messages.add(userMsg);
                
                body.put("messages", messages);
                body.put("temperature", 0.1);
                body.put("max_tokens", 2000); // 增加输出长度限制
                
                // 关闭深度思考功能（适用于DeepSeek等支持该功能的模型）
                // 注意：不支持的模型会忽略这些参数
                body.put("reasoning_content", "disabled");  // DeepSeek R1系列

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(chatEndpoint))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .timeout(Duration.ofSeconds(60))
                        .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString()))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200)
                {
                    throw new RuntimeException("Chat API返回错误: " + response.statusCode()
                            + " " + (response.body() != null && response.body().length() > 500
                            ? response.body().substring(0, 500) : response.body()));
                }

                JSONObject resp = JSON.parseObject(response.body());
                JSONArray choices = resp.getJSONArray("choices");
                if (choices == null || choices.isEmpty())
                {
                    throw new RuntimeException("Chat API响应缺少choices字段");
                }

                String content = choices.getJSONObject(0).getJSONObject("message").getString("content");
                
                // 检查finish_reason，如果是length说明输出被截断
                String finishReason = choices.getJSONObject(0).getString("finish_reason");
                if ("length".equals(finishReason))
                {
                    log.warn("模型输出达到长度限制被截断，可能无法获取完整JSON响应");
                }
                
                // 尝试提取JSON部分（处理模型可能返回的额外文本）
                String jsonContent = extractJson(content);
                
                if (jsonContent == null)
                {
                    log.warn("无法从响应中提取JSON，响应内容: {}", content.length() > 200 ? content.substring(0, 200) : content);
                    
                    // 回退方案：如果模型返回的是思考过程但没有JSON，说明它认为文档不相关
                    // 给所有文档一个基础分数，保持原始顺序
                    log.info("使用回退评分策略：保持原始向量检索顺序");
                    List<RerankResult> fallbackResults = new ArrayList<>();
                    for (int i = 0; i < documents.size(); i++)
                    {
                        // 分数从高到低递减，保持原始顺序
                        double score = 1.0 - (i * 0.01); // 1.0, 0.99, 0.98, ...
                        fallbackResults.add(new RerankResult(i, score));
                    }
                    return fallbackResults.subList(0, Math.min(topN, fallbackResults.size()));
                }
                
                // 解析JSON响应
                JSONObject scoresObj = JSON.parseObject(jsonContent);
                JSONArray scores = scoresObj.getJSONArray("scores");
                
                if (scores == null || scores.isEmpty())
                {
                    throw new RuntimeException("JSON响应中缺少scores数组");
                }
                
                List<RerankResult> rerankResults = new ArrayList<>();
                for (int i = 0; i < scores.size(); i++)
                {
                    JSONObject scoreItem = scores.getJSONObject(i);
                    int index = scoreItem.getIntValue("index");
                    double score = scoreItem.getDoubleValue("score") / 100.0; // 归一化到0-1
                    rerankResults.add(new RerankResult(index, score));
                }

                // 按分数降序排序
                rerankResults.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
                
                // 只返回topN个
                if (rerankResults.size() > topN)
                {
                    rerankResults = rerankResults.subList(0, topN);
                }

                log.info("使用Chat Completions回退方案成功，返回{}个结果", rerankResults.size());
                return rerankResults;
            }
            catch (Exception e)
            {
                log.error("Chat Completions回退方案也失败: {}", e.getMessage(), e);
                throw new RuntimeException("重排序失败(专用端点和回退方案均失败): " + e.getMessage(), e);
            }
        }
        
        /**
         * 从响应文本中提取JSON部分
         * 处理模型可能返回的 <think>...</think> 或其他额外文本
         */
        private String extractJson(String content)
        {
            if (content == null || content.trim().isEmpty())
            {
                return null;
            }
            
            content = content.trim();
            
            // 如果内容以 { 开头，直接尝试解析
            if (content.startsWith("{"))
            {
                return content;
            }
            
            // 尝试查找JSON部分 (查找 { 到 } 的完整JSON对象)
            int jsonStart = content.indexOf("{");
            if (jsonStart != -1)
            {
                // 从后往前找最后一个}
                int jsonEnd = content.lastIndexOf("}");
                if (jsonEnd > jsonStart)
                {
                    return content.substring(jsonStart, jsonEnd + 1);
                }
            }
            
            // 尝试查找被```包围的JSON
            if (content.contains("```json"))
            {
                int start = content.indexOf("```json") + 7;
                int end = content.indexOf("```", start);
                if (end > start)
                {
                    return content.substring(start, end).trim();
                }
            }
            
            if (content.contains("```"))
            {
                int start = content.indexOf("```") + 3;
                int end = content.indexOf("```", start);
                if (end > start)
                {
                    String block = content.substring(start, end).trim();
                    if (block.startsWith("{"))
                    {
                        return block;
                    }
                }
            }
            
            return null;
        }
    }
}
