package com.cortex.agent.mapper;

import com.cortex.agent.domain.AiAgentMemory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * AI Agent Memory Mapper
 *
 * @author cortex
 */
@Mapper
public interface AiAgentMemoryMapper
{
    int insertMemory(AiAgentMemory memory);

    int updateMemory(AiAgentMemory memory);

    int deleteMemoryById(@Param("id") Long id);

    AiAgentMemory selectMemoryById(@Param("id") Long id);

    /**
     * Select active memories for a user (optionally filtered by agent).
     */
    List<AiAgentMemory> selectActiveMemories(
            @Param("userLoginName") String userLoginName,
            @Param("businessSystem") String businessSystem,
            @Param("agentId") Long agentId,
            @Param("limit") int limit);

    /**
     * Search memories by keyword (simple LIKE match on title + content).
     */
    List<AiAgentMemory> searchMemories(
            @Param("userLoginName") String userLoginName,
            @Param("businessSystem") String businessSystem,
            @Param("keyword") String keyword,
            @Param("limit") int limit);

    /**
     * Increment recall count.
     */
    int incrementRecallCount(@Param("id") Long id);

    /**
     * Find similar memory by title (for dedup).
     */
    List<AiAgentMemory> selectByTitle(
            @Param("userLoginName") String userLoginName,
            @Param("businessSystem") String businessSystem,
            @Param("title") String title);

    /**
     * Auto-archive stale memories (not recalled in N days, not pinned).
     */
    int archiveStaleMemories(@Param("days") int days);

    /**
     * Delete archived memories older than N days.
     */
    int deleteArchivedMemories(@Param("days") int days);

    /**
     * Count active memories for a user (for limit enforcement).
     */
    int countActiveMemories(
            @Param("userLoginName") String userLoginName,
            @Param("businessSystem") String businessSystem);
}