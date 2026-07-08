package com.cortex.knowledge.rag;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.cortex.knowledge.domain.*;
import com.cortex.knowledge.mapper.*;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.logical.And;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 知识库检索服务
 * 向量化查询 -> Milvus相似度搜索(支持元数据过滤) -> 返回top-k结果
 *
 * @author cortex
 */
@Service
public class KnowledgeSearchService
{
    private static final Logger log = LoggerFactory.getLogger(KnowledgeSearchService.class);

    @Autowired
    private AiKnowledgeBaseMapper kbMapper;

    @Autowired
    private AiKnowledgeDocumentMapper documentMapper;

    @Autowired
    private AiAgentKnowledgeMapper agentKbMapper;

    @Autowired
    private EmbeddingModelFactory embeddingModelFactory;

    @Autowired
    private MilvusStoreManager storeManager;

    @Autowired
    private RerankModelFactory rerankModelFactory;

    /**
     * 检索单个知识库
     *
     * @param kbId           知识库ID
     * @param query          查询文本
     * @param topK           返回数量(可null, 用KB默认)
     * @param minScore       最小相似度(可null, 用KB默认)
     * @param metadataFilter 元数据过滤(JSON, 如 {"doc_category":"财务"})
     * @return 检索结果列表
     */
    public List<SearchResult> search(Long kbId, String query, Integer topK, Double minScore, String metadataFilter)
    {
        AiKnowledgeBase kb = kbMapper.selectAiKnowledgeBaseById(kbId);
        if (kb == null)
        {
            log.warn("知识库不存在: {}", kbId);
            return Collections.emptyList();
        }

        if (!"0".equals(kb.getStatus()))
        {
            log.warn("知识库已停用: {}", kb.getKbName());
            return Collections.emptyList();
        }

        int limit = topK != null ? topK : (kb.getTopK() != null ? kb.getTopK() : 5);
        double threshold = minScore != null ? minScore :
                (kb.getScoreThreshold() != null ? kb.getScoreThreshold().doubleValue() : 0.5);

        boolean useRerank = kb.getRerankModelId() != null;
        int finalLimit = useRerank && kb.getRerankTopN() != null ? kb.getRerankTopN() : limit;
        int searchLimit = useRerank ? limit * 4 : limit;

        try
        {
            // 向量化查询
            EmbeddingModel model = embeddingModelFactory.getModel(kb.getEmbeddingModelId());
            Embedding queryEmbedding = model.embed(query).content();

            // 构建检索请求
            EmbeddingStore<TextSegment> store = storeManager.getStore(kbId);

            EmbeddingSearchRequest.EmbeddingSearchRequestBuilder builder = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(searchLimit)
                    .minScore(threshold);

            // 元数据过滤
            Filter filter = buildFilter(metadataFilter);
            if (filter != null)
            {
                builder.filter(filter);
            }

            // 执行检索
            EmbeddingSearchResult<TextSegment> searchResult = store.search(builder.build());
            List<EmbeddingMatch<TextSegment>> matches = searchResult.matches();

            // 转换结果
            List<SearchResult> results = new ArrayList<>();
            for (EmbeddingMatch<TextSegment> match : matches)
            {
                TextSegment segment = match.embedded();
                Map<String, String> metaMap = segment.metadata().asMap();

                Long docId = parseLong(metaMap.get("document_id"));
                String docCategory = parseString(metaMap.get("doc_category"));
                String docTags = parseString(metaMap.get("doc_tags"));
                Integer chunkIndex = parseInt(metaMap.get("chunk_index"));
                String imagePath = parseString(metaMap.get("image_path"));

                // 查文档名
                String docName = null;
                if (docId != null)
                {
                    AiKnowledgeDocument doc = documentMapper.selectAiKnowledgeDocumentById(docId);
                    if (doc != null) docName = doc.getFileName();
                }

                SearchResult sr = new SearchResult();
                sr.setContent(segment.text());
                sr.setScore(BigDecimal.valueOf(match.score()).setScale(4, RoundingMode.HALF_UP));
                sr.setDocumentId(docId);
                sr.setDocumentName(docName);
                sr.setDocCategory(docCategory);
                sr.setDocTags(docTags);
                sr.setChunkIndex(chunkIndex);
                sr.setMilvusId(match.embeddingId());
                results.add(sr);
                sr.setImagePath(imagePath);
                if (imagePath != null && segment.text().startsWith("[文档图片] "))
                {
                    sr.setImageDescription(segment.text().substring("[文档图片] ".length()));
                }
            }

            log.info("知识库检索 [kb={}, query='{}', results={}]", kb.getKbName(),
                    query.length() > 50 ? query.substring(0, 50) + "..." : query, results.size());

            // 重排序
            if (useRerank && !results.isEmpty())
            {
                results = applyRerank(kb, query, results, finalLimit);
            }

            return results;

        }
        catch (Exception e)
        {
            log.error("知识库检索失败 [kbId={}, query={}]", kbId, query, e);
            return Collections.emptyList();
        }
    }

    /**
     * 为Agent检索（使用授权的知识库）
     *
     * @param agentId        Agent ID
     * @param query          查询文本
     * @param retrievalMode  检索模式(auto/tool)
     * @return 合并后的检索结果
     */
    public List<SearchResult> searchForAgent(Long agentId, String query, String retrievalMode)
    {
        List<AiAgentKnowledge> grants = agentKbMapper.selectActiveByAgentId(agentId);
        if (grants == null || grants.isEmpty())
        {
            return Collections.emptyList();
        }

        List<SearchResult> allResults = new ArrayList<>();

        for (AiAgentKnowledge grant : grants)
        {
            if (retrievalMode != null && !retrievalMode.equals(grant.getRetrievalMode()))
            {
                continue;
            }

            List<SearchResult> kbResults = search(
                    grant.getKbId(), query, null, null, grant.getMetadataFilter());
            allResults.addAll(kbResults);
        }

        // 按相似度排序，取top 10
        allResults.sort((a, b) -> b.getScore().compareTo(a.getScore()));

        if (allResults.size() > 10)
        {
            return allResults.subList(0, 10);
        }

        return allResults;
    }

    /**
     * 检查Agent是否有权访问指定知识库
     */
    public boolean isAgentAuthorized(Long agentId, Long kbId)
    {
        AiAgentKnowledge grant = agentKbMapper.selectByAgentIdAndKbId(agentId, kbId);
        return grant != null && "0".equals(grant.getStatus());
    }

    /**
     * 对向量检索结果进行重排序
     */
    private List<SearchResult> applyRerank(AiKnowledgeBase kb, String query,
                                           List<SearchResult> results, int finalLimit)
    {
        try
        {
            RerankModelFactory.RerankModel rerankModel = rerankModelFactory.getModel(kb.getRerankModelId());

            List<String> documents = new ArrayList<>();
            for (SearchResult sr : results)
            {
                documents.add(sr.getContent() != null ? sr.getContent() : "");
            }

            List<RerankModelFactory.RerankResult> rerankResults =
                    rerankModel.rerank(query, documents, finalLimit);

            List<SearchResult> reranked = new ArrayList<>();
            for (RerankModelFactory.RerankResult rr : rerankResults)
            {
                if (rr.getIndex() >= 0 && rr.getIndex() < results.size())
                {
                    SearchResult sr = results.get(rr.getIndex());
                    sr.setScore(BigDecimal.valueOf(rr.getScore()).setScale(4, RoundingMode.HALF_UP));
                    reranked.add(sr);
                }
            }

            log.info("重排序完成 [kb={}, candidates={}, reranked={}]", kb.getKbName(), results.size(), reranked.size());
            return reranked;
        }
        catch (Exception e)
        {
            log.warn("重排序失败，使用原始向量检索结果 [kb={}]", kb.getKbName(), e);
            if (results.size() > finalLimit)
            {
                return results.subList(0, finalLimit);
            }
            return results;
        }
    }
    /**
     * 构建元数据过滤Filter
     * 输入: {"doc_category":"财务", "doc_tags":"政策"}
     * 输出: And(IsEqualTo("doc_category","财务"), IsEqualTo("doc_tags","政策"))
     */
    private Filter buildFilter(String metadataFilterJson)
    {
        if (metadataFilterJson == null || metadataFilterJson.trim().isEmpty())
        {
            return null;
        }

        try
        {
            JSONObject json = JSON.parseObject(metadataFilterJson);
            if (json == null || json.isEmpty())
            {
                return null;
            }

            Filter filter = null;
            for (String key : json.keySet())
            {
                Object value = json.get(key);
                IsEqualTo eq = new IsEqualTo(key, value != null ? value.toString() : null);
                filter = (filter == null) ? eq : new And(filter, eq);
            }

            return filter;
        }
        catch (Exception e)
        {
            log.warn("解析元数据过滤条件失败: {}", metadataFilterJson, e);
            return null;
        }
    }

    private Long parseLong(Object obj)
    {
        if (obj == null) return null;
        try { return Long.parseLong(obj.toString()); } catch (Exception e) { return null; }
    }

    private Integer parseInt(Object obj)
    {
        if (obj == null) return null;
        try { return Integer.parseInt(obj.toString()); } catch (Exception e) { return null; }
    }

    private String parseString(Object obj)
    {
        return obj != null ? obj.toString() : null;
    }
}
