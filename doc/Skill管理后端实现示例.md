# Skill管理系统 - 后端实现示例

## 1. 数据库表设计

### skill_node 表（技能节点表）
```sql
CREATE TABLE `skill_node` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '节点ID',
  `name` varchar(255) NOT NULL COMMENT '节点名称',
  `path` varchar(500) NOT NULL COMMENT '节点路径',
  `is_directory` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否为目录(0否 1是)',
  `parent_id` bigint DEFAULT NULL COMMENT '父节点ID',
  `sort_order` int DEFAULT 0 COMMENT '排序',
  `content` longtext COMMENT '文件内容(仅文件有)',
  `file_size` bigint DEFAULT 0 COMMENT '文件大小(字节)',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_path` (`path`(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='技能节点表';
```

## 2. Java实体类

### SkillNode.java
```java
package com.cortex.skill.domain;

import com.cortex.common.core.domain.BaseEntity;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * 技能节点对象 skill_node
 */
public class SkillNode extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /** 节点ID */
    private Long id;

    /** 节点名称 */
    private String name;

    /** 节点路径 */
    private String path;

    /** 是否为目录 */
    private Boolean isDirectory;

    /** 父节点ID */
    private Long parentId;

    /** 排序 */
    private Integer sortOrder;

    /** 文件内容 */
    private String content;

    /** 文件大小 */
    private Long fileSize;

    /** 子节点列表 */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<SkillNode> children;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public Boolean getIsDirectory() { return isDirectory; }
    public void setIsDirectory(Boolean isDirectory) { this.isDirectory = isDirectory; }

    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public List<SkillNode> getChildren() { return children; }
    public void setChildren(List<SkillNode> children) { this.children = children; }
}
```

## 3. Mapper接口

### SkillNodeMapper.java
```java
package com.cortex.skill.mapper;

import com.cortex.skill.domain.SkillNode;
import java.util.List;

/**
 * 技能节点Mapper接口
 */
public interface SkillNodeMapper {
    /**
     * 查询所有节点（构建树形结构）
     */
    List<SkillNode> selectAllNodes();

    /**
     * 根据ID查询节点
     */
    SkillNode selectNodeById(Long id);

    /**
     * 根据路径查询节点
     */
    SkillNode selectNodeByPath(String path);

    /**
     * 新增节点
     */
    int insertNode(SkillNode node);

    /**
     * 修改节点
     */
    int updateNode(SkillNode node);

    /**
     * 删除节点
     */
    int deleteNodeById(Long id);

    /**
     * 批量删除节点（包括子节点）
     */
    int deleteNodesByIds(Long[] ids);

    /**
     * 查询子节点
     */
    List<SkillNode> selectChildrenByParentId(Long parentId);
}
```

## 4. Service接口和实现

### ISkillNodeService.java
```java
package com.cortex.skill.service;

import com.cortex.skill.domain.SkillNode;
import java.util.List;

public interface ISkillNodeService {
    /**
     * 获取技能树
     */
    List<SkillNode> getSkillTree();

    /**
     * 创建文件夹
     */
    int createFolder(SkillNode node);

    /**
     * 创建文件
     */
    int createFile(SkillNode node);

    /**
     * 删除节点
     */
    int deleteNode(Long id);

    /**
     * 重命名节点
     */
    int renameNode(Long id, String newName);

    /**
     * 移动节点
     */
    int moveNode(Long id, Long targetId, String dropType);

    /**
     * 获取文件内容
     */
    String getFileContent(String filePath);

    /**
     * 保存文件内容
     */
    int saveFileContent(Long id, String content);

    /**
     * 获取所有文件列表（用于引用）
     */
    List<SkillNode> getAllFiles();
}
```

### SkillNodeServiceImpl.java
```java
package com.cortex.skill.service.impl;

import com.cortex.skill.domain.SkillNode;
import com.cortex.skill.mapper.SkillNodeMapper;
import com.cortex.skill.service.ISkillNodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SkillNodeServiceImpl implements ISkillNodeService {

    @Autowired
    private SkillNodeMapper skillNodeMapper;

    @Override
    public List<SkillNode> getSkillTree() {
        List<SkillNode> allNodes = skillNodeMapper.selectAllNodes();
        return buildTree(allNodes, null);
    }

    /**
     * 构建树形结构
     */
    private List<SkillNode> buildTree(List<SkillNode> allNodes, Long parentId) {
        List<SkillNode> tree = new ArrayList<>();
        for (SkillNode node : allNodes) {
            if ((parentId == null && node.getParentId() == null) ||
                (parentId != null && parentId.equals(node.getParentId()))) {
                node.setChildren(buildTree(allNodes, node.getId()));
                tree.add(node);
            }
        }
        return tree;
    }

    @Override
    @Transactional
    public int createFolder(SkillNode node) {
        node.setIsDirectory(true);
        node.setContent(null);
        generatePath(node);
        return skillNodeMapper.insertNode(node);
    }

    @Override
    @Transactional
    public int createFile(SkillNode node) {
        node.setIsDirectory(false);
        if (node.getContent() == null) {
            node.setContent("");
        }
        node.setFileSize((long) node.getContent().getBytes().length);
        generatePath(node);
        return skillNodeMapper.insertNode(node);
    }

    /**
     * 生成节点路径
     */
    private void generatePath(SkillNode node) {
        if (node.getParentId() == null) {
            node.setPath("/" + node.getName());
        } else {
            SkillNode parent = skillNodeMapper.selectNodeById(node.getParentId());
            node.setPath(parent.getPath() + "/" + node.getName());
        }
    }

    @Override
    @Transactional
    public int deleteNode(Long id) {
        // 递归删除子节点
        List<SkillNode> children = skillNodeMapper.selectChildrenByParentId(id);
        for (SkillNode child : children) {
            deleteNode(child.getId());
        }
        return skillNodeMapper.deleteNodeById(id);
    }

    @Override
    @Transactional
    public int renameNode(Long id, String newName) {
        SkillNode node = skillNodeMapper.selectNodeById(id);
        if (node == null) {
            return 0;
        }
        node.setName(newName);
        // 更新路径
        updateNodePath(node);
        return skillNodeMapper.updateNode(node);
    }

    /**
     * 更新节点及其子节点的路径
     */
    private void updateNodePath(SkillNode node) {
        generatePath(node);
        skillNodeMapper.updateNode(node);
        
        // 递归更新子节点路径
        if (node.getIsDirectory()) {
            List<SkillNode> children = skillNodeMapper.selectChildrenByParentId(node.getId());
            for (SkillNode child : children) {
                updateNodePath(child);
            }
        }
    }

    @Override
    @Transactional
    public int moveNode(Long id, Long targetId, String dropType) {
        SkillNode node = skillNodeMapper.selectNodeById(id);
        if (node == null) {
            return 0;
        }

        if ("inner".equals(dropType)) {
            // 移动到目标节点内部
            node.setParentId(targetId);
        } else {
            // 移动到目标节点同级
            SkillNode target = skillNodeMapper.selectNodeById(targetId);
            node.setParentId(target.getParentId());
        }

        updateNodePath(node);
        return skillNodeMapper.updateNode(node);
    }

    @Override
    public String getFileContent(String filePath) {
        SkillNode node = skillNodeMapper.selectNodeByPath(filePath);
        return node != null ? node.getContent() : null;
    }

    @Override
    @Transactional
    public int saveFileContent(Long id, String content) {
        SkillNode node = skillNodeMapper.selectNodeById(id);
        if (node == null || node.getIsDirectory()) {
            return 0;
        }
        node.setContent(content);
        node.setFileSize((long) content.getBytes().length);
        return skillNodeMapper.updateNode(node);
    }

    @Override
    public List<SkillNode> getAllFiles() {
        List<SkillNode> allNodes = skillNodeMapper.selectAllNodes();
        return allNodes.stream()
                .filter(node -> !node.getIsDirectory())
                .collect(Collectors.toList());
    }
}
```

## 5. Controller

### SkillNodeController.java
```java
package com.cortex.skill.controller;

import com.cortex.common.core.controller.BaseController;
import com.cortex.common.core.domain.AjaxResult;
import com.cortex.skill.domain.SkillNode;
import com.cortex.skill.service.ISkillNodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 技能管理Controller
 */
@RestController
@RequestMapping("/skill")
public class SkillNodeController extends BaseController {

    @Autowired
    private ISkillNodeService skillNodeService;

    /**
     * 获取技能树
     */
    @PreAuthorize("@ss.hasPermi('skill:node:list')")
    @GetMapping("/tree")
    public AjaxResult getTree() {
        List<SkillNode> tree = skillNodeService.getSkillTree();
        return AjaxResult.success(tree);
    }

    /**
     * 创建文件夹
     */
    @PreAuthorize("@ss.hasPermi('skill:node:add')")
    @PostMapping("/folder")
    public AjaxResult createFolder(@RequestBody SkillNode node) {
        return toAjax(skillNodeService.createFolder(node));
    }

    /**
     * 创建文件
     */
    @PreAuthorize("@ss.hasPermi('skill:node:add')")
    @PostMapping("/file")
    public AjaxResult createFile(@RequestBody SkillNode node) {
        return toAjax(skillNodeService.createFile(node));
    }

    /**
     * 删除节点
     */
    @PreAuthorize("@ss.hasPermi('skill:node:remove')")
    @DeleteMapping("/{id}")
    public AjaxResult deleteNode(@PathVariable Long id) {
        return toAjax(skillNodeService.deleteNode(id));
    }

    /**
     * 重命名节点
     */
    @PreAuthorize("@ss.hasPermi('skill:node:edit')")
    @PutMapping("/rename")
    public AjaxResult renameNode(@RequestBody Map<String, Object> params) {
        Long id = Long.parseLong(params.get("id").toString());
        String name = params.get("name").toString();
        return toAjax(skillNodeService.renameNode(id, name));
    }

    /**
     * 移动节点
     */
    @PreAuthorize("@ss.hasPermi('skill:node:edit')")
    @PutMapping("/move")
    public AjaxResult moveNode(@RequestBody Map<String, Object> params) {
        Long id = Long.parseLong(params.get("id").toString());
        Long targetId = Long.parseLong(params.get("targetId").toString());
        String dropType = params.get("dropType").toString();
        return toAjax(skillNodeService.moveNode(id, targetId, dropType));
    }

    /**
     * 获取文件内容
     */
    @PreAuthorize("@ss.hasPermi('skill:node:query')")
    @GetMapping("/content")
    public AjaxResult getContent(@RequestParam String filePath) {
        String content = skillNodeService.getFileContent(filePath);
        return AjaxResult.success("content", content);
    }

    /**
     * 保存文件内容
     */
    @PreAuthorize("@ss.hasPermi('skill:node:edit')")
    @PostMapping("/content")
    public AjaxResult saveContent(@RequestBody Map<String, Object> params) {
        Long id = Long.parseLong(params.get("id").toString());
        String content = params.get("content").toString();
        return toAjax(skillNodeService.saveFileContent(id, content));
    }

    /**
     * 获取所有文件列表
     */
    @PreAuthorize("@ss.hasPermi('skill:node:list')")
    @GetMapping("/files")
    public AjaxResult getAllFiles() {
        List<SkillNode> files = skillNodeService.getAllFiles();
        return AjaxResult.success(files);
    }
}
```

## 6. Mapper XML

### SkillNodeMapper.xml
```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.cortex.skill.mapper.SkillNodeMapper">

    <resultMap id="SkillNodeResult" type="com.cortex.skill.domain.SkillNode">
        <id     property="id"           column="id"             />
        <result property="name"         column="name"           />
        <result property="path"         column="path"           />
        <result property="isDirectory"  column="is_directory"   />
        <result property="parentId"     column="parent_id"      />
        <result property="sortOrder"    column="sort_order"     />
        <result property="content"      column="content"        />
        <result property="fileSize"     column="file_size"      />
        <result property="createBy"     column="create_by"      />
        <result property="createTime"   column="create_time"    />
        <result property="updateBy"     column="update_by"      />
        <result property="updateTime"   column="update_time"    />
        <result property="remark"       column="remark"         />
    </resultMap>

    <select id="selectAllNodes" resultMap="SkillNodeResult">
        SELECT id, name, path, is_directory, parent_id, sort_order, file_size
        FROM skill_node
        ORDER BY sort_order ASC, create_time ASC
    </select>

    <select id="selectNodeById" parameterType="Long" resultMap="SkillNodeResult">
        SELECT * FROM skill_node WHERE id = #{id}
    </select>

    <select id="selectNodeByPath" parameterType="String" resultMap="SkillNodeResult">
        SELECT * FROM skill_node WHERE path = #{path}
    </select>

    <select id="selectChildrenByParentId" parameterType="Long" resultMap="SkillNodeResult">
        SELECT * FROM skill_node WHERE parent_id = #{parentId}
    </select>

    <insert id="insertNode" parameterType="com.cortex.skill.domain.SkillNode" useGeneratedKeys="true" keyProperty="id">
        INSERT INTO skill_node (
            name, path, is_directory, parent_id, sort_order, content, file_size,
            create_by, create_time, update_by, update_time, remark
        ) VALUES (
            #{name}, #{path}, #{isDirectory}, #{parentId}, #{sortOrder}, #{content}, #{fileSize},
            #{createBy}, NOW(), #{updateBy}, NOW(), #{remark}
        )
    </insert>

    <update id="updateNode" parameterType="com.cortex.skill.domain.SkillNode">
        UPDATE skill_node
        <set>
            <if test="name != null and name != ''">name = #{name},</if>
            <if test="path != null and path != ''">path = #{path},</if>
            <if test="parentId != null">parent_id = #{parentId},</if>
            <if test="sortOrder != null">sort_order = #{sortOrder},</if>
            <if test="content != null">content = #{content},</if>
            <if test="fileSize != null">file_size = #{fileSize},</if>
            update_by = #{updateBy},
            update_time = NOW()
        </set>
        WHERE id = #{id}
    </update>

    <delete id="deleteNodeById" parameterType="Long">
        DELETE FROM skill_node WHERE id = #{id}
    </delete>

    <delete id="deleteNodesByIds" parameterType="Long">
        DELETE FROM skill_node WHERE id IN
        <foreach collection="array" item="id" open="(" separator="," close=")">
            #{id}
        </foreach>
    </delete>

</mapper>
```

## 7. 菜单权限配置

在系统菜单管理中添加以下菜单（或直接插入数据库）：

```sql
-- 菜单 SQL
INSERT INTO sys_menu(menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES('Skill管理', 0, 5, 'skill', NULL, 1, 0, 'M', '0', '0', NULL, 'skill', 'admin', SYSDATE(), '', NULL, 'Skill管理目录');

INSERT INTO sys_menu(menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES('Skill列表', (SELECT menu_id FROM sys_menu WHERE menu_name = 'Skill管理'), 1, 'index', 'skill/index', 1, 0, 'C', '0', '0', 'skill:node:list', 'list', 'admin', SYSDATE(), '', NULL, 'Skill管理列表');
```

## 完成！

按照以上步骤实现后端代码，前端页面即可正常工作。
