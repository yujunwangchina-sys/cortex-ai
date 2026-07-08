package com.cortex.knowledge.service;

import java.util.List;
import com.cortex.knowledge.domain.AiKnowledgeBase;

/**
 * 知识库Service接口
 *
 * @author cortex
 */
public interface IAiKnowledgeBaseService
{
    public AiKnowledgeBase selectAiKnowledgeBaseById(Long id);

    public List<AiKnowledgeBase> selectAiKnowledgeBaseList(AiKnowledgeBase aiKnowledgeBase);

    public int insertAiKnowledgeBase(AiKnowledgeBase aiKnowledgeBase);

    public int updateAiKnowledgeBase(AiKnowledgeBase aiKnowledgeBase);

    public int deleteAiKnowledgeBaseByIds(Long[] ids);

    public void rebuildIndex(Long id);
}