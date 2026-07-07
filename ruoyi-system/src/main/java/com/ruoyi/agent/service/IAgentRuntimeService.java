package com.ruoyi.agent.service;

import com.ruoyi.agent.runtime.model.AgentRunRequest;
import com.ruoyi.agent.runtime.model.AgentRunResult;
import com.ruoyi.agent.runtime.model.SSEEvent;
import java.util.function.Consumer;

/**
 * Agent运行时Service接口
 *
 * @author ruoyi
 */
public interface IAgentRuntimeService
{
    /**
     * 运行Agent(非流式)
     */
    AgentRunResult run(AgentRunRequest request) throws Exception;

    /**
     * 运行Agent(SSE流式)
     */
    AgentRunResult runStream(AgentRunRequest request, Consumer<SSEEvent> sseCallback) throws Exception;
}
