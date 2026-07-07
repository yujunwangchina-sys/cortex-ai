package com.ruoyi.agent.runtime.loop;

import java.util.regex.Pattern;

/**
 * Think block stripper - reference hermes think_scrubber.py
 *
 * Strips <think>/<thinking>/<reasoning> blocks from model output.
 * Also provides a stateful StreamingThinkScrubber for SSE delta cleaning.
 *
 * @author ruoyi
 */
public class ThinkBlockStripper
{
    private static final Pattern THINK_BLOCK = Pattern.compile(
            "<(?:think|thinking|reasoning|REASONING_SCRATCHPAD)[^>]*>.*?</(?:think|thinking|reasoning|REASONING_SCRATCHPAD)>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern THINK_OPEN = Pattern.compile(
            "<(?:think|thinking|reasoning|REASONING_SCRATCHPAD)[^>]*>.*",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /**
     * Strip think blocks from complete content, return plain text.
     */
    public static String strip(String content)
    {
        if (content == null || content.isEmpty()) return content;
        String result = THINK_BLOCK.matcher(content).replaceAll("");
        result = THINK_OPEN.matcher(result).replaceAll("");
        return result.trim();
    }

    /**
     * Check if content has real text after stripping think blocks.
     */
    public static boolean hasContentAfterThink(String content)
    {
        if (content == null || content.isEmpty()) return false;
        String stripped = strip(content);
        return !stripped.isEmpty();
    }

    /**
     * Streaming think-block scrubber for SSE delta output.
     * Buffers partial <think> tags and only emits clean text.
     *
     * Usage:
     *   StreamingThinkScrubber scrubber = new StreamingThinkScrubber();
     *   String clean = scrubber.feed(delta);  // returns text safe to send to client
     *   String tail = scrubber.flush();       // call at end of stream
     */
    public static class StreamingThinkScrubber
    {
        private final StringBuilder buffer = new StringBuilder();
        private boolean insideThinkBlock = false;
        private boolean lastEmittedEndedNewline = true;

        /**
         * Feed a delta chunk, return the clean portion safe to emit.
         */
        public String feed(String delta)
        {
            if (delta == null || delta.isEmpty()) return "";
            buffer.append(delta);
            return drain();
        }

        /**
         * Flush remaining buffer at end of stream.
         */
        public String flush()
        {
            if (buffer.length() == 0) return "";
            String remaining = buffer.toString();
            buffer.setLength(0);
            if (insideThinkBlock)
            {
                insideThinkBlock = false;
                return "";
            }
            // Check if remaining is just a partial open tag
            if (isPartialOpenTag(remaining))
            {
                return "";
            }
            lastEmittedEndedNewline = remaining.endsWith("\n");
            return remaining;
        }

        /**
         * Reset state for a new turn.
         */
        public void reset()
        {
            buffer.setLength(0);
            insideThinkBlock = false;
            lastEmittedEndedNewline = true;
        }

        private String drain()
        {
            StringBuilder output = new StringBuilder();
            while (buffer.length() > 0)
            {
                if (insideThinkBlock)
                {
                    int closeIdx = findCloseTag(buffer);
                    if (closeIdx >= 0)
                    {
                        buffer.delete(0, closeIdx + getCloseTagLength(buffer));
                        insideThinkBlock = false;
                        lastEmittedEndedNewline = true;
                    }
                    else
                    {
                        break;
                    }
                }
                else
                {
                    int openIdx = findOpenTag(buffer);
                    if (openIdx >= 0)
                    {
                        if (openIdx > 0)
                        {
                            String before = buffer.substring(0, openIdx);
                            output.append(before);
                            lastEmittedEndedNewline = before.endsWith("\n");
                            buffer.delete(0, openIdx);
                        }
                        int tagEnd = findTagEnd(buffer);
                        if (tagEnd >= 0)
                        {
                            buffer.delete(0, tagEnd + 1);
                            insideThinkBlock = true;
                        }
                        else
                        {
                            break;
                        }
                    }
                    else
                    {
                        int safeLen = findSafeEmitLength(buffer);
                        if (safeLen > 0)
                        {
                            String safe = buffer.substring(0, safeLen);
                            output.append(safe);
                            lastEmittedEndedNewline = safe.endsWith("\n");
                            buffer.delete(0, safeLen);
                        }
                        break;
                    }
                }
            }
            return output.toString();
        }

        private int findOpenTag(StringBuilder sb)
        {
            String s = sb.toString().toLowerCase();
            int idx = -1;
            for (String tag : new String[]{"<think", "<thinking", "<reasoning", "<reasoning_scratchpad"})
            {
                int i = s.indexOf(tag);
                if (i >= 0 && (idx < 0 || i < idx)) idx = i;
            }
            return idx;
        }

        private int findCloseTag(StringBuilder sb)
        {
            String s = sb.toString().toLowerCase();
            int idx = -1;
            for (String tag : new String[]{"</think>", "</thinking>", "</reasoning>", "</reasoning_scratchpad>"})
            {
                int i = s.indexOf(tag);
                if (i >= 0 && (idx < 0 || i < idx)) idx = i;
            }
            return idx;
        }

        private int getCloseTagLength(StringBuilder sb)
        {
            String s = sb.toString().toLowerCase();
            for (String tag : new String[]{"</think>", "</thinking>", "</reasoning>", "</reasoning_scratchpad>"})
            {
                if (s.startsWith(tag)) return tag.length();
            }
            return 1;
        }

        private int findTagEnd(StringBuilder sb)
        {
            return sb.toString().indexOf(">");
        }

        private int findSafeEmitLength(StringBuilder sb)
        {
            String s = sb.toString();
            for (int i = 1; i <= Math.min(30, s.length()); i++)
            {
                String tail = s.substring(s.length() - i).toLowerCase();
                if ("<think".startsWith(tail) || "<thinking".startsWith(tail)
                        || "<reasoning".startsWith(tail) || "<reasoning_scratchpad".startsWith(tail))
                {
                    return s.length() - i;
                }
            }
            return s.length();
        }

        private boolean isPartialOpenTag(String s)
        {
            String sl = s.toLowerCase();
            return "<think".startsWith(sl) || "<thinking".startsWith(sl)
                    || "<reasoning".startsWith(sl) || "<reasoning_scratchpad".startsWith(sl);
        }
    }
}