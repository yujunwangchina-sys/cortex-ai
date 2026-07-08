package com.cortex.agent.domain;

import com.cortex.common.core.domain.BaseEntity;

/**
 * AI Agent message - one row per message in a conversation.
 * Replaces the monolithic JSON blob in ai_agent_session.messages.
 *
 * @author cortex
 */
public class AiAgentMessage extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long id;

    /** Session ID (FK) */
    private String sessionId;

    /** Message sequence number (0, 1, 2, ...) */
    private Integer seqNum;

    /** Role: system / user / assistant / tool */
    private String role;

    /** Text content (null for tool-call-only assistant messages) */
    private String content;

    /** Tool call ID (for role=tool messages) */
    private String toolCallId;

    /** Tool calls JSON (for assistant messages with tool_calls) */
    private String toolCallsJson;

    /** Image URLs JSON (for multimodal user messages) */
    private String imageUrlsJson;

    /** Message name (optional, for tool messages) */
    private String name;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public Integer getSeqNum() { return seqNum; }
    public void setSeqNum(Integer seqNum) { this.seqNum = seqNum; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getToolCallId() { return toolCallId; }
    public void setToolCallId(String toolCallId) { this.toolCallId = toolCallId; }
    public String getToolCallsJson() { return toolCallsJson; }
    public void setToolCallsJson(String toolCallsJson) { this.toolCallsJson = toolCallsJson; }
    public String getImageUrlsJson() { return imageUrlsJson; }
    public void setImageUrlsJson(String imageUrlsJson) { this.imageUrlsJson = imageUrlsJson; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}