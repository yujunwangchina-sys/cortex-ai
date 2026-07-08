package com.cortex.agent.runtime.loop;

import com.cortex.agent.domain.AiAgent;
import com.cortex.agent.domain.AiAgentExecutionLog;
import com.cortex.agent.mapper.AiAgentExecutionLogMapper;
import com.cortex.agent.runtime.context.AgentSessionContext;
import com.cortex.agent.runtime.llm.LlmRequest;
import com.cortex.agent.runtime.llm.LlmResponse;
import com.cortex.agent.runtime.llm.OpenAiCompatibleClient;
import com.cortex.agent.runtime.model.ChatMessage;
import com.cortex.agent.runtime.prompt.AgentConfigLoader;
import com.cortex.skill.domain.SkillNode;
import com.cortex.skill.mapper.SkillNodeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Background Skill Review - reference hermes background_review.py + curator.py
 *
 * 1. After each turn: fork a background LLM call to review if skills should be saved
 * 2. Dedup: check existing user skills by name similarity before creating new ones
 * 3. Scheduled cleanup: delete user skills older than 30 days (daily at 3am)
 *
 * @author cortex
 */
@Component
public class BackgroundSkillReview
{
    private static final Logger log = LoggerFactory.getLogger(BackgroundSkillReview.class);

    private static final int MAX_USER_SKILLS = 50;
    private static final int EXPIRE_DAYS = 30;
    private static final double DEDUP_SIMILARITY_THRESHOLD = 0.7;

    @Autowired
    private OpenAiCompatibleClient llmClient;

    @Autowired
    private AgentConfigLoader configLoader;

    @Autowired
    private AiAgentExecutionLogMapper executionLogMapper;

    @Autowired
    private SkillNodeMapper skillNodeMapper;


    @Autowired
    @Qualifier("agentIoExecutor")
    private ThreadPoolTaskExecutor ioExecutor;
    private static final String REVIEW_PROMPT =
        "Please review the conversation above and decide if any knowledge should be saved as a skill.\n\n" +
        "Review criteria:\n" +
        "1. Did the user correct your behavior, output format, or workflow? -> save as skill\n" +
        "2. Was there a non-obvious trick, solution, or tool usage pattern? -> save\n" +
        "3. Is there an existing skill that needs updating? -> note it (but system skills are read-only)\n\n" +
        "Rules:\n" +
        "- Only create skill_scope=user skills\n" +
        "- System skills are read-only\n" +
        "- If nothing is worth saving, reply with exactly: NO_SKILL_SAVE\n" +
        "- Skill content should be Markdown: scenario, steps, notes\n" +
        "- Be conservative - only save genuinely valuable knowledge\n\n" +
        "Reply format:\n" +
        "- If saving: SKILL_NAME: <name>\nSKILL_CONTENT: <markdown content>\nSKILL_DESCRIPTION: <one line>\n" +
        "- If not: NO_SKILL_SAVE";

    /**
     * Trigger async skill review (non-blocking)
     */
    public void reviewAsync(AgentSessionContext ctx, AiAgent agent)
    {
        ioExecutor.execute(() -> {
            try { review(ctx, agent); }
            catch (Exception e) { log.debug("Skill review error [session={}]", ctx.getSessionId(), e); }
        });
    }

    /**
     * Execute skill review
     */
    private void review(AgentSessionContext ctx, AiAgent agent)
    {
        log.info("Skill review started [session={}]", ctx.getSessionId());
        try
        {
            AgentConfigLoader.ModelSelection modelSelect = configLoader.selectModel(agent, "chat");

            List<ChatMessage> messages = new ArrayList<>();
            messages.add(ChatMessage.system(
                "You are a skill review assistant. Analyze conversations and identify reusable knowledge.\n" +
                "Be conservative - only save genuinely valuable, non-obvious knowledge."));

            // Conversation snapshot (last 20 messages)
            List<ChatMessage> history = ctx.getMessages();
            int start = Math.max(0, history.size() - 20);
            for (ChatMessage msg : history.subList(start, history.size()))
            {
                messages.add(msg);
            }
            messages.add(ChatMessage.user(REVIEW_PROMPT));

            LlmRequest request = new LlmRequest();
            request.setBaseUrl(modelSelect.supplier.getApiBaseUrl());
            request.setApiKey(modelSelect.supplier.getApiKey());
            request.setModel(modelSelect.model.getModelCode());
            request.setMessages(messages);
            request.setStream(false);
            request.setTemperature(new BigDecimal("0.3"));

            LlmResponse response = llmClient.chatCompletion(request);
            String result = response.getContent();

            if (result == null || result.trim().isEmpty() || result.contains("NO_SKILL_SAVE"))
            {
                log.info("Skill review: nothing to save [session={}]", ctx.getSessionId());
                return;
            }

            // Parse result and create skill with dedup
            ParsedSkill parsed = parseSkillResult(result);
            if (parsed != null)
            {
                createSkillWithDedup(parsed, ctx);
            }

            // Log review
            logReview(ctx, response, result);
        }
        catch (Exception e)
        {
            log.debug("Skill review failed [session={}]", ctx.getSessionId(), e);
        }
    }

    /**
     * Parse LLM review result into skill fields
     */
    private ParsedSkill parseSkillResult(String result)
    {
        try
        {
            String name = extractField(result, "SKILL_NAME:");
            String content = extractField(result, "SKILL_CONTENT:");
            String description = extractField(result, "SKILL_DESCRIPTION:");
            if (name == null || content == null) return null;
            return new ParsedSkill(name.trim(), content.trim(),
                    description != null ? description.trim() : "");
        }
        catch (Exception e) { return null; }
    }

    private String extractField(String text, String prefix)
    {
        int idx = text.indexOf(prefix);
        if (idx < 0) return null;
        int start = idx + prefix.length();
        // Find next field or end
        int end = text.length();
        for (String nextPrefix : new String[]{"SKILL_NAME:", "SKILL_CONTENT:", "SKILL_DESCRIPTION:"})
        {
            int nextIdx = text.indexOf(nextPrefix, start);
            if (nextIdx > 0 && nextIdx < end) end = nextIdx;
        }
        return text.substring(start, end).trim();
    }

    /**
     * Create skill with dedup check against existing user skills
     */
    private void createSkillWithDedup(ParsedSkill parsed, AgentSessionContext ctx)
    {
        // Load existing user skills for dedup
        List<SkillNode> existing = skillNodeMapper.selectUserSkillsByOwner(
                ctx.getUserLoginName(), ctx.getBusinessSystem());

        // Check name similarity
        for (SkillNode skill : existing)
        {
            double similarity = stringSimilarity(parsed.name.toLowerCase(),
                    skill.getName() != null ? skill.getName().toLowerCase() : "");
            if (similarity >= DEDUP_SIMILARITY_THRESHOLD)
            {
                // Update existing skill content instead of creating duplicate
                log.info("Skill dedup: updating existing skill [name={}, similarity={}]",
                        skill.getName(), similarity);
                skill.setContent(parsed.content);
                skill.setFileSize((long) parsed.content.getBytes().length);
                skill.setUpdateBy("agent-review");
                skill.setUpdateTime(new java.util.Date());
                skillNodeMapper.updateNode(skill);
                return;
            }
        }

        // Check user skill limit
        if (existing.size() >= MAX_USER_SKILLS)
        {
            log.info("User skill limit reached ({}), skipping creation", MAX_USER_SKILLS);
            return;
        }

        // Create new skill
        SkillNode node = new SkillNode();
        node.setName(parsed.name);
        node.setFileExtension(".md");
        node.setPath("/AutoLearned/" + parsed.name);
        node.setIsDirectory(false);
        node.setNodeType("skill");
        node.setSkillScope("user");
        node.setSkillType("learned");
        node.setLearnedFromSession(ctx.getSessionId());
        node.setBusinessSystem(ctx.getBusinessSystem());
        node.setOwnerUser(ctx.getUserLoginName());
        node.setContent(parsed.content);
        node.setFileSize((long) parsed.content.getBytes().length);
        node.setMimeType("text/markdown");
        node.setSortOrder(0);
        node.setCreateBy("agent-review");
        node.setCreateTime(new java.util.Date());

        skillNodeMapper.insertNode(node);
        log.info("Skill created via auto-learning [name={}, session={}, user={}]",
                parsed.name, ctx.getSessionId(), ctx.getUserLoginName());
    }

    /**
     * String similarity using Jaccard on word sets (0~1)
     */
    private double stringSimilarity(String a, String b)
    {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return 0;
        java.util.Set<String> setA = new java.util.HashSet<>(java.util.Arrays.asList(a.split("\\s+")));
        java.util.Set<String> setB = new java.util.HashSet<>(java.util.Arrays.asList(b.split("\\s+")));
        java.util.Set<String> intersection = new java.util.HashSet<>(setA);
        intersection.retainAll(setB);
        java.util.Set<String> union = new java.util.HashSet<>(setA);
        union.addAll(setB);
        return (double) intersection.size() / union.size();
    }

    /**
     * Scheduled cleanup: delete expired user skills (daily at 3am)
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupExpiredSkills()
    {
        try
        {
            int deleted = skillNodeMapper.deleteExpiredUserSkills(EXPIRE_DAYS);
            log.info("Expired user skill cleanup: deleted {} skills older than {} days", deleted, EXPIRE_DAYS);
        }
        catch (Exception e)
        {
            log.warn("Skill cleanup failed", e);
        }
    }

    private void logReview(AgentSessionContext ctx, LlmResponse response, String result)
    {
        try
        {
            AiAgentExecutionLog logEntry = new AiAgentExecutionLog();
            logEntry.setSessionId(ctx.getSessionId());
            logEntry.setAgentId(ctx.getAgentId());
            logEntry.setTurnId("review-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            logEntry.setIteration(0);
            logEntry.setEventType("skill_review");
            logEntry.setInputParams("review prompt");
            logEntry.setOutputResult(result != null ? result.substring(0, Math.min(5000, result.length())) : null);
            logEntry.setTokenInput(response.getPromptTokens());
            logEntry.setTokenOutput(response.getCompletionTokens());
            logEntry.setStatus("0");
            logEntry.setBusinessSystem(ctx.getBusinessSystem());
            logEntry.setUserLoginName(ctx.getUserLoginName());
            executionLogMapper.insertAiAgentExecutionLog(logEntry);
        }
        catch (Exception e) { log.warn("Failed to log skill review", e); }
    }

    private static class ParsedSkill
    {
        final String name;
        final String content;
        final String description;
        ParsedSkill(String name, String content, String description)
        {
            this.name = name;
            this.content = content;
            this.description = description;
        }
    }
}
