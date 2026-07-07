package com.ruoyi.agent.runtime.model;

import com.alibaba.fastjson2.JSONObject;

/**
 * SSE Event - classified streaming events for frontend rendering.
 *
 * Event types:
 *   session_created  - session created (with sessionId + agentName)
 *   llm_start        - LLM iteration started (with iteration number)
 *   content_delta    - assistant text delta (the main reply, streaming word-by-word)
 *   thinking_delta   - reasoning/thinking text delta (if model emits <think> blocks, these are
 *                      stripped from content_delta; if you want to show them, use this)
 *   tool_call_start  - tool invocation started (with toolName, pluginName, arguments)
 *   tool_call_end    - tool invocation finished (with status: success/error/rejected/blocked)
 *   tool_result      - tool execution result content (for inline display)
 *   approval_required - approval needed (with grantId, pluginName, toolName)
 *   info             - informational message (context compressed, retrying, etc.)
 *   error            - error (terminal)
 *   done             - conversation complete (with sessionId, iterations, tokenUsage)
 *
 * @author ruoyi
 */
public class SSEEvent
{
    private String type;
    private Object data;

    public SSEEvent() {}

    public SSEEvent(String type, Object data)
    {
        this.type = type;
        this.data = data;
    }

    public static SSEEvent sessionCreated(String sessionId, String agentName)
    {
        JSONObject obj = new JSONObject();
        obj.put("sessionId", sessionId);
        obj.put("agentName", agentName);
        return new SSEEvent("session_created", obj);
    }

    public static SSEEvent llmStart(int iteration)
    {
        JSONObject obj = new JSONObject();
        obj.put("iteration", iteration);
        return new SSEEvent("llm_start", obj);
    }

    public static SSEEvent contentDelta(String delta)
    {
        JSONObject obj = new JSONObject();
        obj.put("delta", delta);
        return new SSEEvent("content_delta", obj);
    }

    /**
     * Thinking/reasoning delta (from &lt;think&gt; blocks, if shown separately)
     */
    public static SSEEvent thinkingDelta(String delta)
    {
        JSONObject obj = new JSONObject();
        obj.put("delta", delta);
        return new SSEEvent("thinking_delta", obj);
    }

    public static SSEEvent toolCallStart(String toolName, String pluginName, String arguments)
    {
        JSONObject obj = new JSONObject();
        obj.put("toolName", toolName);
        obj.put("pluginName", pluginName);
        obj.put("arguments", arguments);
        return new SSEEvent("tool_call_start", obj);
    }

    public static SSEEvent toolCallEnd(String toolName, String status, int durationMs)
    {
        JSONObject obj = new JSONObject();
        obj.put("toolName", toolName);
        obj.put("status", status);
        obj.put("durationMs", durationMs);
        return new SSEEvent("tool_call_end", obj);
    }

    /**
     * Tool execution result (for inline display in chat)
     */
    public static SSEEvent toolResult(String toolName, String result, boolean success)
    {
        JSONObject obj = new JSONObject();
        obj.put("toolName", toolName);
        obj.put("result", result != null ? (result.length() > 2000 ? result.substring(0, 2000) + "..." : result) : "");
        obj.put("success", success);
        return new SSEEvent("tool_result", obj);
    }

    public static SSEEvent approvalRequired(Long grantId, String pluginName, String toolName, String arguments, String reason)
    {
        JSONObject obj = new JSONObject();
        obj.put("grantId", grantId);
        obj.put("pluginName", pluginName);
        obj.put("toolName", toolName);
        obj.put("arguments", arguments);
        obj.put("reason", reason);
        return new SSEEvent("approval_required", obj);
    }

    public static SSEEvent info(String message)
    {
        JSONObject obj = new JSONObject();
        obj.put("message", message);
        return new SSEEvent("info", obj);
    }

    public static SSEEvent clearCurrentMessage()
    {
        JSONObject obj = new JSONObject();
        obj.put("reason", "approval_rejected");
        return new SSEEvent("clear_current_message", obj);
    }

    public static SSEEvent error(String message, String errorType)
    {
        JSONObject obj = new JSONObject();
        obj.put("message", message);
        obj.put("type", errorType);
        return new SSEEvent("error", obj);
    }

    public static SSEEvent done(String sessionId, int totalIterations, int tokenUsage)
    {
        JSONObject obj = new JSONObject();
        obj.put("sessionId", sessionId);
        obj.put("totalIterations", totalIterations);
        obj.put("tokenUsage", tokenUsage);
        return new SSEEvent("done", obj);
    }
    
    /**
     * Context usage update (for progress indicator)
     * @param usedTokens 已使用的token数
     * @param maxTokens 最大token限制
     * @param percentage 使用百分比 (0-100)
     */
    public static SSEEvent contextUsage(int usedTokens, int maxTokens, int percentage)
    {
        JSONObject obj = new JSONObject();
        obj.put("usedTokens", usedTokens);
        obj.put("maxTokens", maxTokens);
        obj.put("percentage", percentage);
        return new SSEEvent("context_usage", obj);
    }
    
    /**
     * Context compressed notification
     * @param beforeCount 压缩前消息数
     * @param afterCount 压缩后消息数
     * @param beforeTokens 压缩前token数
     * @param afterTokens 压缩后token数
     */
    public static SSEEvent contextCompressed(int beforeCount, int afterCount, int beforeTokens, int afterTokens)
    {
        JSONObject obj = new JSONObject();
        obj.put("beforeCount", beforeCount);
        obj.put("afterCount", afterCount);
        obj.put("beforeTokens", beforeTokens);
        obj.put("afterTokens", afterTokens);
        return new SSEEvent("context_compressed", obj);
    }

    /**
     * Context compression started notification
     */
    public static SSEEvent compressingStarted()
    {
        return new SSEEvent("compressing_started", new JSONObject());
    }
    
    /**
     * Session title generated notification
     * @param sessionId 会话ID
     * @param title 生成的标题
     */
    public static SSEEvent titleGenerated(String sessionId, String title)
    {
        JSONObject obj = new JSONObject();
        obj.put("sessionId", sessionId);
        obj.put("title", title);
        return new SSEEvent("title_generated", obj);
    }

    public String getType() { return type; }
    public Object getData() { return data; }

    /**
     * Convert to SSE format string (raw, no extra data: prefix).
     * The SseEmitter already adds the "data: " prefix.
     */
    public String toSseString()
    {
        JSONObject obj = new JSONObject();
        obj.put("type", type);
        obj.put("data", data);
        return obj.toJSONString();
    }
}
