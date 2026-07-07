package com.ruoyi.plugin.builtin;

import java.util.Map;

/**
 * 工具定义
 * 
 * @author ruoyi
 */
public class ToolDefinition {
    
    /** 工具名称 */
    private String name;
    
    /** 工具描述 */
    private String description;
    
    /** 输入参数Schema（JSON Schema格式） */
    private Map<String, Object> inputSchema;
    
    /** 输出参数Schema（JSON Schema格式） */
    private Map<String, Object> outputSchema;
    
    /** 示例输入 */
    private String exampleInput;
    
    /** 示例输出 */
    private String exampleOutput;
    
    public ToolDefinition() {
    }
    
    public ToolDefinition(String name, String description, Map<String, Object> inputSchema) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
    }
    
    // Getters and Setters
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public Map<String, Object> getInputSchema() {
        return inputSchema;
    }
    
    public void setInputSchema(Map<String, Object> inputSchema) {
        this.inputSchema = inputSchema;
    }
    
    public Map<String, Object> getOutputSchema() {
        return outputSchema;
    }
    
    public void setOutputSchema(Map<String, Object> outputSchema) {
        this.outputSchema = outputSchema;
    }
    
    public String getExampleInput() {
        return exampleInput;
    }
    
    public void setExampleInput(String exampleInput) {
        this.exampleInput = exampleInput;
    }
    
    public String getExampleOutput() {
        return exampleOutput;
    }
    
    public void setExampleOutput(String exampleOutput) {
        this.exampleOutput = exampleOutput;
    }
}
