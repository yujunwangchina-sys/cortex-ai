package com.cortex.plugin.builtin;

import com.alibaba.fastjson2.JSON;
import java.util.HashMap;
import java.util.Map;

/**
 * 工具执行结果
 * 
 * @author cortex
 */
public class ToolResult {
    
    /** 是否成功 */
    private boolean success;
    
    /** 消息 */
    private String message;
    
    /** 数据 */
    private Map<String, Object> data;
    
    /** 错误信息 */
    private String error;
    
    public ToolResult() {
        this.data = new HashMap<>();
    }
    
    public static ToolResult success(String message) {
        ToolResult result = new ToolResult();
        result.setSuccess(true);
        result.setMessage(message);
        return result;
    }
    
    public static ToolResult success(String message, Map<String, Object> data) {
        ToolResult result = new ToolResult();
        result.setSuccess(true);
        result.setMessage(message);
        result.setData(data);
        return result;
    }
    
    public static ToolResult error(String error) {
        ToolResult result = new ToolResult();
        result.setSuccess(false);
        result.setError(error);
        return result;
    }
    
    public ToolResult addData(String key, Object value) {
        if (this.data == null) {
            this.data = new HashMap<>();
        }
        this.data.put(key, value);
        return this;
    }
    
    /**
     * 转换为JSON字符串
     */
    public String toJson() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        
        if (message != null) {
            result.put("message", message);
        }
        
        if (error != null) {
            result.put("error", error);
        }
        
        if (data != null && !data.isEmpty()) {
            result.putAll(data);
        }
        
        return JSON.toJSONString(result);
    }
    
    // Getters and Setters
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public Map<String, Object> getData() {
        return data;
    }
    
    public void setData(Map<String, Object> data) {
        this.data = data;
    }
    
    public String getError() {
        return error;
    }
    
    public void setError(String error) {
        this.error = error;
    }
}
