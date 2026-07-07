package com.ruoyi.knowledge.domain;

import java.util.Date;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * AI知识库分块 ai_knowledge_chunk
 * 仅存储元数据，向量存储在 Milvus 中。
 *
 * @author ruoyi
 */
public class AiKnowledgeChunk extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long kbId;

    private Long documentId;

    private Integer chunkIndex;

    private String content;

    private Integer tokenCount;

    private String milvusId;

    private String imagePath;
    private String imageDescription;
    private Date createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getKbId() { return kbId; }
    public void setKbId(Long kbId) { this.kbId = kbId; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public Integer getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Integer getTokenCount() { return tokenCount; }
    public void setTokenCount(Integer tokenCount) { this.tokenCount = tokenCount; }
    public String getMilvusId() { return milvusId; }
    public void setMilvusId(String milvusId) { this.milvusId = milvusId; }
    public Date getCreateTime() { return createTime; }
    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }
    public String getImageDescription() { return imageDescription; }
    public void setImageDescription(String imageDescription) { this.imageDescription = imageDescription; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
}
