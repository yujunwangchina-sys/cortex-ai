package com.ruoyi.agent.runtime.loop;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.agent.runtime.llm.LlmRequest;
import com.ruoyi.agent.runtime.llm.LlmResponse;
import com.ruoyi.agent.runtime.llm.OpenAiCompatibleClient;
import com.ruoyi.agent.runtime.model.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Context Compressor - reference hermes conversation_compression.py
 *
 * Two strategies:
 * 1. LLM summarization: call a small model to summarize old messages into one paragraph
 * 2. Fallback truncation: if LLM summarization fails, keep first + last N messages
 *
 * @author ruoyi
 */
public class ContextCompressor
{
    private static final Logger log = LoggerFactory.getLogger(ContextCompressor.class);

    private int compressionThreshold;
    private final int keepRecent;
    
    // Token估算规则（基于OpenAI、DeepSeek等模型的实测数据）：
    // - 中文：1个汉字 ≈ 1.3-1.7 token（取1.5）
    // - 英文：1个单词 ≈ 1 token，约4-5个字符 = 1 token（取4.5）
    // - 标点符号：1个符号 ≈ 1 token
    // - 数字：连续数字按单词计算，约2-3个数字 = 1 token
    private static final double CHINESE_CHAR_PER_TOKEN = 1.5;    // 中文字符/token比例
    private static final double ENGLISH_CHAR_PER_TOKEN = 4.5;    // 英文字符/token比例
    private static final double PUNCTUATION_PER_TOKEN = 1.0;     // 标点符号通常1个=1token
    private static final int MAX_SUMMARY_MESSAGES = 30;

    private OpenAiCompatibleClient llmClient;
    private String summaryBaseUrl;
    private String summaryApiKey;
    private String summaryModel;

    public ContextCompressor()
    {
        this(50000, 10);
    }

    public ContextCompressor(int compressionThreshold, int keepRecent)
    {
        this.compressionThreshold = compressionThreshold;
        this.keepRecent = keepRecent;
    }

    /**
     * Configure the LLM client for summarization
     */
    public void configureLlm(OpenAiCompatibleClient client, String baseUrl, String apiKey, String model)
    {
        this.llmClient = client;
        this.summaryBaseUrl = baseUrl;
        this.summaryApiKey = apiKey;
        this.summaryModel = model;
    }

    public void setCompressionThreshold(int threshold)
    {
        this.compressionThreshold = threshold;
    }

    public boolean shouldCompress(int lastPromptTokens)
    {
        return lastPromptTokens > compressionThreshold;
    }

    /**
     * 精确估算token数量（区分中英文）
     * 
     * 算法说明：
     * 1. 中文字符（CJK统一表意文字）：每1.5个字符 ≈ 1 token
     * 2. 英文字母和数字：每4.5个字符 ≈ 1 token
     * 3. 标点符号和特殊字符：每1个 ≈ 1 token
     * 4. 空白字符：不计入token（已被tokenizer处理）
     * 
     * @param messages 消息列表
     * @return 估算的token数量
     */
    public int estimateTokens(List<ChatMessage> messages)
    {
        double totalTokens = 0.0;
        
        for (ChatMessage msg : messages)
        {
            if (msg.getContent() != null)
            {
                totalTokens += estimateTextTokens(msg.getContent());
            }
            
            if (msg.getToolCalls() != null)
            {
                for (ChatMessage.ToolCall tc : msg.getToolCalls())
                {
                    if (tc.getArguments() != null)
                    {
                        totalTokens += estimateTextTokens(tc.getArguments());
                    }
                    // 工具名称也算token
                    if (tc.getName() != null)
                    {
                        totalTokens += estimateTextTokens(tc.getName());
                    }
                }
            }
            
            // 消息结构本身的开销（role、name等字段）
            // 每条消息大约有 3-5 个额外的结构token
            totalTokens += 4;
        }
        
        return (int) Math.ceil(totalTokens);
    }
    
    /**
     * 估算单个文本的token数量（区分中英文）
     * 
     * @param text 文本内容
     * @return 估算的token数量
     */
    private double estimateTextTokens(String text)
    {
        if (text == null || text.isEmpty())
        {
            return 0.0;
        }
        
        int chineseCount = 0;      // 中文字符数
        int englishCount = 0;      // 英文字母和数字
        int punctuationCount = 0;  // 标点符号
        int whitespaceCount = 0;   // 空白字符（不计入token）
        
        for (int i = 0; i < text.length(); i++)
        {
            char c = text.charAt(i);
            
            // 判断字符类型
            if (isChinese(c))
            {
                chineseCount++;
            }
            else if (Character.isLetterOrDigit(c))
            {
                englishCount++;
            }
            else if (Character.isWhitespace(c))
            {
                whitespaceCount++;
            }
            else
            {
                // 标点符号和特殊字符
                punctuationCount++;
            }
        }
        
        // 计算token数量
        double tokens = 0.0;
        tokens += chineseCount / CHINESE_CHAR_PER_TOKEN;
        tokens += englishCount / ENGLISH_CHAR_PER_TOKEN;
        tokens += punctuationCount / PUNCTUATION_PER_TOKEN;
        
        // 调试日志（可选）
        if (log.isTraceEnabled())
        {
            log.trace("Token估算: 中文={}, 英文={}, 标点={}, 空白={}, 总tokens≈{}",
                    chineseCount, englishCount, punctuationCount, whitespaceCount, (int) tokens);
        }
        
        return tokens;
    }
    
    /**
     * 判断是否为中文字符（CJK统一表意文字）
     * 
     * Unicode范围：
     * - 4E00-9FFF: CJK统一表意文字
     * - 3400-4DBF: CJK统一表意文字扩展A
     * - 20000-2A6DF: CJK统一表意文字扩展B
     * - F900-FAFF: CJK兼容表意文字
     * 
     * @param c 字符
     * @return 是否为中文字符
     */
    private boolean isChinese(char c)
    {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT;
    }

    /**
     * Compress messages: try LLM summarization first, fallback to truncation.
     */
    public List<ChatMessage> compress(List<ChatMessage> messages)
    {
        if (messages.size() <= keepRecent + 2)
        {
            return messages;
        }

        // Try LLM summarization
        if (llmClient != null && summaryModel != null)
        {
            try
            {
                return compressWithLlm(messages);
            }
            catch (Exception e)
            {
                log.warn("LLM 摘要压缩失败, 回退到截断策略", e);
            }
        }

        return compressByTruncation(messages);
    }

    /**
     * LLM summarization: summarize old messages, keep recent ones verbatim.
     */
    private List<ChatMessage> compressWithLlm(List<ChatMessage> messages) throws Exception
    {
        int originalSize = messages.size();

        // Keep first (system) + last keepRecent messages
        int startIdx = messages.size() - keepRecent;
        // Don't split in the middle of a tool exchange
        while (startIdx > 1 && "tool".equals(messages.get(startIdx).getRole()))
        {
            startIdx--;
        }

        // Messages to summarize: from index 1 (skip system) to startIdx-1
        List<ChatMessage> toSummarize = messages.subList(1, Math.min(startIdx, 1 + MAX_SUMMARY_MESSAGES));

        if (toSummarize.isEmpty())
        {
            return compressByTruncation(messages);
        }

        // Build summarization prompt
        StringBuilder conversationText = new StringBuilder();
        for (ChatMessage msg : toSummarize)
        {
            String role = msg.getRole();
            String content = msg.getContent();
            if (content == null || content.isEmpty()) continue;
            
            // 压缩图片base64数据（可能非常大）
            content = compressImageContent(content);
            
            // Truncate very long tool results
            if (content.length() > 500)
            {
                content = content.substring(0, 250) + "...[truncated]..." + content.substring(content.length() - 250);
            }
            conversationText.append(role).append(": ").append(content).append("\n\n");
        }

        String summaryPrompt = "请将以下对话历史压缩成一段简洁的摘要,保留关键信息、决策和上下文:\n\n" +
                conversationText.toString() +
                "\n\n摘要:";

        // Call LLM for summary
        LlmRequest req = new LlmRequest();
        req.setBaseUrl(summaryBaseUrl);
        req.setApiKey(summaryApiKey);
        req.setModel(summaryModel);
        req.setStream(false);
        List<ChatMessage> summaryMessages = new ArrayList<>();
        summaryMessages.add(ChatMessage.system("你是一个对话摘要助手,请简洁地总结对话历史的关键信息。"));
        summaryMessages.add(ChatMessage.user(summaryPrompt));
        req.setMessages(summaryMessages);

        LlmResponse resp = llmClient.chatCompletion(req);
        String summary = resp.getContent();

        if (summary == null || summary.trim().isEmpty())
        {
            log.warn("LLM 摘要返回空, 回退到截断");
            return compressByTruncation(messages);
        }

        // Build compressed message list: system + summary + recent messages (with compressed images)
        List<ChatMessage> result = new ArrayList<>();
        result.add(messages.get(0)); // system prompt
        result.add(ChatMessage.system("[对话摘要] " + summary.trim()));

        for (int i = startIdx; i < messages.size(); i++)
        {
            ChatMessage msg = messages.get(i);
            // 压缩最近消息中的图片（保留但缩短）
            ChatMessage compressed = compressMessageImages(msg);
            result.add(compressed);
        }

        log.info("LLM 上下文摘要压缩: {} -> {} 条消息 (estimated ~{} tokens)",
                originalSize, result.size(), estimateTokens(result));

        return result;
    }
    
    /**
     * 压缩消息中的图片base64数据
     * 将完整的base64图片替换为占位符
     */
    private ChatMessage compressMessageImages(ChatMessage msg) {
        if (msg.getContent() == null || msg.getContent().isEmpty()) {
            return msg;
        }
        
        String content = msg.getContent();
        String compressed = compressImageContent(content);
        
        if (compressed.equals(content)) {
            return msg; // 没有变化，返回原消息
        }
        
        // 创建新消息对象，避免修改原消息
        ChatMessage newMsg = new ChatMessage();
        newMsg.setRole(msg.getRole());
        newMsg.setContent(compressed);
        newMsg.setName(msg.getName());
        newMsg.setToolCalls(msg.getToolCalls());
        newMsg.setToolCallId(msg.getToolCallId());
        return newMsg;
    }
    
    /**
     * 压缩内容中的图片base64数据
     */
    private String compressImageContent(String content) {
        if (content == null || !content.contains("data:image")) {
            return content;
        }
        
        // 匹配 markdown 图片格式: ![alt](data:image/...;base64,...)
        // 或者 HTML img 标签: <img src="data:image/...;base64,..." />
        String result = content;
        
        // 替换 markdown 格式的base64图片
        result = result.replaceAll(
            "!\\[([^\\]]*)\\]\\(data:image/([^;]+);base64,([A-Za-z0-9+/=]{100,})[^)]*\\)",
            "![\\$1][图片已压缩: $2格式]"
        );
        
        // 替换 HTML img 标签中的base64图片
        result = result.replaceAll(
            "<img[^>]*src=\"data:image/([^;]+);base64,([A-Za-z0-9+/=]{100,})[^\"]*\"[^>]*>",
            "[图片已压缩: $1格式]"
        );
        
        return result;
    }

    /**
     * Fallback: simple truncation (keep first + last N).
     */
    private List<ChatMessage> compressByTruncation(List<ChatMessage> messages)
    {
        if (messages.size() <= keepRecent + 1)
        {
            return messages;
        }

        int originalSize = messages.size();
        List<ChatMessage> result = new ArrayList<>();
        result.add(messages.get(0)); // system

        int startIdx = messages.size() - keepRecent;
        while (startIdx > 1 && "tool".equals(messages.get(startIdx).getRole()))
        {
            startIdx--;
        }

        for (int i = startIdx; i < messages.size(); i++)
        {
            // 压缩图片内容
            ChatMessage msg = messages.get(i);
            ChatMessage compressed = compressMessageImages(msg);
            result.add(compressed);
        }

        log.info("上下文截断压缩: {} -> {} 条消息 (estimated ~{} tokens)", 
                originalSize, result.size(), estimateTokens(result));
        return result;
    }
}
