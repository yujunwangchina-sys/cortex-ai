package com.ruoyi.knowledge.mapper;

import java.util.List;
import com.ruoyi.knowledge.domain.AiKnowledgeTestCase;

/**
 * 知识库召回测试用例Mapper接口
 *
 * @author ruoyi
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