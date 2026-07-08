package com.cortex.agent.runtime.loop;

import java.util.regex.Pattern;

/**
 * ANSI control sequence stripper for SSE output cleanup.
 *
 * Removes terminal escape codes (colors, cursor movement, etc.) that
 * some models emit in tool output or reasoning text.
 *
 * @author cortex
 */
public final class AnsiStripper
{
    private static final Pattern ANSI_PATTERN = Pattern.compile(
            "\u001B\\[[0-9;]*[a-zA-Z]"       // CSI sequences
            + "|\u001B\\][0-9]*;.*?\u0007"    // OSC sequences
            + "|\u001B[()][AB012]"            // Charset selection
            + "|\u001B[=>]"                    // Keypad modes
            + "|[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]" // Other control chars (keep \t \n \r)
    );

    private AnsiStripper() {}

    /**
     * Strip ANSI escape codes and control characters from text.
     */
    public static String strip(String text)
    {
        if (text == null || text.isEmpty()) return text;
        return ANSI_PATTERN.matcher(text).replaceAll("");
    }

    /**
     * Strip + trim excessive whitespace (collapse 3+ newlines to 2).
     */
    public static String clean(String text)
    {
        String result = strip(text);
        if (result == null) return result;
        // Collapse excessive blank lines
        result = result.replaceAll("\n{3,}", "\n\n");
        return result;
    }
}