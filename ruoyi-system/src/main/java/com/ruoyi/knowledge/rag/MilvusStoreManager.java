package com.ruoyi.knowledge.rag;

import com.ruoyi.knowledge.domain.AiKnowledgeBase;
import com.ruoyi.knowledge.mapper.AiKnowledgeBaseMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.R;
import io.milvus.param.collection.DropCollectionParam;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.DeleteParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Milvus向量存储管理器
 * 管理每个知识库对应的Milvus collection，缓存EmbeddingStore实例。
 *
 * @author ruoyi
 */
@Component
public class MilvusStoreManager
{
    private static final Logger log = LoggerFactory.getLogger(MilvusStoreManager.class);

    @Value("${knowledge.milvus.host:localhost}")
    private String host;

    @Value("${knowledge.milvus.port:19530}")
    private int port;

    @Value("${knowledge.milvus.collection-prefix:ruoyi_kb_}")
    private String collectionPrefix;

    @Autowired
    private AiKnowledgeBaseMapper kbMapper;

    @Autowired
    private EmbeddingModelFactory embeddingModelFactory;

    private final ConcurrentHashMap<Long, EmbeddingStore<TextSegment>> storeCache = new ConcurrentHashMap<>();

    private volatile MilvusServiceClient milvusClient;

    /**
     * 获取Milvus客户端（懒加载）
     */
    private synchronized MilvusServiceClient getClient()
    {
        if (milvusClient == null)
        {
            ConnectParam connectParam = ConnectParam.newBuilder()
                    .withHost(host)
                    .withPort(port)
                    .build();
            milvusClient = new MilvusServiceClient(connectParam);
            log.info("Milvus客户端已连接 [{}:{}]", host, port);
        }
        return milvusClient;
    }

    /**
     * 获取知识库对应的collection名
     */
    private String collectionName(Long kbId)
    {
        return collectionPrefix + kbId;
    }

    /**
     * 获取或创建EmbeddingStore
     */
    public EmbeddingStore<TextSegment> getStore(Long kbId)
    {
        return storeCache.computeIfAbsent(kbId, this::createStore);
    }

    private EmbeddingStore<TextSegment> createStore(Long kbId)
    {
        AiKnowledgeBase kb = kbMapper.selectAiKnowledgeBaseById(kbId);
        if (kb == null)
        {
            throw new RuntimeException("知识库不存在: " + kbId);
        }

        int dimension = embeddingModelFactory.getDimension(kb.getEmbeddingModelId());
        String collection = collectionName(kbId);

        log.info("创建MilvusEmbeddingStore [kb={}, collection={}, dim={}]", kb.getKbName(), collection, dimension);

        MilvusEmbeddingStore store = MilvusEmbeddingStore.builder()
                .host(host)
                .port(port)
                .collectionName(collection)
                .dimension(dimension)
                .retrieveEmbeddingsOnSearch(true)
                .build();

        // 确保collection已加载
        try
        {
            R<Boolean> has = getClient().hasCollection(
                    HasCollectionParam.newBuilder().withCollectionName(collection).build());
            if (has.getData() != null && has.getData())
            {
                getClient().loadCollection(
                        LoadCollectionParam.newBuilder().withCollectionName(collection).build());
                log.info("Milvus collection已加载 [{}]", collection);
            }
        }
        catch (Exception e)
        {
            log.warn("加载Milvus collection时异常(可能首次创建，忽略): {}", e.getMessage());
        }

        // 更新collection_name到数据库
        if (kb.getCollectionName() == null || kb.getCollectionName().isEmpty())
        {
            kb.setCollectionName(collection);
            kbMapper.updateAiKnowledgeBase(kb);
        }

        return store;
    }

    /**
     * 删除知识库的collection
     */
    public void dropCollection(Long kbId)
    {
        String collection = collectionName(kbId);
        try
        {
            getClient().dropCollection(
                    DropCollectionParam.newBuilder().withCollectionName(collection).build());
            log.info("已删除Milvus collection [{}]", collection);
        }
        catch (Exception e)
        {
            log.warn("删除Milvus collection失败: {}", e.getMessage());
        }
        storeCache.remove(kbId);
    }

    /**
     * 按ID删除向量
     */
    public void deleteByIds(Long kbId, List<String> milvusIds)
    {
        if (milvusIds == null || milvusIds.isEmpty())
        {
            return;
        }
        String collection = collectionName(kbId);
        try
        {
            StringBuilder expr = new StringBuilder("id in [");
            for (int i = 0; i < milvusIds.size(); i++)
            {
                if (i > 0) expr.append(",");
                expr.append("'").append(milvusIds.get(i)).append("'");
            }
            expr.append("]");

            getClient().delete(DeleteParam.newBuilder()
                    .withCollectionName(collection)
                    .withExpr(expr.toString())
                    .build());
            log.info("已删除Milvus向量 [collection={}, count={}]", collection, milvusIds.size());
        }
        catch (Exception e)
        {
            log.error("删除Milvus向量失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 清除缓存（知识库配置变更时调用）
     */
    public void evictCache(Long kbId)
    {
        storeCache.remove(kbId);
    }
}