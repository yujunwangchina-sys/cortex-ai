package com.cortex.plugin.builtin.impl;

import com.cortex.agent.domain.AiAgentFile;
import com.cortex.agent.mapper.AiAgentFileMapper;
import com.cortex.agent.runtime.context.RuntimeContextHolder;
import com.cortex.plugin.builtin.IBuiltinPlugin;
import com.cortex.plugin.builtin.PluginInfo;
import com.cortex.plugin.builtin.ToolDefinition;
import com.cortex.plugin.builtin.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * 文件操作内置插件 — 参考 hermes file_tools.py
 * 
 * 工具（v2 - 重命名以避免与 FileManagerPlugin 冲突）：
 * 1. create_file           - 创建AI生成的文件并保存到会话目录
 * 2. read_generated_file   - 读取AI生成的文件内容
 * 3. list_generated_files  - 列出当前会话AI生成的所有文件
 *
 * @author cortex
 */
@Component
public class FileOperationPlugin implements IBuiltinPlugin
{
    private static final Logger log = LoggerFactory.getLogger(FileOperationPlugin.class);

    private static final int MAX_READ_CHARS = 100_000;
    
    /** Agent文件操作工作目录 - 默认为上传目录下的agent-workspace */
    @Value("${cortex.profile:D:/cortex/uploadPath}")
    private String uploadPath;
    
    @Autowired
    private AiAgentFileMapper fileMapper;

    /** 敏感路径拦截 */
    private static final List<String> DENIED_PATHS = Arrays.asList(
            ".env", ".pem", ".key", ".pfx", ".keystore", ".jks",
            "/etc/passwd", "/etc/shadow", "/etc/sudoers",
            "C:\\Windows\\System32", "id_rsa", "id_ecdsa"
    );

    /**
     * @deprecated 使用 RuntimeContextHolder 代替
     */
    @Deprecated
    public void setRuntimeContext(String sessionId, String agentCode, String businessSystem, String userLoginName)
    {
        // 保留方法以保持兼容性，但不再使用
        log.warn("setRuntimeContext is deprecated, use RuntimeContextHolder instead");
    }

    /**
     * @deprecated 使用 RuntimeContextHolder 代替
     */
    @Deprecated
    public void clearRuntimeContext()
    {
        // 保留方法以保持兼容性，但不再使用
    }
    
    /**
     * 获取Agent工作目录
     */
    private Path getWorkspaceRoot()
    {
        Path workspace = Paths.get(uploadPath, "agent-workspace");
        try
        {
            if (!Files.exists(workspace))
            {
                Files.createDirectories(workspace);
                log.info("创建Agent工作目录: {}", workspace.toAbsolutePath());
            }
        }
        catch (IOException e)
        {
            log.error("创建Agent工作目录失败", e);
        }
        return workspace;
    }
    
    /**
     * 获取用户的会话目录
     * 结构: agent-workspace/{businessSystem}/{userLoginName}/{sessionId}/
     */
    private Path getSessionDirectory()
    {
        String businessSystem = RuntimeContextHolder.getBusinessSystem();
        String userLoginName = RuntimeContextHolder.getUserLoginName();
        String sessionId = RuntimeContextHolder.getSessionId();
        
        if (businessSystem == null) businessSystem = "default";
        if (userLoginName == null) userLoginName = "anonymous";
        if (sessionId == null) sessionId = "temp";
        
        Path sessionDir = getWorkspaceRoot()
                .resolve(businessSystem)
                .resolve(userLoginName)
                .resolve(sessionId);
        
        try
        {
            if (!Files.exists(sessionDir))
            {
                Files.createDirectories(sessionDir);
                log.info("创建会话目录: {}", sessionDir.toAbsolutePath());
            }
        }
        catch (IOException e)
        {
            log.error("创建会话目录失败", e);
        }
        
        return sessionDir;
    }
    
    /**
     * 解析文件名为完整路径（自动放入会话目录）
     */
    private Path resolveFileName(String fileName) throws IOException
    {
        if (fileName == null || fileName.trim().isEmpty())
        {
            throw new IllegalArgumentException("文件名不能为空");
        }
        
        // 移除路径分隔符，只保留文件名
        fileName = Paths.get(fileName).getFileName().toString();
        
        Path sessionDir = getSessionDirectory();
        return sessionDir.resolve(fileName).normalize();
    }
    
    /**
     * 生成UUID文件名（保留原始文件扩展名）
     */
    private String generateUuidFileName(String originalFileName)
    {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String extension = "";
        int lastDot = originalFileName.lastIndexOf('.');
        if (lastDot > 0)
        {
            extension = originalFileName.substring(lastDot);
        }
        return uuid + extension;
    }
    
    /**
     * 从UUID文件名中提取文件扩展名
     */
    private String extractExtension(String uuidFileName)
    {
        int lastDot = uuidFileName.lastIndexOf('.');
        if (lastDot > 0)
        {
            return uuidFileName.substring(lastDot);
        }
        return "";
    }
    
    /**
     * 生成下载链接
     */
    private String generateDownloadUrl(Long fileId)
    {
        return "/agent/api/file/download/" + fileId;
    }

    @Override
    public PluginInfo getPluginInfo()
    {
        PluginInfo info = new PluginInfo("文件操作", "file-operation", "文件读写、列表、搜索");
        info.setVersion("1.0.0");
        info.setAuthor("CORTEX");
        info.setCategory("file");
        info.setEmoji("📁");
        info.setRequireApproval(false);
        return info;
    }

    @Override
    public List<ToolDefinition> getTools()
    {
        List<ToolDefinition> tools = new ArrayList<>();

        // 1. create_file - 重命名，明确是创建AI生成的文件
        ToolDefinition write = new ToolDefinition();
        write.setName("create_file");
        write.setDescription("创建并保存**AI生成**的文件到当前会话目录，并返回下载链接。" +
                "返回的download_url可以使用Markdown链接格式展示给用户，例如：[点击下载文件名](download_url)");
        Map<String, Object> writeSchema = new HashMap<>();
        writeSchema.put("type", "object");
        Map<String, Object> writeProps = new HashMap<>();
        writeProps.put("file_name", Map.of("type", "string", "description", "文件名（不需要路径）"));
        writeProps.put("content", Map.of("type", "string", "description", "文件内容"));
        writeSchema.put("properties", writeProps);
        writeSchema.put("required", List.of("file_name", "content"));
        write.setInputSchema(writeSchema);
        tools.add(write);

        // 2. read_generated_file - 重命名，明确读取AI生成的文件
        ToolDefinition read = new ToolDefinition();
        read.setName("read_generated_file");
        read.setDescription("读取**AI生成**的文件内容。文件名会自动在当前会话目录中查找。");
        Map<String, Object> readSchema = new HashMap<>();
        readSchema.put("type", "object");
        Map<String, Object> readProps = new HashMap<>();
        readProps.put("file_name", Map.of("type", "string", "description", "文件名（不需要路径）"));
        readSchema.put("properties", readProps);
        readSchema.put("required", List.of("file_name"));
        read.setInputSchema(readSchema);
        tools.add(read);

        // 3. list_generated_files - 重命名，明确列出AI生成的文件
        ToolDefinition list = new ToolDefinition();
        list.setName("list_generated_files");
        list.setDescription("列出当前会话目录中**AI生成**的所有文件。");
        Map<String, Object> listSchema = new HashMap<>();
        listSchema.put("type", "object");
        listSchema.put("properties", new HashMap<>());
        list.setInputSchema(listSchema);
        tools.add(list);

        return tools;
    }

    @Override
    public String executeTool(String toolName, Map<String, Object> arguments)
    {
        try
        {
            switch (toolName)
            {
                // 新工具名称
                case "create_file": return writeFile(arguments);
                case "read_generated_file": return readFile(arguments);
                case "list_generated_files": return listFiles(arguments);
                
                // 向后兼容 - 保留旧工具名称
                case "write_file": return writeFile(arguments);
                case "read_file": return readFile(arguments);
                case "list_files": return listFiles(arguments);
                
                default: return ToolResult.error("未知工具: " + toolName).toJson();
            }
        }
        catch (Exception e)
        {
            log.error("文件操作失败: " + toolName, e);
            return ToolResult.error("执行失败: " + e.getMessage()).toJson();
        }
    }

    private String readFile(Map<String, Object> args) throws IOException
    {
        String fileName = (String) args.get("file_name");

        // 从数据库查找文件记录（根据文件名和会话ID）
        String sessionId = RuntimeContextHolder.getSessionId();
        AiAgentFile fileRecord = fileMapper.selectFileByOriginalNameAndSession(fileName, sessionId);
        
        if (fileRecord == null)
        {
            return ToolResult.error("文件不存在: " + fileName).toJson();
        }
        
        // 将相对路径转换为绝对路径
        Path filePath = Paths.get(uploadPath, fileRecord.getFilePath());
        
        if (!Files.exists(filePath))
        {
            return ToolResult.error("文件不存在: " + fileName).toJson();
        }
        if (Files.isDirectory(filePath))
        {
            return ToolResult.error("这是一个目录，不是文件: " + fileName).toJson();
        }

        String content = Files.readString(filePath, StandardCharsets.UTF_8);
        if (content.length() > MAX_READ_CHARS)
        {
            content = content.substring(0, MAX_READ_CHARS) + "\n... [内容过长，已截断]";
        }

        return ToolResult.success("读取成功")
                .addData("file_name", fileName)
                .addData("size", Files.size(filePath))
                .addData("content", content)
                .toJson();
    }

    private String writeFile(Map<String, Object> args) throws IOException
    {
        String originalFileName = (String) args.get("file_name");
        String content = (String) args.getOrDefault("content", "");

        if (isDeniedFileName(originalFileName))
        {
            return ToolResult.error("文件名被禁止(敏感文件): " + originalFileName).toJson();
        }

        // 生成UUID文件名用于实际存储
        String uuidFileName = generateUuidFileName(originalFileName);
        Path sessionDir = getSessionDirectory();
        Path filePath = sessionDir.resolve(uuidFileName);
        
        // 写入文件
        Files.writeString(filePath, content, StandardCharsets.UTF_8);
        
        // 计算相对路径（相对于uploadPath）
        Path uploadPathRoot = Paths.get(uploadPath);
        String relativePath = uploadPathRoot.relativize(filePath).toString().replace("\\", "/");
        
        // 记录到数据库，file_name字段保存原始文件名
        AiAgentFile fileRecord = new AiAgentFile();
        fileRecord.setFileName(originalFileName);  // 保存原始文件名
        fileRecord.setFilePath(relativePath);  // 保存相对路径
        fileRecord.setFileSize((long) content.length());
        fileRecord.setFileType("generated");  // Agent生成的文件
        fileRecord.setSessionId(RuntimeContextHolder.getSessionId());
        fileRecord.setAgentCode(RuntimeContextHolder.getAgentCode());
        fileRecord.setBusinessSystem(RuntimeContextHolder.getBusinessSystem() != null ? RuntimeContextHolder.getBusinessSystem() : "default");
        fileRecord.setUserLoginName(RuntimeContextHolder.getUserLoginName() != null ? RuntimeContextHolder.getUserLoginName() : "anonymous");
        fileRecord.setRemark("Agent生成");
        
        fileMapper.insertAiAgentFile(fileRecord);
        
        // 生成下载链接
        String downloadUrl = generateDownloadUrl(fileRecord.getFileId());
        
        log.info("文件生成成功 - 原始文件名: {}, UUID文件名: {}, 相对路径: {}, 下载链接: {}", 
                originalFileName, uuidFileName, relativePath, downloadUrl);

        return ToolResult.success("文件已成功生成并保存。请使用以下下载链接：" + downloadUrl)
                .addData("file_name", originalFileName)
                .addData("file_id", fileRecord.getFileId())
                .addData("download_url", downloadUrl)
                .addData("download_link_markdown", "[点击下载 " + originalFileName + "](" + downloadUrl + ")")
                .addData("size", content.length())
                .toJson();
    }

    private String listFiles(Map<String, Object> args) throws IOException
    {
        String sessionId = RuntimeContextHolder.getSessionId();
        
        if (sessionId == null)
        {
            return ToolResult.success("会话目录为空")
                    .addData("files", new ArrayList<>())
                    .toJson();
        }
        
        // 从数据库查询该会话的所有文件
        List<Map<String, Object>> files = new ArrayList<>();
        List<AiAgentFile> dbFiles = fileMapper.selectFilesBySessionId(sessionId);
        
        for (AiAgentFile dbFile : dbFiles)
        {
            Map<String, Object> file = new HashMap<>();
            file.put("file_name", dbFile.getFileName());  // 显示原始文件名
            file.put("file_id", dbFile.getFileId());
            file.put("size", dbFile.getFileSize());
            file.put("created", dbFile.getCreateTime());
            file.put("file_type", dbFile.getFileType());
            files.add(file);
        }

        return ToolResult.success("列出成功")
                .addData("session_id", sessionId)
                .addData("file_count", files.size())
                .addData("files", files)
                .toJson();
    }

    private boolean isDeniedFileName(String fileName)
    {
        String lower = fileName.toLowerCase();
        for (String denied : DENIED_PATHS)
        {
            if (lower.contains(denied.toLowerCase()))
            {
                return true;
            }
        }
        return false;
    }
}
