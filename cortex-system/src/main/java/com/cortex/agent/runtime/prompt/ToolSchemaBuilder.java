package com.cortex.agent.runtime.prompt;

import com.alibaba.fastjson2.JSONObject;
import com.cortex.plugin.domain.AiPlugin;
import com.cortex.plugin.domain.AiPluginTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具 Schema 构建器 — 将 ai_plugin_tool 转为 OpenAI function calling 格式
 *
 * @author cortex
 */
@Component
public class ToolSchemaBuilder
{
    private static final Logger log = LoggerFactory.getLogger(ToolSchemaBuilder.class);

    /**
     * 构建工具 schema 列表
     *
     * @param tools   工具列表
     * @param plugins 插件列表(用于查找工具所属插件的审批配置)
     * @return OpenAI tools 格式的 JSON 字符串列表
     */
    public List<String> build(List<AiPluginTool> tools, List<AiPlugin> plugins)
    {
        System.out.println("==================== 开始构建工具Schema ====================");
        System.out.println("输入工具数: " + tools.size());
        System.out.println("插件数: " + plugins.size());
        
        List<String> result = new ArrayList<>();
        Map<Long, AiPlugin> pluginMap = new HashMap<>();
        for (AiPlugin p : plugins)
        {
            pluginMap.put(p.getPluginId(), p);
            System.out.println("插件: " + p.getPluginName() + " (ID=" + p.getPluginId() + ", Type=" + p.getPluginType() + ")");
        }

        int enabledCount = 0;
        for (AiPluginTool tool : tools)
        {
            System.out.println("处理工具: " + tool.getToolCode() + " - " + tool.getToolName() + " (Status=" + tool.getStatus() + ")");
            
            if ("1".equals(tool.getStatus()))
            {
                System.out.println("  -> 跳过（已禁用）");
                continue; // 跳过禁用的工具
            }

            try
            {
                JSONObject toolSchema = new JSONObject();
                toolSchema.put("type", "function");

                JSONObject function = new JSONObject();
                function.put("name", tool.getToolCode());
                function.put("description", tool.getDescription() != null ? tool.getDescription() : tool.getToolName());

                // inputSchema
                if (tool.getInputSchema() != null && !tool.getInputSchema().isEmpty())
                {
                    function.put("parameters", JSONObject.parse(tool.getInputSchema()));
                }
                else
                {
                    // 默认 schema
                    JSONObject params = new JSONObject();
                    params.put("type", "object");
                    params.put("properties", new JSONObject());
                    function.put("parameters", params);
                }

                toolSchema.put("function", function);
                result.add(toolSchema.toJSONString());
                enabledCount++;
                
                System.out.println("  -> 已加载");
                log.debug("已加载工具 [{}] - {}", tool.getToolCode(), tool.getToolName());
            }
            catch (Exception e)
            {
                System.out.println("  -> 构建失败: " + e.getMessage());
                log.warn("构建工具schema失败 [tool={}]", tool.getToolCode(), e);
            }
        }

        System.out.println("工具schema构建完成: 共" + tools.size() + "个工具，启用" + enabledCount + "个");
        System.out.println("=========================================================");
        
        log.info("工具schema构建完成: 共{}个工具，启用{}个", tools.size(), enabledCount);
        return result;
    }
}
