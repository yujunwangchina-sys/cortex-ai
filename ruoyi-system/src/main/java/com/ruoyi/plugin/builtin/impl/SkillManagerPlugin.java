package com.ruoyi.plugin.builtin.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.plugin.builtin.IBuiltinPlugin;
import com.ruoyi.plugin.builtin.PluginInfo;
import com.ruoyi.plugin.builtin.ToolDefinition;
import com.ruoyi.plugin.builtin.ToolResult;
import com.ruoyi.skill.domain.SkillNode;
import com.ruoyi.skill.mapper.SkillNodeMapper;
import com.ruoyi.skill.util.SkillMetadataParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 技能管理内置插件 - v5.0 简化版（借鉴 Hermes）
 * 
 * 工具：
 * 1. skills_list - 列出所有技能包（全局+个人）
 * 2. skill_view - 查看技能的完整SKILL.md内容
 * 3. skill_tree - 查看技能包的文件树结构
 * 4. skill_read_file - 读取技能包内的任意文件
 * 5. skill_manage - 统一的技能管理工具 ⭐
 *    - create: 创建新技能
 *    - patch: 精确更新部分内容（推荐用于修复）
 *    - edit: 全量重写（仅用于大改）
 *    - delete: 删除技能
 * 
 * 渐进式披露工作流：
 * skills_list → skill_tree → skill_view/skill_read_file
 * 
 * 技能管理工作流：
 * skill_manage(action='create') → skill_manage(action='patch') → skill_manage(action='delete')
 */
@Component
public class SkillManagerPlugin implements IBuiltinPlugin
{
    private static final Logger log = LoggerFactory.getLogger(SkillManagerPlugin.class);

    @Autowired
    private SkillNodeMapper skillNodeMapper;

    @Override
    public PluginInfo getPluginInfo()
    {
        PluginInfo info = new PluginInfo("技能管理", "skill-manager", "查看、创建、编辑和管理个人技能（统一接口 skill_manage）");
        info.setVersion("5.0.0");
        info.setAuthor("CORTEX");
        info.setCategory("skill");
        info.setEmoji("📚");
        info.setRequireApproval(false);
        return info;
    }

    @Override
    public List<ToolDefinition> getTools()
    {
        List<ToolDefinition> tools = new ArrayList<>();

        // 1. skills_list - 列出所有技能包
        ToolDefinition list = new ToolDefinition();
        list.setName("skills_list");
        list.setDescription("列出所有可用的技能包（全局+个人）。返回技能包ID、名称、DESCRIPTION.md内容、子技能列表。");
        Map<String, Object> listSchema = new HashMap<>();
        listSchema.put("type", "object");
        listSchema.put("properties", new HashMap<>());
        list.setInputSchema(listSchema);
        tools.add(list);

        // 2. skill_view - 查看技能完整内容
        ToolDefinition view = new ToolDefinition();
        view.setName("skill_view");
        view.setDescription("查看技能的完整SKILL.md内容。参数：skillId（技能包ID或技能文件ID）。");
        Map<String, Object> viewSchema = new HashMap<>();
        viewSchema.put("type", "object");
        Map<String, Object> viewProps = new HashMap<>();
        viewProps.put("skillId", Map.of("type", "integer", "description", "技能ID（从skills_list获取）"));
        viewSchema.put("properties", viewProps);
        viewSchema.put("required", List.of("skillId"));
        view.setInputSchema(viewSchema);
        tools.add(view);

        // 3. skill_tree - 查看技能包的文件树结构
        ToolDefinition tree = new ToolDefinition();
        tree.setName("skill_tree");
        tree.setDescription("查看技能包的完整文件树结构（包含SKILL.md和所有辅助文件）。参数：skillId（技能包ID）。返回树形结构，方便查看有哪些文件可以读取。");
        Map<String, Object> treeSchema = new HashMap<>();
        treeSchema.put("type", "object");
        Map<String, Object> treeProps = new HashMap<>();
        treeProps.put("skillId", Map.of("type", "integer", "description", "技能包ID（从skills_list获取）"));
        treeSchema.put("properties", treeProps);
        treeSchema.put("required", List.of("skillId"));
        tree.setInputSchema(treeSchema);
        tools.add(tree);

        // 4. skill_read_file - 读取技能包内的任意文件
        ToolDefinition readFile = new ToolDefinition();
        readFile.setName("skill_read_file");
        readFile.setDescription("读取技能包内的任意文件（如references/api.md、templates/config.yaml等）。需要：skillId（技能包ID）、filePath（文件相对路径，从skill_tree获取）。");
        Map<String, Object> readFileSchema = new HashMap<>();
        readFileSchema.put("type", "object");
        Map<String, Object> readFileProps = new HashMap<>();
        readFileProps.put("skillId", Map.of("type", "integer", "description", "技能包ID"));
        readFileProps.put("filePath", Map.of("type", "string", "description", "文件相对路径，如 'references/api.md'"));
        readFileSchema.put("properties", readFileProps);
        readFileSchema.put("required", List.of("skillId", "filePath"));
        readFile.setInputSchema(readFileSchema);
        tools.add(readFile);

        // 5. skill_manage - 统一的技能管理工具 (inspired by Hermes)
        ToolDefinition manage = new ToolDefinition();
        manage.setName("skill_manage");
        manage.setDescription(
            "统一的技能管理工具（创建、更新、删除）。技能是你的流程记忆 - 可复用的方法。\n\n" +
            "操作类型：\n" +
            "- create: 创建新技能（需要 name, content）\n" +
            "- patch: 精确更新部分内容（需要 name, old_string, new_string）- 推荐用于修复\n" +
            "- edit: 全量重写 SKILL.md（需要 name, content）- 仅用于大改\n" +
            "- delete: 删除技能（需要 name）\n\n" +
            "何时创建：复杂任务完成 (5+ 工具调用)、错误克服、用户纠正的方法有效、发现非平凡工作流程、或用户要求记住流程。\n" +
            "何时更新：指令过时/错误、OS特定失败、使用中发现缺少步骤或陷阱。如果使用技能时遇到问题，立即 patch。\n\n" +
            "困难/迭代任务后，主动提议保存为技能。简单一次性操作跳过。创建/删除前与用户确认。\n\n" +
            "好的技能包含：触发条件、编号步骤和具体命令、陷阱部分、验证步骤。使用 skill_view() 查看格式示例。"
        );
        Map<String, Object> manageSchema = new HashMap<>();
        manageSchema.put("type", "object");
        Map<String, Object> manageProps = new HashMap<>();
        manageProps.put("action", Map.of(
            "type", "string",
            "enum", List.of("create", "patch", "edit", "delete"),
            "description", "要执行的操作"
        ));
        manageProps.put("name", Map.of(
            "type", "string",
            "description", "技能名称（小写，连字符/下划线，最多64字符）"
        ));
        manageProps.put("content", Map.of(
            "type", "string",
            "description", "完整的 SKILL.md 内容（YAML frontmatter + markdown正文）。create 和 edit 必需。"
        ));
        manageProps.put("old_string", Map.of(
            "type", "string",
            "description", "patch 操作：要查找的文本。必须唯一。包含足够的上下文以确保唯一性。"
        ));
        manageProps.put("new_string", Map.of(
            "type", "string",
            "description", "patch 操作：替换文本。可以是空字符串来删除匹配文本。"
        ));
        manageSchema.put("properties", manageProps);
        manageSchema.put("required", List.of("action", "name"));
        manage.setInputSchema(manageSchema);
        tools.add(manage);

        return tools;
    }

    @Override
    public String executeTool(String toolName, Map<String, Object> arguments)
    {
        try
        {
            switch (toolName)
            {
                case "skills_list": return listSkills(arguments);
                case "skill_view": return viewSkill(arguments);
                case "skill_tree": return skillTree(arguments);
                case "skill_read_file": return readSkillFile(arguments);
                case "skill_manage": return skillManage(arguments);
                default: return ToolResult.error("未知工具: " + toolName).toJson();
            }
        }
        catch (Exception e)
        {
            log.error("技能管理失败: " + toolName, e);
            return ToolResult.error("执行失败: " + e.getMessage()).toJson();
        }
    }

    /**
     * skills_list - 列出所有技能包（全局+个人）
     */
    @SuppressWarnings("unchecked")
    private String listSkills(Map<String, Object> args)
    {
        String businessSystem = (String) args.getOrDefault("_businessSystem", "cortex");
        String userLoginName = (String) args.get("_userLoginName");
        Long agentId = args.get("_agentId") != null ? ((Number) args.get("_agentId")).longValue() : null;

        log.info("🔍 skills_list called [agentId={}, user={}]", agentId, userLoginName);

        // 1. 加载授权的全局技能
        List<SkillNode> authorizedSkillIds = new ArrayList<>();
        if (agentId != null)
        {
            authorizedSkillIds = loadAuthorizedGlobalSkills(agentId, businessSystem);
        }
        else
        {
            authorizedSkillIds = skillNodeMapper.selectSystemSkills(businessSystem);
        }

        // 2. 构建全局技能包列表
        List<Map<String, Object>> globalPackages = new ArrayList<>();
        Set<Long> processedPackageIds = new HashSet<>();

        for (SkillNode node : authorizedSkillIds)
        {
            String nodeType = node.getNodeType() != null ? node.getNodeType() : "skill";
            
            if ("skill_package".equals(nodeType) || ("directory".equals(nodeType) && node.getIsDirectory()))
            {
                if (!processedPackageIds.contains(node.getId()))
                {
                    processedPackageIds.add(node.getId());
                    Map<String, Object> packageInfo = buildPackageInfo(node.getId());
                    if (packageInfo != null)
                    {
                        globalPackages.add(packageInfo);
                    }
                }
                continue;
            }
            
            Long packageId = findPackageId(node);
            if (packageId != null && !processedPackageIds.contains(packageId))
            {
                processedPackageIds.add(packageId);
                Map<String, Object> packageInfo = buildPackageInfo(packageId);
                if (packageInfo != null)
                {
                    globalPackages.add(packageInfo);
                }
            }
        }

        // 3. 加载个人技能包
        List<Map<String, Object>> personalPackages = new ArrayList<>();
        try
        {
            String packageName = userLoginName + "的个人技能";
            SkillNode personalPackage = findOrCreatePersonalPackage(userLoginName, businessSystem, packageName);
            Map<String, Object> personalPackageInfo = buildPersonalPackageInfo(personalPackage);
            if (personalPackageInfo != null)
            {
                personalPackages.add(personalPackageInfo);
            }
        }
        catch (Exception e)
        {
            log.error("❌ Failed to load personal skills", e);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("globalPackages", globalPackages);
        result.put("personalPackages", personalPackages);
        result.put("totalGlobal", globalPackages.size());
        result.put("totalPersonal", personalPackages.size());

        return ToolResult.success("获取成功").addData("skills", result).toJson();
    }

    /**
     * skill_view - 查看技能的完整SKILL.md内容
     */
    private String viewSkill(Map<String, Object> args)
    {
        Long skillId = ((Number) args.get("skillId")).longValue();
        String businessSystem = (String) args.getOrDefault("_businessSystem", "cortex");
        String userLoginName = (String) args.get("_userLoginName");

        log.info("📖 skill_view called [skillId={}]", skillId);

        SkillNode node = skillNodeMapper.selectNodeById(skillId);
        if (node == null)
        {
            return ToolResult.error("技能不存在: " + skillId).toJson();
        }

        if (!isSkillVisible(node, businessSystem, userLoginName))
        {
            return ToolResult.error("无权访问此技能").toJson();
        }

        // 如果是技能包，查找其下的SKILL.md
        if ("skill_package".equals(node.getNodeType()) || node.getIsDirectory())
        {
            List<SkillNode> children = skillNodeMapper.selectChildrenByParentId(skillId);
            if (children != null)
            {
                for (SkillNode child : children)
                {
                    if ("SKILL.md".equalsIgnoreCase(child.getName()))
                    {
                        node = skillNodeMapper.selectNodeWithContent(child.getId());
                        break;
                    }
                }
            }
        }
        else
        {
            node = skillNodeMapper.selectNodeWithContent(skillId);
        }

        String content = node != null && node.getContent() != null ? node.getContent() : "";
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("skillId", skillId);
        result.put("skillName", node != null ? node.getName() : "");
        result.put("content", content);

        return ToolResult.success("获取成功").addData("skill", result).toJson();
    }

    /**
     * skill_create - 创建个人技能（简化版）
     */
    private String createSkill(Map<String, Object> args)
    {
        String name = (String) args.get("name");
        String content = (String) args.get("content");
        String sessionId = (String) args.get("_sessionId");
        String businessSystem = (String) args.getOrDefault("_businessSystem", "cortex");
        String userLoginName = (String) args.get("_userLoginName");

        log.info("📝 skill_create called [name={}, user={}]", name, userLoginName);

        if (name == null || name.trim().isEmpty())
        {
            return ToolResult.error("技能名称不能为空").toJson();
        }
        if (content == null || content.trim().isEmpty())
        {
            return ToolResult.error("SKILL.md内容不能为空").toJson();
        }
        if (!content.startsWith("---"))
        {
            return ToolResult.error("SKILL.md必须以YAML frontmatter开头（---）").toJson();
        }

        String packageName = userLoginName + "的个人技能";
        SkillNode personalPackage = findOrCreatePersonalPackage(userLoginName, businessSystem, packageName);

        List<SkillNode> existingChildren = skillNodeMapper.selectChildrenByParentId(personalPackage.getId());
        if (existingChildren != null)
        {
            for (SkillNode child : existingChildren)
            {
                if (name.equals(child.getName()) && child.getIsDirectory())
                {
                    return ToolResult.error("技能已存在: " + name + "。使用skill_edit更新现有技能。").toJson();
                }
            }
        }

        SkillNode skillDir = new SkillNode();
        skillDir.setParentId(personalPackage.getId());
        skillDir.setName(name);
        skillDir.setPath(personalPackage.getPath() + "/" + name);
        skillDir.setIsDirectory(true);
        skillDir.setNodeType("directory");
        skillDir.setSkillScope("personal");
        skillDir.setSkillType("general");
        skillDir.setLearnedFromSession(sessionId);
        skillDir.setBusinessSystem(businessSystem);
        skillDir.setOwnerUser(userLoginName);
        skillDir.setSortOrder(0);
        skillDir.setCreateBy("agent");
        skillDir.setCreateTime(new java.util.Date());
        int rows1 = skillNodeMapper.insertNode(skillDir);
        log.info("📊 技能目录创建结果 [rows={}, skillDirId={}]", rows1, skillDir.getId());

        if (skillDir.getId() == null) {
            log.error("❌ 技能目录ID未回填！");
            return ToolResult.error("技能目录创建失败：ID未生成").toJson();
        }

        SkillNode skillMd = new SkillNode();
        skillMd.setParentId(skillDir.getId());
        skillMd.setName("SKILL.md");
        skillMd.setPath(skillDir.getPath() + "/SKILL.md");
        skillMd.setFileExtension(".md");
        skillMd.setIsDirectory(false);
        skillMd.setNodeType("file");
        skillMd.setSkillScope("personal");
        skillMd.setBusinessSystem(businessSystem);
        skillMd.setOwnerUser(userLoginName);
        skillMd.setContent(content);
        skillMd.setFileSize((long) content.getBytes().length);
        skillMd.setMimeType("text/markdown");
        skillMd.setSortOrder(0);
        skillMd.setCreateBy("agent");
        skillMd.setCreateTime(new java.util.Date());
        int rows2 = skillNodeMapper.insertNode(skillMd);
        log.info("📊 SKILL.md创建结果 [rows={}, skillMdId={}, parentId={}]", rows2, skillMd.getId(), skillMd.getParentId());

        updatePersonalPackageDescription(personalPackage, name, content);

        log.info("✨ Skill created [name={}, id={}]", name, skillDir.getId());

        return ToolResult.success("技能创建成功")
                .addData("skillName", name)
                .addData("skillId", skillDir.getId())
                .addData("path", skillDir.getPath())
                .toJson();
    }

    /**
     * skill_edit - 编辑个人技能（全量替换SKILL.md）
     */
    private String editSkill(Map<String, Object> args)
    {
        String name = (String) args.get("name");
        String content = (String) args.get("content");
        String businessSystem = (String) args.getOrDefault("_businessSystem", "cortex");
        String userLoginName = (String) args.get("_userLoginName");

        log.info("✏️ skill_edit called [name={}, user={}]", name, userLoginName);

        if (name == null || name.trim().isEmpty())
        {
            return ToolResult.error("技能名称不能为空").toJson();
        }
        if (content == null || content.trim().isEmpty())
        {
            return ToolResult.error("SKILL.md内容不能为空").toJson();
        }
        if (!content.startsWith("---"))
        {
            return ToolResult.error("SKILL.md必须以YAML frontmatter开头（---）").toJson();
        }

        String packageName = userLoginName + "的个人技能";
        SkillNode personalPackage = findOrCreatePersonalPackage(userLoginName, businessSystem, packageName);

        List<SkillNode> children = skillNodeMapper.selectChildrenByParentId(personalPackage.getId());
        SkillNode skillDir = null;
        if (children != null)
        {
            for (SkillNode child : children)
            {
                if (name.equals(child.getName()) && child.getIsDirectory())
                {
                    skillDir = child;
                    break;
                }
            }
        }

        if (skillDir == null)
        {
            return ToolResult.error("技能不存在: " + name + "。使用skill_create创建新技能。").toJson();
        }

        // 权限检查：所有者 + 业务系统
        if (!userLoginName.equals(skillDir.getOwnerUser()))
        {
            return ToolResult.error("只能编辑自己的个人技能").toJson();
        }
        if (!businessSystem.equals(skillDir.getBusinessSystem()))
        {
            return ToolResult.error("无法跨业务系统编辑技能").toJson();
        }

        List<SkillNode> skillFiles = skillNodeMapper.selectChildrenByParentId(skillDir.getId());
        SkillNode skillMd = null;
        if (skillFiles != null)
        {
            for (SkillNode file : skillFiles)
            {
                if ("SKILL.md".equals(file.getName()))
                {
                    skillMd = file;
                    break;
                }
            }
        }

        if (skillMd == null)
        {
            return ToolResult.error("SKILL.md文件不存在").toJson();
        }

        // 解析并更新 skill_metadata（与createFile保持一致）
        JSONObject meta = SkillMetadataParser.parseFrontmatter(content);
        if (!meta.isEmpty()) {
            skillMd.setSkillMetadata(meta.toJSONString());
            log.info("📋 已更新skill_metadata: {}", meta.toJSONString());
        }

        skillMd.setContent(content);
        skillMd.setFileSize((long) content.getBytes().length);
        skillMd.setUpdateBy("agent");
        skillMd.setUpdateTime(new java.util.Date());
        int rows = skillNodeMapper.updateNode(skillMd);
        log.info("📊 SKILL.md更新结果 [rows={}, skillMdId={}]", rows, skillMd.getId());

        log.info("✨ Skill edited [name={}, id={}]", name, skillDir.getId());

        return ToolResult.success("技能编辑成功")
                .addData("skillName", name)
                .addData("skillId", skillDir.getId())
                .toJson();
    }

    /**
     * skill_tree - 查看技能包的文件树结构
     */
    private String skillTree(Map<String, Object> args)
    {
        Long skillId = ((Number) args.get("skillId")).longValue();
        String businessSystem = (String) args.getOrDefault("_businessSystem", "cortex");
        String userLoginName = (String) args.get("_userLoginName");

        log.info("🌲 skill_tree called [skillId={}]", skillId);

        SkillNode node = skillNodeMapper.selectNodeById(skillId);
        if (node == null)
        {
            return ToolResult.error("技能不存在: " + skillId).toJson();
        }

        if (!isSkillVisible(node, businessSystem, userLoginName))
        {
            return ToolResult.error("无权访问此技能").toJson();
        }

        // 如果传入的不是目录，查找其父目录
        Long targetDirId = skillId;
        if (!node.getIsDirectory())
        {
            targetDirId = node.getParentId();
            if (targetDirId == null)
            {
                return ToolResult.error("无法确定技能包目录").toJson();
            }
            node = skillNodeMapper.selectNodeById(targetDirId);
        }

        // 构建文件树
        Map<String, Object> tree = buildFileTree(targetDirId, "");
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("skillId", targetDirId);
        result.put("skillName", node.getName());
        result.put("skillPath", node.getPath());
        result.put("fileTree", tree);
        result.put("hint", "使用 skill_read_file(skillId, filePath) 读取任意文件");

        return ToolResult.success("获取成功").addData("tree", result).toJson();
    }

    /**
     * skill_read_file - 读取技能包内的任意文件
     */
    private String readSkillFile(Map<String, Object> args)
    {
        Long skillId = ((Number) args.get("skillId")).longValue();
        String filePath = (String) args.get("filePath");
        String businessSystem = (String) args.getOrDefault("_businessSystem", "cortex");
        String userLoginName = (String) args.get("_userLoginName");

        log.info("📄 skill_read_file called [skillId={}, filePath={}]", skillId, filePath);

        SkillNode skillPackage = skillNodeMapper.selectNodeById(skillId);
        if (skillPackage == null)
        {
            return ToolResult.error("技能包不存在: " + skillId).toJson();
        }

        if (!isSkillVisible(skillPackage, businessSystem, userLoginName))
        {
            return ToolResult.error("无权访问此技能").toJson();
        }

        // 在技能包下查找指定路径的文件
        SkillNode targetFile = findFileByPath(skillId, filePath);
        if (targetFile == null)
        {
            return ToolResult.error("文件不存在: " + filePath).toJson();
        }

        // 加载文件内容
        SkillNode fileWithContent = skillNodeMapper.selectNodeWithContent(targetFile.getId());
        String content = fileWithContent != null && fileWithContent.getContent() != null 
                ? fileWithContent.getContent() : "";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("skillId", skillId);
        result.put("filePath", filePath);
        result.put("fileName", targetFile.getName());
        result.put("fileType", targetFile.getFileExtension());
        result.put("content", content);

        return ToolResult.success("获取成功").addData("file", result).toJson();
    }

    /**
     * skill_manage - 统一的技能管理工具（借鉴 Hermes）
     */
    private String skillManage(Map<String, Object> args)
    {
        String action = (String) args.get("action");
        String name = (String) args.get("name");

        log.info("🔧 skill_manage called [action={}, name={}]", action, name);

        if (action == null || action.trim().isEmpty())
        {
            return ToolResult.error("action 参数必填: create, patch, edit, delete").toJson();
        }

        if (name == null || name.trim().isEmpty())
        {
            return ToolResult.error("name 参数必填").toJson();
        }

        switch (action)
        {
            case "create":
                return createSkill(args);
            case "edit":
                return editSkill(args);
            case "patch":
                return patchSkill(args);
            case "delete":
                return deleteSkill(args);
            default:
                return ToolResult.error("未知操作: " + action + "。支持: create, patch, edit, delete").toJson();
        }
    }

    /**
     * patchSkill - 精确更新技能的部分内容（string replacement）
     */
    private String patchSkill(Map<String, Object> args)
    {
        String name = (String) args.get("name");
        String oldString = (String) args.get("old_string");
        String newString = (String) args.get("new_string");
        String businessSystem = (String) args.getOrDefault("_businessSystem", "cortex");
        String userLoginName = (String) args.get("_userLoginName");

        log.info("🔄 patchSkill called [name={}, user={}]", name, userLoginName);

        // 参数验证
        if (oldString == null || oldString.trim().isEmpty())
        {
            return ToolResult.error("old_string 参数必填（要查找的文本）").toJson();
        }
        if (newString == null)
        {
            return ToolResult.error("new_string 参数必填（可以是空字符串来删除匹配文本）").toJson();
        }

        // 查找个人技能
        String packageName = userLoginName + "的个人技能";
        SkillNode personalPackage = findOrCreatePersonalPackage(userLoginName, businessSystem, packageName);

        List<SkillNode> children = skillNodeMapper.selectChildrenByParentId(personalPackage.getId());
        SkillNode skillDir = null;
        if (children != null)
        {
            for (SkillNode child : children)
            {
                if (name.equals(child.getName()) && child.getIsDirectory())
                {
                    skillDir = child;
                    break;
                }
            }
        }

        if (skillDir == null)
        {
            return ToolResult.error("技能不存在: " + name + "。使用 action='create' 创建新技能。").toJson();
        }

        // 权限检查：所有者 + 业务系统
        if (!userLoginName.equals(skillDir.getOwnerUser()))
        {
            return ToolResult.error("只能修改自己的个人技能").toJson();
        }
        if (!businessSystem.equals(skillDir.getBusinessSystem()))
        {
            return ToolResult.error("无法跨业务系统修改技能").toJson();
        }

        // 查找 SKILL.md 文件
        List<SkillNode> skillFiles = skillNodeMapper.selectChildrenByParentId(skillDir.getId());
        SkillNode skillMd = null;
        if (skillFiles != null)
        {
            for (SkillNode file : skillFiles)
            {
                if ("SKILL.md".equals(file.getName()))
                {
                    skillMd = skillNodeMapper.selectNodeWithContent(file.getId());
                    break;
                }
            }
        }

        if (skillMd == null)
        {
            return ToolResult.error("SKILL.md 文件不存在").toJson();
        }

        String content = skillMd.getContent() != null ? skillMd.getContent() : "";

        // 执行模糊匹配和替换（借鉴 Hermes 的 fuzzy_find_and_replace）
        String newContent = "";
        int matchCount;
        String matchError = null;

        try
        {
            // 简化版模糊匹配：处理空白字符差异
            String normalizedContent = normalizeWhitespace(content);
            String normalizedOld = normalizeWhitespace(oldString);

            matchCount = countOccurrences(normalizedContent, normalizedOld);

            if (matchCount == 0)
            {
                // 尝试精确匹配
                matchCount = countOccurrences(content, oldString);
                if (matchCount == 0)
                {
                    matchError = "未找到匹配项。请确保 old_string 准确，包含足够的上下文。";
                }
                else
                {
                    // 精确匹配成功
                    newContent = content.replace(oldString, newString);
                }
            }
            else if (matchCount > 1)
            {
                matchError = "找到 " + matchCount + " 个匹配项，必须唯一。请增加更多上下文使 old_string 唯一。";
            }
            else
            {
                // 唯一匹配，执行替换
                int startIndex = normalizedContent.indexOf(normalizedOld);
                if (startIndex >= 0)
                {
                    // 在原始内容中找到对应位置（考虑空白字符差异）
                    newContent = fuzzyReplace(content, oldString, newString);
                }
                else
                {
                    newContent = content.replace(oldString, newString);
                }
            }

            // 如果有错误，返回预览
            if (matchError != null)
            {
                String preview = content.length() > 500 ? content.substring(0, 500) + "..." : content;
                Map<String, Object> errorData = new LinkedHashMap<>();
                errorData.put("error", matchError);
                errorData.put("matchCount", matchCount);
                errorData.put("filePreview", preview);
                errorData.put("hint", "使用 skill_view(skillId=" + skillDir.getId() + ") 查看完整内容");
                return ToolResult.error(matchError).addData("detail", errorData).toJson();
            }

            // 验证新内容
            if (!newContent.startsWith("---"))
            {
                return ToolResult.error("patch 操作会破坏 SKILL.md 的 YAML frontmatter 结构。请重新检查 old_string 和 new_string。").toJson();
            }

            // 保存更新
            skillMd.setContent(newContent);
            skillMd.setFileSize((long) newContent.getBytes().length);
            skillMd.setUpdateBy("agent");
            skillMd.setUpdateTime(new java.util.Date());
            skillNodeMapper.updateNode(skillMd);

            log.info("✨ Skill patched [name={}, id={}, replacements={}]", name, skillDir.getId(), matchCount);

            return ToolResult.success("技能已更新（" + matchCount + " 处替换）")
                    .addData("skillName", name)
                    .addData("skillId", skillDir.getId())
                    .addData("replacementCount", matchCount)
                    .addData("oldPreview", truncate(oldString, 100))
                    .addData("newPreview", truncate(newString, 100))
                    .toJson();
        }
        catch (Exception e)
        {
            log.error("❌ Patch failed", e);
            return ToolResult.error("patch 失败: " + e.getMessage()).toJson();
        }
    }

    /**
     * deleteSkill - 删除个人技能
     */
    private String deleteSkill(Map<String, Object> args)
    {
        String name = (String) args.get("name");
        String businessSystem = (String) args.getOrDefault("_businessSystem", "cortex");
        String userLoginName = (String) args.get("_userLoginName");

        log.info("🗑️ deleteSkill called [name={}, user={}]", name, userLoginName);

        // 查找个人技能
        String packageName = userLoginName + "的个人技能";
        SkillNode personalPackage = findOrCreatePersonalPackage(userLoginName, businessSystem, packageName);

        List<SkillNode> children = skillNodeMapper.selectChildrenByParentId(personalPackage.getId());
        SkillNode skillDir = null;
        if (children != null)
        {
            for (SkillNode child : children)
            {
                if (name.equals(child.getName()) && child.getIsDirectory())
                {
                    skillDir = child;
                    break;
                }
            }
        }

        if (skillDir == null)
        {
            return ToolResult.error("技能不存在: " + name).toJson();
        }

        // 权限检查：所有者 + 业务系统
        if (!userLoginName.equals(skillDir.getOwnerUser()))
        {
            return ToolResult.error("只能删除自己的个人技能").toJson();
        }
        if (!businessSystem.equals(skillDir.getBusinessSystem()))
        {
            return ToolResult.error("无法跨业务系统删除技能").toJson();
        }

        // 递归删除技能目录及其所有子节点
        // 注意：这里依赖 Service 层的 @Transactional 事务保护
        try
        {
            deleteNodeRecursive(skillDir.getId());
            log.info("✨ Skill deleted [name={}, id={}]", name, skillDir.getId());

            return ToolResult.success("技能已删除: " + name)
                    .addData("skillName", name)
                    .addData("deletedSkillId", skillDir.getId())
                    .toJson();
        }
        catch (Exception e)
        {
            log.error("❌ Delete failed", e);
            return ToolResult.error("删除失败: " + e.getMessage()).toJson();
        }
    }

    // =========================================================================
    // Patch 辅助方法
    // =========================================================================

    /**
     * 标准化空白字符（用于模糊匹配）
     */
    private String normalizeWhitespace(String text)
    {
        if (text == null) return "";
        // 将连续空白字符（空格、制表符、换行）统一为单个空格
        return text.replaceAll("\\s+", " ").trim();
    }

    /**
     * 计算子串出现次数
     */
    private int countOccurrences(String text, String substring)
    {
        if (text == null || substring == null || substring.isEmpty()) return 0;
        
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(substring, index)) != -1)
        {
            count++;
            index += substring.length();
        }
        return count;
    }

    /**
     * 模糊替换：处理空白字符差异
     */
    private String fuzzyReplace(String content, String oldString, String newString)
    {
        // 简化版：尝试直接替换
        if (content.contains(oldString))
        {
            return content.replace(oldString, newString);
        }

        // 如果直接替换失败，尝试标准化空白字符后匹配
        String[] contentLines = content.split("\n");
        String[] oldLines = oldString.split("\n");
        
        // 寻找匹配的起始位置
        for (int i = 0; i <= contentLines.length - oldLines.length; i++)
        {
            boolean match = true;
            for (int j = 0; j < oldLines.length; j++)
            {
                if (!normalizeWhitespace(contentLines[i + j]).equals(normalizeWhitespace(oldLines[j])))
                {
                    match = false;
                    break;
                }
            }
            
            if (match)
            {
                // 找到匹配，执行替换
                StringBuilder result = new StringBuilder();
                for (int k = 0; k < i; k++)
                {
                    result.append(contentLines[k]).append("\n");
                }
                result.append(newString);
                for (int k = i + oldLines.length; k < contentLines.length; k++)
                {
                    result.append("\n").append(contentLines[k]);
                }
                return result.toString();
            }
        }

        // 如果都失败，返回原内容
        return content;
    }

    /**
     * 递归删除节点及其所有子节点
     * 注意：调用方需确保在事务中执行，或使用批量删除避免部分删除
     */
    private void deleteNodeRecursive(Long nodeId)
    {
        if (nodeId == null) return;

        // 收集所有要删除的节点ID（包括子孙节点）
        List<Long> allNodeIds = new ArrayList<>();
        collectNodeIdsRecursive(nodeId, allNodeIds);

        // 批量删除（降低删除失败风险）
        if (!allNodeIds.isEmpty())
        {
            log.debug("Deleting {} nodes in batch", allNodeIds.size());
            for (Long id : allNodeIds)
            {
                skillNodeMapper.deleteNodeById(id);
            }
        }
    }

    /**
     * 递归收集节点ID（深度优先，先子后父）
     */
    private void collectNodeIdsRecursive(Long nodeId, List<Long> result)
    {
        if (nodeId == null) return;

        // 先收集所有子节点
        List<SkillNode> children = skillNodeMapper.selectChildrenByParentId(nodeId);
        if (children != null)
        {
            for (SkillNode child : children)
            {
                collectNodeIdsRecursive(child.getId(), result);
            }
        }

        // 最后添加当前节点（确保先删子后删父）
        result.add(nodeId);
    }

    // =========================================================================
    // 辅助方法
    // =========================================================================

    /**
     * 递归构建文件树
     */
    private Map<String, Object> buildFileTree(Long dirId, String currentPath)
    {
        List<SkillNode> children = skillNodeMapper.selectChildrenByParentId(dirId);
        if (children == null || children.isEmpty())
        {
            return new LinkedHashMap<>();
        }

        Map<String, Object> tree = new LinkedHashMap<>();
        List<Map<String, Object>> files = new ArrayList<>();
        List<Map<String, Object>> directories = new ArrayList<>();

        for (SkillNode child : children)
        {
            String childPath = currentPath.isEmpty() 
                    ? child.getName() 
                    : currentPath + "/" + child.getName();

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", child.getId());
            item.put("name", child.getName());
            item.put("path", childPath);
            item.put("isDirectory", child.getIsDirectory());

            if (child.getIsDirectory())
            {
                // 递归获取子目录内容
                Map<String, Object> subTree = buildFileTree(child.getId(), childPath);
                item.put("children", subTree);
                directories.add(item);
            }
            else
            {
                item.put("extension", child.getFileExtension());
                item.put("size", child.getFileSize());
                files.add(item);
            }
        }

        tree.put("directories", directories);
        tree.put("files", files);
        return tree;
    }

    /**
     * 根据路径查找文件
     */
    private SkillNode findFileByPath(Long packageId, String filePath)
    {
        if (filePath == null || filePath.trim().isEmpty())
        {
            return null;
        }

        String[] parts = filePath.split("/");
        Long currentId = packageId;

        for (String part : parts)
        {
            if (part.trim().isEmpty()) continue;

            List<SkillNode> children = skillNodeMapper.selectChildrenByParentId(currentId);
            if (children == null) return null;

            SkillNode found = null;
            for (SkillNode child : children)
            {
                if (part.equals(child.getName()))
                {
                    found = child;
                    break;
                }
            }

            if (found == null) return null;
            
            if (found.getIsDirectory())
            {
                currentId = found.getId();
            }
            else
            {
                return found;  // 找到目标文件
            }
        }

        return null;
    }

    // =========================================================================
    // 原有辅助方法
    // =========================================================================

    private List<SkillNode> loadAuthorizedGlobalSkills(Long agentId, String businessSystem)
    {
        List<Long> authorizedSkillIds = com.ruoyi.common.utils.spring.SpringUtils
                .getBean(com.ruoyi.agent.mapper.AiAgentSkillMapper.class)
                .selectSkillIdsByAgentId(agentId);
        
        List<SkillNode> result = new ArrayList<>();
        
        for (Long skillId : authorizedSkillIds)
        {
            SkillNode node = skillNodeMapper.selectNodeById(skillId);
            if (node == null) continue;
            
            String scope = node.getSkillScope() != null ? node.getSkillScope() : "global";
            if (!"personal".equals(scope))
            {
                result.add(node);
                
                String nodeType = node.getNodeType() != null ? node.getNodeType() : "skill";
                if ("skill_package".equals(nodeType) || "directory".equals(nodeType))
                {
                    List<SkillNode> children = skillNodeMapper.selectChildrenByParentId(node.getId());
                    if (children != null)
                    {
                        for (SkillNode child : children)
                        {
                            if ("skill".equals(child.getNodeType()))
                            {
                                result.add(child);
                            }
                        }
                    }
                }
            }
        }
        
        return result;
    }

    private Map<String, Object> buildPackageInfo(Long packageId)
    {
        SkillNode packageNode = skillNodeMapper.selectNodeById(packageId);
        if (packageNode == null) return null;
        
        Map<String, Object> packageInfo = new LinkedHashMap<>();
        packageInfo.put("id", packageNode.getId());
        packageInfo.put("name", packageNode.getName());
        packageInfo.put("path", packageNode.getPath());
        
        List<SkillNode> children = skillNodeMapper.selectChildrenByParentId(packageId);
        if (children == null || children.isEmpty())
        {
            packageInfo.put("description", "");
            packageInfo.put("skills", new ArrayList<>());
            return packageInfo;
        }
        
        String description = "";
        List<Map<String, Object>> skillsList = new ArrayList<>();
        
        for (SkillNode child : children)
        {
            String childName = child.getName();
            
            if ("DESCRIPTION.md".equalsIgnoreCase(childName))
            {
                SkillNode descNode = skillNodeMapper.selectNodeWithContent(child.getId());
                if (descNode != null && descNode.getContent() != null && !descNode.getContent().isEmpty())
                {
                    description = extractFirstMeaningfulContent(descNode.getContent(), 200);
                }
            }
            else if (childName != null && childName.toLowerCase().endsWith(".md"))
            {
                Map<String, Object> skillItem = new LinkedHashMap<>();
                skillItem.put("id", child.getId());
                skillItem.put("name", childName);
                
                String skillDesc = extractDescriptionFromYamlFrontmatter(child);
                if (skillDesc == null || skillDesc.isEmpty())
                {
                    skillDesc = extractShortDescription(child);
                }
                skillItem.put("description", skillDesc);
                
                skillsList.add(skillItem);
            }
        }
        
        packageInfo.put("description", description);
        packageInfo.put("skills", skillsList);
        
        return packageInfo;
    }

    private Map<String, Object> buildPersonalPackageInfo(SkillNode personalPackage)
    {
        Map<String, Object> packageInfo = new LinkedHashMap<>();
        packageInfo.put("id", personalPackage.getId());
        packageInfo.put("name", personalPackage.getName());
        packageInfo.put("path", personalPackage.getPath());
        packageInfo.put("scope", "personal");
        
        List<SkillNode> children = skillNodeMapper.selectChildrenByParentId(personalPackage.getId());
        if (children == null || children.isEmpty())
        {
            packageInfo.put("description", "您的个人技能集合，目前还没有创建任何技能");
            packageInfo.put("skills", new ArrayList<>());
            return packageInfo;
        }
        
        String description = "";
        List<Map<String, Object>> skillsList = new ArrayList<>();
        
        for (SkillNode child : children)
        {
            String childName = child.getName();
            
            if ("DESCRIPTION.md".equalsIgnoreCase(childName))
            {
                SkillNode descNode = skillNodeMapper.selectNodeWithContent(child.getId());
                if (descNode != null && descNode.getContent() != null && !descNode.getContent().isEmpty())
                {
                    description = extractFirstMeaningfulContent(descNode.getContent(), 200);
                }
            }
            else if (child.getIsDirectory() != null && child.getIsDirectory())
            {
                Map<String, Object> skillItem = new LinkedHashMap<>();
                skillItem.put("id", child.getId());
                skillItem.put("name", childName);
                
                List<SkillNode> skillFiles = skillNodeMapper.selectChildrenByParentId(child.getId());
                String skillDesc = "";
                if (skillFiles != null)
                {
                    for (SkillNode file : skillFiles)
                    {
                        if ("SKILL.md".equalsIgnoreCase(file.getName()))
                        {
                            SkillNode skillMdNode = skillNodeMapper.selectNodeWithContent(file.getId());
                            if (skillMdNode != null)
                            {
                                skillDesc = extractDescriptionFromYamlFrontmatter(skillMdNode);
                                if (skillDesc.isEmpty())
                                {
                                    skillDesc = extractShortDescription(skillMdNode);
                                }
                            }
                            break;
                        }
                    }
                }
                skillItem.put("description", skillDesc);
                skillsList.add(skillItem);
            }
        }
        
        if (description.isEmpty())
        {
            description = "您的个人技能集合";
        }
        
        packageInfo.put("description", description);
        packageInfo.put("skills", skillsList);
        
        return packageInfo;
    }

    private Long findPackageId(SkillNode node)
    {
        if (node.getParentId() == null) return null;
        
        try
        {
            SkillNode parent = skillNodeMapper.selectNodeById(node.getParentId());
            if (parent != null)
            {
                String nodeType = parent.getNodeType() != null ? parent.getNodeType() : "directory";
                if ("skill_package".equals(nodeType) || "directory".equals(nodeType))
                {
                    return parent.getId();
                }
            }
        }
        catch (Exception e)
        {
            log.warn("Failed to find package for node {}", node.getId(), e);
        }
        
        return null;
    }

    private SkillNode findOrCreatePersonalPackage(String userLoginName, String businessSystem, String packageName)
    {
        // 查询该用户在该业务系统下的所有个人节点（包括技能包）
        List<SkillNode> userNodes = skillNodeMapper.selectUserSkills(userLoginName);
        if (userNodes != null)
        {
            for (SkillNode node : userNodes)
            {
                if ("skill_package".equals(node.getNodeType()) &&
                    "personal".equals(node.getSkillScope()) &&
                    businessSystem.equals(node.getBusinessSystem()))
                {
                    log.debug("✓ Found existing personal package [user={}, business={}, id={}]", 
                             userLoginName, businessSystem, node.getId());
                    return node;
                }
            }
        }

        log.info("✨ Creating new personal package [user={}, business={}]", userLoginName, businessSystem);

        // 规范化路径：/personal/{business_system}/{owner_user}
        String packagePath = "/personal/" + businessSystem + "/" + userLoginName;
        
        // 创建技能包节点
        SkillNode packageNode = new SkillNode();
        packageNode.setName(packageName);
        packageNode.setPath(packagePath);
        packageNode.setIsDirectory(true);
        packageNode.setNodeType("skill_package");
        packageNode.setSkillScope("personal");
        packageNode.setBusinessSystem(businessSystem);
        packageNode.setOwnerUser(userLoginName);
        packageNode.setSortOrder(0);
        packageNode.setCreateBy(userLoginName);
        packageNode.setCreateTime(new java.util.Date());
        
        skillNodeMapper.insertNode(packageNode);
        log.info("✓ Personal package created [id={}, path={}]", packageNode.getId(), packagePath);
        
        // 创建 DESCRIPTION.md
        SkillNode descNode = new SkillNode();
        descNode.setParentId(packageNode.getId());
        descNode.setName("DESCRIPTION.md");
        descNode.setFileExtension(".md");
        descNode.setPath(packagePath + "/DESCRIPTION.md");
        descNode.setIsDirectory(false);
        descNode.setNodeType("file");
        descNode.setSkillScope("personal");
        descNode.setBusinessSystem(businessSystem);
        descNode.setOwnerUser(userLoginName);
        descNode.setContent("# " + userLoginName + " 的个人技能集合\n\n此技能包包含 Agent 自学习生成的技能。\n");
        descNode.setFileSize((long) descNode.getContent().getBytes().length);
        descNode.setMimeType("text/markdown");
        descNode.setSortOrder(0);
        descNode.setCreateBy(userLoginName);
        descNode.setCreateTime(new java.util.Date());
        
        skillNodeMapper.insertNode(descNode);
        log.info("✓ DESCRIPTION.md created [id={}]", descNode.getId());
        
        return packageNode;
    }

    private void updatePersonalPackageDescription(SkillNode personalPackage, String skillName, String skillContent)
    {
        try
        {
            List<SkillNode> children = skillNodeMapper.selectChildrenByParentId(personalPackage.getId());
            SkillNode descNode = null;
            if (children != null)
            {
                for (SkillNode child : children)
                {
                    if ("DESCRIPTION.md".equalsIgnoreCase(child.getName()))
                    {
                        descNode = skillNodeMapper.selectNodeWithContent(child.getId());
                        break;
                    }
                }
            }

            // 如果 DESCRIPTION.md 不存在，创建它（兜底逻辑）
            if (descNode == null)
            {
                log.warn("⚠️ DESCRIPTION.md not found for package {}, creating it", personalPackage.getId());
                descNode = new SkillNode();
                descNode.setParentId(personalPackage.getId());
                descNode.setName("DESCRIPTION.md");
                descNode.setFileExtension(".md");
                descNode.setPath(personalPackage.getPath() + "/DESCRIPTION.md");
                descNode.setIsDirectory(false);
                descNode.setNodeType("file");
                descNode.setSkillScope(personalPackage.getSkillScope());
                descNode.setBusinessSystem(personalPackage.getBusinessSystem());
                descNode.setOwnerUser(personalPackage.getOwnerUser());
                descNode.setContent("# " + personalPackage.getOwnerUser() + " 的个人技能集合\n\n此技能包包含 Agent 自学习生成的技能。\n");
                descNode.setFileSize((long) descNode.getContent().getBytes().length);
                descNode.setMimeType("text/markdown");
                descNode.setSortOrder(0);
                descNode.setCreateBy("agent");
                descNode.setCreateTime(new java.util.Date());
                skillNodeMapper.insertNode(descNode);
                descNode = skillNodeMapper.selectNodeWithContent(descNode.getId());
            }

            // 提取技能描述
            String skillDescription = "";
            if (skillContent.startsWith("---"))
            {
                int endIndex = skillContent.indexOf("---", 3);
                if (endIndex > 3)
                {
                    String yamlBlock = skillContent.substring(3, endIndex).trim();
                    String[] lines = yamlBlock.split("\n");
                    for (String line : lines)
                    {
                        if (line.startsWith("description:"))
                        {
                            skillDescription = line.substring("description:".length()).trim();
                            skillDescription = skillDescription.replaceAll("^\"|\"$|^'|'$", "");
                            break;
                        }
                    }
                }
            }

            if (skillDescription.isEmpty())
            {
                skillDescription = "个人技能";
            }

            // 更新 DESCRIPTION.md 内容
            String currentContent = descNode.getContent() != null ? descNode.getContent() : "";
            if (!currentContent.contains("## " + skillName))
            {
                String newEntry = "\n## " + skillName + "\n" + skillDescription + "\n";
                String updatedContent = currentContent + newEntry;
                descNode.setContent(updatedContent);
                descNode.setFileSize((long) updatedContent.getBytes().length);
                descNode.setUpdateBy("agent");
                descNode.setUpdateTime(new java.util.Date());
                skillNodeMapper.updateNode(descNode);
                log.info("✓ DESCRIPTION.md updated with skill: {}", skillName);
            }
        }
        catch (Exception e)
        {
            log.error("❌ Failed to update DESCRIPTION.md", e);
        }
    }

    private String extractFirstMeaningfulContent(String content, int maxLength)
    {
        if (content == null || content.isEmpty()) return "";
        
        String[] lines = content.split("\n");
        StringBuilder result = new StringBuilder();
        boolean inFrontmatter = false;
        int frontmatterCount = 0;
        
        for (String line : lines)
        {
            String trimmed = line.trim();
            
            if (trimmed.equals("---"))
            {
                frontmatterCount++;
                if (frontmatterCount == 1) inFrontmatter = true;
                if (frontmatterCount == 2) inFrontmatter = false;
                continue;
            }
            
            if (inFrontmatter) continue;
            
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            
            result.append(trimmed).append(" ");
            
            if (result.length() >= maxLength)
            {
                return result.substring(0, maxLength) + "...";
            }
        }
        
        return result.toString().trim();
    }

    private String extractDescriptionFromYamlFrontmatter(SkillNode node)
    {
        if (node.getContent() == null || node.getContent().isEmpty()) return "";
        
        String[] lines = node.getContent().split("\n");
        boolean inFrontmatter = false;
        int dashCount = 0;
        
        for (String line : lines)
        {
            String trimmed = line.trim();
            
            if (trimmed.equals("---"))
            {
                dashCount++;
                if (dashCount == 1) inFrontmatter = true;
                if (dashCount == 2) break;
                continue;
            }
            
            if (inFrontmatter && trimmed.startsWith("description:"))
            {
                String desc = trimmed.substring("description:".length()).trim();
                if (desc.startsWith("\"") && desc.endsWith("\""))
                {
                    desc = desc.substring(1, desc.length() - 1);
                }
                else if (desc.startsWith("'") && desc.endsWith("'"))
                {
                    desc = desc.substring(1, desc.length() - 1);
                }
                return desc;
            }
        }
        
        return "";
    }

    private String extractShortDescription(SkillNode skill)
    {
        if (skill.getSkillMetadata() != null && !skill.getSkillMetadata().isEmpty())
        {
            try
            {
                JSONObject meta = JSON.parseObject(skill.getSkillMetadata());
                String desc = meta.getString("description");
                if (desc != null && !desc.isEmpty())
                {
                    return truncate(desc.trim(), 80);
                }
            }
            catch (Exception ignored) {}
        }
        
        if (skill.getContent() != null && !skill.getContent().isEmpty())
        {
            String yamlDesc = extractDescriptionFromYamlFrontmatter(skill);
            if (yamlDesc != null && !yamlDesc.isEmpty())
            {
                return truncate(yamlDesc.trim(), 80);
            }
        }

        if (skill.getContent() != null && !skill.getContent().isEmpty())
        {
            String[] lines = skill.getContent().split("\n");
            boolean inFrontmatter = false;
            int dashCount = 0;
            
            for (String line : lines)
            {
                String trimmed = line.trim();
                
                if (trimmed.equals("---"))
                {
                    dashCount++;
                    if (dashCount == 1) inFrontmatter = true;
                    if (dashCount == 2) inFrontmatter = false;
                    continue;
                }
                if (inFrontmatter) continue;
                
                if (trimmed.isEmpty()) continue;
                if (trimmed.startsWith("#")) continue;
                
                return truncate(trimmed, 80);
            }
        }

        return "";
    }

    private boolean isSkillVisible(SkillNode node, String businessSystem, String userLoginName)
    {
        String scope = node.getSkillScope() != null ? node.getSkillScope() : "global";
        if ("global".equals(scope))
        {
            return true;
        }
        else if ("personal".equals(scope))
        {
            String nodeBizSys = node.getBusinessSystem();
            String nodeOwner = node.getOwnerUser();
            return businessSystem != null && businessSystem.equals(nodeBizSys) &&
                   userLoginName != null && userLoginName.equals(nodeOwner);
        }
        return true;
    }

    private String truncate(String s, int max)
    {
        if (s.length() <= max) return s;
        return s.substring(0, max - 3) + "...";
    }
}
