package com.ruoyi.knowledge.service;

import java.util.List;
import com.ruoyi.knowledge.domain.AiKnowledgeTestCase;
import com.ruoyi.knowledge.domain.AiKnowledgeTestResult;

/**
 * 召回测试用例Service接口
 *
 * @author ruoyi
 */
public interface IAiKnowledgeTestCaseService
{
    public AiKnowledgeTestCase selectAiKnowledgeTestCaseById(Long id);

    public List<AiKnowledgeTestCase> selectAiKnowledgeTestCaseList(AiKnowledgeTestCase aiKnowledgeTestCase);

    public int insertAiKnowledgeTestCase(AiKnowledgeTestCase aiKnowledgeTestCase);

    public int updateAiKnowledgeTestCase(AiKnowledgeTestCase aiKnowledgeTestCase);

    public int deleteAiKnowledgeTestCaseByIds(Long[] ids);

    public AiKnowledgeTestResult runTest(Long testCaseId);

    public List<AiKnowledgeTestResult> runAllTests(Long kbId);

    public List<AiKnowledgeTestResult> getTestHistory(Long kbId);
}