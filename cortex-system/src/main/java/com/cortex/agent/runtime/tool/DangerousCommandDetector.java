package com.cortex.agent.runtime.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Dangerous command detector - migrated from hermes approval.py DANGEROUS_PATTERNS.
 *
 * @author cortex
 */
public class DangerousCommandDetector
{
    private static final List<PatternEntry> DANGEROUS_PATTERNS = new ArrayList<>();

    static
    {
        add("\\brm\\s+(-[^\\s]*\\s+)*/", "delete in root path");
        add("\\brm\\s+-[^\\s]*r", "recursive delete");
        add("\\brm\\s+--recursive\\b", "recursive delete (long flag)");
        add("\\bfind\\b.*-exec(?:dir)?\\s+(/\\S*/)?rm\\b", "find -exec/-execdir rm");
        add("\\bfind\\b.*-delete\\b", "find -delete");
        add("\\bxargs\\s+.*\\brm\\b", "xargs with rm");
        add("\\bchmod\\s+(-[^\\s]*\\s+)*(777|666|o\\+[rwx]*w|a\\+[rwx]*w)\\b", "world/other-writable permissions");
        add("\\bchmod\\s+--recursive\\b.*(777|666)", "recursive world-writable");
        add("\\bchown\\s+(-[^\\s]*)?R\\s+root", "recursive chown to root");
        add("\\bmkfs\\b", "format filesystem");
        add("\\bdd\\s+.*if=", "disk copy");
        add(">\\s*/dev/sd", "write to block device");
        add("\\bDROP\\s+(TABLE|DATABASE)\\b", "SQL DROP");
        add("\\bDELETE\\s+FROM\\b(?![^\\n]*\\bWHERE\\b)", "SQL DELETE without WHERE");
        add("\\bTRUNCATE\\s+(TABLE)?\\s*\\w", "SQL TRUNCATE");
        add("\\bsystemctl\\s+(-[^\\s]+\\s+)*(stop|restart|disable|mask)\\b", "stop/restart system service");
        add("\\bkill\\s+-9\\s+-1\\b", "kill all processes");
        add("\\bpkill\\s+-9\\b", "force kill processes");
        add("\\bkillall\\s+(-[^\\s]*\\s+)*-(9|KILL|SIGKILL)\\b", "force kill processes (killall)");
        add("\\b(curl|wget)\\b.*\\|\\s*(?:[/\\w]*/)?(?:ba)?sh(?:\\s|$|-c)", "pipe remote content to shell");
        add("\\b(bash|sh|zsh|ksh)\\s+<\\s*<?\\s*\\(\\s*(curl|wget)\\b", "execute remote script");
        add(":\\(\\)\\s*\\{\\s*:\\s*\\|\\s*:\\s*&\\s*\\}\\s*;\\s*:", "fork bomb");
        add("\\bdocker\\s+compose\\s+(restart|stop|kill|down)\\b", "docker compose lifecycle");
        add("\\bdocker\\s+(restart|stop|kill)\\b", "docker container lifecycle");
        add(">>?\\s*[\"']?/etc/(passwd|shadow|hosts|sudoers)", "overwrite system file");
        add("\\btee\\b.*[\"']?/etc/(passwd|shadow|hosts|sudoers)", "overwrite system file via tee");
    }

    private static void add(String regex, String description)
    {
        DANGEROUS_PATTERNS.add(new PatternEntry(Pattern.compile(regex, Pattern.CASE_INSENSITIVE), description));
    }

    /**
     * Detect if a command is dangerous.
     * @return description if dangerous, null if safe
     */
    public static String detect(String command)
    {
        if (command == null || command.isEmpty())
        {
            return null;
        }
        for (PatternEntry entry : DANGEROUS_PATTERNS)
        {
            if (entry.pattern.matcher(command).find())
            {
                return entry.description;
            }
        }
        return null;
    }

    public static boolean isDangerous(String command)
    {
        return detect(command) != null;
    }

    private static class PatternEntry
    {
        final Pattern pattern;
        final String description;
        PatternEntry(Pattern pattern, String description) { this.pattern = pattern; this.description = description; }
    }
}