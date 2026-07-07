package com.ruoyi.knowledge.service.impl;

import java.util.List;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.knowledge.domain.AiKnowledgeTestCase;
import com.ruoyi.knowledge.domain.AiKnowledgeTestResult;
import com.ruoyi.knowledge.mapper.AiKnowledgeTestCaseMapper;
import com.ruoyi.knowledge.rag.KnowledgeRecallTestService;
import com.ruoyi.knowledge.service.IAiKnowledgeTestCaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 召回测试用例Service实现
 *
 * @author ruoyi
 */
@Service
public class AiKnowledgeTestCaseServiceImpl implements IAiKnowledgeTestCaseService
{
    @Autowired
    private AiKnowledgeTestCaseMapper testCaseMapper;

    @Autowired
    private KnowledgeRecallTestService recallTestService;

    @Override
    public AiKnowledgeTestCase selectAiKnowledgeTestCaseById(Long id)
    {
        return testCaseMapper.selectAiKnowledgeTestCaseById(id);
    }

    @Override
    public List<AiKnowledgeTestCase> selectAiKnowledgeTestCaseList(AiKnowledgeTestCase aiKnowledgeTestCase)
    {
        return testCaseMapper.selectAiKnowledgeTestCaseList(aiKnowledgeTestCase);
    }

    @Override
    public int insertAiKnowledgeTestCase(AiKnowledgeTestCase aiKnowledgeTestCase)
    {
        aiKnowledgeTestCase.setCreateTime(DateUtils.getNowDate());
        return testCaseMapper.insertAiKnowledgeTestCase(aiKnowledgeTestCase);
    }

    @Override
    public int updateAiKnowledgeTestCase(AiKnowledgeTestCase aiKnowledgeTestCase)
    {
        aiKnowledgeTestCase.setUpdateTime(DateUtils.getNowDate());
        return testCaseMapper.updateAiKnowledgeTestCase(aiKnowledgeTestCase);
    }

    @Override
    public int deleteAiKnowledgeTestCaseByIds(Long[] ids)
    {
        return testCaseMapper.deleteAiKnowledgeTestCaseByIds(ids);
    }

    @Override
    public AiKnowledgeTestResult runTest(Long testCaseId)
    {
        return recallTestService.runTest(testCaseId);
    }

    @Override
    public List<AiKnowledgeTestResult> runAllTests(Long kbId)
    {
        return recallTestService.runAllTests(kbId);
    }

    @Override
    public List<AiKnowledgeTestResult> getTestHistory(Long kbId)
    {
        return recallTestService.getTestHistory(kbId);
    }
}