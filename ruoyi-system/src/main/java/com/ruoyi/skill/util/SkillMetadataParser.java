package com.ruoyi.skill.util;

import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Skill metadata parser - extracts YAML frontmatter from Markdown files.
 *
 * SKILL.md and DESCRIPTION.md files use YAML frontmatter delimited by
 * --- lines. This parser extracts key fields (name, description,
 * version, author, etc.) into a JSON object for skill_metadata column.
 *
 * @author ruoyi
 */
public class SkillMetadataParser
{
    private static final Logger log = LoggerFactory.getLogger(SkillMetadataParser.class);

    private static final Pattern FRONTMATTER_PATTERN =
            Pattern.compile("^---\\s*\\n(.*?)\\n---\\s*\\n", Pattern.DOTALL);

    private static final Pattern KV_PATTERN =
            Pattern.compile("^(\\w[\\w\\-]*)\\s*:\\s*(.*)$");

    /**
     * Parse YAML frontmatter from markdown content into a JSONObject.
     *
     * @param content markdown file content (with optional frontmatter)
     * @return parsed metadata, or empty JSONObject if no frontmatter found
     */
    public static JSONObject parseFrontmatter(String content)
    {
        JSONObject meta = new JSONObject();
        if (content == null || content.isEmpty())
        {
            return meta;
        }

        Matcher fmMatcher = FRONTMATTER_PATTERN.matcher(content);
        if (!fmMatcher.find())
        {
            return meta;
        }

        String frontmatter = fmMatcher.group(1);
        String[] lines = frontmatter.split("\n");
        for (String line : lines)
        {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#"))
            {
                continue;
            }

            Matcher kv = KV_PATTERN.matcher(line);
            if (kv.matches())
            {
                String key = kv.group(1).trim();
                String value = kv.group(2).trim();

                // Strip surrounding quotes
                if ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")))
                {
                    value = value.substring(1, value.length() - 1);
                }

                meta.put(key, value);
            }
        }

        return meta;
    }

    /**
     * Strip frontmatter from markdown content, returning only the body.
     */
    public static String stripFrontmatter(String content)
    {
        if (content == null || content.isEmpty())
        {
            return content;
        }
        return FRONTMATTER_PATTERN.matcher(content).replaceFirst("");
    }

    /**
     * Validate that a markdown file's frontmatter has required fields.
     *
     * Required fields: name, description, version
     *
     * @param content markdown file content
     * @return error message if validation fails, null if valid
     */
    public static String validateRequiredFields(String content)
    {
        JSONObject meta = parseFrontmatter(content);

        if (meta.isEmpty())
        {
            return "missing YAML frontmatter (file must start with --- delimited metadata block)";
        }

        if (!meta.containsKey("name") || meta.getString("name").isEmpty())
        {
            return "frontmatter missing required field: name";
        }

        if (!meta.containsKey("description") || meta.getString("description").isEmpty())
        {
            return "frontmatter missing required field: description";
        }

        if (!meta.containsKey("version") || meta.getString("version").isEmpty())
        {
            return "frontmatter missing required field: version";
        }

        return null;
    }

    /**
     * Check if content has YAML frontmatter.
     */
    public static boolean hasFrontmatter(String content)
    {
        return content != null && content.startsWith("---")
                && FRONTMATTER_PATTERN.matcher(content).find();
    }
}