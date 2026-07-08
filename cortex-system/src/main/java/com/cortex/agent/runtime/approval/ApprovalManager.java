package com.cortex.agent.runtime.approval;

import com.cortex.agent.domain.AiAgentApprovalGrant;
import com.cortex.agent.mapper.AiAgentApprovalGrantMapper;
import com.cortex.agent.runtime.context.AgentSessionContext;
import com.cortex.agent.runtime.model.SSEEvent;
import com.cortex.agent.runtime.model.ToolCallResult;
import com.cortex.agent.runtime.tool.DangerousCommandDetector;
import com.cortex.plugin.domain.AiPlugin;
import com.cortex.plugin.domain.AiPluginTool;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Approval Manager - policy driven
 *
 * Three layers:
 * 1. Dangerous command detection (always on, cannot be overridden)
 * 2. Session approval mode (full = skip all, always = force all, auto = by plugin config)
 * 3. Plugin requireApproval config + grant cache
 *
 * @author cortex
 */
@Component
public class ApprovalManager
{
    private static final Logger log = LoggerFactory.getLogger(ApprovalManager.class);

    @Autowired
    private AiAgentApprovalGrantMapper grantMapper;
    
    /**
     * 审批等待队列：grantId -> CompletableFuture<Boolean>
     * 用于异步通知机制，避免阻塞式轮询
     */
    private final java.util.concurrent.ConcurrentHashMap<Long, java.util.concurrent.CompletableFuture<Boolean>> 
            pendingApprovals = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Check if a tool call needs approval or is rejected.
     *
     * 审批逻辑：
     * 1. 插件标记为需要审批（requireApproval=1）+ 前端选择"请求批准"(always) → 必须审批
     * 2. 插件标记为需要审批（requireApproval=1）+ 前端选择"完全访问"(full) → 跳过审批
     * 3. 插件标记为需要审批（requireApproval=1）+ 前端选择"自动执行"(auto) → 首次审批，后续自动
     * 4. 插件未标记需要审批（requireApproval=0）→ 无论前端选择什么都不审批
     *
     * @return null = pass, ToolCallResult = rejected (with reason)
     */
    public ToolCallResult check(AiPlugin plugin, AiPluginTool tool, String toolCallId, String arguments,
                                AgentSessionContext ctx,
                                java.util.function.Consumer<SSEEvent> sseCallback)
    {
        String pluginName = plugin.getPluginName();
        String toolName = tool.getToolName();
        Long pluginId = plugin.getPluginId();
        String sessionId = ctx.getSessionId();
        
        log.debug("审批检查 [plugin={}, tool={}, session={}, mode={}]", 
                pluginName, toolName, sessionId, ctx.getApprovalMode());

        // === Layer 1: dangerous command detection (cannot be overridden) ===
        String dangerousPattern = detectDangerous(tool, arguments);
        if (dangerousPattern != null)
        {
            String reason = "Dangerous operation detected: " + dangerousPattern;
            log.warn("⛔ 危险操作被拦截 [plugin={}, tool={}, pattern={}]",
                    pluginName, toolName, dangerousPattern);
            return ToolCallResult.rejected(toolCallId, toolName, reason);
        }

        // === Layer 2: 检查插件是否标记为需要审批 ===
        boolean pluginRequiresApproval = "1".equals(plugin.getRequireApproval());
        
        if (!pluginRequiresApproval)
        {
            // 插件未标记需要审批，直接通过
            log.debug("✅ 插件不需要审批，直接通过 [plugin={}]", pluginName);
            return null;
        }

        // === Layer 3: 插件需要审批，检查前端审批模式 ===
        String mode = ctx.getApprovalMode();
        
        // full = 完全访问模式，跳过所有审批
        if ("full".equals(mode))
        {
            log.debug("✅ 完全访问模式，跳过审批 [plugin={}]", pluginName);
            return null;
        }

        // === Layer 4: 检查会话内的审批决策缓存（内存） ===
        // 注意：always模式不使用缓存，每次都要审批
        if (!"always".equals(mode) && ctx.hasApprovalDecision(pluginId))
        {
            if (ctx.isApprovalGranted(pluginId))
            {
                log.debug("✅ 会话内已批准，直接通过 [plugin={}, mode={}]", pluginName, mode);
                return null;
            }
            else
            {
                log.debug("❌ 会话内已拒绝，阻止执行 [plugin={}]", pluginName);
                return ToolCallResult.rejected(toolCallId, toolName,
                        "Plugin authorization was denied by user");
            }
        }

        // === Layer 5: 检查数据库中的审批记录（持久化） ===
        // 注意：always模式不使用数据库缓存，每次都要审批；auto模式才使用数据库缓存
        if (!"always".equals(mode))
        {
            AiAgentApprovalGrant existingGrant = grantMapper.selectBySessionAndPlugin(sessionId, pluginId);
            if (existingGrant != null)
            {
                if ("0".equals(existingGrant.getGrantStatus()))
                {
                    // 已批准
                    ctx.setApprovalGranted(pluginId, true);
                    log.debug("✅ 数据库中已批准，直接通过 [plugin={}, mode={}]", pluginName, mode);
                    return null;
                }
                if ("1".equals(existingGrant.getGrantStatus()))
                {
                    // 已拒绝
                    ctx.setApprovalGranted(pluginId, false);
                    log.debug("❌ 数据库中已拒绝，阻止执行 [plugin={}]", pluginName);
                    return ToolCallResult.rejected(toolCallId, toolName,
                            "Plugin authorization was denied by user");
                }
                // pending状态（status=2）继续往下走，创建新的审批请求
            }
        }

        // === Layer 6: 创建审批请求，等待用户响应 ===
        AiAgentApprovalGrant grant = new AiAgentApprovalGrant();
        grant.setSessionId(sessionId);
        grant.setAgentId(ctx.getAgentId());
        grant.setPluginId(pluginId);
        grant.setPluginName(pluginName);
        grant.setGrantStatus("2"); // pending
        grant.setBusinessSystem(ctx.getBusinessSystem());
        grantMapper.insertAiAgentApprovalGrant(grant);

        log.info("🔐 创建审批请求 [grantId={}, plugin={}, tool={}, mode={}]，等待用户响应...",
                grant.getGrantId(), pluginName, toolName, mode);

        // 发送SSE事件到前端
        if (sseCallback != null)
        {
            sseCallback.accept(SSEEvent.approvalRequired(
                    grant.getGrantId(), pluginName, toolName,
                    arguments, "Plugin requires user authorization"));
            
            // 立即发送一个空的info事件作为心跳，强制刷新缓冲区
            sseCallback.accept(SSEEvent.info(""));
        }

        // 等待用户决策（轮询最多20秒）
        return waitForApprovalDecision(grant.getGrantId(), pluginId, tool, toolCallId, ctx);
    }

    /**
     * Wait for user approval decision (事件驱动 + 异步等待，最多60秒)
     * 使用CompletableFuture实现异步通知，避免阻塞式轮询
     */
    private ToolCallResult waitForApprovalDecision(Long grantId, Long pluginId, 
                                                   AiPluginTool tool, String toolCallId, AgentSessionContext ctx)
    {
        int maxWaitSeconds = 60; // 延长到60秒，给用户更多思考时间
        
        log.info("⏳ 开始等待审批决策 [grantId={}, maxWait={}s]", grantId, maxWaitSeconds);

        // 创建异步Future，等待通知
        java.util.concurrent.CompletableFuture<Boolean> future = new java.util.concurrent.CompletableFuture<>();
        pendingApprovals.put(grantId, future);
        
        try
        {
            // 异步等待审批结果（带超时）
            Boolean approved = future.get(maxWaitSeconds, java.util.concurrent.TimeUnit.SECONDS);
            
            if (approved != null && approved)
            {
                // 审批通过
                ctx.setApprovalGranted(pluginId, true);
                log.info("✅ 审批通过 [grantId={}, plugin={}]", grantId, tool.getToolName());
                return null; // pass
            }
            else
            {
                // 审批拒绝
                ctx.setApprovalGranted(pluginId, false);
                AiAgentApprovalGrant grant = grantMapper.selectAiAgentApprovalGrantByGrantId(grantId);
                String reason = grant != null ? grant.getRejectReason() : "User denied";
                log.info("❌ 审批拒绝 [grantId={}, plugin={}, reason={}]", 
                        grantId, tool.getToolName(), reason);
                return ToolCallResult.rejected(toolCallId, tool.getToolName(),
                        "❌ 用户已拒绝此操作。你无法使用【" + tool.getToolName() + "】工具。\n" +
                        "请向用户说明操作已被拒绝，并询问是否有其他方式可以帮助他们。");
            }
        }
        catch (java.util.concurrent.TimeoutException e)
        {
            // 超时 - 自动拒绝
            log.warn("⏰ 审批超时({}s) [grantId={}]，自动拒绝", maxWaitSeconds, grantId);
            
            // 更新数据库状态
            AiAgentApprovalGrant grant = grantMapper.selectAiAgentApprovalGrantByGrantId(grantId);
            if (grant != null && "2".equals(grant.getGrantStatus()))
            {
                grant.setGrantStatus("1"); // rejected
                grant.setRejectReason("Timeout: no response within " + maxWaitSeconds + " seconds");
                grantMapper.updateAiAgentApprovalGrant(grant);
            }
            
            ctx.setApprovalGranted(pluginId, false);
            return ToolCallResult.rejected(toolCallId, tool.getToolName(),
                    "❌ 审批超时（超过" + maxWaitSeconds + "秒未响应）。你无法使用【" + tool.getToolName() + "】工具。\n" +
                    "请向用户说明操作超时，并询问是否需要重试。");
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            log.warn("❌ 审批等待被中断 [grantId={}]", grantId);
            return ToolCallResult.rejected(toolCallId, tool.getToolName(),
                    "❌ 审批被中断。你无法使用【" + tool.getToolName() + "】工具。\n" +
                    "请向用户说明操作已被中断。");
        }
        catch (Exception e)
        {
            log.error("❌ 审批等待异常 [grantId={}]", grantId, e);
            return ToolCallResult.rejected(toolCallId, tool.getToolName(),
                    "❌ 审批失败。你无法使用【" + tool.getToolName() + "】工具。\n" +
                    "请向用户说明审批失败，无法执行该操作。");
        }
        finally
        {
            // 清理
            pendingApprovals.remove(grantId);
        }
    }

    /**
     * Approve a plugin grant（同步通知等待的线程）
     */
    public void approve(Long grantId, String grantedBy)
    {
        AiAgentApprovalGrant grant = grantMapper.selectAiAgentApprovalGrantByGrantId(grantId);
        if (grant == null)
        {
            throw new RuntimeException("Approval record not found: " + grantId);
        }
        grant.setGrantStatus("0"); // approved
        grant.setGrantedBy(grantedBy);
        grant.setExpireTime(null);
        grantMapper.updateAiAgentApprovalGrant(grant);
        log.info("✅ Plugin approved [grantId={}, plugin={}, grantedBy={}]",
                grantId, grant.getPluginName(), grantedBy);
        
        // 立即通知等待的线程
        java.util.concurrent.CompletableFuture<Boolean> future = pendingApprovals.remove(grantId);
        if (future != null)
        {
            future.complete(true); // 通知审批通过
            log.info("✅ 已通知等待线程继续执行 [grantId={}]", grantId);
        }
        else
        {
            log.warn("⚠️ 未找到等待的Future [grantId={}]，可能已超时", grantId);
        }
    }

    /**
     * Reject a plugin grant（同步通知等待的线程）
     */
    public void reject(Long grantId, String grantedBy, String reason)
    {
        AiAgentApprovalGrant grant = grantMapper.selectAiAgentApprovalGrantByGrantId(grantId);
        if (grant == null)
        {
            throw new RuntimeException("Approval record not found: " + grantId);
        }
        grant.setGrantStatus("1"); // rejected
        grant.setGrantedBy(grantedBy);
        grant.setRejectReason(reason);
        grantMapper.updateAiAgentApprovalGrant(grant);
        log.info("❌ Plugin rejected [grantId={}, plugin={}, grantedBy={}, reason={}]",
                grantId, grant.getPluginName(), grantedBy, reason);
        
        // 立即通知等待的线程
        java.util.concurrent.CompletableFuture<Boolean> future = pendingApprovals.remove(grantId);
        if (future != null)
        {
            future.complete(false); // 通知审批拒绝
            log.info("❌ 已通知等待线程停止执行 [grantId={}]", grantId);
        }
        else
        {
            log.warn("⚠️ 未找到等待的Future [grantId={}]，可能已超时", grantId);
        }
    }

    /**
     * List pending approvals for a session
     */
    public java.util.List<AiAgentApprovalGrant> getPending(String sessionId)
    {
        return grantMapper.selectPendingBySessionId(sessionId);
    }

    /**
     * Detect dangerous commands in tool arguments
     */
    private String detectDangerous(AiPluginTool tool, String arguments)
    {
        String toolName = tool.getToolName() != null ? tool.getToolName().toLowerCase() : "";
        String toolCode = tool.getToolCode() != null ? tool.getToolCode().toLowerCase() : "";

        if (toolName.contains("terminal") || toolName.contains("command") || toolName.contains("shell")
                || toolCode.contains("run_command") || toolCode.contains("terminal")
                || toolCode.contains("exec") || toolCode.contains("shell"))
        {
            try
            {
                JSONObject args = JSON.parseObject(arguments);
                String command = args != null ? args.getString("command") : null;
                if (command == null) command = args != null ? args.getString("cmd") : null;
                if (command != null)
                {
                    return DangerousCommandDetector.detect(command);
                }
            }
            catch (Exception e) { /* ignore */ }
        }
        return null;
    }
}
