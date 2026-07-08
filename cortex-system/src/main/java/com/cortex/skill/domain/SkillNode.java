package com.cortex.skill.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.cortex.common.core.domain.BaseEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.util.List;
/**
 * 技能节点对象 skill_node
 * 
 * @author cortex
 * @date 2024-06-30
 */
public class SkillNode extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /** 节点ID */
    private Long id;

    /** 节点名称 */
    private String name;

    /** 文件扩展名 */
    private String fileExtension;

    /** 节点路径 */
    private String path;

    /** 是否为目录(0否 1是) */
    private Boolean isDirectory;

    /** 节点类型: skill_package(技能包), skill(技能), file(普通文件), directory(目录) */
    private String nodeType;
    /** 技能范围: global(全局,所有Agent可用) / personal(个人,自学习生成) */
    private String skillScope;

    /** 技能类型: general(通用) / dedicated(专属) */
    private String skillType;

    /** 来源会话ID */
    private String learnedFromSession;
    /** 业务系统标识(个人技能需要,用于隔离不同业务系统的同名用户) */
    private String businessSystem;

    /** 技能所有者(personal级技能的创建者) */
    private String ownerUser;

    /** 父节点ID */
    private Long parentId;

    /** 排序 */
    private Integer sortOrder;

    /** 文件内容(仅文件有) */
    private String content;

    /** 技能元数据(JSON格式) */
    private String skillMetadata;

    /** Lifecycle state: active/stale/archived (user skills only) */
    private String lifecycleState;

    /** Pinned (skip auto-archival, user skills only) */
    private Boolean pinned;
    /** 文件大小(字节) */
    private Long fileSize;

    /** 文件MIME类型 */
    private String mimeType;

    public String getSkillScope() {
        return skillScope;
    }

    public void setSkillScope(String skillScope) {
        this.skillScope = skillScope;
    }

    public String getSkillType() {
        return skillType;
    }

    public void setSkillType(String skillType) {
        this.skillType = skillType;
    }

    public String getLearnedFromSession() {
        return learnedFromSession;
    }

    public void setLearnedFromSession(String learnedFromSession) {
        this.learnedFromSession = learnedFromSession;
    }
    public String getBusinessSystem() {
        return businessSystem;
    }

    public void setBusinessSystem(String businessSystem) {
        this.businessSystem = businessSystem;
    }

    public String getOwnerUser() {
        return ownerUser;
    }

    public void setOwnerUser(String ownerUser) {
        this.ownerUser = ownerUser;
    }

    /** 子节点列表 */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<SkillNode> children;

    /** 子节点数量（非数据库字段，用于前端展示） */
    private Long childCount;

    public void setId(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setFileExtension(String fileExtension) {
        this.fileExtension = fileExtension;
    }

    public String getFileExtension() {
        return fileExtension;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }

    public void setIsDirectory(Boolean isDirectory) {
        this.isDirectory = isDirectory;
    }

    public Boolean getIsDirectory() {
        return isDirectory;
    }

    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    public String getNodeType() {
        return nodeType;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getContent() {
        return content;
    }

    public void setSkillMetadata(String skillMetadata) {
        this.skillMetadata = skillMetadata;
    }

    public String getLifecycleState() { return lifecycleState; }
    public void setLifecycleState(String lifecycleState) { this.lifecycleState = lifecycleState; }
    public Boolean getPinned() { return pinned; }
    public void setPinned(Boolean pinned) { this.pinned = pinned; }

    public String getSkillMetadata() {
        return skillMetadata;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getMimeType() {
        return mimeType;
    }

    public List<SkillNode> getChildren() {
        return children;
    }

    public void setChildren(List<SkillNode> children) {
        this.children = children;
    }

    public Long getChildCount() {
        return childCount;
    }

    public void setChildCount(Long childCount) {
        this.childCount = childCount;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("name", getName())
            .append("fileExtension", getFileExtension())
            .append("path", getPath())
            .append("isDirectory", getIsDirectory())
            .append("nodeType", getNodeType())
            .append("parentId", getParentId())
            .append("sortOrder", getSortOrder())
            .append("content", getContent())
            .append("skillMetadata", getSkillMetadata())
            .append("fileSize", getFileSize())
            .append("mimeType", getMimeType())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
