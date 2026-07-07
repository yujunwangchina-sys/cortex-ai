package com.ruoyi.agent.runtime.model;

import com.alibaba.fastjson2.JSONObject;
import java.util.Map;

/**
 * 工具调用结果
 *
 * @author ruoyi
 */
public class ToolCallResult
{
    private String toolCallId;
    private String toolName;
    private boolean success;
    private String content;
    private String errorMessage;
    private int durationMs;
    private boolean approvalRejected;
    private String pluginName;
    
    /** 会话ID（用于图片存储路径） */
    private String sessionId;

    public static ToolCallResult success(String toolCallId, String toolName, String content, int durationMs)
    {
        ToolCallResult r = new ToolCallResult();
        r.toolCallId = toolCallId;
        r.toolName = toolName;
        r.success = true;
        r.content = content;
        r.durationMs = durationMs;
        return r;
    }

    public static ToolCallResult error(String toolCallId, String toolName, String errorMessage, int durationMs)
    {
        ToolCallResult r = new ToolCallResult();
        r.toolCallId = toolCallId;
        r.toolName = toolName;
        r.success = false;
        r.errorMessage = errorMessage;
        r.durationMs = durationMs;
        return r;
    }

    public static ToolCallResult rejected(String toolCallId, String toolName, String reason)
    {
        ToolCallResult r = new ToolCallResult();
        r.toolCallId = toolCallId;
        r.toolName = toolName;
        r.success = false;
        r.errorMessage = reason;
        r.approvalRejected = true;
        return r;
    }

    /**
     * 转为 tool role 消息内容
     * 注意：图片的base64到URL转换已在ToolExecutor中完成
     */
    public String toToolMessageContent()
    {
        if (success)
        {
            return content;
        }
        
        // 审批被拒绝时返回纯文本消息，不用JSON
        if (approvalRejected)
        {
            return errorMessage != null ? errorMessage : "操作被拒绝";
        }
        
        // 其他错误返回JSON格式
        JSONObject obj = new JSONObject();
        obj.put("error", errorMessage != null ? errorMessage : "unknown error");
        return obj.toJSONString();
    }

    public String getToolCallId() { return toolCallId; }
    public String getToolName() { return toolName; }
    public boolean isSuccess() { return success; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getErrorMessage() { return errorMessage; }
    public int getDurationMs() { return durationMs; }
    public boolean isApprovalRejected() { return approvalRejected; }
    public String getPluginName() { return pluginName; }
    public void setPluginName(String pluginName) { this.pluginName = pluginName; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
}
