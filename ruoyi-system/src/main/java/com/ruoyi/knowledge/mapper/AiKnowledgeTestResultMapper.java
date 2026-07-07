package com.ruoyi.knowledge.mapper;

import java.util.List;
import com.ruoyi.knowledge.domain.AiKnowledgeTestResult;

/**
 * 知识库召回测试结果Mapper接口
 *
 * @author ruoyi
 */
public interface AiKnowledgeTestResultMapper
{
    public int insertAiKnowledgeTestResult(AiKnowledgeTestResult result);

    public List<AiKnowledgeTestResult> selectByKbId(Long kbId);

    public List<AiKnowledgeTestResult> selectByTestCaseId(Long testCaseId);

    public int deleteByKbId(Long kbId);
}