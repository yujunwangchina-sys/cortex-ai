package com.cortex.knowledge.mapper;

import java.util.List;
import com.cortex.knowledge.domain.AiKnowledgeTestResult;

/**
 * 知识库召回测试结果Mapper接口
 *
 * @author cortex
 */
public interface AiKnowledgeTestResultMapper
{
    public int insertAiKnowledgeTestResult(AiKnowledgeTestResult result);

    public List<AiKnowledgeTestResult> selectByKbId(Long kbId);

    public List<AiKnowledgeTestResult> selectByTestCaseId(Long testCaseId);

    public int deleteByKbId(Long kbId);
}