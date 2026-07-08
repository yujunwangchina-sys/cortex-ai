package com.cortex.agent.runtime.loop;

import com.alibaba.fastjson2.JSON;
import com.cortex.agent.runtime.model.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 工具调用校验器 - 参考 hermes _repair_tool_call + tool_call JSON validation
 *
 * 1. 工具名模糊匹配修复
 * 2. JSON 参数校验 + 空字符串容错
 * 3. 截断检测
 *
 * @author cortex
 */
public class ToolCallValidator
{
    private static final Logger log = LoggerFactory.getLogger(ToolCallValidator.class);

    /**
     * 校验结果
     */
    public static class ValidationResult
    {
        public final boolean valid;
        public final String errorMessage;
        public final List<ChatMessage.ToolCall> repairedCalls;

        public ValidationResult(boolean valid, String errorMessage, List<ChatMessage.ToolCall> repairedCalls)
        {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.repairedCalls = repairedCalls;
        }
    }

    /**
     * 校验并修复工具调用
     *
     * @param toolCalls      模型返回的工具调用
     * @param validToolNames 合法工具名集合
     * @return 校验结果
     */
    public static ValidationResult validate(List<ChatMessage.ToolCall> toolCalls, Set<String> validToolNames)
    {
        if (toolCalls == null || toolCalls.isEmpty())
        {
            return new ValidationResult(true, null, toolCalls);
        }

        List<ChatMessage.ToolCall> repaired = new ArrayList<>();
        List<String> invalidNames = new ArrayList<>();

        for (ChatMessage.ToolCall tc : toolCalls)
        {
            String name = tc.getName();

            // 空名检查
            if (name == null || name.trim().isEmpty())
            {
                invalidNames.add(name == null ? "(null)" : "(empty)");
                continue;
            }

            // 模糊匹配修复
            if (!validToolNames.contains(name))
            {
                String repairedName = fuzzyMatch(name, validToolNames);
                if (repairedName != null)
                {
                    log.info("工具名模糊修复: {} -> {}", name, repairedName);
                    // 创建修复后的 ToolCall
                    ChatMessage.ToolCall fixed = ChatMessage.ToolCall.of(tc.getId(), repairedName, tc.getArguments());
                    repaired.add(fixed);
                }
                else
                {
                    invalidNames.add(name);
                }
            }
            else
            {
                // 修复 JSON 参数
                String fixedArgs = repairArguments(tc.getArguments());
                ChatMessage.ToolCall fixed = ChatMessage.ToolCall.of(tc.getId(), name, fixedArgs);
                repaired.add(fixed);
            }
        }

        if (!invalidNames.isEmpty())
        {
            String available = String.join(", ", validToolNames);
            return new ValidationResult(false,
                    "未知工具: " + String.join(", ", invalidNames) + "。可用工具: " + available,
                    repaired);
        }

        return new ValidationResult(true, null, repaired);
    }

    /**
     * 校验 JSON 参数是否合法
     *
     * @return null 表示合法, 否则返回错误信息
     */
    public static String validateArguments(List<ChatMessage.ToolCall> toolCalls)
    {
        if (toolCalls == null) return null;

        for (ChatMessage.ToolCall tc : toolCalls)
        {
            String args = tc.getArguments();
            if (args == null || args.trim().isEmpty()) continue;

            try
            {
                JSON.parseObject(args);
            }
            catch (Exception e)
            {
                // 检查是否截断(不以 } 或 ] 结尾)
                String trimmed = args.trim();
                if (!trimmed.endsWith("}") && !trimmed.endsWith("]"))
                {
                    return "TRUNCATED:" + e.getMessage();
                }
                return "INVALID_JSON:" + e.getMessage();
            }
        }
        return null;
    }

    /**
     * 修复 JSON 参数: 空字符串 -> {}, 非 String -> stringify
     */
    private static String repairArguments(String args)
    {
        if (args == null || args.trim().isEmpty())
        {
            return "{}";
        }
        try
        {
            JSON.parseObject(args);
            return args;
        }
        catch (Exception e)
        {
            // 尝试提取 JSON
            log.warn("工具参数 JSON 修复: args={}", args.substring(0, Math.min(100, args.length())));
            return "{}";
        }
    }

    /**
     * 模糊匹配工具名
     */
    private static String fuzzyMatch(String name, Set<String> validNames)
    {
        String lower = name.toLowerCase().replace("-", "_").replace(" ", "_");

        // 精确匹配(忽略大小写和分隔符)
        for (String valid : validNames)
        {
            if (valid.toLowerCase().replace("-", "_").equals(lower))
            {
                return valid;
            }
        }

        // 包含匹配
        for (String valid : validNames)
        {
            String validLower = valid.toLowerCase();
            if (validLower.contains(lower) || lower.contains(validLower))
            {
                return valid;
            }
        }

        // 编辑距离匹配(距离 <= 2)
        String best = null;
        int bestDist = 3;
        for (String valid : validNames)
        {
            int dist = levenshtein(lower, valid.toLowerCase().replace("-", "_"));
            if (dist < bestDist)
            {
                bestDist = dist;
                best = valid;
            }
        }

        return best;
    }

    /**
     * Levenshtein 编辑距离
     */
    private static int levenshtein(String a, String b)
    {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= a.length(); i++)
        {
            for (int j = 1; j <= b.length(); j++)
            {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[a.length()][b.length()];
    }

    /**
     * 去重工具调用(相同工具+相同参数只保留第一个)
     */
    public static List<ChatMessage.ToolCall> deduplicate(List<ChatMessage.ToolCall> toolCalls)
    {
        if (toolCalls == null || toolCalls.size() <= 1) return toolCalls;

        List<ChatMessage.ToolCall> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ChatMessage.ToolCall tc : toolCalls)
        {
            String key = tc.getName() + "|" + (tc.getArguments() != null ? tc.getArguments() : "{}");
            if (seen.add(key))
            {
                result.add(tc);
            }
            else
            {
                log.debug("去重工具调用: {}", tc.getName());
            }
        }
        return result;
    }
}