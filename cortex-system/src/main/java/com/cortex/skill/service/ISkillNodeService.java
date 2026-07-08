package com.cortex.skill.service;

import com.cortex.skill.domain.SkillNode;
import java.util.List;

/**
 * 技能节点Service接口
 * 
 * @author cortex
 * @date 2024-06-30
 */
public interface ISkillNodeService {
    /**
     * 获取技能树
     * 
     * @return 技能树
     */
    List<SkillNode> getSkillTree();

    /**
     * 获取技能包列表（只返回第一层）
     * 
     * @return 技能包列表
     */
    List<SkillNode> getSkillPackages();

    /**
     * 获取 Agent 可用的技能包列表
     * 支持三层权限：全局 > 业务系统 > 个人
     * 
     * @param businessSystem 业务系统标识（Agent所属业务系统）
     * @param ownerUser 用户名（用于查询个人技能）
     * @return 技能包列表
     */
    List<SkillNode> getAvailableSkillPackages(String businessSystem, String ownerUser);

    /**
     * 创建文件夹
     * 
     * @param node 节点信息
     * @return 结果
     */
    int createFolder(SkillNode node);

    /**
     * 创建文件
     * 
     * @param node 节点信息
     * @return 结果
     */
    int createFile(SkillNode node);

    /**
     * 删除节点（递归删除子节点）
     * 
     * @param id 节点ID
     * @return 结果
     */
    int deleteNode(Long id);

    /**
     * 重命名节点
     * 
     * @param id 节点ID
     * @param newName 新名称
     * @return 结果
     */
    int renameNode(Long id, String newName);

    /**
     * 移动节点
     * 
     * @param id 节点ID
     * @param targetId 目标节点ID
     * @param dropType 拖拽类型(before/after/inner)
     * @return 结果
     */
    int moveNode(Long id, Long targetId, String dropType);

    /**
     * 获取文件内容
     * 
     * @param filePath 文件路径
     * @return 文件内容
     */
    String getFileContent(String filePath);

    /**
     * 保存文件内容
     * 
     * @param id 节点ID
     * @param content 文件内容
     * @return 结果
     */
    int saveFileContent(Long id, String content);

    /**
     * 获取所有文件列表（用于引用）
     * 
     * @return 文件列表
     */
    List<SkillNode> getAllFiles();

    /**
     * 上传技能包压缩包
     * 
     * @param file 压缩包文件
     * @param skillScope 技能范围：system(系统级) / user(用户级)
     * @param businessSystem 业务系统标识
     * @param ownerUser 所有者用户名
     * @return 技能包名称
     * @throws Exception 上传失败异常
     */
    String uploadSkillPackage(
            org.springframework.web.multipart.MultipartFile file,
            String skillScope,
            String businessSystem,
            String ownerUser) throws Exception;

    /**
     * 上传单个技能到指定技能包下
     * 
     * @param file 技能ZIP压缩包文件
     * @param parentId 父节点ID（技能包ID）
     * @return 技能名称
     * @throws Exception 上传失败异常
     */
    String uploadSingleSkill(
            org.springframework.web.multipart.MultipartFile file,
            Long parentId) throws Exception;
}
