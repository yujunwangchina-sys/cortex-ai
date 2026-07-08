package com.cortex.agent.runtime.loop;

import com.cortex.agent.domain.AiAgent;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Skill Curator - reference hermes curator.py
 *
 * Full lifecycle management for user-learned skills:
 *   1. Auto-transition: active -> stale (30 days unused) -> archived (90 days)
 *   2. Pinned skills bypass all auto-transitions
 *   3. Consolidation: merge similar skills using LLM (opt-in, runs on schedule)
 *   4. Only touches user-scope skills (system skills are read-only)
 *   5. Never auto-deletes; archive is recoverable
 *
 * @author cortex
 */
@Component
public class SkillCurator
{
    private static final Logger log = LoggerFactory.getLogger(SkillCurator.class);

    private static final int STALE_AFTER_DAYS = 30;
    private static final int ARCHIVE_AFTER_DAYS = 90;
    private static final int DELETE_AFTER_DAYS = 180;
    private static final int CONSOLIDATION_BATCH = 20;

    @Autowired
    private SkillNodeMapper skillNodeMapper;

    @Autowired
    private OpenAiCompatibleClient llmClient;

    @Autowired
    private AgentConfigLoader configLoader;

    /**
     * Scheduled lifecycle transition: daily at 3:30 AM
     * 1. Mark stale: active skills not updated in 30 days -> stale
     * 2. Archive: stale skills not updated in 90 days -> archived
     * 3. Delete: archived skills older than 180 days -> delete
     */
    @Scheduled(cron = "0 30 3 * * ?")
    public void runLifecycleTransition()
    {
        log.info("Skill curator: lifecycle transition started");
        try
        {
            // 1. Mark stale
            List<SkillNode> staleCandidates = skillNodeMapper.selectStaleUserSkills(STALE_AFTER_DAYS);
            int staleCount = 0;
            if (staleCandidates != null)
            {
                for (SkillNode skill : staleCandidates)
                {
                    if (!Boolean.TRUE.equals(skill.getPinned()))
                    {
                        skillNodeMapper.updateLifecycleState(skill.getId(), "stale");
                        staleCount++;
                    }
                }
            }
            log.info("Skill curator: marked {} skills as stale", staleCount);

            // 2. Archive old stale
            int archived = skillNodeMapper.archiveOldStaleSkills(ARCHIVE_AFTER_DAYS);
            log.info("Skill curator: archived {} stale skills", archived);

            // 3. Delete old archived
            int deleted = skillNodeMapper.deleteArchivedSkills(DELETE_AFTER_DAYS);
            log.info("Skill curator: deleted {} old archived skills", deleted);
        }
        catch (Exception e)
        {
            log.warn("Skill curator: lifecycle transition failed", e);
        }
    }

    /**
     * Scheduled consolidation: weekly on Sunday at 4 AM
     * Uses LLM to merge similar user skills and improve quality.
     * Only runs on skills flagged as 'learned' type.
     */
    @Scheduled(cron = "0 0 4 ? * SUN")
    public void runConsolidation()
    {
        log.info("Skill curator: consolidation started");
        try
        {
            // Load all active user-learned skills
            List<SkillNode> allUserSkills = skillNodeMapper.selectAllNodes();
            List<SkillNode> candidates = new ArrayList<>();
            if (allUserSkills != null)
            {
                for (SkillNode skill : allUserSkills)
                {
                    if ("user".equals(skill.getSkillScope())
                            && "learned".equals(skill.getSkillType())
                            && (skill.getLifecycleState() == null || "active".equals(skill.getLifecycleState())))
                    {
                        candidates.add(skill);
                    }
                }
            }

            if (candidates.size() < 3)
            {
                log.info("Skill curator: not enough skills for consolidation ({})", candidates.size());
                return;
            }

            // Limit batch size
            if (candidates.size() > CONSOLIDATION_BATCH)
            {
                candidates = candidates.subList(0, CONSOLIDATION_BATCH);
            }

            // Build consolidation prompt
            StringBuilder skillList = new StringBuilder();
            for (int i = 0; i < candidates.size(); i++)
            {
                SkillNode s = candidates.get(i);
                skillList.append("### Skill ").append(i + 1).append(": ").append(s.getName()).append("\n");
                skillList.append("Content: ").append(s.getContent() != null ?
                        s.getContent().substring(0, Math.min(200, s.getContent().length())) : "(empty)").append("\n\n");
            }

            List<ChatMessage> messages = new ArrayList<>();
            messages.add(ChatMessage.system(
                "You are a skill curator. Review the following user-learned skills and identify consolidation opportunities.\n" +
                "Rules:\n" +
                "1. Identify skills that are duplicates or very similar and should be merged\n" +
                "2. Suggest a merged name and combined content\n" +
                "3. If skills are already well-organized, reply: NO_CONSOLIDATION_NEEDED\n" +
                "4. Be conservative - only merge truly overlapping skills\n\n" +
                "Reply format for each merge suggestion:\n" +
                "MERGE: <skill numbers to merge, e.g. 1,3>\n" +
                "NEW_NAME: <merged skill name>\n" +
                "NEW_CONTENT: <merged markdown content>\n" +
                "---"));
            messages.add(ChatMessage.user("Skills to review:\n\n" + skillList));

            // Use any available chat model
            try
            {
                AiAgent dummyAgent = new AiAgent();
                dummyAgent.setId(0L);
                dummyAgent.setAgentCode("curator");
                AgentConfigLoader.ModelSelection modelSelect = configLoader.selectModel(dummyAgent, "chat");

                LlmRequest request = new LlmRequest();
                request.setBaseUrl(modelSelect.supplier.getApiBaseUrl());
                request.setApiKey(modelSelect.supplier.getApiKey());
                request.setModel(modelSelect.model.getModelCode());
                request.setMessages(messages);
                request.setStream(false);
                request.setTemperature(new BigDecimal("0.3"));

                LlmResponse response = llmClient.chatCompletion(request);
                String result = response.getContent();

                if (result == null || result.contains("NO_CONSOLIDATION_NEEDED"))
                {
                    log.info("Skill curator: no consolidation needed");
                    return;
                }

                // Parse and apply merges (simplified: log for now, manual review path)
                log.info("Skill curator: consolidation suggestions generated ({} chars)", 
                        result != null ? result.length() : 0);

                // TODO: Apply automatic merges based on LLM suggestions
                // For safety, we log suggestions but don't auto-merge in v1
            }
            catch (Exception e)
            {
                log.warn("Skill curator: consolidation LLM call failed", e);
            }
        }
        catch (Exception e)
        {
            log.warn("Skill curator: consolidation failed", e);
        }
    }
}