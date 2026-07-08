package com.cortex.agent.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.cortex.common.annotation.Excel;
import com.cortex.common.core.domain.BaseEntity;

/**
 * Agent文件记录对象 ai_agent_file
 * 
 * @author cortex
 * @date 2026-07-02
 */
public class AiAgentFile extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 文件ID */
    private Long fileId;

    /** 文件名称 */
    @Excel(name = "文件名称")
    private String fileName;

    /** 文件存储路径 */
    @Excel(name = "文件存储路径")
    private String filePath;

    /** 文件大小(字节) */
    @Excel(name = "文件大小", readConverterExp = "字=节")
    private Long fileSize;

    /** 文件类型: upload=用户上传, generated=Agent生成 */
    @Excel(name = "文件类型", readConverterExp = "u=pload=用户上传,g=enerated=Agent生成")
    private String fileType;

    /** 会话ID */
    @Excel(name = "会话ID")
    private String sessionId;

    /** Agent代码 */
    @Excel(name = "Agent代码")
    private String agentCode;

    /** 业务系统 */
    @Excel(name = "业务系统")
    private String businessSystem;

    /** 用户登录名 */
    @Excel(name = "用户登录名")
    private String userLoginName;

    public void setFileId(Long fileId) 
    {
        this.fileId = fileId;
    }

    public Long getFileId() 
    {
        return fileId;
    }

    public void setFileName(String fileName) 
    {
        this.fileName = fileName;
    }

    public String getFileName() 
    {
        return fileName;
    }

    public void setFilePath(String filePath) 
    {
        this.filePath = filePath;
    }

    public String getFilePath() 
    {
        return filePath;
    }

    public void setFileSize(Long fileSize) 
    {
        this.fileSize = fileSize;
    }

    public Long getFileSize() 
    {
        return fileSize;
    }

    public void setFileType(String fileType) 
    {
        this.fileType = fileType;
    }

    public String getFileType() 
    {
        return fileType;
    }

    public void setSessionId(String sessionId) 
    {
        this.sessionId = sessionId;
    }

    public String getSessionId() 
    {
        return sessionId;
    }

    public void setAgentCode(String agentCode) 
    {
        this.agentCode = agentCode;
    }

    public String getAgentCode() 
    {
        return agentCode;
    }

    public void setBusinessSystem(String businessSystem) 
    {
        this.businessSystem = businessSystem;
    }

    public String getBusinessSystem() 
    {
        return businessSystem;
    }

    public void setUserLoginName(String userLoginName) 
    {
        this.userLoginName = userLoginName;
    }

    public String getUserLoginName() 
    {
        return userLoginName;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("fileId", getFileId())
            .append("fileName", getFileName())
            .append("filePath", getFilePath())
            .append("fileSize", getFileSize())
            .append("fileType", getFileType())
            .append("sessionId", getSessionId())
            .append("agentCode", getAgentCode())
            .append("businessSystem", getBusinessSystem())
            .append("userLoginName", getUserLoginName())
            .append("createTime", getCreateTime())
            .append("remark", getRemark())
            .toString();
    }
}
