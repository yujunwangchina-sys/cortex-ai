package com.cortex.agent.runtime.tool;

import com.cortex.agent.domain.AiAgentExecutionLog;
import com.cortex.agent.mapper.AiAgentExecutionLogMapper;
import com.cortex.agent.runtime.context.AgentSessionContext;
import com.cortex.agent.runtime.model.ChatMessage;
import com.cortex.agent.runtime.model.SSEEvent;
import com.cortex.agent.runtime.model.ToolCallResult;
import com.cortex.plugin.builtin.impl.SubAgentPlugin;
import com.cortex.agent.runtime.util.ImageStorageService;
import com.cortex.plugin.domain.AiPlugin;
import com.cortex.plugin.domain.AiPluginTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * 工具执行器 — 参考 hermes tool_executor.py
 * 并发执行多个 tool_call，支持审批拦截
 *
 * @author cortex
 */
@Component
public class ToolExecutor
{
    private static final Logger log = LoggerFactory.getLogger(ToolExecutor.class);

    private static final int MAX_WORKERS = 8;

    private final ExecutorService executor = Executors.newFixedThreadPool(MAX_WORKERS);

    @Autowired
    private ToolDispatcher toolDispatcher;

    @Autowired
    private com.cortex.agent.runtime.approval.ApprovalManager approvalManager;

    @Autowired
    private AiAgentExecutionLogMapper executionLogMapper;
    
    @Autowired
    private ImageStorageService imageStorageService;


    /** Delegate context for SubAgentPlugin (set before executeAll, cleared after) */
    private volatile SubAgentPlugin.DelegateContext pendingDelegateContext;

    public void setDelegateContext(SubAgentPlugin.DelegateContext ctx) { this.pendingDelegateContext = ctx; }
    public void clearDelegateContext() { this.pendingDelegateContext = null; }
    /**
     * 并发执行所有 tool_calls
     *
     * @param toolCalls     模型返回的工具调用列表
     * @param pluginMap     工具名 → 插件 映射
     * @param toolMap       工具名 → 工具定义 映射
     * @param ctx           会话上下文
     * @param turnId        轮次ID
     * @param iteration     当前迭代次数
     * @param sseCallback   SSE 回调(可为null)
     * @return 工具调用结果列表
     */
    public List<ToolCallResult> executeAll(
            List<ChatMessage.ToolCall> toolCalls,
            Map<String, AiPlugin> pluginMap,
            Map<String, AiPluginTool> toolMap,
            AgentSessionContext ctx,
            String turnId,
            int iteration,
            Consumer<SSEEvent> sseCallback)
    {
        List<CompletableFuture<ToolCallResult>> futures = new ArrayList<>();

        for (ChatMessage.ToolCall tc : toolCalls)
        {
            futures.add(CompletableFuture.supplyAsync(() ->
                    executeSingle(tc, pluginMap, toolMap, ctx, turnId, iteration, sseCallback), executor));
        }

        // 等待全部完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<ToolCallResult> results = new ArrayList<>();
        for (CompletableFuture<ToolCallResult> f : futures)
        {
            try
            {
                results.add(f.get());
            }
            catch (Exception e)
            {
                log.error("获取工具执行结果失败", e);
                results.add(ToolCallResult.error("unknown", "unknown", "执行异常: " + e.getMessage(), 0));
            }
        }

        return results;
    }

    /**
     * 执行单个工具调用
     */
    private ToolCallResult executeSingle(
            ChatMessage.ToolCall tc,
            Map<String, AiPlugin> pluginMap,
            Map<String, AiPluginTool> toolMap,
            AgentSessionContext ctx,
            String turnId,
            int iteration,
            Consumer<SSEEvent> sseCallback)
    {
        String toolName = tc.getName();
        String toolCallId = tc.getId();
        long startTime = System.currentTimeMillis();

        AiPluginTool tool = toolMap.get(toolName);
        AiPlugin plugin = pluginMap.get(toolName);

        if (tool == null || plugin == null)
        {
            log.error("❌ 工具不存在 [toolName={}, session={}]", toolName, ctx.getSessionId());
            return ToolCallResult.error(toolCallId, toolName, "工具或插件不存在: " + toolName, 0);
        }

        log.info("🔧 开始执行工具 [plugin={}, tool={}, session={}, arguments={}]", 
                plugin.getPluginName(), toolName, ctx.getSessionId(), 
                tc.getArguments().length() > 200 ? tc.getArguments().substring(0, 200) + "..." : tc.getArguments());

        // 审批检查（在tool_call_start之前）
        ToolCallResult approvalResult = approvalManager.check(plugin, tool, toolCallId, tc.getArguments(), ctx, sseCallback);
        if (approvalResult != null)
        {
            // 审批未通过
            log.warn("⛔ 工具审批未通过 [plugin={}, tool={}, session={}]", 
                    plugin.getPluginName(), toolName, ctx.getSessionId());
            logExecution(ctx, turnId, iteration, plugin.getPluginName(), toolName,
                    tc.getArguments(), approvalResult.toToolMessageContent(),
                    (int)(System.currentTimeMillis() - startTime), "1", "审批未通过", sseCallback);
            if (sseCallback != null)
            {
                sseCallback.accept(SSEEvent.toolCallEnd(toolName, "rejected", (int)(System.currentTimeMillis() - startTime)));
            }
            return approvalResult;
        }

        // 审批通过后才发送 tool_call_start
        if (sseCallback != null)
        {
            sseCallback.accept(SSEEvent.toolCallStart(toolName, plugin.getPluginName(), tc.getArguments()));
        }

        // Inject session context for SkillManagerPlugin isolation
        // Set SubAgentPlugin context in worker thread (thread-local does not propagate)
        if (pendingDelegateContext != null) {
            SubAgentPlugin.setContext(pendingDelegateContext);
        }
        String enrichedArgs = enrichArguments(tc.getArguments(), plugin, ctx);
        
        // 危险命令检测 (Terminal 类工具)
        String dangerWarning = checkDangerousCommand(tool, enrichedArgs);
        if (dangerWarning != null)
        {
            log.warn("⚠️ 危险命令拦截 [plugin={}, tool={}, session={}, warning={}]", 
                    plugin.getPluginName(), toolName, ctx.getSessionId(), dangerWarning);
            ToolCallResult dangerResult = ToolCallResult.error(toolCallId, toolName,
                    "危险命令被拦截: " + dangerWarning + "。如需执行, 请先获得授权。", 0);
            if (sseCallback != null)
            {
                sseCallback.accept(SSEEvent.toolCallEnd(toolName, "blocked", 0));
            }
            logExecution(ctx, turnId, iteration, plugin.getPluginName(), toolName,
                    enrichedArgs, dangerResult.toToolMessageContent(), 0, "1", "危险命令拦截: " + dangerWarning, sseCallback);
            return dangerResult;
        }

        // 执行工具
        try
        {
            log.info("🚀 调用工具执行器 [plugin={}, tool={}, pluginType={}]", 
                    plugin.getPluginName(), toolName, plugin.getPluginType());
            
            // 注入运行时上下文到ToolDispatcher（用于FileOperationPlugin等需要上下文的插件）
            toolDispatcher.setRuntimeContext(
                ctx.getSessionId(),
                ctx.getAgentId(),
                ctx.getAgentCode(),
                ctx.getBusinessSystem(),
                ctx.getUserLoginName()
            );
            
            log.info("📝 设置运行时上下文 [sessionId={}, agentId={}, agentCode={}, businessSystem={}, userLoginName={}]",
                    ctx.getSessionId(), ctx.getAgentId(), ctx.getAgentCode(), ctx.getBusinessSystem(), ctx.getUserLoginName());
            
            String result = toolDispatcher.dispatch(tool, plugin, ctx.getSessionId(), toolCallId, enrichedArgs);
            int duration = (int)(System.currentTimeMillis() - startTime);

            log.info("✅ 工具执行成功 [plugin={}, tool={}, duration={}ms, resultLength={}]", 
                    plugin.getPluginName(), toolName, duration, result != null ? result.length() : 0);

            // 处理结果中的base64图片，保存到文件系统并替换为URL
            // 注意：在清除上下文之前处理图片，确保ImageStorageService能获取到上下文
            String processedResult = imageStorageService.processAndReplaceImages(result, ctx.getSessionId());
            
            // 清除上下文（在图片处理完成后）
            toolDispatcher.clearRuntimeContext();

            ToolCallResult tcr = ToolCallResult.success(toolCallId, toolName, processedResult, duration);
            tcr.setPluginName(plugin.getPluginName());
            tcr.setSessionId(ctx.getSessionId());
            if (sseCallback != null)
            {
                // SSE推送处理后的结果（URL格式），避免前端接收大量base64数据
                sseCallback.accept(SSEEvent.toolResult(toolName, processedResult, true));
            }

            logExecution(ctx, turnId, iteration, plugin.getPluginName(), toolName,
                    tc.getArguments(), processedResult, duration, "0", null, sseCallback);

            if (sseCallback != null)
            {
                sseCallback.accept(SSEEvent.toolCallEnd(toolName, "success", duration));
            }

            SubAgentPlugin.clearContext();
            return tcr;
        }
        catch (Exception e)
        {
            int duration = (int)(System.currentTimeMillis() - startTime);
            log.error("工具执行失败 [tool={}]", toolName, e);
            
            // 确保清除上下文
            toolDispatcher.clearRuntimeContext();
            
            logExecution(ctx, turnId, iteration, plugin.getPluginName(), toolName,
                    tc.getArguments(), null, duration, "1", e.getMessage(), sseCallback);

            if (sseCallback != null)
            {
                sseCallback.accept(SSEEvent.toolCallEnd(toolName, "error", duration));
            }

            SubAgentPlugin.clearContext();
            if (sseCallback != null)
            {
                sseCallback.accept(SSEEvent.toolResult(toolName, e.getMessage(), false));
            }
            return ToolCallResult.error(toolCallId, toolName, e.getMessage(), duration);
        }
    }

    /**
     * 注入会话上下文到工具参数(用于SkillManagerPlugin隔离)
     */
    private String enrichArguments(String arguments, AiPlugin plugin, AgentSessionContext ctx)
    {
        if (plugin.getBuiltinClass() != null && plugin.getBuiltinClass().contains("SkillManager"))
        {
            try
            {
                com.alibaba.fastjson2.JSONObject obj = com.alibaba.fastjson2.JSON.parseObject(arguments);
                if (obj == null) obj = new com.alibaba.fastjson2.JSONObject();
                obj.put("_businessSystem", ctx.getBusinessSystem());
                obj.put("_userLoginName", ctx.getUserLoginName());
                obj.put("_sessionId", ctx.getSessionId());
                obj.put("_agentId", ctx.getAgentId());  // ✅ 添加 agentId，用于权限检查
                return obj.toJSONString();
            }
            catch (Exception e) { return arguments; }
        }
        return arguments;
    }

    /**
     * 危险命令检测 (仅对 Terminal 类工具生效)
     * @return null 表示安全, 非 null 表示拦截原因
     */
    private String checkDangerousCommand(AiPluginTool tool, String arguments)
    {
        if (tool == null || tool.getToolCode() == null) return null;
        String toolCode = tool.getToolCode().toLowerCase();
        if (!toolCode.contains("terminal") && !toolCode.contains("exec")
                && !toolCode.contains("shell") && !toolCode.contains("command"))
        {
            return null;
        }
        try
        {
            com.alibaba.fastjson2.JSONObject obj = com.alibaba.fastjson2.JSON.parseObject(arguments);
            if (obj == null) return null;
            String cmd = obj.getString("command");
            if (cmd == null) cmd = obj.getString("cmd");
            if (cmd == null) return null;
            return DangerousCommandDetector.detect(cmd);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /**
     * 记录执行日志
     */
    private void logExecution(AgentSessionContext ctx, String turnId, int iteration,
                              String pluginName, String toolName, String input, String output,
                              int durationMs, String status, String error, Consumer<SSEEvent> sseCallback)
    {
        try
        {
            AiAgentExecutionLog logEntry = new AiAgentExecutionLog();
            logEntry.setSessionId(ctx.getSessionId());
            logEntry.setAgentId(ctx.getAgentId());
            logEntry.setTurnId(turnId);
            logEntry.setIteration(iteration);
            logEntry.setEventType("tool_call");
            logEntry.setPluginName(pluginName);
            logEntry.setToolName(toolName);
            logEntry.setInputParams(input);
            logEntry.setOutputResult(output != null ? (output.length() > 10000 ? output.substring(0, 10000) : output) : null);
            logEntry.setDurationMs(durationMs);
            logEntry.setStatus(status);
            logEntry.setErrorMessage(error);
            logEntry.setBusinessSystem(ctx.getBusinessSystem());
            logEntry.setUserLoginName(ctx.getUserLoginName());
            executionLogMapper.insertAiAgentExecutionLog(logEntry);
        }
        catch (Exception e)
        {
            log.warn("记录执行日志失败", e);
        }
    }
}
