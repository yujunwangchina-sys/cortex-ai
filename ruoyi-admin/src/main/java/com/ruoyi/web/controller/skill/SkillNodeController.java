package com.ruoyi.web.controller.skill;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.skill.domain.SkillNode;
import com.ruoyi.skill.service.ISkillNodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 技能管理Controller
 * 
 * @author ruoyi
 * @date 2024-06-30
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
        return success(tree);
    }

    /**
     * 获取技能包列表（仅第一层）
     */
    @PreAuthorize("@ss.hasPermi('skill:node:list')")
    @GetMapping("/packages")
    public AjaxResult getPackages() {
        List<SkillNode> packages = skillNodeService.getSkillPackages();
        return success(packages);
    }

    /**
     * 获取 Agent 可用的技能包列表（支持两层权限）
     * 
     * @param businessSystem 业务系统标识
     * @param ownerUser 用户名
     */
    @PreAuthorize("@ss.hasPermi('skill:node:list')")
    @GetMapping("/packages/available")
    public AjaxResult getAvailablePackages(
            @RequestParam(required = false) String businessSystem,
            @RequestParam(required = false) String ownerUser) {
        List<SkillNode> packages = skillNodeService.getAvailableSkillPackages(businessSystem, ownerUser);
        return success(packages);
    }

    /**
     * 创建文件夹
     */
    /**
     * 创建文件夹
     */
    @PreAuthorize("@ss.hasPermi('skill:node:add')")
    @Log(title = "技能节点", businessType = BusinessType.INSERT)
    @PostMapping("/folder")
    public AjaxResult createFolder(@RequestBody SkillNode node) {
        int rows = skillNodeService.createFolder(node);
        if (rows > 0) {
            // 返回创建的节点对象（包含 ID）
            return success(node);
        }
        return error("创建失败");
    }

    /**
     * 创建文件
     */
    @PreAuthorize("@ss.hasPermi('skill:node:add')")
    @Log(title = "技能节点", businessType = BusinessType.INSERT)
    @PostMapping("/file")
    public AjaxResult createFile(@RequestBody SkillNode node) {
        try {
            return toAjax(skillNodeService.createFile(node));
        } catch (RuntimeException e) {
            return error(e.getMessage());
        }
    }

    /**
     * 删除节点
     */
    @PreAuthorize("@ss.hasPermi('skill:node:remove')")
    @Log(title = "技能节点", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult deleteNode(@PathVariable Long id) {
        return toAjax(skillNodeService.deleteNode(id));
    }

    /**
     * 重命名节点
     */
    @PreAuthorize("@ss.hasPermi('skill:node:edit')")
    @Log(title = "技能节点", businessType = BusinessType.UPDATE)
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
    @Log(title = "技能节点", businessType = BusinessType.UPDATE)
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
        Map<String, Object> data = new HashMap<>();
        data.put("content", content);
        return success(data);
    }

    /**
     * 保存文件内容
     */
    @PreAuthorize("@ss.hasPermi('skill:node:edit')")
    @Log(title = "技能节点", businessType = BusinessType.UPDATE)
    @PostMapping("/content")
    public AjaxResult saveContent(@RequestBody Map<String, Object> params) {
        Long id = Long.parseLong(params.get("id").toString());
        String content = params.get("content") != null ? params.get("content").toString() : "";
        try {
            return toAjax(skillNodeService.saveFileContent(id, content));
        } catch (RuntimeException e) {
            return error(e.getMessage());
        }
    }

    /**
     * 获取所有文件列表
     */
    @PreAuthorize("@ss.hasPermi('skill:node:list')")
    @GetMapping("/files")
    public AjaxResult getAllFiles() {
        List<SkillNode> files = skillNodeService.getAllFiles();
        return success(files);
    }

    /**
     * 上传技能包压缩包
     * 
     * @param file 压缩包文件
     * @param skillScope 技能范围：global(全局) / personal(个人)
     * @param businessSystem 业务系统标识（个人技能必填）
     * @param ownerUser 所有者（个人技能必填）
     */
    @PreAuthorize("@ss.hasPermi('skill:node:add')")
    @Log(title = "技能包上传", businessType = BusinessType.IMPORT)
    @PostMapping("/upload")
    public AjaxResult uploadSkillPackage(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(required = false, defaultValue = "personal") String skillScope,
            @RequestParam(required = false) String businessSystem,
            @RequestParam(required = false) String ownerUser) {
        
        logger.info("=== 开始处理技能包上传 ===");
        logger.info("skillScope: {}", skillScope);
        logger.info("businessSystem: {}", businessSystem);
        logger.info("ownerUser: {}", ownerUser);
        logger.info("fileName: {}", file.getOriginalFilename());
        logger.info("fileSize: {}", file.getSize());
        
        if (file.isEmpty()) {
            logger.error("上传文件为空");
            return error("上传文件不能为空");
        }
        
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".zip")) {
            logger.error("文件格式错误: {}", originalFilename);
            return error("只支持 .zip 格式的压缩包");
        }
        
        try {
            // 如果是个人级技能，默认使用当前登录用户
            if ("personal".equals(skillScope) && (ownerUser == null || ownerUser.isEmpty())) {
                ownerUser = getUsername();
                logger.info("使用当前登录用户: {}", ownerUser);
            }
            
            logger.info("开始调用uploadSkillPackage服务");
            String packageName = skillNodeService.uploadSkillPackage(file, skillScope, businessSystem, ownerUser);
            logger.info("技能包上传成功: {}", packageName);
            
            // 提示：不支持的文件类型会被自动跳过
            String message = "技能包 \"" + packageName + "\" 上传成功";
            String tip = "（注意：只支持 .md, .py, .js, .txt, .json 文件，其他类型文件已自动跳过）";
            
            return success(message + tip);
        } catch (Exception e) {
            logger.error("技能包上传失败", e);
            return error("上传失败：" + e.getMessage());
        }
    }

    /**
     * 上传单个技能到指定技能包下
     * 
     * @param file 技能ZIP压缩包文件
     * @param parentId 父节点ID（技能包ID）
     */
    @PreAuthorize("@ss.hasPermi('skill:node:add')")
    @Log(title = "技能上传", businessType = BusinessType.IMPORT)
    @PostMapping("/upload-skill")
    public AjaxResult uploadSingleSkill(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam("parentId") Long parentId) {
        
        logger.info("=== 开始处理单个技能上传 ===");
        logger.info("parentId: {}", parentId);
        logger.info("fileName: {}", file.getOriginalFilename());
        logger.info("fileSize: {}", file.getSize());
        
        if (file.isEmpty()) {
            logger.error("上传文件为空");
            return error("上传文件不能为空");
        }
        
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".zip")) {
            logger.error("文件格式错误: {}", originalFilename);
            return error("只支持 .zip 格式的压缩包");
        }
        
        try {
            logger.info("开始调用uploadSingleSkill服务");
            String skillName = skillNodeService.uploadSingleSkill(file, parentId);
            logger.info("技能上传成功: {}", skillName);
            
            // 提示：不支持的文件类型会被自动跳过
            String message = "技能 \"" + skillName + "\" 上传成功";
            String tip = "（注意：只支持 .md, .py, .js, .txt, .json 文件，其他类型文件已自动跳过）";
            
            return success(message + tip);
        } catch (Exception e) {
            logger.error("技能上传失败", e);
            return error("上传失败：" + e.getMessage());
        }
    }
}
