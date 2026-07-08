package com.cortex.knowledge.domain;

import java.util.Date;
import com.cortex.common.annotation.Excel;
import com.cortex.common.core.domain.BaseEntity;

/**
 * AI知识库文档 ai_knowledge_document
 *
 * @author cortex
 */
public class AiKnowledgeDocument extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long kbId;

    @Excel(name = "文件名")
    private String fileName;

    private String filePath;

    @Excel(name = "文件类型")
    private String fileType;

    private Long fileSize;

    private String contentText;

    private Integer chunkCount;

    @Excel(name = "分类")
    private String docCategory;

    @Excel(name = "标签")
    private String docTags;

    @Excel(name = "来源")
    private String docSource;

    @Excel(name = "作者")
    private String docAuthor;

    private Date effectiveDate;

    private String extraMetadata;

    @Excel(name = "状态", readConverterExp = "0=待处理,1=处理中,2=已索引,3=失败")
    private String status;

    private String errorMessage;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getKbId() { return kbId; }
    public void setKbId(Long kbId) { this.kbId = kbId; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getContentText() { return contentText; }
    public void setContentText(String contentText) { this.contentText = contentText; }
    public Integer getChunkCount() { return chunkCount; }
    public void setChunkCount(Integer chunkCount) { this.chunkCount = chunkCount; }
    public String getDocCategory() { return docCategory; }
    public void setDocCategory(String docCategory) { this.docCategory = docCategory; }
    public String getDocTags() { return docTags; }
    public void setDocTags(String docTags) { this.docTags = docTags; }
    public String getDocSource() { return docSource; }
    public void setDocSource(String docSource) { this.docSource = docSource; }
    public String getDocAuthor() { return docAuthor; }
    public void setDocAuthor(String docAuthor) { this.docAuthor = docAuthor; }
    public Date getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(Date effectiveDate) { this.effectiveDate = effectiveDate; }
    public String getExtraMetadata() { return extraMetadata; }
    public void setExtraMetadata(String extraMetadata) { this.extraMetadata = extraMetadata; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}