package com.ruoyi.agent.runtime.model;

import java.util.List;

/**
 * Agent 运行结果
 *
 * @author ruoyi
 */
public class AgentRunResult
{
    private String sessionId;
    private String response;
    private int iterations;
    private int tokenInput;
    private int tokenOutput;
    private List<ToolCallResult> toolCallResults;
    private boolean success;
    private String errorMessage;

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getResponse() { return response; }
    public void setResponse(String response) { this.response = response; }
    public int getIterations() { return iterations; }
    public void setIterations(int iterations) { this.iterations = iterations; }
    public int getTokenInput() { return tokenInput; }
    public void setTokenInput(int tokenInput) { this.tokenInput = tokenInput; }
    public int getTokenOutput() { return tokenOutput; }
    public void setTokenOutput(int tokenOutput) { this.tokenOutput = tokenOutput; }
    public List<ToolCallResult> getToolCallResults() { return toolCallResults; }
    public void setToolCallResults(List<ToolCallResult> toolCallResults) { this.toolCallResults = toolCallResults; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
