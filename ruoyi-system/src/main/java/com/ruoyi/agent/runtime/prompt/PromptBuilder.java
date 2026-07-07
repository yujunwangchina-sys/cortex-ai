package com.ruoyi.agent.runtime.prompt;

import com.ruoyi.agent.domain.AiAgent;
import com.ruoyi.skill.domain.SkillNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Prompt builder — assembles systemPrompt + skill index + memory.
 *
 * <p>Skill loading follows a <b>pull model</b> (progressive disclosure):
 * only a lightweight index (skill package names + skill names + short
 * descriptions) is injected into the system prompt. The LLM then uses
 * the {@code skill_view} tool to pull the full SKILL.md content on demand,
 * exactly like hermes does.
 *
 * @author ruoyi
 */
@Component
public class PromptBuilder
{
    private static final Logger log = LoggerFactory.getLogger(PromptBuilder.class);

    private static final String SKILL_INDEX_HEADER =
            "\n\n---\n\n## 技能索引\n\n" +
            "以下是当前可用的技能列表（仅名称和简要描述）。" +
            "当你需要某个技能的完整内容时，使用 skill_view 工具获取：\n\n";

    private static final String SKILL_INDEX_HINT =
            "\n> 提示：技能内容不会自动加载。需要时请调用 skill_view(skillId) 获取完整技能文档。\n";

    // ========================================================================
    // Skill Learning & Management Guidance (inspired by Hermes Agent)
    // ========================================================================

    /**
     * 核心技能引导 - 告诉AI什么时候应该主动学习和保存技能
     * 
     * 借鉴 hermes-agent 的 SKILLS_GUIDANCE 设计：
     * - 明确触发条件（复杂任务、错误修复、新流程）
     * - 强调主动性（offer to save, patch immediately）
     * - 区分技能 vs 内存（流程→技能，偏好→内存）
     */
    private static final String SKILLS_LEARNING_GUIDANCE =
            "\n## 💡 技能学习与管理\n\n" +
            "### 何时保存为技能\n\n" +
            "当你遇到以下情况时，应该使用 **skill_manage** 工具保存为个人技能：\n\n" +
            "1. **复杂任务完成后** (5次以上工具调用)\n" +
            "   - 用户的问题需要多步骤解决\n" +
            "   - 涉及多个工具协同工作\n" +
            "   - 有明确的操作流程\n\n" +
            "2. **修复棘手错误**\n" +
            "   - 遇到非常见的错误\n" +
            "   - 解决方案不明显\n" +
            "   - 可能再次遇到\n\n" +
            "3. **发现非平凡的工作流程**\n" +
            "   - 不是简单的一次性操作\n" +
            "   - 有可复用的价值\n" +
            "   - 包含特定领域知识\n\n" +
            "4. **用户明确要求**\n" +
            "   - 用户说\"记住这个\"、\"保存下来\"、\"以后要用\"\n" +
            "   - 用户表示这是常见需求\n\n" +
            "### 何时更新技能\n\n" +
            "使用技能时发现问题，**立即更新** (不要等用户要求)：\n\n" +
            "- 指令过时或错误\n" +
            "- 缺少关键步骤\n" +
            "- 命令在当前环境无效\n" +
            "- 发现新的陷阱或注意事项\n\n" +
            "**重要**：不维护的技能会成为负担。发现问题立即用 `skill_manage(action='patch')` 修复。\n\n" +
            "### 技能 vs 记忆\n\n" +
            "**保存为技能** (可复用的流程)：\n" +
            "- ✅ 操作步骤和命令序列\n" +
            "- ✅ 故障排查指南\n" +
            "- ✅ 配置方法和最佳实践\n" +
            "- ✅ 代码模板和架构模式\n\n" +
            "**保存为记忆** (用户偏好和环境事实)：\n" +
            "- ✅ 用户喜欢简洁回答\n" +
            "- ✅ 项目使用 pytest + xdist\n" +
            "- ✅ 数据库连接配置\n\n" +
            "❌ **不要保存**：任务进度、PR编号、完成日志、临时状态\n\n" +
            "### 最佳实践\n\n" +
            "**好的技能包含**：\n" +
            "1. 触发条件 (什么时候使用)\n" +
            "2. 编号的步骤 (具体命令)\n" +
            "3. 陷阱部分 (常见问题)\n" +
            "4. 验证步骤 (如何确认成功)\n\n" +
            "**主动询问**：\n" +
            "- 困难任务完成后：\"这个解决方案将来可能用得上，要保存为技能吗？\"\n" +
            "- 跳过简单的一次性操作\n" +
            "- 创建或删除前确认用户意图\n\n" +
            "使用 `skill_view()` 查看现有技能的格式示例。\n";

    /**
     * Build the full system prompt.
     *
     * @param agent      Agent config
     * @param skills     Full skill nodes (包含技能包和技能，技能包可能包含packageDescription)
     * @param memoryText Cross-session memory text
     * @param businessSystem 业务系统标识
     * @param userLoginName 用户登录名
     * @param userNickName 用户昵称
     * @return Complete system prompt
     */
    public String buildSystemPrompt(
            AiAgent agent, 
            List<SkillNode> skills, 
            String memoryText,
            String businessSystem,
            String userLoginName,
            String userNickName)
    {
        StringBuilder sb = new StringBuilder();

        // 0. User Context (新增 - 让 Agent 知道当前用户)
        sb.append(buildUserContext(businessSystem, userLoginName, userNickName));

        // 1. Agent systemPrompt
        if (agent.getSystemPrompt() != null && !agent.getSystemPrompt().isEmpty())
        {
            sb.append(agent.getSystemPrompt());
        }

        // 2. Skill index (按技能包分组，显示DESCRIPTION.md描述)
        if (skills != null && !skills.isEmpty())
        {
            sb.append(SKILL_INDEX_HEADER);
            
            // 分组：技能包 -> 技能列表
            java.util.Map<String, java.util.List<SkillNode>> groupedSkills = groupSkillsByPackage(skills);
            
            for (java.util.Map.Entry<String, java.util.List<SkillNode>> entry : groupedSkills.entrySet())
            {
                String packageName = entry.getKey();
                java.util.List<SkillNode> packageSkills = entry.getValue();
                
                // 技能包标题
                if (!"__standalone__".equals(packageName))
                {
                    sb.append("### ").append(packageName).append("\n");
                    
                    // 显示技能包描述（来自DESCRIPTION.md）
                    String packageDesc = getPackageDescription(skills, packageName);
                    if (packageDesc != null && !packageDesc.isEmpty())
                    {
                        sb.append("*").append(packageDesc).append("*\n\n");
                    }
                }
                
                // 技能列表
                for (SkillNode skill : packageSkills)
                {
                    sb.append("- **").append(skill.getName()).append("**");
                    String desc = extractShortDescription(skill);
                    if (desc != null && !desc.isEmpty())
                    {
                        sb.append(" — ").append(desc);
                    }
                    sb.append("  [id=").append(skill.getId()).append("]");
                    sb.append("\n");
                }
                sb.append("\n");
            }
            
            sb.append(SKILL_INDEX_HINT);
            sb.append(SKILLS_LEARNING_GUIDANCE);  // 添加学习指南
        }

        // 3. Memory injection (cross-session recall)
        if (memoryText != null && !memoryText.isEmpty())
        {
            sb.append(memoryText);
        }
        return sb.toString();
    }

    /**
     * 构建用户上下文信息
     */
    private String buildUserContext(String businessSystem, String userLoginName, String userNickName)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("\n## 👤 当前用户信息\n\n");
        
        if (userNickName != null && !userNickName.isEmpty())
        {
            sb.append("**用户昵称**：").append(userNickName).append("\n");
        }
        
        if (userLoginName != null && !userLoginName.isEmpty())
        {
            sb.append("**用户名**：").append(userLoginName).append("\n");
        }
        
        if (businessSystem != null && !businessSystem.isEmpty())
        {
            sb.append("**业务系统**：").append(businessSystem).append("\n");
        }
        
        sb.append("\n> 提示：你可以根据用户身份提供个性化服务。创建个人技能时会自动关联到该用户。\n\n");
        sb.append("---\n\n");
        
        return sb.toString();
    }

    /**
     * Build the full system prompt (向后兼容的重载方法).
     *
     * @param agent      Agent config
     * @param skills     Full skill nodes
     * @param memoryText Cross-session memory text
     * @return Complete system prompt
     */
    public String buildSystemPrompt(AiAgent agent, List<SkillNode> skills, String memoryText)
    {
        return buildSystemPrompt(agent, skills, memoryText, null, null, null);
    }

    /**
     * Group skills by package name.
     * 技能包节点本身也在列表中，需要识别并分组。
     */
    private java.util.Map<String, java.util.List<SkillNode>> groupSkillsByPackage(List<SkillNode> skills)
    {
        java.util.Map<String, java.util.List<SkillNode>> grouped = new java.util.LinkedHashMap<>();
        java.util.Set<Long> packageIds = new java.util.HashSet<>();
        
        // 第一遍：识别所有技能包节点
        for (SkillNode skill : skills)
        {
            String nodeType = skill.getNodeType() != null ? skill.getNodeType() : "skill";
            if ("skill_package".equals(nodeType) || "directory".equals(nodeType))
            {
                packageIds.add(skill.getId());
            }
        }
        
        // 第二遍：按父节点分组技能
        for (SkillNode skill : skills)
        {
            String nodeType = skill.getNodeType() != null ? skill.getNodeType() : "skill";
            
            // 跳过技能包节点本身（它们不显示为技能项）
            if ("skill_package".equals(nodeType) || "directory".equals(nodeType))
            {
                continue;
            }
            
            // 查找父技能包名称
            String packageName = "__standalone__";
            if (skill.getParentId() != null)
            {
                for (SkillNode pkg : skills)
                {
                    if (pkg.getId().equals(skill.getParentId()))
                    {
                        packageName = pkg.getName();
                        break;
                    }
                }
            }
            
            grouped.computeIfAbsent(packageName, k -> new java.util.ArrayList<>()).add(skill);
        }
        
        return grouped;
    }

    /**
     * Get package description from packageDescription in skillMetadata.
     */
    private String getPackageDescription(List<SkillNode> skills, String packageName)
    {
        for (SkillNode skill : skills)
        {
            if (packageName.equals(skill.getName()))
            {
                String nodeType = skill.getNodeType() != null ? skill.getNodeType() : "";
                if ("skill_package".equals(nodeType) || "directory".equals(nodeType))
                {
                    // 从 skillMetadata 中提取 packageDescription
                    if (skill.getSkillMetadata() != null && !skill.getSkillMetadata().isEmpty())
                    {
                        try
                        {
                            com.alibaba.fastjson2.JSONObject meta =
                                    com.alibaba.fastjson2.JSON.parseObject(skill.getSkillMetadata());
                            String desc = meta.getString("packageDescription");
                            if (desc != null && !desc.isEmpty())
                            {
                                return desc;
                            }
                        }
                        catch (Exception ignored) {}
                    }
                }
            }
        }
        return null;
    }

    /**
     * Extract a short description from a skill node.
     *
     * <p>Tries (in order):
     * <ol>
     *   <li>YAML frontmatter "description" field from content (SKILL.md开头)</li>
     *   <li>skillMetadata JSON field "description" (set during skill upload)</li>
     *   <li>First non-empty, non-heading line of content (max 80 chars)</li>
     *   <li>Empty string if nothing found</li>
     * </ol>
     */
    private String extractShortDescription(SkillNode skill)
    {
        // 1. Try YAML frontmatter from content (highest priority)
        if (skill.getContent() != null && !skill.getContent().isEmpty())
        {
            String frontmatterDesc = extractDescriptionFromYamlFrontmatter(skill.getContent());
            if (frontmatterDesc != null && !frontmatterDesc.isEmpty())
            {
                return truncate(frontmatterDesc.trim(), 80);
            }
        }

        // 2. Try skillMetadata
        if (skill.getSkillMetadata() != null && !skill.getSkillMetadata().isEmpty())
        {
            try
            {
                com.alibaba.fastjson2.JSONObject meta =
                        com.alibaba.fastjson2.JSON.parseObject(skill.getSkillMetadata());
                String desc = meta.getString("description");
                if (desc != null && !desc.isEmpty())
                {
                    return truncate(desc.trim(), 80);
                }
            }
            catch (Exception ignored) {}
        }

        // 3. Try first meaningful line of content
        if (skill.getContent() != null && !skill.getContent().isEmpty())
        {
            String[] lines = skill.getContent().split("\n");
            boolean inFrontmatter = false;
            boolean frontmatterClosed = false;
            
            for (String line : lines)
            {
                String trimmed = line.trim();
                
                // Skip YAML frontmatter
                if (trimmed.equals("---"))
                {
                    if (!inFrontmatter && !frontmatterClosed)
                    {
                        inFrontmatter = true;
                        continue;
                    }
                    else if (inFrontmatter)
                    {
                        inFrontmatter = false;
                        frontmatterClosed = true;
                        continue;
                    }
                }
                
                if (inFrontmatter) continue;
                if (frontmatterClosed && trimmed.isEmpty()) continue;
                
                // Skip markdown headings, YAML keys
                if (trimmed.startsWith("#")) continue;
                if (trimmed.matches("^[a-zA-Z_]+:.*")) continue;
                
                return truncate(trimmed, 80);
            }
        }

        return "";
    }

    /**
     * Extract description field from YAML frontmatter.
     * 
     * @param content Markdown content with YAML frontmatter
     * @return Description value, or null if not found
     */
    private String extractDescriptionFromYamlFrontmatter(String content)
    {
        if (content == null || content.isEmpty()) return null;
        
        String[] lines = content.split("\n");
        boolean inFrontmatter = false;
        
        for (int i = 0; i < lines.length; i++)
        {
            String line = lines[i].trim();
            
            // Check for frontmatter delimiter
            if (line.equals("---"))
            {
                if (!inFrontmatter)
                {
                    inFrontmatter = true;
                    continue;
                }
                else
                {
                    // End of frontmatter
                    break;
                }
            }
            
            if (!inFrontmatter) continue;
            
            // Look for "description:" field
            if (line.startsWith("description:"))
            {
                String desc = line.substring("description:".length()).trim();
                
                // Remove surrounding quotes if present
                if (desc.startsWith("\"") && desc.endsWith("\""))
                {
                    desc = desc.substring(1, desc.length() - 1);
                }
                else if (desc.startsWith("'") && desc.endsWith("'"))
                {
                    desc = desc.substring(1, desc.length() - 1);
                }
                
                return desc;
            }
        }
        
        return null;
    }

    private String truncate(String s, int max)
    {
        if (s.length() <= max) return s;
        return s.substring(0, max - 3) + "...";
    }
}