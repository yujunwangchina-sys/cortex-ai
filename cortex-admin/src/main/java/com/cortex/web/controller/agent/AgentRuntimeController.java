package com.cortex.web.controller.agent;

import com.cortex.agent.domain.AiAgentApprovalGrant;
import com.cortex.agent.domain.AiAgentExecutionLog;
import com.cortex.agent.domain.AiAgentSession;
import com.cortex.agent.runtime.approval.ApprovalManager;
import com.cortex.agent.runtime.context.SessionManager;
import com.cortex.agent.runtime.model.AgentRunRequest;
import com.cortex.agent.runtime.model.AgentRunResult;
import com.cortex.agent.runtime.model.SSEEvent;
import com.cortex.agent.service.IAgentRuntimeService;
import com.cortex.agent.service.IAiAgentService;
import com.cortex.common.core.controller.BaseController;
import com.cortex.common.core.domain.AjaxResult;
import com.cortex.common.core.page.TableDataInfo;
import com.cortex.supplier.mapper.AiModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Agent运行时Controller
 *
 * @author cortex
 */
@RestController
@RequestMapping("/agent/api")
public class AgentRuntimeController extends BaseController
{
    private static final Logger log = LoggerFactory.getLogger(AgentRuntimeController.class);

    private final ExecutorService sseExecutor = Executors.newCachedThreadPool();

    @Autowired
    private IAgentRuntimeService runtimeService;

    @Autowired
    private IAiAgentService aiAgentService;

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private ApprovalManager approvalManager;

    @Autowired
    private AiModelMapper modelMapper;

    @Autowired
    private com.cortex.agent.mapper.AiAgentExecutionLogMapper executionLogMapper;

    @Autowired
    private com.cortex.agent.mapper.AiAgentApprovalGrantMapper approvalGrantMapper;


    /**
     * 非流式对话
     */
    @PostMapping("/chat")
    public AjaxResult chat(@RequestBody AgentRunRequest request,
                           @RequestHeader(value = "X-Business-System", defaultValue = "cortex") String businessSystem,
                           @RequestHeader(value = "X-API-Key", required = false) String headerApiKey)
    {
        try
        {
            request.setBusinessSystem(businessSystem);
            
            // API Key 验证（外部系统调用）
            String apiKey = request.getApiKey() != null ? request.getApiKey() : headerApiKey;
            if (apiKey != null && !apiKey.isEmpty())
            {
                // 外部系统调用，验证 API Key
                if (!aiAgentService.validateApiKey(request.getAgentCode(), apiKey, businessSystem))
                {
                    return AjaxResult.error("API Key 验证失败或 Agent 未授权给该业务系统");
                }
                // API Key 验证通过，使用请求中的用户名
                if (request.getUserLoginName() == null || request.getUserLoginName().isEmpty())
                {
                    return AjaxResult.error("外部调用必须提供 userLoginName 参数");
                }
            }
            else
            {
                // 内部系统调用，使用当前登录用户
                if (request.getUserLoginName() == null || request.getUserLoginName().isEmpty())
                {
                    request.setUserLoginName(getUsername());
                }
            }
            
            AgentRunResult result = runtimeService.run(request);
            return AjaxResult.success(result);
        }
        catch (Exception e)
        {
            log.error("对话失败", e);
            return AjaxResult.error("对话失败: " + e.getMessage());
        }
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody AgentRunRequest request,
                                 @RequestHeader(value = "X-Business-System", defaultValue = "cortex") String businessSystem,
                                 @RequestHeader(value = "X-API-Key", required = false) String headerApiKey)
    {
        // 设置较长的超时时间，并禁用缓冲以实现实时推送
        SseEmitter emitter = new SseEmitter(300000L); // 5分钟超时

        request.setBusinessSystem(businessSystem);
        
        // API Key 验证（外部系统调用）
        String apiKey = request.getApiKey() != null ? request.getApiKey() : headerApiKey;
        if (apiKey != null && !apiKey.isEmpty())
        {
            // 外部系统调用，验证 API Key
            if (!aiAgentService.validateApiKey(request.getAgentCode(), apiKey, businessSystem))
            {
                try
                {
                    emitter.send(SseEmitter.event().data(
                            new SSEEvent("error", java.util.Map.of(
                                    "message", "API Key 验证失败或 Agent 未授权给该业务系统",
                                    "type", "auth_error")).toSseString()));
                }
                catch (IOException ignored) {}
                emitter.completeWithError(new RuntimeException("API Key 验证失败"));
                return emitter;
            }
            // API Key 验证通过，使用请求中的用户名
            if (request.getUserLoginName() == null || request.getUserLoginName().isEmpty())
            {
                try
                {
                    emitter.send(SseEmitter.event().data(
                            new SSEEvent("error", java.util.Map.of(
                                    "message", "外部调用必须提供 userLoginName 参数",
                                    "type", "param_error")).toSseString()));
                }
                catch (IOException ignored) {}
                emitter.completeWithError(new RuntimeException("缺少 userLoginName 参数"));
                return emitter;
            }
        }
        else
        {
            // 内部系统调用，使用当前登录用户
            if (request.getUserLoginName() == null || request.getUserLoginName().isEmpty())
            {
                request.setUserLoginName(getUsername());
            }
        }

        sseExecutor.execute(() -> {
            try
            {
                // 🔴 添加连接状态检查标志
                final boolean[] connectionClosed = {false};
                
                // 🔴 设置超时和完成回调，检测连接断开
                emitter.onTimeout(() -> {
                    log.info("SSE连接超时，会话ID: {}", request.getSessionId());
                    connectionClosed[0] = true;
                    // 通知后端中断任务
                    if (request.getSessionId() != null) {
                        try {
                            sessionManager.requestInterrupt(request.getSessionId());
                        } catch (Exception e) {
                            log.warn("中断会话失败: {}", e.getMessage());
                        }
                    }
                });
                
                emitter.onCompletion(() -> {
                    log.debug("SSE连接正常完成");
                });
                
                emitter.onError(throwable -> {
                    log.info("SSE连接异常断开，会话ID: {}", request.getSessionId(), throwable);
                    connectionClosed[0] = true;
                    // 通知后端中断任务
                    if (request.getSessionId() != null) {
                        try {
                            sessionManager.requestInterrupt(request.getSessionId());
                        } catch (Exception e) {
                            log.warn("中断会话失败: {}", e.getMessage());
                        }
                    }
                });
                
                runtimeService.runStream(request, sseEvent -> {
                    // 🔴 检查连接是否已断开，如果断开则不再发送事件
                    if (connectionClosed[0]) {
                        log.debug("连接已断开，跳过事件发送");
                        throw new RuntimeException("客户端连接已断开");
                    }
                    
                    try
                    {
                        // 发送SSE事件，使用注释确保立即发送
                        emitter.send(SseEmitter.event()
                                .data(sseEvent.toSseString())
                                .comment(""));  // 添加空注释强制刷新缓冲区
                    }
                    catch (IllegalStateException e)
                    {
                        // Emitter已完成，标记连接已关闭
                        connectionClosed[0] = true;
                        log.debug("SSE emitter已完成，忽略后续事件");
                        throw new RuntimeException("客户端连接已断开");
                    }
                    catch (IOException e)
                    {
                        // 客户端断开，标记连接已关闭
                        connectionClosed[0] = true;
                        log.debug("SSE发送失败（客户端可能已断开）", e);
                        throw new RuntimeException("客户端连接已断开", e);
                    }
                });
                emitter.complete();
            }
            catch (Exception e)
            {
                log.error("SSE对话失败", e);
                try
                {
                    emitter.send(SseEmitter.event().data(
                            new SSEEvent("error", java.util.Map.of("message", e.getMessage(), "type", "runtime_error")).toSseString()));
                }
                catch (IOException ignored) {}
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * 会话列表
     */
    @PreAuthorize("@ss.hasPermi('runtime:session:query')")
    @GetMapping("/session/list")
    public TableDataInfo sessionList(
            @RequestParam(required = false) String businessSystem,
            @RequestParam(required = false) String userLoginName)
    {
        startPage();
        List<AiAgentSession> list = sessionManager.listSessions(businessSystem, userLoginName);
        return getDataTable(list);
    }
    
    /**
     * 创建新会话
     */
    @PostMapping("/session/create")
    public AjaxResult createSession(@RequestBody java.util.Map<String, String> params)
    {
        try
        {
            String agentCode = params.get("agentCode");
            String userLoginName = params.get("userLoginName");
            String businessSystem = params.get("businessSystem");
            
            if (agentCode == null || agentCode.isEmpty())
            {
                return AjaxResult.error("agentCode不能为空");
            }
            
            if (userLoginName == null || userLoginName.isEmpty())
            {
                userLoginName = getUsername();
            }
            
            if (businessSystem == null || businessSystem.isEmpty())
            {
                businessSystem = "cortex";
            }
            
            // 获取AgentId
            com.cortex.agent.domain.AiAgent agent = aiAgentService.selectAiAgentByCode(agentCode);
            if (agent == null)
            {
                return AjaxResult.error("Agent不存在: " + agentCode);
            }
            
            // 创建会话
            com.cortex.agent.runtime.context.AgentSessionContext session = 
                sessionManager.createSession(agent.getId(), agentCode, userLoginName, businessSystem, 100);
            
            java.util.Map<String, String> result = new java.util.HashMap<>();
            result.put("sessionId", session.getSessionId());
            
            log.info("✅ 新会话已创建 [sessionId={}, agent={}, user={}]", 
                     session.getSessionId(), agentCode, userLoginName);
            
            return AjaxResult.success(result);
        }
        catch (Exception e)
        {
            log.error("❌ 创建会话失败", e);
            return AjaxResult.error("创建会话失败: " + e.getMessage());
        }
    }

    /**
     * 用户列表(按业务系统)
     */
    @GetMapping("/session/users")
    @PreAuthorize("@ss.hasPermi('runtime:session:query')")
    public AjaxResult sessionUsers(@RequestParam(defaultValue = "cortex") String businessSystem)
    {
        return AjaxResult.success(sessionManager.listUsers(businessSystem));
    }


    /**
     * Session tree (business system > users, one query)
     */
    @GetMapping("/session/tree")
    @PreAuthorize("@ss.hasPermi('runtime:session:query')")
    public AjaxResult sessionTree()
    {
        return AjaxResult.success(sessionManager.getSessionTree());
    }

    /**
     * 会话详情
     */
    @GetMapping("/session/{sessionId}")
    @PreAuthorize("@ss.hasPermi('runtime:session:query')")
    public AjaxResult sessionDetail(@PathVariable String sessionId)
    {
        com.cortex.agent.domain.AiAgentSession detail = sessionManager.getSessionDetail(sessionId);
        if (detail != null)
        {
            // Load messages from Redis/DB instead of the deprecated JSON blob
            java.util.List<com.cortex.agent.runtime.model.ChatMessage> messages =
                    sessionManager.getSessionMessages(sessionId);
            com.alibaba.fastjson2.JSONArray msgArr = new com.alibaba.fastjson2.JSONArray();
            for (com.cortex.agent.runtime.model.ChatMessage msg : messages)
            {
                msgArr.add(msg.toJson());
            }
            detail.setMessages(msgArr.toJSONString());
        }
        return AjaxResult.success(detail);
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/session/{sessionId}")
    @PreAuthorize("@ss.hasPermi('runtime:session:remove')")
    public AjaxResult deleteSession(@PathVariable String sessionId)
    {
        sessionManager.removeSession(sessionId);
        return AjaxResult.success();
    }

    /**
     * 中断会话
     */
    @PostMapping("/session/{sessionId}/interrupt")
    @PreAuthorize("@ss.hasPermi('runtime:session:remove')")
    public AjaxResult interruptSession(@PathVariable String sessionId)
    {
        sessionManager.requestInterrupt(sessionId);
        return AjaxResult.success();
    }

    /**
     * 待审批列表
     */
    @GetMapping("/approval/pending/{sessionId}")
    @PreAuthorize("@ss.hasPermi('runtime:approval:query')")
    public AjaxResult pendingApprovals(@PathVariable String sessionId)
    {
        return AjaxResult.success(approvalManager.getPending(sessionId));
    }

    /**
     * 批准
     */
    @PostMapping("/approval/{grantId}/approve")
    @PreAuthorize("@ss.hasPermi('runtime:approval:approve')")
    public AjaxResult approve(@PathVariable Long grantId)
    {
        approvalManager.approve(grantId, getUsername());
        return AjaxResult.success();
    }

    /**
     * 拒绝
     */
    @PostMapping("/approval/{grantId}/reject")
    @PreAuthorize("@ss.hasPermi('runtime:approval:reject')")
    public AjaxResult reject(@PathVariable Long grantId, @RequestBody(required = false) java.util.Map<String, String> body)
    {
        String reason = body != null ? body.get("reason") : null;
        approvalManager.reject(grantId, getUsername(), reason);
        return AjaxResult.success();
    }
    /**
     * 所有业务系统列表(中台视角)
     */
    @GetMapping("/business-systems")
    @PreAuthorize("@ss.hasPermi('runtime:session:query')")
    public AjaxResult listBusinessSystems()
    {
        return AjaxResult.success(sessionManager.listBusinessSystems());
    }


    // ==================== 执行日志 ====================

    /**
     * 执行日志列表
     */
    @PreAuthorize("@ss.hasPermi('runtime:execlog:query')")
    @GetMapping("/execlog/list")
    public TableDataInfo execlogList(
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String businessSystem,
            @RequestParam(required = false) String userLoginName)
    {
        startPage();
        com.cortex.agent.domain.AiAgentExecutionLog query = new com.cortex.agent.domain.AiAgentExecutionLog();
        query.setSessionId(sessionId);
        query.setEventType(eventType);
        query.setStatus(status);
        query.setBusinessSystem(businessSystem);
        query.setUserLoginName(userLoginName);
        java.util.List<com.cortex.agent.domain.AiAgentExecutionLog> list = executionLogMapper.selectAiAgentExecutionLogList(query);
        return getDataTable(list);
    }

    /**
     * 执行日志详情
     */
    @GetMapping("/execlog/{logId}")
    @PreAuthorize("@ss.hasPermi('runtime:execlog:query')")
    public AjaxResult execlogDetail(@PathVariable Long logId)
    {
        return AjaxResult.success(executionLogMapper.selectAiAgentExecutionLogByLogId(logId));
    }

    /**
     * 按会话查询执行日志
     */
    @GetMapping("/execlog/session/{sessionId}")
    @PreAuthorize("@ss.hasPermi('runtime:execlog:query')")
    public AjaxResult execlogBySession(@PathVariable String sessionId)
    {
        return AjaxResult.success(executionLogMapper.selectBySessionId(sessionId));
    }

    /**
     * 删除执行日志
     */
    @DeleteMapping("/execlog/{logId}")
    @PreAuthorize("@ss.hasPermi('runtime:execlog:remove')")
    public AjaxResult deleteExeclog(@PathVariable Long logId)
    {
        executionLogMapper.deleteAiAgentExecutionLogByLogId(logId);
        return AjaxResult.success();
    }

    // ==================== 审批管理 ====================

    /**
     * 审批列表(全局, 支持按状态/系统筛选)
     */
    @PreAuthorize("@ss.hasPermi('runtime:approval:query')")
    @GetMapping("/approval/list")
    public TableDataInfo approvalList(
            @RequestParam(required = false) String grantStatus,
            @RequestParam(required = false) String businessSystem,
            @RequestParam(required = false) String sessionId)
    {
        startPage();
        com.cortex.agent.domain.AiAgentApprovalGrant query = new com.cortex.agent.domain.AiAgentApprovalGrant();
        query.setGrantStatus(grantStatus);
        query.setBusinessSystem(businessSystem);
        query.setSessionId(sessionId);
        java.util.List<com.cortex.agent.domain.AiAgentApprovalGrant> list = approvalGrantMapper.selectAiAgentApprovalGrantList(query);
        return getDataTable(list);
    }

    /**
     * 审批详情
     */
    @GetMapping("/approval/{grantId}")
    @PreAuthorize("@ss.hasPermi('runtime:approval:query')")
    public AjaxResult approvalDetail(@PathVariable Long grantId)
    {
        return AjaxResult.success(approvalGrantMapper.selectAiAgentApprovalGrantByGrantId(grantId));
    }

    /**
     * 删除审批记录
     */
    @DeleteMapping("/approval/{grantId}")
    @PreAuthorize("@ss.hasPermi('runtime:approval:remove')")
    public AjaxResult deleteApproval(@PathVariable Long grantId)
    {
        approvalGrantMapper.deleteAiAgentApprovalGrantByGrantId(grantId);
        return AjaxResult.success();
    }

    /**
     * 可用模型列表(供对话界面切换)
     */
    @GetMapping("/models")
    @PreAuthorize("@ss.hasPermi('runtime:session:query')")
    public AjaxResult listModels(@RequestParam(required = false) String modelType)
    {
        com.cortex.supplier.domain.AiModel query = new com.cortex.supplier.domain.AiModel();
        if (modelType != null && !modelType.isEmpty())
        {
            query.setModelType(modelType);
        }
        query.setStatus("0");
        java.util.List<com.cortex.supplier.domain.AiModel> models = modelMapper.selectAiModelList(query);
        return AjaxResult.success(models);
    }
}
