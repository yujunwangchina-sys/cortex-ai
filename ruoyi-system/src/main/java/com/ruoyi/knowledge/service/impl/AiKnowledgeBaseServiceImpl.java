package com.ruoyi.knowledge.service.impl;

import java.util.List;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.knowledge.domain.AiKnowledgeBase;
import com.ruoyi.knowledge.mapper.*;
import com.ruoyi.knowledge.rag.KnowledgeIngestService;
import com.ruoyi.knowledge.rag.MilvusStoreManager;
import com.ruoyi.knowledge.service.IAiKnowledgeBaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 知识库Service实现
 *
 * @author ruoyi
 */
@Service
public class AiKnowledgeBaseServiceImpl implements IAiKnowledgeBaseService
{
    @Autowired
    private AiKnowledgeBaseMapper kbMapper;

    @Autowired
    private AiKnowledgeDocumentMapper documentMapper;

    @Autowired
    private AiKnowledgeChunkMapper chunkMapper;

    @Autowired
    private AiKnowledgeTestCaseMapper testCaseMapper;

    @Autowired
    private AiKnowledgeTestResultMapper testResultMapper;

    @Autowired
    private AiAgentKnowledgeMapper agentKbMapper;

    @Autowired
    private KnowledgeIngestService ingestService;

    @Autowired
    private MilvusStoreManager storeManager;

    @Override
    public AiKnowledgeBase selectAiKnowledgeBaseById(Long id)
    {
        return kbMapper.selectAiKnowledgeBaseById(id);
    }

    @Override
    public List<AiKnowledgeBase> selectAiKnowledgeBaseList(AiKnowledgeBase aiKnowledgeBase)
    {
        return kbMapper.selectAiKnowledgeBaseList(aiKnowledgeBase);
    }

    @Override
    public int insertAiKnowledgeBase(AiKnowledgeBase aiKnowledgeBase)
    {
        // 自动生成collection名
        aiKnowledgeBase.setCollectionName("ruoyi_kb_" + System.currentTimeMillis());
        aiKnowledgeBase.setCreateTime(DateUtils.getNowDate());
        return kbMapper.insertAiKnowledgeBase(aiKnowledgeBase);
    }

    @Override
    public int updateAiKnowledgeBase(AiKnowledgeBase aiKnowledgeBase)
    {
        aiKnowledgeBase.setUpdateTime(DateUtils.getNowDate());
        // 如果embedding模型变更，清除缓存
        storeManager.evictCache(aiKnowledgeBase.getId());
        return kbMapper.updateAiKnowledgeBase(aiKnowledgeBase);
    }

    @Override
    @Transactional
    public int deleteAiKnowledgeBaseByIds(Long[] ids)
    {
        for (Long id : ids)
        {
            // 删除Milvus collection
            storeManager.dropCollection(id);
            // 删除关联数据
            chunkMapper.deleteByKbId(id);
            documentMapper.deleteByKbId(id);
            testCaseMapper.deleteByKbId(id);
            testResultMapper.deleteByKbId(id);
        }
        return kbMapper.deleteAiKnowledgeBaseByIds(ids);
    }

    @Override
    public void rebuildIndex(Long id)
    {
        ingestService.rebuildIndex(id);
    }
}