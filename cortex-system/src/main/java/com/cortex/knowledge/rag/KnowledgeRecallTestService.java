package com.cortex.knowledge.rag;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.cortex.knowledge.domain.AiKnowledgeTestCase;
import com.cortex.knowledge.domain.AiKnowledgeTestResult;
import com.cortex.knowledge.mapper.AiKnowledgeTestCaseMapper;
import com.cortex.knowledge.mapper.AiKnowledgeTestResultMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 知识库召回测试服务
 * 执行测试用例，计算召回率和准确率。
 *
 * 召回率(Recall) = 命中的期望文档数 / 期望文档总数
 * 准确率(Precision) = 命中的期望文档数 / 实际返回文档数
 *
 * @author cortex
 */
@Service
public class KnowledgeRecallTestService
{
    private static final Logger log = LoggerFactory.getLogger(KnowledgeRecallTestService.class);

    @Autowired
    private AiKnowledgeTestCaseMapper testCaseMapper;

    @Autowired
    private AiKnowledgeTestResultMapper testResultMapper;

    @Autowired
    private KnowledgeSearchService searchService;

    /**
     * 执行单个测试用例
     */
    public AiKnowledgeTestResult runTest(Long testCaseId)
    {
        AiKnowledgeTestCase testCase = testCaseMapper.selectAiKnowledgeTestCaseById(testCaseId);
        if (testCase == null)
        {
            throw new RuntimeException("测试用例不存在: " + testCaseId);
        }

        log.info("执行召回测试 [case={}, query='{}']", testCase.getTestName(), testCase.getQuery());

        // 执行检索
        List<SearchResult> searchResults = searchService.search(
                testCase.getKbId(), testCase.getQuery(), null, null, testCase.getMetadataFilter());

        // 获取实际命中的文档ID
        Set<Long> hitDocIds = new LinkedHashSet<>();
        for (SearchResult sr : searchResults)
        {
            if (sr.getDocumentId() != null)
            {
                hitDocIds.add(sr.getDocumentId());
            }
        }

        // 获取期望文档ID
        Set<Long> expectedDocIds = parseIdSet(testCase.getExpectedDocIds());

        // 计算召回率
        int expectedTotal = expectedDocIds.size();
        int hitExpected = 0;
        for (Long docId : expectedDocIds)
        {
            if (hitDocIds.contains(docId))
            {
                hitExpected++;
            }
        }

        // 关键词命中检测
        Set<String> expectedKeywords = parseKeywordSet(testCase.getExpectedKeywords());
        int keywordHits = 0;
        if (!expectedKeywords.isEmpty())
        {
            for (SearchResult sr : searchResults)
            {
                String content = sr.getContent() != null ? sr.getContent().toLowerCase() : "";
                for (String kw : expectedKeywords)
                {
                    if (content.contains(kw.toLowerCase()))
                    {
                        keywordHits++;
                    }
                }
            }
        }

        double recall = expectedTotal > 0 ? (double) hitExpected / expectedTotal : 1.0;
        double precision = !hitDocIds.isEmpty() ? (double) hitExpected / hitDocIds.size() : 0.0;

        // 如果没有期望文档但有期望关键词，用关键词命中来补充召回率
        if (expectedTotal == 0 && !expectedKeywords.isEmpty())
        {
            recall = Math.min(1.0, (double) keywordHits / expectedKeywords.size());
        }

        // 平均相似度
        double avgScore = 0.0;
        if (!searchResults.isEmpty())
        {
            double sum = 0;
            for (SearchResult sr : searchResults)
            {
                sum += sr.getScore() != null ? sr.getScore().doubleValue() : 0;
            }
            avgScore = sum / searchResults.size();
        }

        // 构建命中详情JSON
        JSONArray hitChunksArray = new JSONArray();
        for (SearchResult sr : searchResults)
        {
            JSONObject obj = new JSONObject();
            obj.put("content", sr.getContent() != null && sr.getContent().length() > 200
                    ? sr.getContent().substring(0, 200) + "..." : sr.getContent());
            obj.put("score", sr.getScore());
            obj.put("document_id", sr.getDocumentId());
            obj.put("document_name", sr.getDocumentName());
            obj.put("doc_category", sr.getDocCategory());
            obj.put("chunk_index", sr.getChunkIndex());
            hitChunksArray.add(obj);
            if (sr.getImagePath() != null)
            {
                obj.put("image_path", sr.getImagePath());
                obj.put("image_description", sr.getImageDescription());
            }
        }

        // 保存结果
        AiKnowledgeTestResult result = new AiKnowledgeTestResult();
        result.setKbId(testCase.getKbId());
        result.setTestCaseId(testCaseId);
        result.setQuery(testCase.getQuery());
        result.setHitDocIds(String.join(",", hitDocIds.stream().map(String::valueOf).toList()));
        result.setHitChunks(hitChunksArray.toJSONString());
        result.setRecallScore(BigDecimal.valueOf(recall).setScale(4, RoundingMode.HALF_UP));
        result.setPrecisionScore(BigDecimal.valueOf(precision).setScale(4, RoundingMode.HALF_UP));
        result.setAvgScore(BigDecimal.valueOf(avgScore).setScale(4, RoundingMode.HALF_UP));
        result.setTopKUsed(searchResults.size());
        testResultMapper.insertAiKnowledgeTestResult(result);

        result.setTestName(testCase.getTestName());
        log.info("召回测试完成 [case={}, recall={}, precision={}, avgScore={}]",
                testCase.getTestName(), recall, precision, avgScore);

        return result;
    }

    /**
     * 批量执行知识库所有测试用例
     */
    public List<AiKnowledgeTestResult> runAllTests(Long kbId)
    {
        List<AiKnowledgeTestCase> cases = testCaseMapper.selectActiveByKbId(kbId);
        List<AiKnowledgeTestResult> results = new ArrayList<>();

        for (AiKnowledgeTestCase tc : cases)
        {
            try
            {
                results.add(runTest(tc.getId()));
            }
            catch (Exception e)
            {
                log.error("测试用例执行失败 [case={}]", tc.getTestName(), e);
            }
        }

        return results;
    }

    /**
     * 获取测试历史
     */
    public List<AiKnowledgeTestResult> getTestHistory(Long kbId)
    {
        return testResultMapper.selectByKbId(kbId);
    }

    private Set<Long> parseIdSet(String idsStr)
    {
        Set<Long> ids = new LinkedHashSet<>();
        if (idsStr == null || idsStr.trim().isEmpty()) return ids;
        for (String s : idsStr.split(","))
        {
            try { ids.add(Long.parseLong(s.trim())); } catch (Exception e) { /* skip */ }
        }
        return ids;
    }

    private Set<String> parseKeywordSet(String keywordsStr)
    {
        Set<String> keywords = new LinkedHashSet<>();
        if (keywordsStr == null || keywordsStr.trim().isEmpty()) return keywords;
        for (String s : keywordsStr.split(","))
        {
            String kw = s.trim();
            if (!kw.isEmpty()) keywords.add(kw);
        }
        return keywords;
    }
}
