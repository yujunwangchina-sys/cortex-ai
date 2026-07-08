package com.cortex.knowledge.domain;

import java.math.BigDecimal;
import com.cortex.common.annotation.Excel;
import com.cortex.common.core.domain.BaseEntity;

/**
 * AI知识库配置 ai_knowledge_base
 *
 * @author cortex
 */
public class AiKnowledgeBase extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long id;

    @Excel(name = "知识库名称")
    private String kbName;

    @Excel(name = "知识库编码")
    private String kbCode;

    @Excel(name = "描述")
    private String description;

    private Long embeddingModelId;

    @Excel(name = "嵌入模型名称")
    private String embeddingModelName;

    private Long rerankModelId;

    @Excel(name = "重排序模型名称")
    private String rerankModelName;

    private Integer rerankTopN;

    private Integer chunkSize;

    private Integer chunkOverlap;

    private String chunkSeparator;
    private String extractImages;
    private String imageDescEnabled;
    private Integer topK;

    private BigDecimal scoreThreshold;

    private String collectionName;

    private Integer documentCount;

    private Integer chunkCount;

    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    private String status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getKbName() { return kbName; }
    public void setKbName(String kbName) { this.kbName = kbName; }
    public String getKbCode() { return kbCode; }
    public void setKbCode(String kbCode) { this.kbCode = kbCode; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getEmbeddingModelId() { return embeddingModelId; }
    public void setEmbeddingModelId(Long embeddingModelId) { this.embeddingModelId = embeddingModelId; }
    public String getEmbeddingModelName() { return embeddingModelName; }
    public void setEmbeddingModelName(String embeddingModelName) { this.embeddingModelName = embeddingModelName; }
    public Long getRerankModelId() { return rerankModelId; }
    public void setRerankModelId(Long rerankModelId) { this.rerankModelId = rerankModelId; }
    public String getRerankModelName() { return rerankModelName; }
    public void setRerankModelName(String rerankModelName) { this.rerankModelName = rerankModelName; }
    public Integer getRerankTopN() { return rerankTopN; }
    public void setRerankTopN(Integer rerankTopN) { this.rerankTopN = rerankTopN; }
    public Integer getChunkSize() { return chunkSize; }
    public void setChunkSize(Integer chunkSize) { this.chunkSize = chunkSize; }
    public Integer getChunkOverlap() { return chunkOverlap; }
    public void setChunkOverlap(Integer chunkOverlap) { this.chunkOverlap = chunkOverlap; }
    public Integer getTopK() { return topK; }
    public String getChunkSeparator() { return chunkSeparator; }
    public void setChunkSeparator(String chunkSeparator) { this.chunkSeparator = chunkSeparator; }
    public void setTopK(Integer topK) { this.topK = topK; }
    public String getExtractImages() { return extractImages; }
    public void setExtractImages(String extractImages) { this.extractImages = extractImages; }
    public String getImageDescEnabled() { return imageDescEnabled; }
    public void setImageDescEnabled(String imageDescEnabled) { this.imageDescEnabled = imageDescEnabled; }
    public BigDecimal getScoreThreshold() { return scoreThreshold; }
    public void setScoreThreshold(BigDecimal scoreThreshold) { this.scoreThreshold = scoreThreshold; }
    public String getCollectionName() { return collectionName; }
    public void setCollectionName(String collectionName) { this.collectionName = collectionName; }
    public Integer getDocumentCount() { return documentCount; }
    public void setDocumentCount(Integer documentCount) { this.documentCount = documentCount; }
    public Integer getChunkCount() { return chunkCount; }
    public void setChunkCount(Integer chunkCount) { this.chunkCount = chunkCount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
