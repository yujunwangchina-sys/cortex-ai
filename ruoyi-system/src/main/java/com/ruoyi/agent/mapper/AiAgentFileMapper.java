package com.ruoyi.agent.mapper;

import java.util.List;
import com.ruoyi.agent.domain.AiAgentFile;

/**
 * Agent文件记录Mapper接口
 * 
 * @author ruoyi
 * @date 2026-07-02
 */
public interface AiAgentFileMapper 
{
    /**
     * 查询Agent文件记录
     * 
     * @param fileId 文件ID
     * @return Agent文件记录
     */
    public AiAgentFile selectAiAgentFileByFileId(Long fileId);

    /**
     * 查询Agent文件记录列表
     * 
     * @param aiAgentFile Agent文件记录
     * @return Agent文件记录集合
     */
    public List<AiAgentFile> selectAiAgentFileList(AiAgentFile aiAgentFile);

    /**
     * 新增Agent文件记录
     * 
     * @param aiAgentFile Agent文件记录
     * @return 结果
     */
    public int insertAiAgentFile(AiAgentFile aiAgentFile);

    /**
     * 修改Agent文件记录
     * 
     * @param aiAgentFile Agent文件记录
     * @return 结果
     */
    public int updateAiAgentFile(AiAgentFile aiAgentFile);

    /**
     * 删除Agent文件记录
     * 
     * @param fileId 文件ID
     * @return 结果
     */
    public int deleteAiAgentFileByFileId(Long fileId);

    /**
     * 批量删除Agent文件记录
     * 
     * @param fileIds 需要删除的数据ID
     * @return 结果
     */
    public int deleteAiAgentFileByFileIds(Long[] fileIds);
    
    /**
     * 根据会话ID查询文件列表
     * 
     * @param sessionId 会话ID
     * @return 文件列表
     */
    public List<AiAgentFile> selectFilesBySessionId(String sessionId);
    
    /**
     * 根据用户和业务系统查询文件列表
     * 
     * @param userLoginName 用户登录名
     * @param businessSystem 业务系统
     * @return 文件列表
     */
    public List<AiAgentFile> selectFilesByUserAndBusiness(String userLoginName, String businessSystem);
    
    /**
     * 根据文件名和会话ID查询文件记录
     * 
     * @param fileName 文件名
     * @param sessionId 会话ID
     * @return 文件记录
     */
    public AiAgentFile selectFileByOriginalNameAndSession(String fileName, String sessionId);
}
