package com.ruoyi.agent.runtime.model;

/**
 * Agent 运行请求
 *
 * @author ruoyi
 */
public class AgentRunRequest
{
    /** Agent编码 */
    private String agentCode;

    /** 用户登录名 */
    private String userLoginName;

    /** 业务系统标识(默认hai) */
    private String businessSystem = "cortex";

    /** 用户消息 */
    private String message;

    /** 会话ID(null=新建会话) */
    private String sessionId;

    /** API Key(外部系统调用时传入) */
    private String apiKey;

    /** 运行时指定模型ID(null=用Agent默认) */
    private Long modelId;

    /** 运行时模态类型(chat/vision/multimodal) */
    private String modelType = "chat";

    /** 审批模式: always=每次询问, full=完全访问(免审批), auto=按插件配置 */
    private String approvalMode = "auto";

    /** 多模态图片URL列表 */
    private java.util.List<String> imageUrls;

    /** 上传文件ID列表 */
    private java.util.List<Long> fileIds;

    public String getAgentCode() { return agentCode; }
    public void setAgentCode(String agentCode) { this.agentCode = agentCode; }
    public String getUserLoginName() { return userLoginName; }
    public void setUserLoginName(String userLoginName) { this.userLoginName = userLoginName; }
    public String getBusinessSystem() { return businessSystem; }
    public void setBusinessSystem(String businessSystem) { this.businessSystem = businessSystem; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public Long getModelId() { return modelId; }
    public void setModelId(Long modelId) { this.modelId = modelId; }
    public String getModelType() { return modelType; }
    public void setModelType(String modelType) { this.modelType = modelType; }
    public String getApprovalMode() { return approvalMode; }
    public void setApprovalMode(String approvalMode) { this.approvalMode = approvalMode; }

    public java.util.List<String> getImageUrls() { return imageUrls; }
    public void setImageUrls(java.util.List<String> imageUrls) { this.imageUrls = imageUrls; }

    public java.util.List<Long> getFileIds() { return fileIds; }
    public void setFileIds(java.util.List<Long> fileIds) { this.fileIds = fileIds; }
}
