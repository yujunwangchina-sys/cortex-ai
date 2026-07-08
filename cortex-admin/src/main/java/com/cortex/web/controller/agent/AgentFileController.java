package com.cortex.web.controller.agent;

import com.cortex.agent.domain.AiAgentFile;
import com.cortex.agent.runtime.file.FileContentParser;
import com.cortex.plugin.builtin.impl.VoiceManagerPlugin;
import com.cortex.agent.service.IAiAgentFileService;
import com.cortex.common.config.CortexConfig;
import com.cortex.common.core.controller.BaseController;
import com.cortex.common.core.domain.AjaxResult;
import com.cortex.common.utils.DateUtils;
import com.cortex.common.utils.SecurityUtils;
import com.cortex.common.utils.uuid.IdUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 文件上传下载 Controller
 * 
 * @author CORTEX
 */
@RestController
@RequestMapping("/agent/api/file")
public class AgentFileController extends BaseController
{
    private static final Logger log = LoggerFactory.getLogger(AgentFileController.class);

    @Autowired
    private IAiAgentFileService fileService;

    @Autowired
    private FileContentParser fileParser;

    @Autowired
    private VoiceManagerPlugin voicePlugin;

    /**
     * 上传文件/图片
     * 
     * 支持格式：
     * - 文档：doc, docx, xls, xlsx, ppt, pptx, pdf, txt, md, csv, json, xml, html
     * - 图片：jpg, jpeg, png, gif, bmp, webp
     * - 语音：mp3, wav, m4a, ogg (暂不支持，预留)
     */
    @PostMapping("/upload")
    public AjaxResult uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("sessionId") String sessionId,
            @RequestParam(value = "agentCode", required = false) String agentCode,
            @RequestParam(value = "businessSystem", defaultValue = "cortex") String businessSystem,
            @RequestParam(value = "userLoginName", required = false) String userLoginName)
    {
        try
        {
            log.info("📤 收到文件上传请求: {}, sessionId: {}, agentCode: {}", 
                     file.getOriginalFilename(), sessionId, agentCode);

            // 文件验证
            if (file.isEmpty())
            {
                return error("文件不能为空");
            }

            // 文件大小限制 50MB
            if (file.getSize() > 50 * 1024 * 1024)
            {
                return error("文件大小不能超过50MB");
            }

            // 文件格式验证
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || !isAllowedFileType(originalFilename))
            {
                return error("不支持的文件格式");
            }

            // 保存文件
            String extension = getFileExtension(originalFilename);
            String storedFilename = IdUtils.fastSimpleUUID() + extension;
            
            // 按日期存储: /agent/2024/01/15/xxx.pdf (相对路径)
            String datePath = DateUtils.datePath();
            String relativePath = "agent/" + datePath + "/" + storedFilename;
            
            // 完整的物理路径
            String uploadPath = CortexConfig.getProfile();
            String absolutePath = uploadPath + "/" + relativePath;
            
            File dest = new File(absolutePath);
            if (!dest.getParentFile().exists())
            {
                dest.getParentFile().mkdirs();
            }
            file.transferTo(dest);

            // 保存到数据库（使用相对路径）
            AiAgentFile agentFile = new AiAgentFile();
            agentFile.setFileName(originalFilename);
            agentFile.setFilePath(relativePath);  // 存储相对路径
            agentFile.setFileSize(file.getSize());
            agentFile.setFileType("upload");
            agentFile.setSessionId(sessionId);
            agentFile.setAgentCode(agentCode);  // 存储agentCode
            agentFile.setBusinessSystem(businessSystem);
            agentFile.setUserLoginName(userLoginName != null ? userLoginName : SecurityUtils.getUsername());
            agentFile.setCreateBy(SecurityUtils.getUsername());

            fileService.insertAiAgentFile(agentFile);

            // 返回结果
            Map<String, Object> result = new HashMap<>();
            result.put("fileId", agentFile.getFileId());
            result.put("fileName", agentFile.getFileName());
            result.put("fileSize", agentFile.getFileSize());
            
            log.info("✅ 文件上传成功: {}, ID: {}, 相对路径: {}", 
                     originalFilename, agentFile.getFileId(), relativePath);

            return success(result);
        }
        catch (Exception e)
        {
            log.error("❌ 文件上传失败", e);
            return error("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 语音转文字（录完音直接转写，返回文本）
     * 前端录音转 16kHz 单声道 wav 后上传，返回转写文本供输入框使用。
     */
    @PostMapping("/transcribe")
    public AjaxResult transcribeVoice(@RequestParam("file") MultipartFile file)
    {
        File tempFile = null;
        try
        {
            if (file.isEmpty())
            {
                return error("音频文件不能为空");
            }
            // 落盘临时 wav 供 sherpa-onnx WaveReader 读取
            tempFile = File.createTempFile("voice_stt_", ".wav");
            file.transferTo(tempFile);

            String text = voicePlugin.transcribePath(tempFile.getAbsolutePath());
            if (text == null || text.isBlank())
            {
                return error("语音转写结果为空");
            }
            Map<String, Object> data = new HashMap<>();
            data.put("text", text);
            log.info("语音转写成功: textLen={}", text.length());
            return success(data);
        }
        catch (Exception e)
        {
            log.error("语音转写失败", e);
            return error("语音转写失败: " + e.getMessage());
        }
        finally
        {
            if (tempFile != null && tempFile.exists())
            {
                tempFile.delete();
            }
        }
    }

    /**
     * 文字转语音（朗读 AI 回复）
     * 接收文字，返回 wav 音频字节流，前端直接播放。
     */
    @PostMapping("/speak")
    public ResponseEntity<byte[]> speak(@RequestBody Map<String, Object> body)
    {
        try
        {
            String text = (String) body.get("text");
            if (text == null || text.isBlank())
            {
                return ResponseEntity.badRequest().build();
            }
            byte[] wavBytes = voicePlugin.generateSpeechWav(text, null);
            if (wavBytes == null)
            {
                return ResponseEntity.internalServerError().build();
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("audio/wav"))
                    .body(wavBytes);
        }
        catch (Exception e)
        {
            log.error("语音合成失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 查看文件（在线预览）
     */
    @GetMapping("/view/{fileId}")
    public ResponseEntity<Resource> viewFile(@PathVariable("fileId") Long fileId)
    {
        try
        {
            AiAgentFile agentFile = fileService.selectAiAgentFileByFileId(fileId);
            if (agentFile == null)
            {
                log.error("❌ 文件不存在: fileId={}", fileId);
                return ResponseEntity.notFound().build();
            }

            // 处理文件路径：如果是相对路径，拼接 uploadPath
            String filePath = agentFile.getFilePath();
            File file;
            
            if (new File(filePath).isAbsolute())
            {
                // 绝对路径（旧的上传文件）
                file = new File(filePath);
            }
            else
            {
                // 相对路径（Agent 生成的文件）
                String absolutePath = CortexConfig.getProfile() + "/" + filePath;
                file = new File(absolutePath);
                log.debug("📁 相对路径转绝对路径: {} -> {}", filePath, absolutePath);
            }

            if (!file.exists())
            {
                log.error("❌ 文件物理文件不存在: {}", file.getAbsolutePath());
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(file);

            String contentType = guessContentType(agentFile.getFileName());

            log.info("✅ 文件查看成功: fileId={}, fileName={}, path={}", 
                     fileId, agentFile.getFileName(), file.getAbsolutePath());

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + 
                            encodeFilename(agentFile.getFileName()) + "\"")
                    .body(resource);
        }
        catch (Exception e)
        {
            log.error("❌ 文件查看失败: fileId={}", fileId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 下载文件
     */
    @GetMapping("/download/{fileId}")
    public ResponseEntity<Resource> downloadFile(@PathVariable("fileId") Long fileId)
    {
        try
        {
            log.info("📥 收到文件下载请求: fileId={}", fileId);
            
            AiAgentFile agentFile = fileService.selectAiAgentFileByFileId(fileId);
            if (agentFile == null)
            {
                log.error("❌ 文件记录不存在: fileId={}", fileId);
                return ResponseEntity.notFound().build();
            }

            log.info("📄 文件记录: fileName={}, filePath={}, fileType={}", 
                     agentFile.getFileName(), agentFile.getFilePath(), agentFile.getFileType());

            // 处理文件路径：如果是相对路径，拼接 uploadPath
            String filePath = agentFile.getFilePath();
            File file;
            
            if (new File(filePath).isAbsolute())
            {
                // 绝对路径（旧的上传文件）
                file = new File(filePath);
                log.info("📁 使用绝对路径: {}", filePath);
            }
            else
            {
                // 相对路径（Agent 生成的文件）
                String absolutePath = CortexConfig.getProfile() + "/" + filePath;
                file = new File(absolutePath);
                log.info("📁 相对路径转绝对路径: {} -> {}", filePath, absolutePath);
            }

            if (!file.exists())
            {
                log.error("❌ 文件物理文件不存在: {}", file.getAbsolutePath());
                log.error("   检查路径: uploadPath={}, relativePath={}", 
                         CortexConfig.getUploadPath(), filePath);
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(file);

            log.info("✅ 文件下载成功: fileId={}, fileName={}, size={} bytes", 
                     fileId, agentFile.getFileName(), file.length());

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + 
                            encodeFilename(agentFile.getFileName()) + "\"")
                    .body(resource);
        }
        catch (Exception e)
        {
            log.error("❌ 文件下载异常: fileId={}", fileId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取会话的所有文件
     */
    @GetMapping("/list/{sessionId}")
    public AjaxResult listFiles(@PathVariable("sessionId") String sessionId)
    {
        try
        {
            List<AiAgentFile> files = fileService.selectFilesBySessionId(sessionId);
            return success(files);
        }
        catch (Exception e)
        {
            log.error("❌ 查询文件列表失败", e);
            return error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 删除文件
     */
    @DeleteMapping("/{fileId}")
    public AjaxResult deleteFile(@PathVariable("fileId") Long fileId)
    {
        try
        {
            AiAgentFile agentFile = fileService.selectAiAgentFileByFileId(fileId);
            if (agentFile != null && agentFile.getFilePath() != null)
            {
                File file = new File(agentFile.getFilePath());
                if (file.exists())
                {
                    file.delete();
                }
            }

            int result = fileService.deleteAiAgentFileByFileId(fileId);
            return result > 0 ? success() : error("删除失败");
        }
        catch (Exception e)
        {
            log.error("❌ 删除文件失败", e);
            return error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 验证文件类型
     */
    private boolean isAllowedFileType(String filename)
    {
        String extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        return extension.matches("(doc|docx|xls|xlsx|ppt|pptx|pdf|txt|md|csv|json|xml|html|jpg|jpeg|png|gif|bmp|webp|mp3|wav|m4a|ogg)");
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename)
    {
        if (filename == null || filename.isEmpty())
        {
            return "";
        }
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(lastDot) : "";
    }

    /**
     * 猜测 Content-Type
     */
    private String guessContentType(String filename)
    {
        String ext = getFileExtension(filename).toLowerCase();
        Map<String, String> mimeTypes = new HashMap<>();
        mimeTypes.put(".pdf", "application/pdf");
        mimeTypes.put(".doc", "application/msword");
        mimeTypes.put(".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        mimeTypes.put(".xls", "application/vnd.ms-excel");
        mimeTypes.put(".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        mimeTypes.put(".ppt", "application/vnd.ms-powerpoint");
        mimeTypes.put(".pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation");
        mimeTypes.put(".txt", "text/plain");
        mimeTypes.put(".md", "text/markdown");
        mimeTypes.put(".jpg", "image/jpeg");
        mimeTypes.put(".jpeg", "image/jpeg");
        mimeTypes.put(".png", "image/png");
        mimeTypes.put(".gif", "image/gif");
        mimeTypes.put(".bmp", "image/bmp");
        mimeTypes.put(".webp", "image/webp");
        return mimeTypes.getOrDefault(ext, "application/octet-stream");
    }

    /**
     * 编码文件名
     */
    private String encodeFilename(String filename)
    {
        try
        {
            return URLEncoder.encode(filename, "UTF-8").replace("+", "%20");
        }
        catch (UnsupportedEncodingException e)
        {
            return filename;
        }
    }
}
