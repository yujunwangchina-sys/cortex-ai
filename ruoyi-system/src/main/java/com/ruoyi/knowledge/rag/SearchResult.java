package com.ruoyi.knowledge.rag;

import java.math.BigDecimal;

/**
 * 知识库检索结果
 *
 * @author ruoyi
 */
public class SearchResult
{
    private String content;
    private BigDecimal score;
    private Long documentId;
    private String documentName;
    private String docCategory;
    private String docTags;
    private Integer chunkIndex;
    private String milvusId;
    private String imagePath;
    private String imageDescription;

    public SearchResult() {}

    public SearchResult(String content, BigDecimal score, Long documentId, String documentName,
                        String docCategory, String docTags, Integer chunkIndex, String milvusId)
    {
        this.content = content;
        this.score = score;
        this.documentId = documentId;
        this.documentName = documentName;
        this.docCategory = docCategory;
        this.docTags = docTags;
        this.chunkIndex = chunkIndex;
        this.milvusId = milvusId;
    }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public String getDocumentName() { return documentName; }
    public void setDocumentName(String documentName) { this.documentName = documentName; }
    public String getDocCategory() { return docCategory; }
    public void setDocCategory(String docCategory) { this.docCategory = docCategory; }
    public String getDocTags() { return docTags; }
    public void setDocTags(String docTags) { this.docTags = docTags; }
    public Integer getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }
    public String getMilvusId() { return milvusId; }
    public void setMilvusId(String milvusId) { this.milvusId = milvusId; }
    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }
    public String getImageDescription() { return imageDescription; }
    public void setImageDescription(String imageDescription) { this.imageDescription = imageDescription; }
}
