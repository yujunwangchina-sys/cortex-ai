package com.cortex.agent.runtime.context;

/**
 * 运行时上下文持有者（ThreadLocal实现）
 * 使用ThreadLocal存储当前线程的运行时上下文
 * 
 * @author cortex
 */
public class RuntimeContextHolder
{
    private static final ThreadLocal<RuntimeContext> contextHolder = new ThreadLocal<>();
    
    /**
     * 设置当前线程的运行时上下文
     */
    public static void setContext(RuntimeContext context)
    {
        contextHolder.set(context);
    }
    
    /**
     * 获取当前线程的运行时上下文
     */
    public static RuntimeContext getContext()
    {
        return contextHolder.get();
    }
    
    /**
     * 清除当前线程的运行时上下文
     */
    public static void clearContext()
    {
        contextHolder.remove();
    }
    
    /**
     * 获取会话ID
     */
    public static String getSessionId()
    {
        RuntimeContext context = getContext();
        return context != null ? context.getSessionId() : null;
    }
    
    /**
     * 获取Agent ID
     */
    public static Long getAgentId()
    {
        RuntimeContext context = getContext();
        return context != null ? context.getAgentId() : null;
    }
    
    /**
     * 获取Agent代码
     */
    public static String getAgentCode()
    {
        RuntimeContext context = getContext();
        return context != null ? context.getAgentCode() : null;
    }
    
    /**
     * 获取业务系统
     */
    public static String getBusinessSystem()
    {
        RuntimeContext context = getContext();
        return context != null ? context.getBusinessSystem() : null;
    }
    
    /**
     * 获取用户登录名
     */
    public static String getUserLoginName()
    {
        RuntimeContext context = getContext();
        return context != null ? context.getUserLoginName() : null;
    }
}
