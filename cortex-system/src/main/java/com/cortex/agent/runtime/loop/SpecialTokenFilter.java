package com.cortex.agent.runtime.loop;

import java.util.regex.Pattern;

/**
 * 过滤流式输出中的特殊token（如 <|begin_of_sentence|>, <|end_of_text|> 等）
 * 
 * @author cortex
 */
public class SpecialTokenFilter
{
    // 匹配特殊token的正则表达式
    // 包括: <|xxx|>, <｜xxx｜> (全角), <|xxx▁yyy|> (带下划线)
    private static final Pattern SPECIAL_TOKEN_PATTERN = Pattern.compile(
        "<[|｜][^|｜<>]*?[▁_][^|｜<>]*?[|｜]>|<[|｜][a-zA-Z_][a-zA-Z0-9_]*[|｜]>"
    );
    
    /**
     * 过滤掉文本中的特殊token
     * 
     * @param text 原始文本
     * @return 过滤后的文本
     */
    public static String filter(String text)
    {
        if (text == null || text.isEmpty())
        {
            return text;
        }
        
        // 移除特殊token
        String filtered = SPECIAL_TOKEN_PATTERN.matcher(text).replaceAll("");
        
        return filtered;
    }
}
