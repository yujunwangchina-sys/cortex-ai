package com.cortex.knowledge.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.cortex.common.core.domain.BaseEntity;

/**
 * AI知识库召回测试结果 ai_knowledge_test_result
 *
 * @author cortex
 */
public class AiKnowledgeTestResult extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long kbId;

    private Long testCaseId;

    private String query;

    private String hitDocIds;

    private String hitChunks;

    private BigDecimal recallScore;

    private BigDecimal precisionScore;

    private BigDecimal avgScore;

    private Integer topKUsed;

    private Date runTime;

    private String testName;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getKbId() { return kbId; }
    public void setKbId(Long kbId) { this.kbId = kbId; }
    public Long getTestCaseId() { return testCaseId; }
    public void setTestCaseId(Long testCaseId) { this.testCaseId = testCaseId; }
    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public String getHitDocIds() { return hitDocIds; }
    public void setHitDocIds(String hitDocIds) { this.hitDocIds = hitDocIds; }
    public String getHitChunks() { return hitChunks; }
    public void setHitChunks(String hitChunks) { this.hitChunks = hitChunks; }
    public BigDecimal getRecallScore() { return recallScore; }
    public void setRecallScore(BigDecimal recallScore) { this.recallScore = recallScore; }
    public BigDecimal getPrecisionScore() { return precisionScore; }
    public void setPrecisionScore(BigDecimal precisionScore) { this.precisionScore = precisionScore; }
    public BigDecimal getAvgScore() { return avgScore; }
    public void setAvgScore(BigDecimal avgScore) { this.avgScore = avgScore; }
    public Integer getTopKUsed() { return topKUsed; }
    public void setTopKUsed(Integer topKUsed) { this.topKUsed = topKUsed; }
    public Date getRunTime() { return runTime; }
    public void setRunTime(Date runTime) { this.runTime = runTime; }
    public String getTestName() { return testName; }
    public void setTestName(String testName) { this.testName = testName; }
}