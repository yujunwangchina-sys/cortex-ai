package com.cortex.agent.runtime.llm;

import java.math.BigDecimal;
import java.util.List;
import com.cortex.agent.runtime.model.ChatMessage;

/**
 * LLM 请求
 *
 * @author cortex
 */
public class LlmRequest
{
    /** API基础地址 */
    private String baseUrl;
    /** API密钥 */
    private String apiKey;
    /** 模型编码 */
    private String model;
    /** 消息列表 */
    private List<ChatMessage> messages;
    /** 工具定义(JSON字符串列表) */
    private List<String> tools;
    /** 温度 */
    private BigDecimal temperature;
    /** 最大输出token */
    private Integer maxTokens;
    /** Top P */
    private BigDecimal topP;
    /** 是否流式 */
    private boolean stream;
    private Integer timeoutSeconds;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public List<ChatMessage> getMessages() { return messages; }
    public void setMessages(List<ChatMessage> messages) { this.messages = messages; }
    public List<String> getTools() { return tools; }
    public void setTools(List<String> tools) { this.tools = tools; }
    public BigDecimal getTemperature() { return temperature; }
    public void setTemperature(BigDecimal temperature) { this.temperature = temperature; }
    public Integer getMaxTokens() { return Integer.valueOf(maxTokens == null ? 0 : maxTokens); }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }
    public BigDecimal getTopP() { return topP; }
    public void setTopP(BigDecimal topP) { this.topP = topP; }
    public boolean isStream() { return stream; }
    public void setStream(boolean stream) { this.stream = stream; }
    public Integer getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(Integer timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
}
