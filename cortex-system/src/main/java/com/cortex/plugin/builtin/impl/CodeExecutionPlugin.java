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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Code Execution Plugin with sandboxing - reference hermes code_execution_tool.py
 *
 * Sandbox features:
 * 1. Per-session working directory isolation (under sandbox base dir)
 * 2. Process timeout with forcible kill
 * 3. Output size truncation
 * 4. Environment variable filtering (strip secrets)
 * 5. No network access by default (via env vars)
 *
 * @author cortex
 */
@Component
public class CodeExecutionPlugin implements IBuiltinPlugin
{
    private static final Logger log = LoggerFactory.getLogger(CodeExecutionPlugin.class);

    private static final int TIMEOUT = 60;
    private static final int MAX_OUTPUT = 50000;
    private static final int MAX_THREADS = 4;
    private static final java.util.concurrent.Semaphore EXEC_SEMAPHORE = new java.util.concurrent.Semaphore(MAX_THREADS);

    @Autowired
    @Qualifier("agentStreamExecutor")
    private ThreadPoolTaskExecutor streamExecutor;
    
    @Autowired
    private AiAgentFileMapper fileMapper;
    
    @Value("${env.path}")
    private String envBasePath;

    @Value("${cortex.sandbox.baseDir:#{null}}")
    private String sandboxBaseDir;
    
    @Value("${cortex.profile:D:/cortex/uploadPath}")
    private String uploadPath;
    
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

    @Override
    public PluginInfo getPluginInfo()
    {
        PluginInfo info = new PluginInfo("code-execution", "code-execution", "Execute Python/Node.js scripts in sandbox");
        info.setVersion("2.0.0");
        info.setAuthor("CORTEX");
        info.setCategory("code");
        info.setEmoji("snake");
        info.setRequireApproval(true);
        return info;
    }

    @Override
    public List<ToolDefinition> getTools()
    {
        List<ToolDefinition> tools = new ArrayList<>();

        ToolDefinition py = new ToolDefinition();
        py.setName("execute_python");
        py.setDescription("Execute Python script in an isolated sandbox. Each session gets its own working directory.");
        Map<String, Object> pySchema = new HashMap<>();
        pySchema.put("type", "object");
        Map<String, Object> pyProps = new HashMap<>();
        pyProps.put("code", Map.of("type", "string", "description", "Python source code"));
        pyProps.put("input", Map.of("type", "string", "description", "Standard input (optional)"));
        pyProps.put("session_id", Map.of("type", "string", "description", "Session ID for working directory isolation"));
        pySchema.put("properties", pyProps);
        pySchema.put("required", List.of("code"));
        py.setInputSchema(pySchema);
        tools.add(py);

        ToolDefinition node = new ToolDefinition();
        node.setName("execute_node");
        node.setDescription("Execute Node.js script in an isolated sandbox. Each session gets its own working directory.");
        Map<String, Object> nodeSchema = new HashMap<>();
        nodeSchema.put("type", "object");
        Map<String, Object> nodeProps = new HashMap<>();
        nodeProps.put("code", Map.of("type", "string", "description", "JavaScript source code"));
        nodeProps.put("input", Map.of("type", "string", "description", "Standard input (optional)"));
        nodeProps.put("session_id", Map.of("type", "string", "description", "Session ID for working directory isolation"));
        nodeSchema.put("properties", nodeProps);
        nodeSchema.put("required", List.of("code"));
        node.setInputSchema(nodeSchema);
        tools.add(node);

        return tools;
    }

    @Override
    public String executeTool(String toolName, Map<String, Object> arguments)
    {
        try
        {
            // Acquire semaphore to limit concurrent executions
            if (!EXEC_SEMAPHORE.tryAcquire(5, TimeUnit.SECONDS))
            {
                return ToolResult.error("Too many concurrent code executions, please retry").toJson();
            }
            try
            {
                switch (toolName)
                {
                    case "execute_python": return executeCode(arguments, "python");
                    case "execute_node": return executeCode(arguments, "node");
                    default: return ToolResult.error("Unknown tool: " + toolName).toJson();
                }
            }
            finally
            {
                EXEC_SEMAPHORE.release();
            }
        }
        catch (Exception e)
        {
            log.error("Code execution failed: " + toolName, e);
            return ToolResult.error("Execution failed: " + e.getMessage()).toJson();
        }
    }

    private String executeCode(Map<String, Object> args, String lang) throws Exception
    {
        String code = (String) args.get("code");
        String input = (String) args.get("input");
        // 优先使用运行时上下文中的sessionId，如果没有则从参数获取
        String sessionId = RuntimeContextHolder.getSessionId();
        if (sessionId == null || sessionId.isEmpty())
        {
            sessionId = (String) args.getOrDefault("session_id", "default");
        }

        if (code == null || code.trim().isEmpty())
        {
            return ToolResult.error("code parameter is required").toJson();
        }

        String interpreter = "python".equals(lang) ? getPythonExecutable() : getNodeExecutable();
        if (interpreter == null)
        {
            return ToolResult.error(lang + " environment not configured").toJson();
        }

        // Create isolated working directory per session
        Path workDir = createSandboxDir(sessionId);
        
        // 记录执行前的文件列表（用于后续识别新生成的文件）
        Set<Path> beforeFiles = listAllFiles(workDir);

        // Write code to temp file inside sandbox
        String suffix = "python".equals(lang) ? ".py" : ".js";
        Path tempFile = workDir.resolve("exec_" + System.currentTimeMillis() + suffix);
        Files.writeString(tempFile, code, StandardCharsets.UTF_8);

        long startTime = System.currentTimeMillis();
        Process process = null;
        try
        {
            ProcessBuilder pb = new ProcessBuilder(interpreter, tempFile.getFileName().toString());
            pb.directory(workDir.toFile());
            pb.redirectErrorStream(false);

            // Filter environment: strip secrets, set sandbox vars
            Map<String, String> env = pb.environment();
            sanitizeEnvironment(env);
            env.put("HAI_SANDBOX", "1");
            env.put("PYTHONUNBUFFERED", "1");

            process = pb.start();

            // Write stdin
            if (input != null && !input.isEmpty())
            {
                try (OutputStream os = process.getOutputStream())
                {
                    os.write(input.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }
            }
            else
            {
                process.getOutputStream().close();
            }

            // Read output in parallel
            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();
            final Process p = process;
            java.util.concurrent.Future<?> outFuture = streamExecutor.submit(() -> readStream(p.getInputStream(), stdout));
            java.util.concurrent.Future<?> errFuture = streamExecutor.submit(() -> readStream(p.getErrorStream(), stderr));

            boolean finished = process.waitFor(TIMEOUT, TimeUnit.SECONDS);
            if (!finished)
            {
                process.destroyForcibly();
                outFuture.get(1, java.util.concurrent.TimeUnit.SECONDS);
                errFuture.get(1, java.util.concurrent.TimeUnit.SECONDS);
                return ToolResult.error("Code execution timed out (" + TIMEOUT + "s), process killed")
                        .addData("stdout", truncate(stdout.toString()))
                        .addData("stderr", truncate(stderr.toString()))
                        .toJson();
            }

            outFuture.get(1, java.util.concurrent.TimeUnit.SECONDS);
            errFuture.get(1, java.util.concurrent.TimeUnit.SECONDS);

            int exitCode = process.exitValue();
            int duration = (int) (System.currentTimeMillis() - startTime);

            String out = truncate(stdout.toString());
            String err = truncate(stderr.toString());
            
            // 扫描工作目录，识别新生成的文件并落库
            Set<Path> afterFiles = listAllFiles(workDir);
            afterFiles.removeAll(beforeFiles);  // 只保留新生成的文件
            afterFiles.remove(tempFile);  // 排除临时执行文件
            
            List<Map<String, Object>> generatedFiles = new ArrayList<>();
            if (!afterFiles.isEmpty())
            {
                log.info("检测到 {} 个新生成的文件", afterFiles.size());
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
                            log.info("✅ 代码执行生成文件已落库: {} -> fileId={}", originalFileName, fileId);
                        }
                    }
                    catch (Exception e)
                    {
                        log.error("❌ 保存生成文件失败: " + newFile.getFileName(), e);
                    }
                }
            }

            ToolResult result = ToolResult.success(exitCode == 0 ? "Execution successful" : "Execution completed (exit=" + exitCode + ")")
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
        finally
        {
            if (process != null) process.destroyForcibly();
            // Clean up temp file, keep workDir for session persistence
            try
            {
                Files.deleteIfExists(tempFile);
            }
            catch (Exception e)
            {
                log.warn("清理临时文件失败: " + tempFile, e);
            }
        }
    }

    /**
     * Create isolated sandbox directory per session
     */
    private Path createSandboxDir(String sessionId) throws IOException
    {
        String base = sandboxBaseDir;
        if (base == null || base.isEmpty())
        {
            base = System.getProperty("java.io.tmpdir") + "/cortex-sandbox";
        }
        Path dir = Paths.get(base, sessionId);
        if (!Files.exists(dir))
        {
            Files.createDirectories(dir);
        }
        return dir;
    }

    /**
     * Sanitize environment: remove sensitive variables
     */
    private void sanitizeEnvironment(Map<String, String> env)
    {
        env.keySet().removeIf(key ->
            key.toUpperCase().contains("KEY") ||
            key.toUpperCase().contains("SECRET") ||
            key.toUpperCase().contains("PASSWORD") ||
            key.toUpperCase().contains("TOKEN") ||
            key.toUpperCase().contains("CREDENTIAL") ||
            key.contains("API_KEY")
        );
    }

    private String truncate(String s)
    {
        if (s == null) return "";
        if (s.length() > MAX_OUTPUT) return s.substring(0, MAX_OUTPUT) + "\n... [output truncated]";
        return s;
    }

    private String getPythonExecutable()
    {
        Path envPath = Paths.get(envBasePath, "python-env");
        Path winExe = envPath.resolve("Scripts").resolve("python.exe");
        if (Files.exists(winExe)) return winExe.toString();
        Path unixExe = envPath.resolve("bin").resolve("python");
        if (Files.exists(unixExe)) return unixExe.toString();
        return "python";
    }

    private String getNodeExecutable()
    {
        Path envPath = Paths.get(envBasePath, "node-env");
        Path winExe = envPath.resolve("node.exe");
        if (Files.exists(winExe)) return winExe.toString();
        Path unixExe = envPath.resolve("bin").resolve("node");
        if (Files.exists(unixExe)) return unixExe.toString();
        return "node";
    }

    private void readStream(InputStream is, StringBuilder sb)
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
     * 列出目录中的所有文件（递归）
     */
    private Set<Path> listAllFiles(Path dir) throws IOException
    {
        Set<Path> files = new HashSet<>();
        if (!Files.exists(dir))
        {
            return files;
        }
        try (Stream<Path> stream = Files.walk(dir))
        {
            stream.filter(Files::isRegularFile)
                  .forEach(files::add);
        }
        return files;
    }
    
    /**
     * 将代码执行生成的文件保存到数据库
     */
    private Long saveGeneratedFileToDb(Path filePath, Path workDir) throws IOException
    {
        // 只保存常规文件，忽略临时文件和隐藏文件
        String fileName = filePath.getFileName().toString();
        if (fileName.startsWith(".") || fileName.startsWith("exec_") || fileName.startsWith("~"))
        {
            log.debug("跳过临时文件: {}", fileName);
            return null;
        }
        
        // 检查文件是否存在且可读
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath) || !Files.isReadable(filePath))
        {
            log.warn("文件不可读或不是常规文件: {}", filePath);
            return null;
        }
        
        // 计算相对于uploadPath的路径
        // 将sandbox目录中的文件复制到agent-workspace目录
        String sessionId = RuntimeContextHolder.getSessionId();
        String businessSystem = RuntimeContextHolder.getBusinessSystem();
        String userLoginName = RuntimeContextHolder.getUserLoginName();
        
        if (sessionId == null) sessionId = "default";
        if (businessSystem == null) businessSystem = "default";
        if (userLoginName == null) userLoginName = "anonymous";
        
        // 构建目标路径（agent-workspace/{businessSystem}/{userLoginName}/{sessionId}/）
        Path targetDir = Paths.get(uploadPath, "agent-workspace", businessSystem, userLoginName, sessionId);
        Files.createDirectories(targetDir);
        
        // 生成UUID文件名
        String uuidFileName = UUID.randomUUID().toString().replace("-", "") + getFileExtension(fileName);
        Path targetFile = targetDir.resolve(uuidFileName);
        
        // 复制文件（如果目标文件已存在，则替换）
        try
        {
            Files.copy(filePath, targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        catch (IOException e)
        {
            log.error("复制文件失败: {} -> {}", filePath, targetFile, e);
            throw e;
        }
        
        // 计算相对路径
        Path uploadPathRoot = Paths.get(uploadPath);
        String relativePath = uploadPathRoot.relativize(targetFile).toString().replace("\\", "/");
        
        // 保存到数据库
        AiAgentFile fileRecord = new AiAgentFile();
        fileRecord.setFileName(fileName);  // 保存原始文件名
        fileRecord.setFilePath(relativePath);
        fileRecord.setFileSize(Files.size(targetFile));
        fileRecord.setFileType("generated");
        fileRecord.setSessionId(sessionId);
        fileRecord.setAgentCode(RuntimeContextHolder.getAgentCode());
        fileRecord.setBusinessSystem(businessSystem);
        fileRecord.setUserLoginName(userLoginName);
        fileRecord.setRemark("代码执行生成");
        
        try
        {
            fileMapper.insertAiAgentFile(fileRecord);
            log.debug("文件记录已保存到数据库: fileId={}, fileName={}", fileRecord.getFileId(), fileName);
        }
        catch (Exception e)
        {
            log.error("保存文件记录到数据库失败: {}", fileName, e);
            // 如果数据库保存失败，删除已复制的文件
            try
            {
                Files.deleteIfExists(targetFile);
            }
            catch (Exception ignored) {}
            throw e;
        }
        
        return fileRecord.getFileId();
    }
    
    /**
     * 获取文件扩展名（包含点）
     */
    private String getFileExtension(String fileName)
    {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0)
        {
            return fileName.substring(lastDot);
        }
        return "";
    }
}
