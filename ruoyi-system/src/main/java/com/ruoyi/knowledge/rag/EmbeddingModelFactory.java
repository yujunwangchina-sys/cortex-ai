package com.ruoyi.knowledge.rag;

import com.ruoyi.supplier.domain.AiModel;
import com.ruoyi.supplier.domain.AiSupplier;
import com.ruoyi.supplier.mapper.AiModelMapper;
import com.ruoyi.supplier.mapper.AiSupplierMapper;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Embedding模型工厂
 * 根据ai_model表配置构建LangChain4j EmbeddingModel实例，按modelId缓存。
 *
 * @author ruoyi
 */
@Component
public class EmbeddingModelFactory
{
    private static final Logger log = LoggerFactory.getLogger(EmbeddingModelFactory.class);

    @Autowired
    private AiModelMapper modelMapper;

    @Autowired
    private AiSupplierMapper supplierMapper;

    private final ConcurrentHashMap<Long, EmbeddingModel> cache = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<Long, Integer> dimensionCache = new ConcurrentHashMap<>();

    /**
     * 获取Embedding模型（带缓存）
     */
    public EmbeddingModel getModel(Long modelId)
    {
        return cache.computeIfAbsent(modelId, this::buildModel);
    }

    /**
     * 获取向量维度（通过嵌入一段测试文本得到）
     */
    public int getDimension(Long modelId)
    {
        return dimensionCache.computeIfAbsent(modelId, mid -> {
            EmbeddingModel model = getModel(mid);
            dev.langchain4j.data.embedding.Embedding emb = model.embed("dimension_probe").content();
            return emb.vector().length;
        });
    }

    private EmbeddingModel buildModel(Long modelId)
    {
        AiModel model = modelMapper.selectAiModelByModelId(modelId);
        if (model == null)
        {
            throw new RuntimeException("Embedding模型不存在: modelId=" + modelId);
        }

        AiSupplier supplier = supplierMapper.selectAiSupplierBySupplierId(model.getSupplierId());
        if (supplier == null)
        {
            throw new RuntimeException("供应商不存在: supplierId=" + model.getSupplierId());
        }

        log.info("构建Embedding模型 [model={}, code={}, supplier={}, baseUrl={}]",
                model.getModelName(), model.getModelCode(), supplier.getSupplierName(), supplier.getApiBaseUrl());

        // 统一走OpenAI兼容接口（DashScope等也兼容/v1/embeddings）
        OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder builder = OpenAiEmbeddingModel.builder()
                .baseUrl(normalizeBaseUrl(supplier.getApiBaseUrl()))
                .apiKey(supplier.getApiKey())
                .modelName(model.getModelCode())  // 注意：使用 modelName 而不是 model
                .timeout(Duration.ofSeconds(60));

        log.info("Embedding模型构建完成 [model={}]", model.getModelCode());
        return builder.build();
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
     * 清除缓存（模型配置变更时调用）
     */
    public void evict(Long modelId)
    {
        cache.remove(modelId);
        dimensionCache.remove(modelId);
    }
}