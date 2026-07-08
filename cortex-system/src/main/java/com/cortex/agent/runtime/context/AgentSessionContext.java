package com.cortex.agent.runtime.context;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.cortex.agent.runtime.model.ChatMessage;
import com.cortex.agent.runtime.model.SSEEvent;
import com.cortex.agent.runtime.model.ToolCallResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Agent 会话上下文 — 每个会话一个实例
 * 线程安全：同一会话的对话循环串行化
 *
 * @author cortex
 */
public class AgentSessionContext
{
    private static final Logger log = LoggerFactory.getLogger(AgentSessionContext.class);

    private final String sessionId;
    private final Long agentId;
    private final String agentCode;
    private final String userLoginName;
    private final String businessSystem;

    /** 消息历史（完整历史，用于持久化和前端显示）*/
    private final List<ChatMessage> messages = new ArrayList<>();
    
    /** 压缩后的工作上下文（用于发送给LLM，可能被压缩）*/
    private List<ChatMessage> workingContext = null;

    /** 迭代预算 */
    private final IterationBudget iterationBudget;

    /** 累计token */
    private int totalTokenInput = 0;
    private int totalTokenOutput = 0;

    /** Last iteration prompt tokens (for compression decisions) */
    private volatile int lastPromptTokens = 0;
    /** 会话锁 — 防止同一会话并发 */
    private final ReentrantLock sessionLock = new ReentrantLock();

    /** 系统提示词 */
    private String systemPrompt;

    /** 审批模式: auto/always/full */
    private String approvalMode = "auto";

    /** 中断标志 — 用户可打断正在进行的 LLM 调用 */
    private volatile boolean interruptRequested = false;

    /** 授权缓存: pluginId → 已授权(true)/已拒绝(false) */
    private final java.util.concurrent.ConcurrentHashMap<Long, Boolean> approvalCache = new java.util.concurrent.ConcurrentHashMap<>();

    /** 是否已从数据库加载消息 */
    private boolean loaded = false;

    /** Index of the last message flushed to DB/Redis (for incremental persistence) */
    private int lastFlushedSeq = 0;

    public AgentSessionContext(String sessionId, Long agentId, String agentCode,
                               String userLoginName, String businessSystem, int maxIterations)
    {
        this.sessionId = sessionId;
        this.agentId = agentId;
        this.agentCode = agentCode;
        this.userLoginName = userLoginName;
        this.businessSystem = businessSystem;
        this.iterationBudget = new IterationBudget(maxIterations);
    }

    /**
     * 获取会话锁
     */
    public void lock() { sessionLock.lock(); }
    public void unlock() { sessionLock.unlock(); }

    /**
     * 从 JSON 恢复消息历史
     */
    public void loadMessages(String messagesJson)
    {
        if (messagesJson == null || messagesJson.isEmpty() || loaded)
        {
            loaded = true;
            return;
        }
        messages.clear();
        try
        {
            JSONArray arr = JSON.parseArray(messagesJson);
            for (Object item : arr)
            {
                messages.add(ChatMessage.fromJson((JSONObject) item));
            }
        }
        catch (Exception e)
        {
            log.warn("加载消息历史失败, session={}", sessionId, e);
        }
        loaded = true;
    }

    /**
     * 序列化消息历史为 JSON
     */
    public String serializeMessages()
    {
        JSONArray arr = new JSONArray();
        for (ChatMessage msg : messages)
        {
            arr.add(msg.toJson());
        }
        return arr.toJSONString();
    }

    /**
     * 添加消息
     */
    public void addMessage(ChatMessage message)
    {
        messages.add(message);
    }

    /**
     * 获取消息列表（完整历史，用于持久化和前端显示）
     */
    public List<ChatMessage> getMessages()
    {
        return new ArrayList<>(messages);
    }
    
    /**
     * 获取工作上下文（用于发送给LLM，可能被压缩）
     * 如果还没有压缩过，返回完整历史
     */
    public List<ChatMessage> getWorkingContext()
    {
        return workingContext != null ? new ArrayList<>(workingContext) : getMessages();
    }
    
    /**
     * 设置工作上下文（压缩后的消息）
     * 注意：这不会影响完整历史记录
     */
    public void setWorkingContext(List<ChatMessage> compressed)
    {
        this.workingContext = new ArrayList<>(compressed);
    }
    
    /**
     * 清除工作上下文（下次会重新从完整历史构建）
     */
    public void clearWorkingContext()
    {
        this.workingContext = null;
    }

    /**
     * 替换全部消息(上下文压缩后使用)
     * 
     * @deprecated 不再使用此方法，上下文压缩只影响发送给LLM的临时消息副本，
     *             不应该修改会话的完整历史记录。保留此方法仅为兼容性。
     */
    @Deprecated
    public void replaceMessages(List<ChatMessage> newMessages)
    {
        messages.clear();
        messages.addAll(newMessages);
        // Compression replaced all messages; reset flush pointer so
        // the compressed set is re-persisted from scratch.
        this.lastFlushedSeq = 0;
    }

    /**
     * 重置迭代预算（每轮对话开始时调用）
     */
    public void resetBudget()
    {
        iterationBudget.reset();
    }

    public IterationBudget getIterationBudget() { return iterationBudget; }
    public String getSessionId() { return sessionId; }
    public Long getAgentId() { return agentId; }
    public String getAgentCode() { return agentCode; }
    public String getUserLoginName() { return userLoginName; }
    public String getBusinessSystem() { return businessSystem; }
    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    public int getTotalTokenInput() { return totalTokenInput; }

    public String getApprovalMode() { return approvalMode; }
    public void setApprovalMode(String approvalMode) { this.approvalMode = approvalMode; }

    public boolean isInterruptRequested() { return interruptRequested; }
    public void requestInterrupt() { this.interruptRequested = true; }
    public void clearInterrupt() { this.interruptRequested = false; }
    public int getTotalTokenOutput() { return totalTokenOutput; }

    public int getLastPromptTokens() { return lastPromptTokens; }
    public void addTokenUsage(int input, int output)
    {
        totalTokenInput += input;
        totalTokenOutput += output;
        this.lastPromptTokens = input;
    }

    public int getTotalTokenUsage() { return totalTokenInput + totalTokenOutput; }

    public boolean isApprovalGranted(Long pluginId)
    {
        return approvalCache.getOrDefault(pluginId, false);
    }

    public void setApprovalGranted(Long pluginId, boolean granted)
    {
        approvalCache.put(pluginId, granted);
    }

    public boolean hasApprovalDecision(Long pluginId)
    {
        return approvalCache.containsKey(pluginId);
    }

    public int getLastFlushedSeq() { return lastFlushedSeq; }
    public void setLastFlushedSeq(int lastFlushedSeq) { this.lastFlushedSeq = lastFlushedSeq; }
}
