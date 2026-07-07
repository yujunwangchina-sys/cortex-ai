package com.ruoyi.knowledge.mapper;

import java.util.List;
import com.ruoyi.knowledge.domain.AiKnowledgeChunk;

/**
 * 知识库分块Mapper接口
 *
 * @author ruoyi
 */
public interface AiKnowledgeChunkMapper
{
    public AiKnowledgeChunk selectAiKnowledgeChunkById(Long id);

    public List<AiKnowledgeChunk> selectChunksByDocumentId(Long documentId);

    public List<AiKnowledgeChunk> selectChunksByKbId(Long kbId);

    public int insertAiKnowledgeChunk(AiKnowledgeChunk aiKnowledgeChunk);

    public int batchInsertChunks(List<AiKnowledgeChunk> chunks);

    public int deleteByDocumentId(Long documentId);

    public int deleteByKbId(Long kbId);

    public int countByKbId(Long kbId);
}