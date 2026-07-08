package com.cortex.plugin.builtin.impl;

import com.cortex.agent.domain.AiAgentFile;
import com.cortex.agent.mapper.AiAgentFileMapper;
import com.cortex.agent.runtime.context.RuntimeContextHolder;
import com.cortex.common.utils.uuid.IdUtils;
import com.cortex.plugin.builtin.IBuiltinPlugin;
import com.cortex.plugin.builtin.PluginInfo;
import com.cortex.plugin.builtin.ToolDefinition;
import com.cortex.plugin.builtin.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 终端命令执行内置插件 — 参考 hermes terminal_tool.py
 * 默认 requireApproval=true，所有命令过 DangerousCommandDetector
 * 支持自动扫描命令执行后生成的文件并提供下载链接
 *
 * @author cortex
 */
@Component
public class TerminalPlugin implements IBuiltinPlugin
{
    private static final Logger log = LoggerFactory.getLogger(TerminalPlugin.class);

    @Autowired
    @Qualifier("agentStreamExecutor")
    private ThreadPoolTaskExecutor streamExecutor;
    
    @Autowired
    private AiAgentFileMapper fileMapper;
    
    @Value("${cortex.profile:D:/cortex/uploadPath}")
    private String uploadPath;
    
    private static final int DEFAULT_TIMEOUT = 30;
    private static final int MAX_TIMEOUT = 120;
    private static final int MAX_OUTPUT = 50000;

    @Override
    public PluginInfo getPluginInfo()
    {
        PluginInfo info = new PluginInfo("终端执行", "terminal", "执行Shell命令");
        info.setVersion("1.0.0");
        info.setAuthor("CORTEX");
        info.setCategory("terminal");
        info.setEmoji("🖥️");
        info.setRequireApproval(true);
        return info;
    }

    @Override
    public List<ToolDefinition> getTools()
    {
        List<ToolDefinition> tools = new ArrayList<>();

        ToolDefinition run = new ToolDefinition();
        run.setName("run_command");
        run.setDescription("在服务器上执行Shell命令。危险命令会被拦截。");
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new HashMap<>();
        props.put("command", Map.of("type", "string", "description", "要执行的命令"));
        props.put("timeout", Map.of("type", "integer", "description", "超时秒数(默认30,最大120)", "default", 30));
        props.put("workdir", Map.of("type", "string", "description", "工作目录(可选)"));
        schema.put("properties", props);
        schema.put("required", List.of("command"));
        run.setInputSchema(schema);
        tools.add(run);

        return tools;
    }

    @Override
    public String executeTool(String toolName, Map<String, Object> arguments)
    {
        if (!"run_command".equals(toolName))
        {
            return ToolResult.error("未知工具: " + toolName).toJson();
        }

        String command = (String) arguments.get("command");
        int timeout = arguments.containsKey("timeout") ? ((Number) arguments.get("timeout")).intValue() : DEFAULT_TIMEOUT;
        String workdir = (String) arguments.get("workdir");

        timeout = Math.min(timeout, MAX_TIMEOUT);
        if (command == null || command.trim().isEmpty())
        {
            return ToolResult.error("command参数不能为空").toJson();
        }

        // 判断操作系统
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        String[] cmd;
        if (isWindows)
        {
            cmd = new String[]{"cmd", "/c", command};
        }
        else
        {
            cmd = new String[]{"/bin/sh", "-c", command};
        }

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false);
        
        // 确定工作目录
        Path workDir;
        if (workdir != null && !workdir.isEmpty())
        {
            workDir = Paths.get(workdir);
            pb.directory(workDir.toFile());
        }
        else
        {
            workDir = Paths.get(System.getProperty("user.dir"));
        }
        
        // 扫描命令执行前的文件列表（用于检测新生成的文件）
        Set<Path> beforeFiles = listAllFiles(workDir);
        log.info("命令执行前文件数量: {} [workDir={}]", beforeFiles.size(), workDir);

        long startTime = System.currentTimeMillis();
        try
        {
            Process process = pb.start();

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            // 异步读取输出
            java.util.concurrent.Future<?> stdoutFuture = streamExecutor.submit(() -> readStream(process.getInputStream(), stdout));
            java.util.concurrent.Future<?> stderrFuture = streamExecutor.submit(() -> readStream(process.getErrorStream(), stderr));

            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
            if (!finished)
            {
                process.destroyForcibly();
                return ToolResult.error("命令执行超时(" + timeout + "s): " + command).toJson();
            }

            stdoutFuture.get(1, java.util.concurrent.TimeUnit.SECONDS);
            stderrFuture.get(1, java.util.concurrent.TimeUnit.SECONDS);

            int exitCode = process.exitValue();
            int duration = (int)(System.currentTimeMillis() - startTime);

            String out = stdout.toString();
            if (out.length() > MAX_OUTPUT) out = out.substring(0, MAX_OUTPUT) + "\n... [输出截断]";
            String err = stderr.toString();
            if (err.length() > MAX_OUTPUT) err = err.substring(0, MAX_OUTPUT) + "\n... [输出截断]";

            // 扫描命令执行后的文件列表，检测新生成的文件
            Set<Path> afterFiles = listAllFiles(workDir);
            afterFiles.removeAll(beforeFiles);  // 只保留新生成的文件
            
            List<Map<String, Object>> generatedFiles = new ArrayList<>();
            if (!afterFiles.isEmpty())
            {
                log.info("检测到 {} 个新生成的文件 [workDir={}]", afterFiles.size(), workDir);
                for (Path newFile : afterFiles)
                {
                    try
                    {
                        Long fileId = saveGeneratedFileToDb(newFile, workDir);
                        if (fileId != null)
                        {
                            String originalFileName = newFile.getFileName().toString();
                            Map<String, Object> fileInfo = new HashMap<>();
                            fileInfo.put("file_name", originalFileName);
                            fileInfo.put("file_id", fileId);
                            fileInfo.put("size", Files.size(newFile));
                            String downloadUrl = "/agent/api/file/download/" + fileId;
                            fileInfo.put("download_url", downloadUrl);
                            fileInfo.put("download_link_markdown", "[点击下载 " + originalFileName + "](" + downloadUrl + ")");
                            generatedFiles.add(fileInfo);
                            log.info("✅ 终端命令生成文件已落库: {} -> fileId={}", originalFileName, fileId);
                        }
                    }
                    catch (Exception e)
                    {
                        log.error("❌ 保存生成文件失败: " + newFile.getFileName(), e);
                    }
                }
            }

            if (exitCode == 0)
            {
                ToolResult result = ToolResult.success("执行成功")
                        .addData("exitCode", exitCode)
                        .addData("stdout", out)
                        .addData("stderr", err)
                        .addData("durationMs", duration)
                        .addData("workDir", workDir.toString());
                
                if (!generatedFiles.isEmpty())
                {
                    result.addData("generated_files", generatedFiles);
                    result.addData("generated_files_count", generatedFiles.size());
                    
                    // 构建友好的提示消息
                    StringBuilder fileListMsg = new StringBuilder("\n\n生成的文件：\n");
                    for (Map<String, Object> file : generatedFiles)
                    {
                        fileListMsg.append("- ")
                                  .append(file.get("download_link_markdown"))
                                  .append(" (")
                                  .append(file.get("size"))
                                  .append(" bytes)\n");
                    }
                    result.addData("files_message", fileListMsg.toString());
                }
                
                return result.toJson();
            }
            else
            {
                ToolResult result = ToolResult.success("命令已执行(退出码=" + exitCode + ")");
                result.setSuccess(true);
                result.addData("exitCode", exitCode);
                result.addData("stdout", out);
                result.addData("stderr", err);
                result.addData("durationMs", duration);
                result.addData("workDir", workDir.toString());
                
                if (!generatedFiles.isEmpty())
                {
                    result.addData("generated_files", generatedFiles);
                    result.addData("generated_files_count", generatedFiles.size());
                    
                    // 构建友好的提示消息
                    StringBuilder fileListMsg = new StringBuilder("\n\n生成的文件：\n");
                    for (Map<String, Object> file : generatedFiles)
                    {
                        fileListMsg.append("- ")
                                  .append(file.get("download_link_markdown"))
                                  .append(" (")
                                  .append(file.get("size"))
                                  .append(" bytes)\n");
                    }
                    result.addData("files_message", fileListMsg.toString());
                }
                
                return result.toJson();
            }
        }
        catch (Exception e)
        {
            log.error("命令执行失败: " + command, e);
            return ToolResult.error("执行失败: " + e.getMessage()).toJson();
        }
    }

    private void readStream(java.io.InputStream is, StringBuilder sb)
    {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                sb.append(line).append("\n");
            }
        }
        catch (Exception ignored) {}
    }
    
    /**
     * 递归列出目录下所有文件
     */
    private Set<Path> listAllFiles(Path dir)
    {
        if (!Files.isDirectory(dir)) return Collections.emptySet();
        try
        {
            return Files.walk(dir)
                    .filter(Files::isRegularFile)
                    .collect(Collectors.toSet());
        }
        catch (Exception e)
        {
            log.warn("扫描目录失败: {}", dir, e);
            return Collections.emptySet();
        }
    }
    
    /**
     * 保存生成的文件到数据库，参考CodeExecutionPlugin的实现
     * 
     * @param newFile 新生成的文件路径
     * @param workDir 工作目录
     * @return 文件ID，失败返回null
     */
    private Long saveGeneratedFileToDb(Path newFile, Path workDir)
    {
        try
        {
            // 从RuntimeContextHolder获取上下文信息
            String sessionId = RuntimeContextHolder.getSessionId();
            String agentCode = RuntimeContextHolder.getAgentCode();
            String businessSystem = RuntimeContextHolder.getBusinessSystem();
            String userLoginName = RuntimeContextHolder.getUserLoginName();
            
            if (businessSystem == null) businessSystem = "default";
            if (userLoginName == null) userLoginName = "anonymous";
            if (sessionId == null) sessionId = "temp";
            
            // 构建目标路径: agent-workspace/{businessSystem}/{userLoginName}/{sessionId}/
            Path targetDir = Paths.get(uploadPath, "agent-workspace", 
                    businessSystem, userLoginName, sessionId);
            
            // 确保目标目录存在
            if (!Files.exists(targetDir))
            {
                Files.createDirectories(targetDir);
            }
            
            // 生成唯一文件名（保留原扩展名）
            String originalFileName = newFile.getFileName().toString();
            String extension = "";
            int dotIndex = originalFileName.lastIndexOf('.');
            if (dotIndex > 0)
            {
                extension = originalFileName.substring(dotIndex);
            }
            String uniqueFileName = IdUtils.fastSimpleUUID() + extension;
            
            // 目标文件路径
            Path targetFile = targetDir.resolve(uniqueFileName);
            
            // 复制文件到agent-workspace目录
            Files.copy(newFile, targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            
            // 计算相对路径（相对于uploadPath）
            Path uploadPathRoot = Paths.get(uploadPath);
            String relativePath = uploadPathRoot.relativize(targetFile).toString().replace("\\", "/");
            
            // 记录到数据库
            AiAgentFile fileRecord = new AiAgentFile();
            fileRecord.setFileName(originalFileName);  // 保存原始文件名
            fileRecord.setFilePath(relativePath);
            fileRecord.setFileSize(Files.size(targetFile));
            fileRecord.setFileType(getFileType(originalFileName));
            fileRecord.setSessionId(sessionId);
            fileRecord.setAgentCode(agentCode);
            fileRecord.setBusinessSystem(businessSystem);
            fileRecord.setUserLoginName(userLoginName);
            fileRecord.setRemark("终端命令生成的文件");
            
            fileMapper.insertAiAgentFile(fileRecord);
            
            log.info("文件已保存到数据库 [fileId={}, originalName={}, targetPath={}]", 
                    fileRecord.getFileId(), originalFileName, relativePath);
            
            return fileRecord.getFileId();
        }
        catch (Exception e)
        {
            log.error("保存文件到数据库失败: " + newFile.getFileName(), e);
            return null;
        }
    }
    
    /**
     * 根据文件名判断文件类型
     */
    private String getFileType(String fileName)
    {
        if (fileName == null) return "unknown";
        String lower = fileName.toLowerCase();
        
        if (lower.endsWith(".py")) return "python";
        if (lower.endsWith(".js")) return "javascript";
        if (lower.endsWith(".java")) return "java";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "html";
        if (lower.endsWith(".css")) return "css";
        if (lower.endsWith(".json")) return "json";
        if (lower.endsWith(".xml")) return "xml";
        if (lower.endsWith(".txt")) return "text";
        if (lower.endsWith(".md")) return "markdown";
        if (lower.endsWith(".pdf")) return "pdf";
        if (lower.endsWith(".doc") || lower.endsWith(".docx")) return "document";
        if (lower.endsWith(".xls") || lower.endsWith(".xlsx")) return "spreadsheet";
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif")) return "image";
        if (lower.endsWith(".zip") || lower.endsWith(".tar") || lower.endsWith(".gz")) return "archive";
        
        return "file";
    }
}
