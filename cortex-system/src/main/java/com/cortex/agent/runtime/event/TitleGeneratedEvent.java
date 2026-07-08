package com.cortex.agent.runtime.event;

import org.springframework.context.ApplicationEvent;

/**
 * 会话标题生成完成事件
 * 
 * @author cortex
 */
public class TitleGeneratedEvent extends ApplicationEvent
{
    private static final long serialVersionUID = 1L;
    
    private final String sessionId;
    private final String title;
    
    public TitleGeneratedEvent(Object source, String sessionId, String title)
    {
        super(source);
        this.sessionId = sessionId;
        this.title = title;
    }
    
    public String getSessionId()
    {
        return sessionId;
    }
    
    public String getTitle()
    {
        return title;
    }
}
