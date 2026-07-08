package com.cortex.knowledge.mapper;

import java.util.List;
import com.cortex.knowledge.domain.AiKnowledgeTestCase;

/**
 * 知识库召回测试用例Mapper接口
 *
 * @author cortex
 */
public interface AiKnowledgeTestCaseMapper
{
    public AiKnowledgeTestCase selectAiKnowledgeTestCaseById(Long id);

    public List<AiKnowledgeTestCase> selectAiKnowledgeTestCaseList(AiKnowledgeTestCase aiKnowledgeTestCase);

    public List<AiKnowledgeTestCase> selectActiveByKbId(Long kbId);

    public int insertAiKnowledgeTestCase(AiKnowledgeTestCase aiKnowledgeTestCase);

    public int updateAiKnowledgeTestCase(AiKnowledgeTestCase aiKnowledgeTestCase);

    public int deleteAiKnowledgeTestCaseById(Long id);

    public int deleteAiKnowledgeTestCaseByIds(Long[] ids);

    public int deleteByKbId(Long kbId);
}