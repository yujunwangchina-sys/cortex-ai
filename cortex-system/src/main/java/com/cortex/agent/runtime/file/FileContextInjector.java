package com.cortex.agent.runtime.file;

import com.cortex.agent.domain.AiAgentFile;
import com.cortex.agent.runtime.model.AgentRunRequest;
import com.cortex.agent.runtime.model.SSEEvent;
import com.cortex.agent.service.IAiAgentFileService;
import com.cortex.supplier.domain.AiModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 文件上下文注入器
 * 在对话开始前, 将上传的文件内容解析并注入到用户消息中
 *
 * 处理流程:
 * 1. 文档文件 (doc/xls/ppt/pdf/txt/md) → 解析文本, 拼接到用户消息
 * 2. 图片文件:
 *    - 当前模型支持 vision/multimodal → 图片 URL 直接加入 imageUrls
 *    - 当前模型不支持 → 调用 vision 模型降级识别, 描述文本拼接到用户消息
 *
 * @author cortex
 */
@Component
public class FileContextInjector
{
    private static final Logger log = LoggerFactory.getLogger(FileContextInjector.class);

    /** 每个文件最大字符数 (单文件截断) */
    private static final int PER_FILE_MAX_CHARS = 20000;

    /** 多文件总内容最大字符数 */
    private static final int TOTAL_MAX_CHARS = 50000;

    @Autowired
    private FileContentParser fileParser;

    @Autowired
    private ImageVisionFallback visionFallback;

    @Autowired
    private IAiAgentFileService fileService;

    @Autowired
    private com.cortex.plugin.builtin.impl.VoiceManagerPlugin voicePlugin;

    /**
     * 处理文件, 注入到用户消息
     *
     * @param request       运行请求 (会被修改 message 和 imageUrls)
     * @param currentModel  当前使用的模型
     * @param sseCallback   SSE 回调 (可发送进度通知)
     */
    public void inject(AgentRunRequest request, AiModel currentModel, Consumer<SSEEvent> sseCallback)
    {
        List<Long> fileIds = request.getFileIds();
        if (fileIds == null || fileIds.isEmpty())
        {
            return;
        }

        String userMessage = request.getMessage() != null ? request.getMessage() : "";
        StringBuilder docContent = new StringBuilder();
        StringBuilder voiceContent = new StringBuilder();
        List<String> visionImageUrls = new ArrayList<>();
        List<String> fallbackDescriptions = new ArrayList<>();
        int totalChars = 0;

        for (Long fileId : fileIds)
        {
            AiAgentFile fileRecord = fileService.selectAiAgentFileByFileId(fileId);
            if (fileRecord == null)
            {
                log.warn("文件记录不存在 [fileId={}]", fileId);
                continue;
            }

            if (isAudio(fileRecord.getFileName()))
            {
                if (sseCallback != null)
                {
                    sseCallback.accept(SSEEvent.info("Transcribing audio: " + fileRecord.getFileName()));
                }
                try
                {
                    String transcript = voicePlugin.transcribeFile(fileId);
                    if (transcript != null && !transcript.isBlank())
                    {
                        voiceContent.append("\n\n--- voice: ").append(fileRecord.getFileName())
                                .append(" ---\n").append(transcript);
                    }
                    else
                    {
                        voiceContent.append("\n\n--- voice: ").append(fileRecord.getFileName())
                                .append(" (empty transcript) ---");
                    }
                }
                catch (Exception e)
                {
                    log.warn("Audio transcribe failed [file={}]", fileRecord.getFileName(), e);
                    voiceContent.append("\n\n--- voice: ").append(fileRecord.getFileName())
                            .append(" (transcribe failed) ---");
                }
                continue;
            }
            if (fileParser.isImage(fileRecord.getFileName()))
            {
                // 图片处理
                if (visionFallback.supportsVision(currentModel))
                {
                    // 模型支持视觉, 直接加入 imageUrls
                    String imageUrl = buildImageUrl(fileRecord);
                    if (imageUrl != null)
                    {
                        visionImageUrls.add(imageUrl);
                        log.info("图片直接传给视觉模型 [file={}]", fileRecord.getFileName());
                        if (sseCallback != null)
                        {
                            sseCallback.accept(SSEEvent.info(
                                    "图片已加载: " + fileRecord.getFileName()));
                        }
                    }
                }
                else
                {
                    // 模型不支持视觉, 降级调用 vision 模型
                    if (sseCallback != null)
                    {
                        sseCallback.accept(SSEEvent.info(
                                "正在识别图片: " + fileRecord.getFileName() + " (当前模型不支持图像, 使用视觉模型降级)"));
                    }
                    String desc = visionFallback.describeImage(
                            fileRecord.getFilePath(), fileRecord.getFileName());
                    if (desc != null)
                    {
                        fallbackDescriptions.add(
                                "[图片: " + fileRecord.getFileName() + "]\n" + desc);
                        log.info("图片降级识别完成 [file={}]", fileRecord.getFileName());
                    }
                    else
                    {
                        fallbackDescriptions.add(
                                "[图片: " + fileRecord.getFileName() + " (识别失败, 当前无可用视觉模型)]");
                    }
                }
            }
            else
            {
                // 文档处理
                int remaining = TOTAL_MAX_CHARS - totalChars;
                if (remaining <= 0)
                {
                    docContent.append("\n[... 已达到总内容上限, 文件 ")
                            .append(fileRecord.getFileName())
                            .append(" 未解析 ...]");
                    continue;
                }

                int perFileLimit = Math.min(PER_FILE_MAX_CHARS, remaining);
                FileContentParser.ParsedFile parsed = fileParser.parse(
                        fileRecord.getFilePath(), fileRecord.getFileName(), perFileLimit);

                if (parsed.getContent() != null && !parsed.getContent().isEmpty())
                {
                    docContent.append("\n\n--- 文件: ")
                            .append(fileRecord.getFileName())
                            .append(" ---\n")
                            .append(parsed.getContent());

                    totalChars += parsed.getContent().length();

                    if (sseCallback != null)
                    {
                        String info = "文件已解析: " + fileRecord.getFileName();
                        if (parsed.isTruncated())
                        {
                            info += " (内容过长, 已截断)";
                        }
                        sseCallback.accept(SSEEvent.info(info));
                    }
                }
            }
        }

        // 拼装最终消息
        StringBuilder enrichedMessage = new StringBuilder(userMessage);

        if (voiceContent.length() > 0)
        {
            enrichedMessage.append("\n\n[Voice transcript]")
                    .append(voiceContent);
        }
        if (docContent.length() > 0)
        {
            enrichedMessage.append("\n\n[上传文件内容]")
                    .append(docContent);
        }

        if (!fallbackDescriptions.isEmpty())
        {
            enrichedMessage.append("\n\n[图片识别结果]");
            for (String desc : fallbackDescriptions)
            {
                enrichedMessage.append("\n\n").append(desc);
            }
        }

        request.setMessage(enrichedMessage.toString());

        // 设置图片 URL (仅当模型支持视觉时)
        if (!visionImageUrls.isEmpty())
        {
            // 合并请求中已有的 imageUrls
            List<String> allUrls = new ArrayList<>(visionImageUrls);
            if (request.getImageUrls() != null)
            {
                allUrls.addAll(request.getImageUrls());
            }
            request.setImageUrls(allUrls);
        }

        log.info("文件上下文注入完成 [fileIds={}, docChars={}, images={}, fallbacks={}]",
                fileIds.size(), docContent.length(), visionImageUrls.size(), fallbackDescriptions.size());
    }

    /**
     * 构建图片访问 URL (base64 data URL, 供 LLM 直接使用)
     */
    private boolean isAudio(String fileName)
    {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase();
        String[] exts = {".wav", ".mp3", ".m4a", ".webm", ".ogg", ".flac", ".aac", ".opus"};
        for (String ext : exts)
        {
            if (lower.endsWith(ext)) return true;
        }
        return false;
    }
    private String buildImageUrl(AiAgentFile fileRecord)
    {
        try
        {
            return visionFallback.compressAndEncodeImage(
                    fileRecord.getFilePath(), fileRecord.getFileName());
        }
        catch (Exception e)
        {
            log.error("构建图片URL失败 [file={}]", fileRecord.getFileName(), e);
            return null;
        }
    }

    @Value("${cortex.profile:D:/cortex/uploadPath}")
    private String uploadPathField;

    private String getUploadPath()
    {
        return uploadPathField;
    }

    private String getMimeType(String fileName)
    {
        String ext = "";
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) ext = fileName.substring(lastDot).toLowerCase();
        switch (ext)
        {
            case ".png": return "image/png";
            case ".jpg":
            case ".jpeg": return "image/jpeg";
            case ".gif": return "image/gif";
            case ".webp": return "image/webp";
            case ".bmp": return "image/bmp";
            default: return "image/jpeg";
        }
    }
}