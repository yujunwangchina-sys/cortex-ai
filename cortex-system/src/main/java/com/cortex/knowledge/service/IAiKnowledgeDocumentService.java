package com.cortex.knowledge.service;

import java.util.List;
import com.cortex.knowledge.domain.AiKnowledgeDocument;

/**
 * 知识库文档Service接口
 *
 * @author cortex
 */
public interface IAiKnowledgeDocumentService
{
    public AiKnowledgeDocument selectAiKnowledgeDocumentById(Long id);

    public List<AiKnowledgeDocument> selectAiKnowledgeDocumentList(AiKnowledgeDocument aiKnowledgeDocument);

    public AiKnowledgeDocument uploadDocument(Long kbId, org.springframework.web.multipart.MultipartFile file);

    public int updateAiKnowledgeDocument(AiKnowledgeDocument aiKnowledgeDocument);

    public int deleteAiKnowledgeDocumentByIds(Long[] ids);

    public void reprocessDocument(Long id);
}