package com.cortex.agent.runtime.loop;

import com.cortex.agent.domain.AiAgent;
import com.cortex.agent.runtime.context.AgentSessionContext;
import com.cortex.agent.runtime.context.SessionManager;
import com.cortex.agent.runtime.llm.LlmRequest;
import com.cortex.agent.runtime.llm.LlmResponse;
import com.cortex.agent.runtime.llm.OpenAiCompatibleClient;
import com.cortex.agent.runtime.model.ChatMessage;
import com.cortex.agent.runtime.model.SSEEvent;
import com.cortex.agent.runtime.prompt.AgentConfigLoader;
import com.cortex.agent.runtime.prompt.ToolSchemaBuilder;
import com.cortex.agent.runtime.tool.ToolExecutor;
import com.cortex.plugin.domain.AiPlugin;
import com.cortex.plugin.domain.AiPluginTool;
import com.cortex.skill.domain.SkillNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Sub-Agent delegation with cross-agent and parallel support.
 *
 * Key invariants:
 *   - Child gets a FRESH conversation (no parent history)
 *   - Child has restricted tools (no delegate_task recursion)
 *   - Child has limited iterations (max 10)
 *   - Parent only sees the delegation call and the summary result
 *   - When agentCode is specified, child uses the target agent's config
 *
 * @author cortex
 */
@Component
public class SubAgentDelegate
{
    private static final Logger log = LoggerFactory.getLogger(SubAgentDelegate.class);

    private static final int SUB_AGENT_MAX_ITERATIONS = 10;
    private static final int SUB_AGENT_MAX_API_RETRIES = 2;

    @Autowired
    private OpenAiCompatibleClient llmClient;

    @Autowired
    private AgentConfigLoader configLoader;

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private ToolExecutor toolExecutor;

    @Autowired
    private ToolSchemaBuilder toolSchemaBuilder;

    /**
     * Result of a parallel sub-task.
     */
    public static class SubTaskResult
    {
        public final String goal;
        public final String agentCode;
        public final boolean success;
        public final String result;

        public SubTaskResult(String goal, String agentCode, boolean success, String result)
        {
            this.goal = goal;
            this.agentCode = agentCode;
            this.success = success;
            this.result = result;
        }
    }

    // ==================== Single delegation ====================

    /**
     * Original delegate (backward compat) - delegates to self.
     */
    public String delegate(AgentSessionContext parentCtx, AiAgent agent,
                           String goal, String context,
                           List<SkillNode> skills,
                           List<AiPlugin> plugins,
                           List<AiPluginTool> tools,
                           List<String> toolSchemas,
                           AgentConfigLoader.ModelSelection modelSelect)
    {
        return delegate(parentCtx, agent, goal, context,
                skills, plugins, tools, toolSchemas, modelSelect, null, null);
    }

    /**
     * Delegate a subtask, optionally to a different specialist agent.
     *
     * @param targetAgentCode  null/empty = use parent agent config; otherwise load target agent
     * @param sseCallback      optional SSE callback for progress events
     */
    public String delegate(AgentSessionContext parentCtx, AiAgent agent,
                           String goal, String context,
                           List<SkillNode> skills,
                           List<AiPlugin> plugins,
                           List<AiPluginTool> tools,
                           List<String> toolSchemas,
                           AgentConfigLoader.ModelSelection modelSelect,
                           String targetAgentCode,
                           Consumer<SSEEvent> sseCallback)
    {
        // Resolve target agent config
        AgentConfig resolved;
        if (targetAgentCode != null && !targetAgentCode.trim().isEmpty())
        {
            resolved = resolveTargetAgent(targetAgentCode, parentCtx);
            if (resolved == null)
            {
                return "Failed to load target agent: " + targetAgentCode;
            }
            log.info("Cross-agent delegation [parent={}, target={}, goal={}]",
                    parentCtx.getSessionId(), targetAgentCode, truncate(goal, 60));
        }
        else
        {
            resolved = new AgentConfig(agent, agent.getSystemPrompt(),
                    skills, plugins, tools, toolSchemas, modelSelect);
        }

        return runSubAgent(parentCtx, resolved, goal, context, sseCallback);
    }

    // ==================== Parallel delegation ====================

    /**
     * Delegate multiple subtasks concurrently.
     */
    public List<SubTaskResult> delegateParallel(AgentSessionContext parentCtx, AiAgent agent,
                                                  List<Map<String, Object>> taskList,
                                                  List<SkillNode> skills,
                                                  List<AiPlugin> plugins,
                                                  List<AiPluginTool> tools,
                                                  List<String> toolSchemas,
                                                  AgentConfigLoader.ModelSelection modelSelect,
                                                  Consumer<SSEEvent> sseCallback)
    {
        List<CompletableFuture<SubTaskResult>> futures = new ArrayList<>();

        for (Map<String, Object> task : taskList)
        {
            String goal = (String) task.get("goal");
            String ctx = (String) task.getOrDefault("context", "");
            String agentCode = (String) task.get("agentCode");

            futures.add(CompletableFuture.supplyAsync(() -> {
                try
                {
                    String result = delegate(parentCtx, agent, goal, ctx,
                            skills, plugins, tools, toolSchemas, modelSelect,
                            agentCode, sseCallback);
                    boolean success = !result.startsWith("Failed to load target agent");
                    return new SubTaskResult(goal, agentCode, success, result);
                }
                catch (Exception e)
                {
                    log.error("Parallel sub-task failed [goal={}]", truncate(goal, 60), e);
                    return new SubTaskResult(goal, agentCode, false, "Error: " + e.getMessage());
                }
            }));
        }

        // Wait for all
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<SubTaskResult> results = new ArrayList<>();
        for (CompletableFuture<SubTaskResult> f : futures)
        {
            results.add(f.join());
        }
        return results;
    }

    // ==================== Core sub-agent loop ====================

    private String runSubAgent(AgentSessionContext parentCtx, AgentConfig resolved,
                               String goal, String context,
                               Consumer<SSEEvent> sseCallback)
    {
        AiAgent agent = resolved.agent;
        String agentLabel = agent.getAgentCode() != null ? agent.getAgentName() : "sub-agent";

        // SSE: sub-agent started
        if (sseCallback != null)
        {
            sseCallback.accept(SSEEvent.info("📤 子Agent [" + agentLabel + "] 开始执行: " + truncate(goal, 50)));
        }

        // 1. Create isolated child context
        AgentSessionContext childCtx = sessionManager.createSession(
                agent.getId(), agent.getAgentCode(),
                parentCtx.getUserLoginName(), parentCtx.getBusinessSystem(),
                SUB_AGENT_MAX_ITERATIONS);
        log.info("Sub-agent started [parent={}, sub={}, agent={}]",
                parentCtx.getSessionId(), childCtx.getSessionId(), agent.getAgentCode());

        childCtx.setApprovalMode("full");

        // 2. Build system prompt
        String systemPrompt = buildSubAgentPrompt(agent, goal, context, resolved.systemPrompt);

        // 3. Filter tools: remove delegate_task to prevent recursion
        List<AiPluginTool> filteredTools = new ArrayList<>();
        List<AiPlugin> filteredPlugins = new ArrayList<>();
        List<String> filteredSchemas = new ArrayList<>();
        for (int i = 0; i < resolved.tools.size(); i++)
        {
            AiPluginTool tool = resolved.tools.get(i);
            if (!"delegate_task".equals(tool.getToolCode()) && !"delegate_parallel".equals(tool.getToolCode()))
            {
                filteredTools.add(tool);
                if (i < resolved.toolSchemas.size()) filteredSchemas.add(resolved.toolSchemas.get(i));
            }
        }
        for (AiPlugin plugin : resolved.plugins)
        {
            for (AiPluginTool t : filteredTools)
            {
                if (t.getPluginId().equals(plugin.getPluginId()))
                {
                    filteredPlugins.add(plugin);
                    break;
                }
            }
        }

        // 4. Run simplified loop
        childCtx.addMessage(ChatMessage.user(goal));
        childCtx.resetBudget();

        int iteration = 0;
        while (iteration < SUB_AGENT_MAX_ITERATIONS && childCtx.getIterationBudget().getRemaining() > 0)
        {
            iteration++;
            if (!childCtx.getIterationBudget().consume()) break;

            List<ChatMessage> allMessages = new ArrayList<>();
            allMessages.add(ChatMessage.system(systemPrompt));
            allMessages.addAll(childCtx.getMessages());

            LlmRequest llmRequest = new LlmRequest();
            llmRequest.setBaseUrl(resolved.modelSelect.supplier.getApiBaseUrl());
            llmRequest.setApiKey(resolved.modelSelect.supplier.getApiKey());
            llmRequest.setModel(resolved.modelSelect.model.getModelCode());
            llmRequest.setMessages(allMessages);
            llmRequest.setTools(filteredSchemas);
            llmRequest.setStream(false);

            LlmResponse response = null;
            boolean success = false;
            for (int retry = 0; retry < SUB_AGENT_MAX_API_RETRIES; retry++)
            {
                try
                {
                    response = llmClient.chatCompletion(llmRequest);
                    success = true;
                    break;
                }
                catch (Exception e)
                {
                    log.warn("Sub-agent LLM call failed [retry={}, sub={}]", retry + 1, childCtx.getSessionId(), e);
                    if (retry >= SUB_AGENT_MAX_API_RETRIES - 1) break;
                    try { Thread.sleep(2000); } catch (InterruptedException ie)
                    {
                        Thread.currentThread().interrupt();
                        return "Sub-agent interrupted.";
                    }
                }
            }

            if (!success || response == null)
            {
                log.warn("Sub-agent LLM call failed after retries [sub={}]", childCtx.getSessionId());
                break;
            }

            childCtx.addTokenUsage(response.getPromptTokens(), response.getCompletionTokens());

            // Handle tool calls
            if (response.hasToolCalls())
            {
                childCtx.addMessage(ChatMessage.assistantWithTools(
                        response.getContent(), response.getToolCalls()));

                java.util.Map<String, AiPlugin> pluginMap = new java.util.HashMap<>();
                java.util.Map<String, AiPluginTool> toolMap = new java.util.HashMap<>();
                for (AiPluginTool t : filteredTools)
                {
                    toolMap.put(t.getToolCode(), t);
                    for (AiPlugin p : filteredPlugins)
                    {
                        if (p.getPluginId().equals(t.getPluginId()))
                        {
                            pluginMap.put(t.getToolCode(), p);
                            break;
                        }
                    }
                }

                List<com.cortex.agent.runtime.model.ToolCallResult> results = toolExecutor.executeAll(
                        response.getToolCalls(), pluginMap, toolMap,
                        childCtx, "sub-" + childCtx.getSessionId(), iteration, null);

                for (com.cortex.agent.runtime.model.ToolCallResult result : results)
                {
                    childCtx.addMessage(ChatMessage.tool(result.getToolCallId(),
                            result.toToolMessageContent()));
                }
                continue;
            }

            // Final response
            String content = response.getContent();
            if (content != null && !content.trim().isEmpty())
            {
                content = ThinkBlockStripper.strip(content);
                log.info("Sub-agent completed [sub={}, iterations={}]", childCtx.getSessionId(), iteration);

                // SSE: sub-agent completed
                if (sseCallback != null)
                {
                    sseCallback.accept(SSEEvent.info("✅ 子Agent [" + agentLabel + "] 完成"));
                }
                return content;
            }

            childCtx.addMessage(ChatMessage.assistant("(empty)"));
            childCtx.addMessage(ChatMessage.user("Please continue with your task."));
        }

        log.warn("Sub-agent reached iteration limit [sub={}, iterations={}]", childCtx.getSessionId(), iteration);
        if (sseCallback != null)
        {
            sseCallback.accept(SSEEvent.info("⚠️ 子Agent [" + agentLabel + "] 达到迭代上限"));
        }
        return "Sub-agent could not complete the task within the iteration limit.";
    }

    // ==================== Cross-agent config loading ====================

    private AgentConfig resolveTargetAgent(String agentCode, AgentSessionContext parentCtx)
    {
        try
        {
            AiAgent targetAgent = configLoader.loadAgent(agentCode);
            if (targetAgent == null)
            {
                log.warn("Target agent not found [agentCode={}]", agentCode);
                return null;
            }

            // Load target agent's model
            AgentConfigLoader.ModelSelection targetModel;
            try
            {
                targetModel = configLoader.selectModel(targetAgent, "chat");
            }
            catch (Exception e)
            {
                log.warn("Failed to load target agent model, using parent's [agentCode={}]", agentCode, e);
                return null;
            }

            // Load target agent's skills, plugins, tools
            List<SkillNode> targetSkills = configLoader.loadSkills(
                    targetAgent.getId(), parentCtx.getBusinessSystem(), parentCtx.getUserLoginName());
            List<AiPlugin> targetPlugins = configLoader.loadPlugins(targetAgent.getId());
            List<AiPluginTool> targetTools = configLoader.loadTools(targetPlugins);
            List<String> targetSchemas = toolSchemaBuilder.build(targetTools, targetPlugins);

            return new AgentConfig(targetAgent, targetAgent.getSystemPrompt(),
                    targetSkills, targetPlugins, targetTools, targetSchemas, targetModel);
        }
        catch (Exception e)
        {
            log.error("Failed to resolve target agent [agentCode={}]", agentCode, e);
            return null;
        }
    }

    private String buildSubAgentPrompt(AiAgent agent, String goal, String context,
                                       String customSystemPrompt)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a sub-agent delegated to handle a specific task.\n\n");
        sb.append("## Task\n\n").append(goal).append("\n\n");

        if (context != null && !context.isEmpty())
        {
            sb.append("## Context from parent agent\n\n").append(context).append("\n\n");
        }

        sb.append("## Rules\n\n");
        sb.append("1. Focus exclusively on the delegated task.\n");
        sb.append("2. Use available tools as needed, but be efficient.\n");
        sb.append("3. When the task is complete, provide a concise summary of your findings/results.\n");
        sb.append("4. Do not attempt to delegate tasks to other agents.\n");
        sb.append("5. Respond in Chinese.\n");

        if (customSystemPrompt != null && !customSystemPrompt.isEmpty())
        {
            sb.append("\n## Agent configuration\n\n").append(customSystemPrompt).append("\n");
        }

        return sb.toString();
    }

    private String truncate(String s, int max)
    {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    // ==================== Inner classes ====================

    private static class AgentConfig
    {
        final AiAgent agent;
        final String systemPrompt;
        final List<SkillNode> skills;
        final List<AiPlugin> plugins;
        final List<AiPluginTool> tools;
        final List<String> toolSchemas;
        final AgentConfigLoader.ModelSelection modelSelect;

        AgentConfig(AiAgent agent, String systemPrompt,
                    List<SkillNode> skills, List<AiPlugin> plugins,
                    List<AiPluginTool> tools, List<String> toolSchemas,
                    AgentConfigLoader.ModelSelection modelSelect)
        {
            this.agent = agent;
            this.systemPrompt = systemPrompt;
            this.skills = skills != null ? skills : Collections.emptyList();
            this.plugins = plugins != null ? plugins : Collections.emptyList();
            this.tools = tools != null ? tools : Collections.emptyList();
            this.toolSchemas = toolSchemas != null ? toolSchemas : Collections.emptyList();
            this.modelSelect = modelSelect;
        }
    }
}