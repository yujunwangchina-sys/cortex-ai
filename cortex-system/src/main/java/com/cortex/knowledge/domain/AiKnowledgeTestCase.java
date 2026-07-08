package com.cortex.knowledge.domain;

import com.cortex.common.annotation.Excel;
import com.cortex.common.core.domain.BaseEntity;

/**
 * AI知识库召回测试用例 ai_knowledge_test_case
 *
 * @author cortex
 */
public class AiKnowledgeTestCase extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long kbId;

    @Excel(name = "测试名称")
    private String testName;

    @Excel(name = "测试问题")
    private String query;

    private String expectedDocIds;

    private String expectedKeywords;

    private String metadataFilter;

    @Excel(name = "状态", readConverterExp = "0=启用,1=停用")
    private String status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getKbId() { return kbId; }
    public void setKbId(Long kbId) { this.kbId = kbId; }
    public String getTestName() { return testName; }
    public void setTestName(String testName) { this.testName = testName; }
    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public String getExpectedDocIds() { return expectedDocIds; }
    public void setExpectedDocIds(String expectedDocIds) { this.expectedDocIds = expectedDocIds; }
    public String getExpectedKeywords() { return expectedKeywords; }
    public void setExpectedKeywords(String expectedKeywords) { this.expectedKeywords = expectedKeywords; }
    public String getMetadataFilter() { return metadataFilter; }
    public void setMetadataFilter(String metadataFilter) { this.metadataFilter = metadataFilter; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}