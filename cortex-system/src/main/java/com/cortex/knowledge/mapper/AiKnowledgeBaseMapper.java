package com.cortex.knowledge.mapper;

import java.util.List;
import com.cortex.knowledge.domain.AiKnowledgeBase;

/**
 * 知识库Mapper接口
 *
 * @author cortex
 */
public interface AiKnowledgeBaseMapper
{
    public AiKnowledgeBase selectAiKnowledgeBaseById(Long id);

    public AiKnowledgeBase selectByKbCode(String kbCode);

    public List<AiKnowledgeBase> selectAiKnowledgeBaseList(AiKnowledgeBase aiKnowledgeBase);

    public int insertAiKnowledgeBase(AiKnowledgeBase aiKnowledgeBase);

    public int updateAiKnowledgeBase(AiKnowledgeBase aiKnowledgeBase);

    public int deleteAiKnowledgeBaseById(Long id);

    public int deleteAiKnowledgeBaseByIds(Long[] ids);

    public int updateDocumentCount(Long id);

    public int updateChunkCount(Long id);
}