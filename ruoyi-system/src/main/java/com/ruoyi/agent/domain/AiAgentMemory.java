package com.ruoyi.agent.domain;

import com.ruoyi.common.core.domain.BaseEntity;

/**
 * AI Agent cross-session memory
 * Stores key facts/preferences extracted from conversations for later recall.
 *
 * @author ruoyi
 */
public class AiAgentMemory extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long id;

    /** Agent ID (null = global memory for this user) */
    private Long agentId;

    /** User login name */
    private String userLoginName;

    /** Business system identifier */
    private String businessSystem;

    /** Memory category: preference / fact / instruction / context */
    private String memoryType;

    /** Short title / summary */
    private String title;

    /** Full memory content */
    private String content;

    /** Importance score 0-100 (higher = more important) */
    private Integer importance;

    /** How many times this memory was recalled */
    private Integer recallCount;

    /** Session where this memory was learned */
    private String sourceSessionId;

    /** Lifecycle: active / stale / archived */
    private String lifecycleState;

    /** Pinned (skip auto-archival) */
    private Boolean pinned;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }
    public String getUserLoginName() { return userLoginName; }
    public void setUserLoginName(String userLoginName) { this.userLoginName = userLoginName; }
    public String getBusinessSystem() { return businessSystem; }
    public void setBusinessSystem(String businessSystem) { this.businessSystem = businessSystem; }
    public String getMemoryType() { return memoryType; }
    public void setMemoryType(String memoryType) { this.memoryType = memoryType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Integer getImportance() { return importance; }
    public void setImportance(Integer importance) { this.importance = importance; }
    public Integer getRecallCount() { return recallCount; }
    public void setRecallCount(Integer recallCount) { this.recallCount = recallCount; }
    public String getSourceSessionId() { return sourceSessionId; }
    public void setSourceSessionId(String sourceSessionId) { this.sourceSessionId = sourceSessionId; }
    public String getLifecycleState() { return lifecycleState; }
    public void setLifecycleState(String lifecycleState) { this.lifecycleState = lifecycleState; }
    public Boolean getPinned() { return pinned; }
    public void setPinned(Boolean pinned) { this.pinned = pinned; }
}