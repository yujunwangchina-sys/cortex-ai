package com.cortex.agent.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.cortex.agent.mapper.AiAgentFileMapper;
import com.cortex.agent.domain.AiAgentFile;
import com.cortex.agent.service.IAiAgentFileService;

/**
 * Agent文件记录Service业务层处理
 * 
 * @author cortex
 * @date 2026-07-02
 */
@Service
public class AiAgentFileServiceImpl implements IAiAgentFileService 
{
    @Autowired
    private AiAgentFileMapper aiAgentFileMapper;

    /**
     * 查询Agent文件记录
     * 
     * @param fileId 文件ID
     * @return Agent文件记录
     */
    @Override
    public AiAgentFile selectAiAgentFileByFileId(Long fileId)
    {
        return aiAgentFileMapper.selectAiAgentFileByFileId(fileId);
    }

    /**
     * 查询Agent文件记录列表
     * 
     * @param aiAgentFile Agent文件记录
     * @return Agent文件记录
     */
    @Override
    public List<AiAgentFile> selectAiAgentFileList(AiAgentFile aiAgentFile)
    {
        return aiAgentFileMapper.selectAiAgentFileList(aiAgentFile);
    }

    /**
     * 新增Agent文件记录
     * 
     * @param aiAgentFile Agent文件记录
     * @return 结果
     */
    @Override
    public int insertAiAgentFile(AiAgentFile aiAgentFile)
    {
        return aiAgentFileMapper.insertAiAgentFile(aiAgentFile);
    }

    /**
     * 修改Agent文件记录
     * 
     * @param aiAgentFile Agent文件记录
     * @return 结果
     */
    @Override
    public int updateAiAgentFile(AiAgentFile aiAgentFile)
    {
        return aiAgentFileMapper.updateAiAgentFile(aiAgentFile);
    }

    /**
     * 批量删除Agent文件记录
     * 
     * @param fileIds 需要删除的Agent文件记录主键
     * @return 结果
     */
    @Override
    public int deleteAiAgentFileByFileIds(Long[] fileIds)
    {
        return aiAgentFileMapper.deleteAiAgentFileByFileIds(fileIds);
    }

    /**
     * 删除Agent文件记录信息
     * 
     * @param fileId 文件ID
     * @return 结果
     */
    @Override
    public int deleteAiAgentFileByFileId(Long fileId)
    {
        return aiAgentFileMapper.deleteAiAgentFileByFileId(fileId);
    }
    
    /**
     * 根据会话ID查询文件列表
     * 
     * @param sessionId 会话ID
     * @return 文件列表
     */
    @Override
    public List<AiAgentFile> selectFilesBySessionId(String sessionId)
    {
        return aiAgentFileMapper.selectFilesBySessionId(sessionId);
    }
    
    /**
     * 根据用户和业务系统查询文件列表
     * 
     * @param userLoginName 用户登录名
     * @param businessSystem 业务系统
     * @return 文件列表
     */
    @Override
    public List<AiAgentFile> selectFilesByUserAndBusiness(String userLoginName, String businessSystem)
    {
        return aiAgentFileMapper.selectFilesByUserAndBusiness(userLoginName, businessSystem);
    }
}
