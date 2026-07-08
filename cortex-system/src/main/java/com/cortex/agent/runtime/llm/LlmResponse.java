package com.cortex.agent.runtime.llm;

import java.util.List;
import com.cortex.agent.runtime.model.ChatMessage;

/**
 * LLM 响应
 *
 * @author cortex
 */
public class LlmResponse
{
    /** 回复内容 */
    private String content;
    /** 工具调用列表 */
    private List<ChatMessage.ToolCall> toolCalls;
    /** 输入token数 */
    private int promptTokens;
    /** 输出token数 */
    private int completionTokens;
    /** 总token数 */
    private int totalTokens;
    /** 结束原因(stop/length/tool_calls) */
    private String finishReason;

    public boolean hasToolCalls()
    {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public List<ChatMessage.ToolCall> getToolCalls() { return toolCalls; }
    public void setToolCalls(List<ChatMessage.ToolCall> toolCalls) { this.toolCalls = toolCalls; }
    public int getPromptTokens() { return promptTokens; }
    public void setPromptTokens(int promptTokens) { this.promptTokens = promptTokens; }
    public int getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(int completionTokens) { this.completionTokens = completionTokens; }
    public int getTotalTokens() { return totalTokens; }
    public void setTotalTokens(int totalTokens) { this.totalTokens = totalTokens; }
    public String getFinishReason() { return finishReason; }
    public void setFinishReason(String finishReason) { this.finishReason = finishReason; }
}
