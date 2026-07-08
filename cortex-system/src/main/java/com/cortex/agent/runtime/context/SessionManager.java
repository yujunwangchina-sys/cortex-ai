package com.cortex.agent.runtime.context;

import com.cortex.agent.domain.AiAgentSession;
import com.cortex.agent.mapper.AiAgentSessionMapper;
import com.cortex.agent.runtime.llm.OpenAiCompatibleClient;
import com.cortex.common.utils.uuid.IdUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话管理器 — 内存会话池 + 每轮落库
 *
 * @author cortex
 */
@Component
public class SessionManager
{
    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);

    /** 空闲超时(30分钟) */
    private static final long IDLE_TIMEOUT_MS = 30 * 60 * 1000;

    @Autowired
    private AiAgentSessionMapper sessionMapper;

    @Autowired
    private AgentMessageStore messageStore;

    @Autowired
    private OpenAiCompatibleClient llmClient;

    @Autowired
    private com.cortex.supplier.mapper.AiModelMapper modelMapper;

    @Autowired
    private com.cortex.supplier.mapper.AiSupplierMapper supplierMapper;

    /** 内存会话池 */
    private final ConcurrentHashMap<String, AgentSessionContext> sessions = new ConcurrentHashMap<>();

    /** 会话最后访问时间 */
    private final ConcurrentHashMap<String, Long> lastAccessTime = new ConcurrentHashMap<>();

    /**
     * 创建新会话
     */
    public AgentSessionContext createSession(Long agentId, String agentCode,
                                             String userLoginName, String businessSystem, int maxIterations)
    {
        String sessionId = IdUtils.getSnowflakeIdStr();

        AgentSessionContext ctx = new AgentSessionContext(
                sessionId, agentId, agentCode, userLoginName, businessSystem, maxIterations);

        // 持久化到数据库
        AiAgentSession session = new AiAgentSession();
        session.setSessionId(sessionId);
        session.setAgentId(agentId);
        session.setAgentCode(agentCode);
        session.setUserLoginName(userLoginName);
        session.setBusinessSystem(businessSystem);
        session.setTokenUsage(0);
        session.setIterationCount(0);
        session.setStatus("0");
        session.setLastMessageTime(new Date());
        session.setCreateBy(userLoginName);
        session.setCreateTime(new Date());
        sessionMapper.insertAiAgentSession(session);
        messageStore.initSession(sessionId);

        sessions.put(sessionId, ctx);
        lastAccessTime.put(sessionId, System.currentTimeMillis());

        log.info("创建会话 [sessionId={}, agent={}, user={}, system={}]",
                sessionId, agentCode, userLoginName, businessSystem);

        return ctx;
    }

    /**
     * 获取会话(从内存或数据库恢复)
     */
    public AgentSessionContext getSession(String sessionId, int maxIterations)
    {
        AgentSessionContext ctx = sessions.get(sessionId);
        if (ctx != null)
        {
            lastAccessTime.put(sessionId, System.currentTimeMillis());
            return ctx;
        }

        // 从数据库恢复
        AiAgentSession session = sessionMapper.selectAiAgentSessionBySessionId(sessionId);
        if (session == null)
        {
            return null;
        }

        ctx = new AgentSessionContext(
                session.getSessionId(),
                session.getAgentId(),
                session.getAgentCode(),
                session.getUserLoginName(),
                session.getBusinessSystem(),
                maxIterations);

        List<com.cortex.agent.runtime.model.ChatMessage> loadedMessages = messageStore.loadMessages(sessionId);
        for (com.cortex.agent.runtime.model.ChatMessage msg : loadedMessages)
        {
            ctx.addMessage(msg);
        }
        ctx.setLastFlushedSeq(loadedMessages.size());
        
        // 恢复压缩的工作上下文（如果存在）
        if (session.getCompressedContext() != null && !session.getCompressedContext().isEmpty())
        {
            try
            {
                com.alibaba.fastjson2.JSONArray arr = com.alibaba.fastjson2.JSON.parseArray(session.getCompressedContext());
                List<com.cortex.agent.runtime.model.ChatMessage> compressedMessages = new java.util.ArrayList<>();
                for (Object item : arr)
                {
                    compressedMessages.add(com.cortex.agent.runtime.model.ChatMessage.fromJson((com.alibaba.fastjson2.JSONObject) item));
                }
                ctx.setWorkingContext(compressedMessages);
                log.info("恢复会话的压缩上下文 [sessionId={}, 完整历史={}, 工作上下文={}]", 
                        sessionId, ctx.getMessages().size(), compressedMessages.size());
            }
            catch (Exception e)
            {
                log.warn("恢复压缩上下文失败，将使用完整历史 [sessionId={}]", sessionId, e);
            }
        }

        sessions.put(sessionId, ctx);
        lastAccessTime.put(sessionId, System.currentTimeMillis());

        log.info("恢复会话 [sessionId={}, messages={}]", sessionId, ctx.getMessages().size());

        return ctx;
    }

    /**
     * 每轮对话后落库
     */
    public void persistSession(AgentSessionContext ctx)
    {
        persistSession(ctx, null, null, null);
    }

    /**
     * 每轮对话后落库（支持传入模型配置用于标题生成）
     * @param ctx 会话上下文
     * @param modelId 用户选择的模型ID（可选）
     * @param model 模型配置（可选，如果为null会根据modelId查询）
     * @param sseCallback SSE回调（用于实时推送标题）
     */
    public void persistSession(AgentSessionContext ctx, Long modelId, com.cortex.supplier.domain.AiModel model, 
                               java.util.function.Consumer<com.cortex.agent.runtime.model.SSEEvent> sseCallback)
    {
        try
        {
            // Incremental flush: only new messages go to Redis + DB
            int newSeq = messageStore.flushNewMessages(
                    ctx.getSessionId(), ctx.getMessages(),
                    ctx.getLastFlushedSeq(), ctx.getUserLoginName());
            ctx.setLastFlushedSeq(newSeq);

            AiAgentSession session = new AiAgentSession();
            session.setSessionId(ctx.getSessionId());
            session.setTokenUsage(ctx.getTotalTokenUsage());
            session.setIterationCount(ctx.getIterationBudget().getUsed());
            session.setLastMessageTime(new Date());
            
            // 记录模型ID（如果切换了模型，更新为最新的）
            if (modelId != null)
            {
                // 检查是否切换了模型，如果切换则记录到历史中
                AiAgentSession currentSession = sessionMapper.selectAiAgentSessionBySessionId(ctx.getSessionId());
                if (currentSession != null && currentSession.getModelId() != null 
                    && !currentSession.getModelId().equals(modelId))
                {
                    // 模型发生切换，记录到历史
                    log.info("检测到模型切换 [sessionId={}, 旧模型={}, 新模型={}]",
                            ctx.getSessionId(), currentSession.getModelId(), modelId);
                    
                    // 追加到模型切换历史
                    String history = currentSession.getModelSwitchHistory();
                    com.alibaba.fastjson2.JSONArray historyArray;
                    if (history != null && !history.isEmpty())
                    {
                        historyArray = com.alibaba.fastjson2.JSON.parseArray(history);
                    }
                    else
                    {
                        historyArray = new com.alibaba.fastjson2.JSONArray();
                    }
                    
                    com.alibaba.fastjson2.JSONObject switchRecord = new com.alibaba.fastjson2.JSONObject();
                    switchRecord.put("turnId", ctx.getMessages().size());
                    switchRecord.put("fromModelId", currentSession.getModelId());
                    switchRecord.put("toModelId", modelId);
                    switchRecord.put("timestamp", new Date().getTime());
                    historyArray.add(switchRecord);
                    
                    session.setModelSwitchHistory(historyArray.toJSONString());
                }
                
                session.setModelId(modelId);
            }
            
            // 持久化压缩的工作上下文（如果存在）
            List<com.cortex.agent.runtime.model.ChatMessage> workingCtx = ctx.getWorkingContext();
            if (workingCtx != null && !workingCtx.isEmpty() && workingCtx.size() < ctx.getMessages().size())
            {
                // 只有在工作上下文被压缩时才保存
                com.alibaba.fastjson2.JSONArray arr = new com.alibaba.fastjson2.JSONArray();
                for (com.cortex.agent.runtime.model.ChatMessage msg : workingCtx)
                {
                    arr.add(msg.toJson());
                }
                session.setCompressedContext(arr.toJSONString());
                log.debug("持久化压缩上下文 [sessionId={}, 完整历史={}, 工作上下文={}]",
                        ctx.getSessionId(), ctx.getMessages().size(), workingCtx.size());
            }
            
            session.setUpdateTime(new Date());
            sessionMapper.updateSessionStats(session);

            // 首次对话生成标题（不依赖消息数量，工具调用会增加消息数）
            if (!ctx.getMessages().isEmpty() && "user".equals(ctx.getMessages().get(0).getRole()))
            {
                AiAgentSession titleCheck = sessionMapper.selectAiAgentSessionBySessionId(ctx.getSessionId());
                if (titleCheck != null && (titleCheck.getTitle() == null || titleCheck.getTitle().isEmpty()))
                {
                    generateAndUpdateTitle(ctx, modelId, model, sseCallback);
                }
            }

            log.debug("会话落库 [sessionId={}, messages={}, tokens={}]",
                    ctx.getSessionId(), ctx.getMessages().size(), ctx.getTotalTokenUsage());
        }
        catch (Exception e)
        {
            log.error("会话落库失败 [sessionId={}]", ctx.getSessionId(), e);
        }
    }

    /**
     * 生成并更新会话标题（同步，但不阻塞主要对话流程）
     * 通过 SSE 实时推送标题给前端
     */
    private void generateAndUpdateTitle(AgentSessionContext ctx, Long modelId, com.cortex.supplier.domain.AiModel model,
                                       java.util.function.Consumer<com.cortex.agent.runtime.model.SSEEvent> sseCallback)
    {
        try
        {
            String userMessage = ctx.getMessages().get(0).getContent();
            if (userMessage != null && userMessage.length() > 500)
            {
                userMessage = userMessage.substring(0, 500);
            }
            String title = generateTitle(userMessage, modelId, model);
            
            if (title != null && !title.isEmpty())
            {
                // 更新数据库
                AiAgentSession session = new AiAgentSession();
                session.setSessionId(ctx.getSessionId());
                session.setTitle(title);
            session.setUpdateTime(new Date());
                sessionMapper.updateAiAgentSession(session);
                
                log.info("会话标题已生成 [sessionId={}, title={}]", ctx.getSessionId(), title);
                
                // 通过 SSE 推送标题给前端
                if (sseCallback != null)
                {
                    sseCallback.accept(com.cortex.agent.runtime.model.SSEEvent.titleGenerated(
                        ctx.getSessionId(), title));
                }
            }
        }
        catch (Exception e)
        {
            log.warn("生成会话标题失败 [sessionId={}]", ctx.getSessionId(), e);
        }
    }

    /**
     * 使用LLM生成会话标题（不超过20字）
     * 使用用户选择的模型和模型配置的参数
     */
    private String generateTitle(String userMessage, Long modelId, com.cortex.supplier.domain.AiModel modelConfig)
    {
        try
        {
            // 1. 获取模型配置
            com.cortex.supplier.domain.AiModel model = modelConfig;
            
            if (model == null && modelId != null)
            {
                model = modelMapper.selectAiModelByModelId(modelId);
            }
            
            if (model == null)
            {
                // 获取默认的chat模型
                com.cortex.supplier.domain.AiModel query = new com.cortex.supplier.domain.AiModel();
                query.setModelType("chat");
                query.setStatus("0");
                java.util.List<com.cortex.supplier.domain.AiModel> models = modelMapper.selectAiModelList(query);
                
                if (models == null || models.isEmpty())
                {
                    log.warn("未找到可用的chat模型，无法生成标题");
                    return null;
                }
                
                model = models.get(0);
            }
            
            // 2. 获取供应商配置
            com.cortex.supplier.domain.AiSupplier supplier = supplierMapper.selectAiSupplierBySupplierId(model.getSupplierId());
            if (supplier == null)
            {
                log.warn("未找到模型对应的供应商配置 [supplierId={}]", model.getSupplierId());
                return null;
            }
            
            // 3. 构建LLM请求
            com.cortex.agent.runtime.llm.LlmRequest request = new com.cortex.agent.runtime.llm.LlmRequest();
            request.setBaseUrl(supplier.getApiBaseUrl());
            request.setApiKey(supplier.getApiKey());
            request.setModel(model.getModelCode());
            
            // 4. 构建消息
            java.util.List<com.cortex.agent.runtime.model.ChatMessage> messages = new java.util.ArrayList<>();
            messages.add(com.cortex.agent.runtime.model.ChatMessage.system(
                    "你是一个会话标题生成助手。根据用户的第一条消息，识别用户的意图，生成一个简洁的标题，不超过20个字，直接输出标题文本，不要有任何解释或标点符号。"
            ));
            messages.add(com.cortex.agent.runtime.model.ChatMessage.user(
                    "用户消息：" + userMessage
            ));
            
            request.setMessages(messages);
            
            // 5. 使用模型配置的参数
            if (model.getTemperature() != null)
            {
                request.setTemperature(model.getTemperature());
            }
            else
            {
                request.setTemperature(new BigDecimal(0.7));
            }
            
            if (model.getTopP() != null)
            {
                request.setTopP(model.getTopP());
            }
            
            // 标题生成只需要少量token
            request.setMaxTokens(50);
            
            // 6. 调用LLM
            com.cortex.agent.runtime.llm.LlmResponse response = llmClient.chatCompletion(request);
            String title = response.getContent();
            
            // 7. 清理标题
            if (title != null)
            {
                title = title.trim()
                        .replaceAll("^[\"'《「【]|[\"'》」】]$", "")
                        .replaceAll("[。！？.,!?]$", "");
                
                if (title.length() > 20)
                {
                    title = title.substring(0, 20);
                }
            }
            
            return title;
        }
        catch (Exception e)
        {
            log.error("调用LLM生成标题失败", e);
            return null;
        }
    }

    /**
     * 结束会话
     */
    public void closeSession(String sessionId)
    {
        AgentSessionContext ctx = sessions.remove(sessionId);
        if (ctx != null)
        {
            persistSession(ctx);
            messageStore.closeSession(ctx.getSessionId());
            AiAgentSession session = new AiAgentSession();
            session.setSessionId(sessionId);
            session.setStatus("1");
            session.setUpdateTime(new Date());
            sessionMapper.updateAiAgentSession(session);
        }
        lastAccessTime.remove(sessionId);
        log.info("会话已结束 [sessionId={}]", sessionId);
    }

    /**
     * 移除会话(删除)
     */
    public void removeSession(String sessionId)
    {
        sessions.remove(sessionId);
        lastAccessTime.remove(sessionId);
        messageStore.deleteSession(sessionId);
        sessionMapper.deleteAiAgentSessionBySessionId(sessionId);
    }

    /**
     * 查询会话列表
     */
    public List<AiAgentSession> listSessions(String businessSystem, String userLoginName)
    {
        AiAgentSession query = new AiAgentSession();
        if (businessSystem != null && !businessSystem.isEmpty())
        {
            query.setBusinessSystem(businessSystem);
        }
        if (userLoginName != null && !userLoginName.isEmpty())
        {
            query.setUserLoginName(userLoginName);
        }
        return sessionMapper.selectAiAgentSessionList(query);
    }

    /**
     * 获取业务系统下的用户列表
     */
    public List<String> listUsers(String businessSystem)
    {
        return sessionMapper.selectDistinctUserLoginNames(businessSystem);
    }

    /**
     * 获取会话详情
     */
    public AiAgentSession getSessionDetail(String sessionId)
    {
        return sessionMapper.selectAiAgentSessionBySessionId(sessionId);
    }

    /**
     * Get session messages (from Redis or DB).
     */
    public java.util.List<com.cortex.agent.runtime.model.ChatMessage> getSessionMessages(String sessionId)
    {
        return messageStore.loadMessages(sessionId);
    }

    /**
     * 定时清理空闲会话(每5分钟)
     */
    @Scheduled(fixedRate = 300000)
    public void cleanupIdleSessions()
    {
        long now = System.currentTimeMillis();
        for (String sessionId : lastAccessTime.keySet())
        {
            Long lastAccess = lastAccessTime.get(sessionId);
            if (lastAccess != null && (now - lastAccess) > IDLE_TIMEOUT_MS)
            {
                log.info("清理空闲会话 [sessionId={}]", sessionId);
                closeSession(sessionId);
            }
        }

        // 清理数据库中的空闲会话(60分钟)
        try
        {
            java.util.Date cutoff = new java.util.Date(System.currentTimeMillis() - 60 * 60 * 1000);
            sessionMapper.cleanupIdleSessions(cutoff);
        }
        catch (Exception e)
        {
            log.warn("清理数据库空闲会话失败", e);
        }
    }

    /**
     * 获取完整会话树(业务系统 > 用户)
     */
    public List<java.util.Map<String, Object>> getSessionTree()
    {
        return sessionMapper.selectSessionTree();
    }

    /**
     * 请求中断会话
     */
    public void requestInterrupt(String sessionId)
    {
        AgentSessionContext ctx = sessions.get(sessionId);
        if (ctx != null)
        {
            ctx.requestInterrupt();
            log.info("中断请求已发送 [sessionId={}]", sessionId);
        }
    }

    public int getActiveSessionCount()
    {
        return sessions.size();
    }
    /**
     * 查询所有业务系统
     */
    public List<String> listBusinessSystems()
    {
        return sessionMapper.selectAllBusinessSystems();
    }

}
