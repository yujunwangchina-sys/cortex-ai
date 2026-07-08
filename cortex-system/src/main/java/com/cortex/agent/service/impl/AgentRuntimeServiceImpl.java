package com.cortex.agent.service.impl;

import com.cortex.agent.runtime.AgentRuntime;
import com.cortex.agent.runtime.model.AgentRunRequest;
import com.cortex.agent.runtime.model.AgentRunResult;
import com.cortex.agent.runtime.model.SSEEvent;
import com.cortex.agent.service.IAgentRuntimeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/**
 * Agent运行时Service实现
 *
 * @author cortex
 */
@Service
public class AgentRuntimeServiceImpl implements IAgentRuntimeService
{
    @Autowired
    private AgentRuntime agentRuntime;

    @Override
    public AgentRunResult run(AgentRunRequest request) throws Exception
    {
        return agentRuntime.run(request);
    }

    @Override
    public AgentRunResult runStream(AgentRunRequest request, Consumer<SSEEvent> sseCallback) throws Exception
    {
        return agentRuntime.run(request, sseCallback);
    }
}
