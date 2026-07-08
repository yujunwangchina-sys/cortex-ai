package com.cortex.knowledge.mapper;

import java.util.List;
import com.cortex.knowledge.domain.AiKnowledgeDocument;

/**
 * 知识库文档Mapper接口
 *
 * @author cortex
 */
public interface AiKnowledgeDocumentMapper
{
    public AiKnowledgeDocument selectAiKnowledgeDocumentById(Long id);

    public List<AiKnowledgeDocument> selectAiKnowledgeDocumentList(AiKnowledgeDocument aiKnowledgeDocument);

    public int insertAiKnowledgeDocument(AiKnowledgeDocument aiKnowledgeDocument);

    public int updateAiKnowledgeDocument(AiKnowledgeDocument aiKnowledgeDocument);

    public int deleteAiKnowledgeDocumentById(Long id);

    public int deleteAiKnowledgeDocumentByIds(Long[] ids);

    public int deleteByKbId(Long kbId);

    public int updateStatus(Long id, String status, String errorMessage);
}