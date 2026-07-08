package com.cortex.skill.mapper;

import com.cortex.skill.domain.SkillNode;
import java.util.List;

/**
 * 技能节点Mapper接口
 * 
 * @author cortex
 * @date 2024-06-30
 */
public interface SkillNodeMapper {
    /**
     * 查询所有节点（构建树形结构）
     * 
     * @return 节点列表
     */
    List<SkillNode> selectAllNodes();

    /**
     * 根据ID查询节点
     * 
     * @param id 节点ID
     * @return 节点信息
     */
    SkillNode selectNodeById(Long id);

    /**
     * 根据路径查询节点
     * 
     * @param path 节点路径
     * @return 节点信息
     */
    SkillNode selectNodeByPath(String path);

    /**
     * 新增节点
     * 
     * @param node 节点信息
     * @return 结果
     */
    int insertNode(SkillNode node);

    /**
     * 修改节点
     * 
     * @param node 节点信息
     * @return 结果
     */
    int updateNode(SkillNode node);

    /**
     * 删除节点
     * 
     * @param id 节点ID
     * @return 结果
     */
    int deleteNodeById(Long id);

    /**
     * 批量删除节点
     * 
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    int deleteNodesByIds(Long[] ids);

    /**
     * 查询子节点
     * 
     * @param parentId 父节点ID
     * @return 子节点列表
     */
    List<SkillNode> selectChildrenByParentId(Long parentId);

    /**
     * 查询用户可见的技能(三层隔离)
     * 1. 系统公共技能: skill_scope='system' AND business_system=#{businessSystem}
     * 2. 个人技能: skill_scope='user' AND owner_user=#{userLoginName}
     *
     * @param businessSystem 业务系统
     * @param userLoginName  用户登录名
     * @return 可见技能列表
     */
    List<SkillNode> selectVisibleSkills(@org.apache.ibatis.annotations.Param("businessSystem") String businessSystem,
                                        @org.apache.ibatis.annotations.Param("userLoginName") String userLoginName);

    /**
     * 查询系统公共技能(按业务系统)
     */
    List<SkillNode> selectSystemSkills(@org.apache.ibatis.annotations.Param("businessSystem") String businessSystem);

    /**
     * 查询用户个人技能
     */
    List<SkillNode> selectUserSkills(@org.apache.ibatis.annotations.Param("ownerUser") String ownerUser);
    /**
     * Query user skills by owner (for dedup check)
     */
    List<SkillNode> selectUserSkillsByOwner(@org.apache.ibatis.annotations.Param("ownerUser") String ownerUser,
                                           @org.apache.ibatis.annotations.Param("businessSystem") String businessSystem);

    /**
     * Delete user skills older than specified days (for cleanup)
     */
    int deleteExpiredUserSkills(@org.apache.ibatis.annotations.Param("days") int days);


    /**
     * Find user skills not updated in N days (for stale transition).
     */
    java.util.List<SkillNode> selectStaleUserSkills(@org.apache.ibatis.annotations.Param("days") int days);

    /**
     * Update lifecycle state for a skill.
     */
    int updateLifecycleState(@org.apache.ibatis.annotations.Param("id") Long id,
                            @org.apache.ibatis.annotations.Param("state") String state);

    /**
     * Archive stale skills older than N days (not pinned).
     */
    int archiveOldStaleSkills(@org.apache.ibatis.annotations.Param("days") int days);

    /**
     * Delete archived skills older than N days.
     */
    int deleteArchivedSkills(@org.apache.ibatis.annotations.Param("days") int days);

    /**
     * Select nodes by parent ID list (for batch loading skill package children).
     */
    List<SkillNode> selectChildrenByParentIds(@org.apache.ibatis.annotations.Param("parentIds") List<Long> parentIds);

    /**
     * Select a skill node including its content (lazy load for skill_view).
     */
    SkillNode selectNodeWithContent(@org.apache.ibatis.annotations.Param("id") Long id);

    /**
     * Search skills by keyword (name or content LIKE).
     */
    List<SkillNode> searchSkills(@org.apache.ibatis.annotations.Param("keyword") String keyword,
                                @org.apache.ibatis.annotations.Param("businessSystem") String businessSystem,
                                @org.apache.ibatis.annotations.Param("userLoginName") String userLoginName);

    /**
     * Find SKILL.md file within a skill package by package ID (for viewSkill optimization).
     * 
     * @param packageId Skill package ID (parent_id)
     * @return SKILL.md node with content, or null if not found
     */
    SkillNode selectSkillMdByPackageId(@org.apache.ibatis.annotations.Param("packageId") Long packageId);
    
    /**
     * 统计技能包总数（仅第一层文件夹）
     * 
     * @return 技能包数量
     */
    int countSkillPackages();
    
    /**
     * 统计上一周期的技能包数
     * 
     * @return 技能包数量
     */
    int countSkillPackagesLastPeriod();
    
    /**
     * 按日期统计技能包数
     * 
     * @param date 日期
     * @return 技能包数量
     */
    int countSkillPackagesByDate(String date);
}
