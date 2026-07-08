package com.cortex.plugin.builtin.impl;

import com.cortex.agent.domain.AiAgent;
import com.cortex.agent.runtime.context.AgentSessionContext;
import com.cortex.agent.runtime.loop.SubAgentDelegate;
import com.cortex.agent.runtime.model.SSEEvent;
import com.cortex.agent.runtime.prompt.AgentConfigLoader;
import com.cortex.plugin.builtin.IBuiltinPlugin;
import com.cortex.plugin.builtin.PluginInfo;
import com.cortex.plugin.builtin.ToolDefinition;
import com.cortex.plugin.domain.AiPlugin;
import com.cortex.plugin.domain.AiPluginTool;
import com.cortex.skill.domain.SkillNode;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Consumer;

/**
 * Sub-Agent delegation plugin - exposes delegate_task and delegate_parallel tools.
 *
 * delegate_task: delegate a focused subtask to a child agent (self or a specialist agent)
 * delegate_parallel: delegate multiple subtasks concurrently
 *
 * @author cortex
 */
@Component
public class SubAgentPlugin implements IBuiltinPlugin
{
    private static final Logger log = LoggerFactory.getLogger(SubAgentPlugin.class);

    private static final ThreadLocal<DelegateContext> CONTEXT = new ThreadLocal<>();

    @Autowired
    private SubAgentDelegate subAgentDelegate;

    public static void setContext(DelegateContext ctx) { CONTEXT.set(ctx); }
    public static void clearContext() { CONTEXT.remove(); }

    @Override
    public PluginInfo getPluginInfo()
    {
        PluginInfo info = new PluginInfo("Sub-Agent Delegate", "sub_agent_delegate",
                "Delegate focused subtasks to child agents with isolated context");
        info.setVersion("2.0.0");
        info.setCategory("agent");
        info.setRequireApproval(false);
        return info;
    }

    @Override
    public List<ToolDefinition> getTools()
    {
        List<ToolDefinition> tools = new ArrayList<>();

        // 1. delegate_task - single subtask delegation
        ToolDefinition delegate = new ToolDefinition();
        delegate.setName("delegate_task");
        delegate.setDescription(
            "Delegate a focused subtask to a child agent with isolated context. " +
            "The child agent runs independently and returns a summary result. " +
            "Use agentCode to delegate to a specialist agent (if available). " +
            "If agentCode is omitted, delegates to yourself (same config).");

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> goal = new LinkedHashMap<>();
        goal.put("type", "string");
        goal.put("description", "The focused goal for the sub-agent.");
        properties.put("goal", goal);

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("type", "string");
        context.put("description", "Additional context from the parent conversation.");
        properties.put("context", context);

        Map<String, Object> agentCode = new LinkedHashMap<>();
        agentCode.put("type", "string");
        agentCode.put("description", "Optional: agentCode of a specialist agent to delegate to. Omit to delegate to yourself.");
        properties.put("agentCode", agentCode);

        schema.put("properties", properties);
        schema.put("required", List.of("goal"));
        delegate.setInputSchema(schema);
        tools.add(delegate);

        // 2. delegate_parallel - concurrent multi-task delegation
        ToolDefinition parallel = new ToolDefinition();
        parallel.setName("delegate_parallel");
        parallel.setDescription(
            "Delegate multiple subtasks to child agents concurrently (in parallel). " +
            "Each task can optionally specify a specialist agentCode. " +
            "All tasks run simultaneously and results are returned together. " +
            "Use this when subtasks are independent and can run in parallel.");

        Map<String, Object> pSchema = new LinkedHashMap<>();
        pSchema.put("type", "object");
        Map<String, Object> pProps = new LinkedHashMap<>();

        Map<String, Object> tasksProp = new LinkedHashMap<>();
        tasksProp.put("type", "array");
        tasksProp.put("description", "Array of subtasks to execute in parallel");
        Map<String, Object> taskItem = new LinkedHashMap<>();
        taskItem.put("type", "object");
        Map<String, Object> taskProps = new LinkedHashMap<>();
        taskProps.put("goal", Map.of("type", "string", "description", "Subtask goal"));
        taskProps.put("context", Map.of("type", "string", "description", "Additional context (optional)"));
        taskProps.put("agentCode", Map.of("type", "string", "description", "Specialist agent code (optional)"));
        taskItem.put("properties", taskProps);
        taskItem.put("required", List.of("goal"));
        tasksProp.put("items", taskItem);
        pProps.put("tasks", tasksProp);

        pSchema.put("properties", pProps);
        pSchema.put("required", List.of("tasks"));
        parallel.setInputSchema(pSchema);
        tools.add(parallel);

        return tools;
    }

    @Override
    public String executeTool(String toolName, Map<String, Object> args)
    {
        DelegateContext ctx = CONTEXT.get();
        if (ctx == null)
        {
            return JSON.toJSONString(Map.of("error",
                    "Delegate context not available. This tool can only be used during an active agent session."));
        }

        switch (toolName)
        {
            case "delegate_task":
                return executeDelegateTask(ctx, args);
            case "delegate_parallel":
                return executeDelegateParallel(ctx, args);
            default:
                return JSON.toJSONString(Map.of("error", "Unknown tool: " + toolName));
        }
    }

    @SuppressWarnings("unchecked")
    private String executeDelegateTask(DelegateContext ctx, Map<String, Object> args)
    {
        String goal = (String) args.get("goal");
        String context = (String) args.getOrDefault("context", "");
        String agentCode = (String) args.get("agentCode");

        if (goal == null || goal.trim().isEmpty())
        {
            return JSON.toJSONString(Map.of("error", "goal is required"));
        }

        // Validate agentCode if provided
        if (agentCode != null && !agentCode.trim().isEmpty())
        {
            String validation = validateAgentCode(ctx, agentCode);
            if (validation != null) return validation;
        }

        try
        {
            log.info("delegate_task invoked [parent={}, agentCode={}, goal={}]",
                    ctx.parentCtx.getSessionId(), agentCode != null ? agentCode : "self",
                    goal.length() > 80 ? goal.substring(0, 80) + "..." : goal);

            String result = subAgentDelegate.delegate(
                    ctx.parentCtx, ctx.agent, goal, context,
                    ctx.skills, ctx.plugins, ctx.tools, ctx.toolSchemas, ctx.modelSelect,
                    agentCode, ctx.sseCallback);

            JSONObject output = new JSONObject();
            output.put("success", true);
            output.put("result", result);
            output.put("summary", result.length() > 500 ? result.substring(0, 500) + "..." : result);
            return output.toJSONString();
        }
        catch (Exception e)
        {
            log.error("delegate_task failed", e);
            JSONObject output = new JSONObject();
            output.put("success", false);
            output.put("error", e.getMessage());
            return output.toJSONString();
        }
    }

    @SuppressWarnings("unchecked")
    private String executeDelegateParallel(DelegateContext ctx, Map<String, Object> args)
    {
        Object tasksObj = args.get("tasks");
        if (tasksObj == null)
        {
            return JSON.toJSONString(Map.of("error", "tasks is required"));
        }

        List<Map<String, Object>> taskList;
        if (tasksObj instanceof List)
        {
            taskList = (List<Map<String, Object>>) tasksObj;
        }
        else
        {
            taskList = (List<Map<String, Object>>) (List) JSON.parseArray(JSON.toJSONString(tasksObj));
        }

        if (taskList.isEmpty())
        {
            return JSON.toJSONString(Map.of("error", "tasks cannot be empty"));
        }

        // Validate all agentCodes
        for (Map<String, Object> task : taskList)
        {
            String ac = (String) task.get("agentCode");
            if (ac != null && !ac.trim().isEmpty())
            {
                String validation = validateAgentCode(ctx, ac);
                if (validation != null) return validation;
            }
        }

        try
        {
            log.info("delegate_parallel invoked [parent={}, taskCount={}]", ctx.parentCtx.getSessionId(), taskList.size());

            List<SubAgentDelegate.SubTaskResult> results = subAgentDelegate.delegateParallel(
                    ctx.parentCtx, ctx.agent, taskList,
                    ctx.skills, ctx.plugins, ctx.tools, ctx.toolSchemas, ctx.modelSelect,
                    ctx.sseCallback);

            JSONArray arr = new JSONArray();
            for (SubAgentDelegate.SubTaskResult r : results)
            {
                JSONObject item = new JSONObject();
                item.put("goal", r.goal);
                item.put("success", r.success);
                item.put("result", r.result);
                item.put("agentCode", r.agentCode != null ? r.agentCode : "self");
                arr.add(item);
            }

            JSONObject output = new JSONObject();
            output.put("success", true);
            output.put("taskCount", taskList.size());
            output.put("results", arr);
            return output.toJSONString();
        }
        catch (Exception e)
        {
            log.error("delegate_parallel failed", e);
            JSONObject output = new JSONObject();
            output.put("success", false);
            output.put("error", e.getMessage());
            return output.toJSONString();
        }
    }

    private String validateAgentCode(DelegateContext ctx, String agentCode)
    {
        if (ctx.availableAgents == null || ctx.availableAgents.isEmpty())
        {
            return JSON.toJSONString(Map.of("error",
                    "No specialist agents available for delegation. Omit agentCode to delegate to yourself."));
        }
        for (AiAgent a : ctx.availableAgents)
        {
            if (agentCode.equals(a.getAgentCode()))
            {
                return null; // valid
            }
        }
        // Invalid: list available agents
        StringBuilder sb = new StringBuilder("Invalid agentCode: " + agentCode + ". Available agents: ");
        for (int i = 0; i < ctx.availableAgents.size(); i++)
        {
            AiAgent a = ctx.availableAgents.get(i);
            if (i > 0) sb.append(", ");
            sb.append(a.getAgentCode()).append(" (").append(a.getAgentName()).append(")");
        }
        return JSON.toJSONString(Map.of("error", sb.toString()));
    }

    public static class DelegateContext
    {
        public final AgentSessionContext parentCtx;
        public final AiAgent agent;
        public final List<SkillNode> skills;
        public final List<AiPlugin> plugins;
        public final List<AiPluginTool> tools;
        public final List<String> toolSchemas;
        public final AgentConfigLoader.ModelSelection modelSelect;
        public final List<AiAgent> availableAgents;
        public final Consumer<SSEEvent> sseCallback;

        public DelegateContext(AgentSessionContext parentCtx, AiAgent agent,
                               List<SkillNode> skills, List<AiPlugin> plugins,
                               List<AiPluginTool> tools, List<String> toolSchemas,
                               AgentConfigLoader.ModelSelection modelSelect)
        {
            this(parentCtx, agent, skills, plugins, tools, toolSchemas, modelSelect, null, null);
        }

        public DelegateContext(AgentSessionContext parentCtx, AiAgent agent,
                               List<SkillNode> skills, List<AiPlugin> plugins,
                               List<AiPluginTool> tools, List<String> toolSchemas,
                               AgentConfigLoader.ModelSelection modelSelect,
                               List<AiAgent> availableAgents,
                               Consumer<SSEEvent> sseCallback)
        {
            this.parentCtx = parentCtx;
            this.agent = agent;
            this.skills = skills;
            this.plugins = plugins;
            this.tools = tools;
            this.toolSchemas = toolSchemas;
            this.modelSelect = modelSelect;
            this.availableAgents = availableAgents != null ? availableAgents : Collections.emptyList();
            this.sseCallback = sseCallback;
        }
    }
}