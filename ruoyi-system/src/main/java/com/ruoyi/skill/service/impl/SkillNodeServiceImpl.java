package com.ruoyi.skill.service.impl;

import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.skill.domain.SkillNode;
import com.ruoyi.skill.util.SkillMetadataParser;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.skill.mapper.SkillNodeMapper;
import com.ruoyi.skill.service.ISkillNodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 技能节点Service业务层处理
 * 
 * @author ruoyi
 * @date 2024-06-30
 */
@Service
public class SkillNodeServiceImpl implements ISkillNodeService {

    private static final Logger log = LoggerFactory.getLogger(SkillNodeServiceImpl.class);
    
    // 允许的文件扩展名
    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>(Arrays.asList(
        ".md", ".py", ".js", ".txt", ".json"
    ));

    @Autowired
    private SkillNodeMapper skillNodeMapper;

    /**
     * 获取技能树
     * 
     * @return 技能树
     */
    @Override
    public List<SkillNode> getSkillTree() {
        List<SkillNode> allNodes = skillNodeMapper.selectAllNodes();
        return buildTree(allNodes, null);
    }

    /**
     * 获取技能包列表（只返回第一层）
     * 
     * @return 技能包列表
     */
    @Override
    public List<SkillNode> getSkillPackages() {
        List<SkillNode> allNodes = skillNodeMapper.selectAllNodes();
        // 只返回第一层（技能包），并统计子节点数量
        List<SkillNode> packages = new ArrayList<>();
        for (SkillNode node : allNodes) {
            if (node.getParentId() == null) {
                // 统计子节点数量
                long childCount = allNodes.stream()
                    .filter(n -> node.getId().equals(n.getParentId()))
                    .count();
                node.setChildCount(childCount);
                packages.add(node);
            }
        }
        return packages;
    }

    /**
     * 获取 Agent 可用的技能包列表
     * 两层权限：全局(所有Agent可用) + 个人(按业务系统+用户隔离，不可手动分配)
     * 
     * @param businessSystem 业务系统标识（用于过滤个人技能）
     * @param ownerUser 用户名（用于过滤个人技能）
     * @return 技能包列表（仅返回全局技能包用于Agent分配）
     */
    @Override
    public List<SkillNode> getAvailableSkillPackages(String businessSystem, String ownerUser) {
        List<SkillNode> allNodes = skillNodeMapper.selectAllNodes();
        List<SkillNode> packages = new ArrayList<>();
        
        for (SkillNode node : allNodes) {
            // 只处理第一层（技能包）
            if (node.getParentId() == null) {
                boolean isAvailable = false;
                
                // 只返回全局技能包（用于Agent配置时手动分配）
                // 个人技能由自学习功能自动生成，不出现在可分配列表中
                if ("global".equals(node.getSkillScope())) {
                    isAvailable = true;
                }
                
                if (isAvailable) {
                    // 统计子节点数量
                    long childCount = allNodes.stream()
                        .filter(n -> node.getId().equals(n.getParentId()))
                        .count();
                    node.setChildCount(childCount);
                    packages.add(node);
                }
            }
        }
        
        return packages;
    }

    /**
     * 构建树形结构
     * 
     * @param allNodes 所有节点
     * @param parentId 父节点ID
     * @return 树形结构
     */
    private List<SkillNode> buildTree(List<SkillNode> allNodes, Long parentId) {
        List<SkillNode> tree = new ArrayList<>();
        for (SkillNode node : allNodes) {
            if ((parentId == null && node.getParentId() == null) ||
                (parentId != null && parentId.equals(node.getParentId()))) {
                List<SkillNode> children = buildTree(allNodes, node.getId());
                if (!children.isEmpty()) {
                    node.setChildren(children);
                }
                tree.add(node);
            }
        }
        return tree;
    }

    /**
     * 创建文件夹
     * 
     * @param node 节点信息
     * @return 结果
     */
    @Override
    @Transactional
    public int createFolder(SkillNode node) {
        node.setIsDirectory(true);
        node.setContent(null);
        node.setFileSize(0L);
        if (node.getSortOrder() == null) {
            node.setSortOrder(0);
        }
        generatePath(node);
        node.setCreateBy(SecurityUtils.getUsername());
        node.setCreateTime(new Date());
        return skillNodeMapper.insertNode(node);
    }

    /**
     * 创建文件
     * 
     * @param node 节点信息
     * @return 结果
     */
    @Override
    @Transactional
    public int createFile(SkillNode node) {
        node.setIsDirectory(false);
        node.setNodeType("file");

        // Set file extension and mimeType for all file types
        String fileName = node.getName();
        if (fileName != null && fileName.contains(".")) {
            int dotIdx = fileName.lastIndexOf(".");
            String ext = fileName.substring(dotIdx).toLowerCase();
            node.setFileExtension(ext);
            node.setMimeType(getMimeType(ext));
        }

        // Validate MD frontmatter for .md files (required: name, description, version)
        if (fileName != null && fileName.toLowerCase().endsWith(".md")) {
            String validationError = SkillMetadataParser.validateRequiredFields(node.getContent());
            if (validationError != null) {
                throw new RuntimeException("创建失败: " + fileName + " " + validationError);
            }
            // Parse frontmatter into skill_metadata
            JSONObject meta = SkillMetadataParser.parseFrontmatter(node.getContent());
            if (!meta.isEmpty()) {
                node.setSkillMetadata(meta.toJSONString());
            }
        }
        if (node.getContent() == null) {
            node.setContent("");
        }
        node.setFileSize((long) node.getContent().getBytes().length);
        if (node.getSortOrder() == null) {
            node.setSortOrder(0);
        }
        generatePath(node);
        node.setCreateBy(SecurityUtils.getUsername());
        node.setCreateTime(new Date());
        return skillNodeMapper.insertNode(node);
    }

    /**
     * 生成节点路径
     * 
     * @param node 节点信息
     */
    private void generatePath(SkillNode node) {
        if (node.getParentId() == null) {
            node.setPath("/" + node.getName());
        } else {
            SkillNode parent = skillNodeMapper.selectNodeById(node.getParentId());
            if (parent != null) {
                node.setPath(parent.getPath() + "/" + node.getName());
            } else {
                node.setPath("/" + node.getName());
            }
        }
    }

    /**
     * 删除节点（递归删除子节点）
     * 
     * @param id 节点ID
     * @return 结果
     */
    @Override
    @Transactional
    public int deleteNode(Long id) {
        // 递归删除子节点
        List<SkillNode> children = skillNodeMapper.selectChildrenByParentId(id);
        for (SkillNode child : children) {
            deleteNode(child.getId());
        }
        // 删除当前节点
        return skillNodeMapper.deleteNodeById(id);
    }

    /**
     * 重命名节点
     * 
     * @param id 节点ID
     * @param newName 新名称
     * @return 结果
     */
    @Override
    @Transactional
    public int renameNode(Long id, String newName) {
        SkillNode node = skillNodeMapper.selectNodeById(id);
        if (node == null) {
            return 0;
        }
        node.setName(newName);
        node.setUpdateBy(SecurityUtils.getUsername());
        node.setUpdateTime(new Date());
        // 更新路径
        updateNodePath(node);
        return skillNodeMapper.updateNode(node);
    }

    /**
     * 更新节点及其子节点的路径
     * 
     * @param node 节点信息
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

    /**
     * 移动节点
     * 
     * @param id 节点ID
     * @param targetId 目标节点ID
     * @param dropType 拖拽类型(before/after/inner)
     * @return 结果
     */
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
            if (target != null) {
                node.setParentId(target.getParentId());
            }
        }

        node.setUpdateBy(SecurityUtils.getUsername());
        node.setUpdateTime(new Date());
        updateNodePath(node);
        return skillNodeMapper.updateNode(node);
    }

    /**
     * 获取文件内容
     * 
     * @param filePath 文件路径
     * @return 文件内容
     */
    @Override
    public String getFileContent(String filePath) {
        SkillNode node = skillNodeMapper.selectNodeByPath(filePath);
        return node != null ? node.getContent() : null;
    }

    /**
     * 保存文件内容
     * 
     * @param id 节点ID
     * @param content 文件内容
     * @return 结果
     */
    @Override
    @Transactional
    public int saveFileContent(Long id, String content) {
        SkillNode node = skillNodeMapper.selectNodeById(id);
        if (node == null || node.getIsDirectory()) {
            return 0;
        }
        // Validate MD frontmatter for .md files
        String fileName = node.getName();
        if (fileName != null && fileName.toLowerCase().endsWith(".md")) {
            String validationError = SkillMetadataParser.validateRequiredFields(content);
            if (validationError != null) {
                throw new RuntimeException("保存失败: " + fileName + " " + validationError);
            }
            // Re-parse frontmatter into skill_metadata
            JSONObject meta = SkillMetadataParser.parseFrontmatter(content);
            if (!meta.isEmpty()) {
                node.setSkillMetadata(meta.toJSONString());
            }
        }
        
        node.setContent(content);
        node.setFileSize((long) content.getBytes().length);
        node.setUpdateBy(SecurityUtils.getUsername());
        node.setUpdateTime(new Date());
        return skillNodeMapper.updateNode(node);
    }

    /**
     * 获取所有文件列表（用于引用）
     * 
     * @return 文件列表
     */
    @Override
    public List<SkillNode> getAllFiles() {
        List<SkillNode> allNodes = skillNodeMapper.selectAllNodes();
        return allNodes.stream()
                .filter(node -> !node.getIsDirectory())
                .collect(Collectors.toList());
    }

    /**
     * 上传技能包压缩包
     * 
     * @param file 压缩包文件
     * @param skillScope 技能范围：global(全局) / personal(个人)
     * @param businessSystem 业务系统标识（个人技能必填）
     * @param ownerUser 所有者用户名（个人技能必填）
     * @return 技能包名称
     * @throws Exception 上传失败异常
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String uploadSkillPackage(
            org.springframework.web.multipart.MultipartFile file,
            String skillScope,
            String businessSystem,
            String ownerUser) throws Exception {
        
        log.info("开始上传技能包 [fileName={}, skillScope={}, businessSystem={}, ownerUser={}]",
                file.getOriginalFilename(), skillScope, businessSystem, ownerUser);
        
        // 1. 创建临时目录解压文件
        Path tempDir = Files.createTempDirectory("skill-upload-");
        try {
            // 2. 解压zip文件
            unzipFile(file.getInputStream(), tempDir);
            
            // 3. 查找技能包根目录（第一层应该是技能包名称文件夹）
            Path skillPackageRoot = findSkillPackageRoot(tempDir);
            String packageName = skillPackageRoot.getFileName().toString();
            
            log.info("识别技能包根目录 [packageName={}, path={}]", packageName, skillPackageRoot);
            
            // 4. 校验技能包结构
            validateSkillPackageStructure(skillPackageRoot);
            
            // 5. 检查文件扩展名（收集不支持的文件，不阻止上传）
            List<String> unsupportedFiles = checkFileExtensions(skillPackageRoot);
            
            // 6. 检查同名技能包是否存在
            SkillNode existingPackage = findExistingPackage(packageName, skillScope, businessSystem, ownerUser);
            if (existingPackage != null) {
                log.info("发现同名技能包，准备覆盖 [packageName={}, existingId={}]", packageName, existingPackage.getId());
                // 删除旧的技能包
                deleteNode(existingPackage.getId());
            }
            
            // 7. 导入技能包到数据库
            importSkillPackageToDb(skillPackageRoot, null, skillScope, businessSystem, ownerUser);
            
            log.info("技能包上传成功 [packageName={}]", packageName);
            return packageName;
            
        } finally {
            // 清理临时目录
            deleteDirectory(tempDir);
        }
    }
    
    /**
     * 解压zip文件（过滤Mac系统文件）
     */
    private void unzipFile(InputStream inputStream, Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                
                // 过滤Mac系统文件和文件夹
                if (shouldSkipEntry(entryName)) {
                    log.debug("跳过Mac系统文件: {}", entryName);
                    zis.closeEntry();
                    continue;
                }
                
                Path targetPath = targetDir.resolve(entryName);
                
                // 防止zip slip攻击
                if (!targetPath.normalize().startsWith(targetDir.normalize())) {
                    throw new IOException("非法的zip条目: " + entryName);
                }
                
                if (entry.isDirectory()) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.createDirectories(targetPath.getParent());
                    Files.copy(zis, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }
    
    /**
     * 判断是否应该跳过该文件/文件夹
     * 过滤Mac系统文件：__MACOSX、.DS_Store、._开头的文件
     */
    private boolean shouldSkipEntry(String entryName) {
        // 1. 过滤 __MACOSX 文件夹
        if (entryName.contains("__MACOSX")) {
            return true;
        }
        
        // 2. 过滤 .DS_Store 文件
        if (entryName.endsWith(".DS_Store")) {
            return true;
        }
        
        // 3. 过滤 ._ 开头的文件（Mac资源分支文件）
        String fileName = entryName.substring(entryName.lastIndexOf('/') + 1);
        if (fileName.startsWith("._")) {
            return true;
        }
        
        // 4. 过滤 Thumbs.db (Windows)
        if (entryName.endsWith("Thumbs.db")) {
            return true;
        }
        
        // 5. 过滤 .git 文件夹
        if (entryName.contains("/.git/") || entryName.startsWith(".git/")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 查找技能包根目录（第一层应该是技能包名称文件夹）
     * 过滤Mac和Windows系统文件
     */
    private Path findSkillPackageRoot(Path tempDir) throws Exception {
        List<Path> topLevelDirs = Files.list(tempDir)
                .filter(Files::isDirectory)
                .filter(path -> {
                    String dirName = path.getFileName().toString();
                    // 过滤系统文件夹
                    return !dirName.equals("__MACOSX") && 
                           !dirName.startsWith(".") &&
                           !dirName.equals("$RECYCLE.BIN");
                })
                .collect(Collectors.toList());
        
        if (topLevelDirs.isEmpty()) {
            throw new Exception("压缩包结构错误：第一层必须是技能包名称文件夹");
        }
        
        if (topLevelDirs.size() > 1) {
            // 列出所有文件夹名称，方便调试
            String dirNames = topLevelDirs.stream()
                    .map(p -> p.getFileName().toString())
                    .collect(Collectors.joining(", "));
            throw new Exception("压缩包结构错误：第一层只能有一个技能包文件夹，但发现了多个: " + dirNames);
        }
        
        return topLevelDirs.get(0);
    }
    
    /**
     * 校验技能包结构
     */
    private void validateSkillPackageStructure(Path packageRoot) throws Exception {
        // 1. 检查 DESCRIPTION.md 是否存在（不区分大小写）
        Path descriptionFile = findFileIgnoreCase(packageRoot, "DESCRIPTION.md");
        if (descriptionFile == null) {
            throw new Exception("技能包根目录缺少 DESCRIPTION.md 文件（不区分大小写：description.md、Description.md 等也可以）");
        }
        
        // 2. 扫描子目录，检查每个技能是否有 SKILL.md
        List<Path> skillDirs = Files.list(packageRoot)
                .filter(Files::isDirectory)
                .collect(Collectors.toList());
        
        for (Path skillDir : skillDirs) {
            Path skillMd = findFileIgnoreCase(skillDir, "SKILL.md");
            if (skillMd == null) {
                throw new Exception("技能目录 \"" + skillDir.getFileName() + "\" 缺少 SKILL.md 文件（不区分大小写：skill.md、Skill.md 等也可以）");
            }
        }
        
        
        // 3. Validate SKILL.md frontmatter (required: name, description, version)
        for (Path skillDir : skillDirs) {
            Path skillMd = findFileIgnoreCase(skillDir, "SKILL.md");
            String mdContent = new String(Files.readAllBytes(skillMd), "UTF-8");
            String validationError = SkillMetadataParser.validateRequiredFields(mdContent);
            if (validationError != null) {
                throw new Exception("技能 \"" + skillDir.getFileName() + "\" 的 SKILL.md " + validationError);
            }
        }
        
        // 4. Validate DESCRIPTION.md frontmatter if present
        Path descFile = findFileIgnoreCase(packageRoot, "DESCRIPTION.md");
        if (descFile != null) {
            String descContent = new String(Files.readAllBytes(descFile), "UTF-8");
            String descError = SkillMetadataParser.validateRequiredFields(descContent);
            if (descError != null) {
                throw new Exception("DESCRIPTION.md " + descError);
            }
        }
        log.info("技能包结构校验通过 [skillCount={}]", skillDirs.size());
    }
    
    /**
     * 查找文件（不区分大小写）
     * 
     * @param dir 目录路径
     * @param fileName 文件名（不区分大小写）
     * @return 找到的文件路径，未找到返回null
     */
    private Path findFileIgnoreCase(Path dir, String fileName) throws IOException {
        String lowerFileName = fileName.toLowerCase();
        List<Path> files = Files.list(dir)
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().toLowerCase().equals(lowerFileName))
                .collect(Collectors.toList());
        
        return files.isEmpty() ? null : files.get(0);
    }
    
    /**
     * 查找文件（不区分大小写，安全版本，捕获异常）
     * 
     * @param dir 目录路径
     * @param fileName 文件名（不区分大小写）
     * @return 找到的文件路径，未找到或异常返回null
     */
    private Path findFileIgnoreCaseSafe(Path dir, String fileName) {
        try {
            return findFileIgnoreCase(dir, fileName);
        } catch (IOException e) {
            log.warn("查找文件失败: {} in {}", fileName, dir, e);
            return null;
        }
    }
    
    /**
     * 检查文件扩展名并返回不支持的文件列表（不抛异常，只记录警告）
     * 支持的扩展名：.md, .py, .js, .txt, .json
     * 
     * @return 不支持的文件列表（用于提示用户）
     */
    private List<String> checkFileExtensions(Path packageRoot) throws IOException {
        List<String> unsupportedFiles = new ArrayList<>();
        
        Files.walk(packageRoot)
                .filter(Files::isRegularFile)
                .forEach(file -> {
                    String fileName = file.getFileName().toString();
                    String extension = getFileExtension(fileName);
                    if (!ALLOWED_EXTENSIONS.contains(extension)) {
                        String relativePath = packageRoot.relativize(file).toString();
                        unsupportedFiles.add(relativePath);
                        log.warn("跳过不支持的文件: {}", relativePath);
                    }
                });
        
        if (!unsupportedFiles.isEmpty()) {
            log.info("发现 {} 个不支持的文件，将被跳过", unsupportedFiles.size());
        } else {
            log.info("所有文件扩展名校验通过");
        }
        
        return unsupportedFiles;
    }
    
    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return (lastDot > 0) ? fileName.substring(lastDot).toLowerCase() : "";
    }
    
    /**
     * 查找已存在的同名技能包
     */
    private SkillNode findExistingPackage(String packageName, String skillScope, 
                                          String businessSystem, String ownerUser) {
        List<SkillNode> allNodes = skillNodeMapper.selectAllNodes();
        
        for (SkillNode node : allNodes) {
            if (node.getParentId() == null && packageName.equals(node.getName())) {
                // 检查权限是否匹配
                boolean scopeMatch = skillScope.equals(node.getSkillScope());
                
                // 全局技能：只需匹配scope
                if ("global".equals(skillScope)) {
                    if (scopeMatch) {
                        return node;
                    }
                }
                // 个人技能：需匹配scope + businessSystem + ownerUser
                else if ("personal".equals(skillScope)) {
                    boolean businessMatch = (businessSystem != null && businessSystem.equals(node.getBusinessSystem()));
                    boolean ownerMatch = (ownerUser != null && ownerUser.equals(node.getOwnerUser()));
                    if (scopeMatch && businessMatch && ownerMatch) {
                        return node;
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * 递归导入技能包到数据库
     */
    private void importSkillPackageToDb(Path currentPath, Long parentId, 
                                        String skillScope, String businessSystem, String ownerUser) throws IOException {
        String nodeName = currentPath.getFileName().toString();
        
        if (Files.isDirectory(currentPath)) {
            // 创建文件夹节点
            SkillNode folderNode = new SkillNode();
            folderNode.setName(nodeName);
            folderNode.setParentId(parentId);
            folderNode.setIsDirectory(true);
            folderNode.setSortOrder(0);
            
            // 设置权限信息（只在根节点设置）
            if (parentId == null) {
                folderNode.setNodeType("skill_package");
                folderNode.setSkillScope(skillScope);
                
                // ✅ 修复：全局技能不设置businessSystem和ownerUser
                if ("global".equals(skillScope)) {
                    folderNode.setBusinessSystem(null);
                    folderNode.setOwnerUser(null);
                } else if ("personal".equals(skillScope)) {
                    folderNode.setBusinessSystem(businessSystem);
                    folderNode.setOwnerUser(ownerUser);
                }
            } else {
                // 子目录判断是否是技能目录（不区分大小写）
                Path skillMd = findFileIgnoreCaseSafe(currentPath, "SKILL.md");
                if (skillMd != null) {
                    folderNode.setNodeType("skill");
                } else {
                    folderNode.setNodeType("folder");
                }
            }
            
            
            // Read DESCRIPTION.md and store in skill_metadata
            Path descFile = findFileIgnoreCaseSafe(currentPath, "DESCRIPTION.md");
            if (descFile != null) {
                try {
                    String descContent = new String(Files.readAllBytes(descFile), "UTF-8");
                    JSONObject pkgMeta = SkillMetadataParser.parseFrontmatter(descContent);
                    if (!pkgMeta.isEmpty()) {
                        pkgMeta.put("packageDescription", pkgMeta.getString("description"));
                        folderNode.setSkillMetadata(pkgMeta.toJSONString());
                    }
                } catch (IOException e) {
                    log.warn("读取DESCRIPTION.md失败: {}", descFile, e);
                }
            }
            folderNode.setCreateBy(SecurityUtils.getUsername());
            folderNode.setCreateTime(new Date());
            generatePath(folderNode);
            
            skillNodeMapper.insertNode(folderNode);
            Long folderId = folderNode.getId();
            
            log.info("创建文件夹节点 [name={}, id={}, nodeType={}]", nodeName, folderId, folderNode.getNodeType());
            
            // 递归处理子文件和文件夹
            List<Path> children = Files.list(currentPath)
                    .sorted()
                    .collect(Collectors.toList());
            
            for (Path child : children) {
                importSkillPackageToDb(child, folderId, skillScope, businessSystem, ownerUser);
            }
            
        } else {
            // 创建文件节点 - 只导入支持的文件类型
            String extension = getFileExtension(nodeName);
            
            // 检查是否是支持的文件类型
            if (!ALLOWED_EXTENSIONS.contains(extension)) {
                log.warn("跳过不支持的文件类型: {} [extension={}]", nodeName, extension);
                return; // 跳过此文件，不导入
            }
            
            // 规范化文件名：自动将 skill.md 和 description.md 转换为大写
            String normalizedName = normalizeFileName(nodeName);
            if (!normalizedName.equals(nodeName)) {
                log.info("文件名已规范化: {} -> {}", nodeName, normalizedName);
            }
            
            String content = new String(Files.readAllBytes(currentPath), "UTF-8");
            
            SkillNode fileNode = new SkillNode();
            fileNode.setName(normalizedName);  // 使用规范化后的文件名
            fileNode.setParentId(parentId);
            fileNode.setIsDirectory(false);
            fileNode.setContent(content);
            fileNode.setFileSize((long) content.getBytes().length);
            fileNode.setFileExtension(extension);
            fileNode.setMimeType(getMimeType(extension));
            fileNode.setNodeType("file");

            // Parse MD frontmatter into skill_metadata
            if (".md".equals(extension)) {
                JSONObject meta = SkillMetadataParser.parseFrontmatter(content);
                if (!meta.isEmpty()) {
                    fileNode.setSkillMetadata(meta.toJSONString());
                }
            }
            fileNode.setSortOrder(0);
            fileNode.setCreateBy(SecurityUtils.getUsername());
            fileNode.setCreateTime(new Date());
            generatePath(fileNode);
            
            skillNodeMapper.insertNode(fileNode);
            
            log.info("创建文件节点 [name={}, id={}, size={}]", normalizedName, fileNode.getId(), fileNode.getFileSize());
        }
    }
    
    /**
     * 获取MIME类型
     */
    private String getMimeType(String extension) {
        if (extension == null) return "application/octet-stream";
        switch (extension.toLowerCase()) {
            case ".md": return "text/markdown";
            case ".py": return "text/x-python";
            case ".js": return "text/javascript";
            case ".json": return "application/json";
            case ".txt": return "text/plain";
            default: return "application/octet-stream";
        }
    }
    
    /**
     * 规范化文件名：将特殊文件名转换为大写
     * - skill.md -> SKILL.md
     * - Skill.md -> SKILL.md
     * - description.md -> DESCRIPTION.md
     * - Description.md -> DESCRIPTION.md
     * - 其他文件名保持不变
     * 
     * @param fileName 原始文件名
     * @return 规范化后的文件名
     */
    private String normalizeFileName(String fileName) {
        if (fileName == null) {
            return fileName;
        }
        
        // 转换为小写进行比较（不区分大小写）
        String lowerName = fileName.toLowerCase();
        
        // SKILL.md 的各种变体都转换为 SKILL.md
        if (lowerName.equals("skill.md")) {
            return "SKILL.md";
        }
        
        // DESCRIPTION.md 的各种变体都转换为 DESCRIPTION.md
        if (lowerName.equals("description.md")) {
            return "DESCRIPTION.md";
        }
        
        // README.md 也统一转换为大写（可选，根据需求）
        if (lowerName.equals("readme.md")) {
            return "README.md";
        }
        
        // 其他文件名保持原样
        return fileName;
    }
    
    /**
     * 上传单个技能到指定技能包下
     * 
     * @param file 技能ZIP压缩包文件
     * @param parentId 父节点ID（技能包ID）
     * @return 技能名称
     * @throws Exception 上传失败异常
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String uploadSingleSkill(
            org.springframework.web.multipart.MultipartFile file,
            Long parentId) throws Exception {
        
        log.info("开始上传单个技能 [fileName={}, parentId={}]", file.getOriginalFilename(), parentId);
        
        // 1. 验证父节点存在
        SkillNode parentNode = skillNodeMapper.selectNodeById(parentId);
        if (parentNode == null) {
            throw new Exception("父节点不存在");
        }
        if (!"skill_package".equals(parentNode.getNodeType())) {
            throw new Exception("只能上传到技能包节点下");
        }
        
        // 2. 创建临时目录解压文件
        Path tempDir = Files.createTempDirectory("skill-upload-");
        try {
            // 3. 解压zip文件
            unzipFile(file.getInputStream(), tempDir);
            
            // 4. 查找技能根目录（第一层应该是技能名称文件夹）
            List<Path> topLevelDirs = Files.list(tempDir)
                    .filter(Files::isDirectory)
                    .filter(path -> {
                        String dirName = path.getFileName().toString();
                        return !dirName.equals("__MACOSX") && 
                               !dirName.startsWith(".") &&
                               !dirName.equals("$RECYCLE.BIN");
                    })
                    .collect(Collectors.toList());
            
            if (topLevelDirs.isEmpty()) {
                throw new Exception("压缩包结构错误：第一层必须是技能名称文件夹");
            }
            
            if (topLevelDirs.size() > 1) {
                String dirNames = topLevelDirs.stream()
                        .map(p -> p.getFileName().toString())
                        .collect(Collectors.joining(", "));
                throw new Exception("压缩包结构错误：第一层只能有一个技能文件夹，但发现了多个: " + dirNames);
            }
            
            Path skillRoot = topLevelDirs.get(0);
            String skillName = skillRoot.getFileName().toString();
            
            log.info("识别技能根目录 [skillName={}, path={}]", skillName, skillRoot);
            
            // 5. 校验技能结构（必须有SKILL.md，不区分大小写）
            Path skillMd = findFileIgnoreCase(skillRoot, "SKILL.md");
            if (skillMd == null) {
                throw new Exception("技能目录缺少 SKILL.md 文件（不区分大小写：skill.md、Skill.md 等也可以）");
            }
            
            // 6. 检查文件扩展名（收集不支持的文件，不阻止上传）
            List<String> unsupportedFiles = checkFileExtensions(skillRoot);
            
            // 7. 检查同名技能是否存在
            SkillNode existingSkill = findExistingSkillInParent(skillName, parentId);
            if (existingSkill != null) {
                log.info("发现同名技能，准备覆盖 [skillName={}, existingId={}]", skillName, existingSkill.getId());
                deleteNode(existingSkill.getId());
            }
            
            // Validate SKILL.md frontmatter (required: name, description, version)
            String mdContent = new String(Files.readAllBytes(skillMd), "UTF-8");
            String validationError = SkillMetadataParser.validateRequiredFields(mdContent);
            if (validationError != null) {
                throw new Exception("SKILL.md " + validationError);
            }
            
            // 8. 导入技能到数据库（继承父节点的权限属性）
            importSkillToDb(skillRoot, parentId, parentNode);
            
            log.info("技能上传成功 [skillName={}]", skillName);
            return skillName;
            
        } finally {
            // 清理临时目录
            deleteDirectory(tempDir);
        }
    }
    
    /**
     * 查找父节点下是否存在同名技能
     */
    private SkillNode findExistingSkillInParent(String skillName, Long parentId) {
        List<SkillNode> children = skillNodeMapper.selectChildrenByParentId(parentId);
        for (SkillNode child : children) {
            if (skillName.equals(child.getName()) && "skill".equals(child.getNodeType())) {
                return child;
            }
        }
        return null;
    }
    
    /**
     * 导入单个技能到数据库（继承父节点权限）
     */
    private void importSkillToDb(Path skillRoot, Long parentId, SkillNode parentNode) throws IOException {
        String skillName = skillRoot.getFileName().toString();
        
        // 创建技能节点（继承父节点的权限设置）
        SkillNode skillNode = new SkillNode();
        skillNode.setName(skillName);
        skillNode.setParentId(parentId);
        skillNode.setIsDirectory(true);
        skillNode.setNodeType("skill");
        skillNode.setSkillScope(parentNode.getSkillScope());
        skillNode.setBusinessSystem(parentNode.getBusinessSystem());
        skillNode.setOwnerUser(parentNode.getOwnerUser());
        skillNode.setSortOrder(0);
        skillNode.setCreateBy(SecurityUtils.getUsername());
        skillNode.setCreateTime(new Date());
        generatePath(skillNode);
        
        skillNodeMapper.insertNode(skillNode);
        Long skillId = skillNode.getId();
        
        log.info("创建技能节点 [name={}, id={}]", skillName, skillId);
        
        // 递归处理技能目录下的文件和文件夹
        List<Path> children = Files.list(skillRoot)
                .sorted()
                .collect(Collectors.toList());
        
        for (Path child : children) {
            importSkillPackageToDb(child, skillId, parentNode.getSkillScope(), 
                                  parentNode.getBusinessSystem(), parentNode.getOwnerUser());
        }
    }
    
    /**
     * 递归删除目录
     */
    private void deleteDirectory(Path directory) {
        try {
            if (Files.exists(directory)) {
                Files.walk(directory)
                        .sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                log.warn("删除临时文件失败: {}", path, e);
                            }
                        });
            }
        } catch (IOException e) {
            log.warn("清理临时目录失败: {}", directory, e);
        }
    }
}
