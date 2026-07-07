package com.ruoyi.agent.runtime.loop;

import com.ruoyi.agent.runtime.model.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 消息清理器 - 参考 hermes _sanitize_api_messages + repair_message_sequence
 *
 * 1. 孤儿 tool_result 清理: 删除没有对应 tool_call_id 的 tool 消息
 * 2. 角色交替修复: 合并连续 user 消息, 在 tool 后补 stub assistant
 * 3. 代理字符清理: 剔除 U+D800~U+DFFF surrogate 字符
 *
 * @author ruoyi
 */
public class MessageSanitizer
{
    private static final Logger log = LoggerFactory.getLogger(MessageSanitizer.class);

    /**
     * 清理消息列表(API 调用前调用, 不修改原始列表)
     */
    public static List<ChatMessage> sanitize(List<ChatMessage> messages)
    {
        // Work on copies to avoid mutating original messages
        List<ChatMessage> copy = new ArrayList<>();
        for (ChatMessage msg : messages)
        {
            ChatMessage m = new ChatMessage();
            m.setRole(msg.getRole());
            m.setContent(stripSurrogates(msg.getContent()));
            m.setToolCallId(msg.getToolCallId());
            m.setName(msg.getName());
            m.setToolCalls(msg.getToolCalls());
            m.setImageUrls(msg.getImageUrls());
            copy.add(m);
        }
        
        // 1. Collect all tool_call_ids
        Set<String> toolCallIds = new HashSet<>();
        for (ChatMessage msg : copy)
        {
            if (msg.getToolCalls() != null)
            {
                for (ChatMessage.ToolCall tc : msg.getToolCalls())
                {
                    if (tc.getId() != null) toolCallIds.add(tc.getId());
                }
            }
        }
        
        // 2. Filter orphan tool messages
        List<ChatMessage> result = new ArrayList<>();
        for (ChatMessage msg : copy)
        {
            if ("tool".equals(msg.getRole()))
            {
                if (msg.getToolCallId() == null || !toolCallIds.contains(msg.getToolCallId()))
                {
                    log.debug("Remove orphan tool message: toolCallId={}", msg.getToolCallId());
                    continue;
                }
            }
            result.add(msg);
        }
        
        // 3. Role alternation repair
        result = repairRoleAlternation(result);
        return result;
    }

    /**
     * 修复消息角色交替
     * - 合并连续 user 消息
     * - tool 后面如果不是 assistant, 插入 stub
     * - 连续 assistant 合并(无 tool_calls 的情况)
     */
    private static List<ChatMessage> repairRoleAlternation(List<ChatMessage> messages)
    {
        if (messages.isEmpty()) return messages;

        List<ChatMessage> result = new ArrayList<>();

        for (ChatMessage msg : messages)
        {
            if (result.isEmpty())
            {
                result.add(msg);
                continue;
            }

            ChatMessage last = result.get(result.size() - 1);
            String lastRole = last.getRole();
            String curRole = msg.getRole();

            if ("user".equals(lastRole) && "user".equals(curRole))
            {
                // 合并连续 user 消息
                last.setContent((last.getContent() == null ? "" : last.getContent())
                        + "\n\n" + (msg.getContent() == null ? "" : msg.getContent()));
            }
            else if ("assistant".equals(lastRole) && "assistant".equals(curRole)
                    && (last.getToolCalls() == null || last.getToolCalls().isEmpty()))
            {
                // 合并连续 assistant(无 tool_calls)
                last.setContent((last.getContent() == null ? "" : last.getContent())
                        + "\n" + (msg.getContent() == null ? "" : msg.getContent()));
            }
            else
            {
                result.add(msg);
            }
        }

        return result;
    }

    /**
     * 剔除 surrogate 字符 (U+D800 ~ U+DFFF)
     */
    public static String stripSurrogates(String text)
    {
        if (text == null) return null;
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++)
        {
            char c = text.charAt(i);
            if (c < 0xD800 || c > 0xDFFF)
            {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
