package com.cortex.knowledge.domain;

import java.util.Date;
import com.cortex.common.core.domain.BaseEntity;

/**
 * Agent知识库授权 ai_agent_knowledge
 *
 * @author cortex
 */
public class AiAgentKnowledge extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long agentId;

    private Long kbId;

    private String retrievalMode;

    private String metadataFilter;

    private String status;

    private String grantedBy;

    private Date createTime;

    private String kbName;

    private String kbCode;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }
    public Long getKbId() { return kbId; }
    public void setKbId(Long kbId) { this.kbId = kbId; }
    public String getRetrievalMode() { return retrievalMode; }
    public void setRetrievalMode(String retrievalMode) { this.retrievalMode = retrievalMode; }
    public String getMetadataFilter() { return metadataFilter; }
    public void setMetadataFilter(String metadataFilter) { this.metadataFilter = metadataFilter; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getGrantedBy() { return grantedBy; }
    public void setGrantedBy(String grantedBy) { this.grantedBy = grantedBy; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public String getKbName() { return kbName; }
    public void setKbName(String kbName) { this.kbName = kbName; }
    public String getKbCode() { return kbCode; }
    public void setKbCode(String kbCode) { this.kbCode = kbCode; }
}