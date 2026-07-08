package com.cortex.agent.runtime.util;

import com.cortex.agent.domain.AiAgentFile;
import com.cortex.agent.mapper.AiAgentFileMapper;
import com.cortex.agent.runtime.context.RuntimeContextHolder;
import com.cortex.common.config.CortexConfig;
import com.cortex.common.utils.DateUtils;
import com.cortex.common.utils.uuid.IdUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 图片存储服务
 * 将base64图片保存到本地文件系统和数据库，返回可访问的URL
 * 参考 FileOperationPlugin 的实现方式
 * 
 * @author cortex
 */
@Service
public class ImageStorageService {
    private static final Logger log = LoggerFactory.getLogger(ImageStorageService.class);
    
    @Value("${cortex.profile:D:/cortex/uploadPath}")
    private String uploadPath;
    
    @Autowired
    private AiAgentFileMapper fileMapper;
    
    // 匹配 markdown 格式的base64图片: ![alt](data:image/type;base64,...)
    // 使用 [^)]+ 匹配到右括号为止，避免截断长base64字符串
    private static final Pattern MARKDOWN_IMAGE_PATTERN = Pattern.compile(
        "!\\[([^\\]]*)\\]\\(data:image/([^;]+);base64,([^)]+)\\)",
        Pattern.DOTALL
    );
    
    // 匹配 HTML img 标签中的base64图片: <img src="data:image/type;base64,..." />
    // 使用 [^"]+ 匹配到引号为止
    private static final Pattern HTML_IMAGE_PATTERN = Pattern.compile(
        "<img[^>]*src=\"data:image/([^;]+);base64,([^\"]+)\"[^>]*>",
        Pattern.DOTALL
    );
    
    /**
     * 处理内容中的所有base64图片，保存到文件并替换为结构化JSON
     * 返回格式参考CodeExecutionPlugin的文件返回格式
     * 
     * @param content 原始内容（可能包含base64图片）
     * @param sessionId 会话ID（用于组织目录结构）
     * @return 替换后的内容（结构化JSON或原内容）
     */
    public String processAndReplaceImages(String content, String sessionId) {
        if (content == null || !content.contains("data:image")) {
            return content;
        }
        
        // 用于存储所有图片信息
        java.util.List<java.util.Map<String, Object>> generatedImages = new java.util.ArrayList<>();
        String textContent = content;
        int savedCount = 0;
        long savedBytes = 0;
        
        try {
            // 处理 markdown 格式的图片
            Matcher markdownMatcher = MARKDOWN_IMAGE_PATTERN.matcher(content);
            StringBuffer markdownBuffer = new StringBuffer();
            
            while (markdownMatcher.find()) {
                String alt = markdownMatcher.group(1);
                String imageType = markdownMatcher.group(2); // png, jpeg, etc.
                String base64Data = markdownMatcher.group(3);
                
                try {
                    java.util.Map<String, Object> imageInfo = saveBase64ImageWithInfo(base64Data, imageType, sessionId, alt);
                    savedBytes += base64Data.length();
                    savedCount++;
                    generatedImages.add(imageInfo);
                    
                    // 替换为占位符
                    String placeholder = "[IMAGE_" + savedCount + "]";
                    markdownMatcher.appendReplacement(markdownBuffer, Matcher.quoteReplacement(placeholder));
                    
                    log.info("Markdown图片替换成功 [alt={}, originalBase64Length={}, fileId={}]", 
                            alt, base64Data.length(), imageInfo.get("file_id"));
                } catch (Exception e) {
                    log.error("保存markdown格式图片失败", e);
                    // 保持原样
                }
            }
            markdownMatcher.appendTail(markdownBuffer);
            textContent = markdownBuffer.toString();
            
            // 处理 HTML 格式的图片
            Matcher htmlMatcher = HTML_IMAGE_PATTERN.matcher(textContent);
            StringBuffer htmlBuffer = new StringBuffer();
            
            while (htmlMatcher.find()) {
                String imageType = htmlMatcher.group(1);
                String base64Data = htmlMatcher.group(2);
                
                try {
                    java.util.Map<String, Object> imageInfo = saveBase64ImageWithInfo(base64Data, imageType, sessionId, "图片");
                    savedBytes += base64Data.length();
                    savedCount++;
                    generatedImages.add(imageInfo);
                    
                    // 替换为占位符
                    String placeholder = "[IMAGE_" + savedCount + "]";
                    htmlMatcher.appendReplacement(htmlBuffer, Matcher.quoteReplacement(placeholder));
                    
                    log.info("HTML图片替换成功 [originalBase64Length={}, fileId={}]", 
                            base64Data.length(), imageInfo.get("file_id"));
                } catch (Exception e) {
                    log.error("保存HTML格式图片失败", e);
                    // 保持原样
                }
            }
            htmlMatcher.appendTail(htmlBuffer);
            textContent = htmlBuffer.toString();
            
        } catch (Exception e) {
            log.error("处理图片时发生异常", e);
            return content; // 发生异常时返回原内容
        }
        
        // 如果有图片，返回结构化JSON（参考CodeExecutionPlugin格式）
        if (savedCount > 0) {
            log.info("图片存储完成 [count={}, savedBytes={}KB, session={}]", 
                savedCount, savedBytes / 1024, sessionId);
            
            // 构建友好的提示消息（给AI看的）
            StringBuilder imagesMessage = new StringBuilder();
            imagesMessage.append("\n\n✅ 图片已生成！请在回复中使用以下markdown格式展示给用户：\n\n");
            for (java.util.Map<String, Object> image : generatedImages) {
                imagesMessage.append(image.get("view_link_markdown")).append("\n");
            }
            imagesMessage.append("\n提示：直接在你的回复中使用上面的markdown格式，用户就能看到图片。\n");
            
            // 构造结构化JSON返回
            com.alibaba.fastjson2.JSONObject result = new com.alibaba.fastjson2.JSONObject();
            result.put("success", true);
            result.put("message", "图片生成成功");
            result.put("generated_images", generatedImages);
            result.put("generated_images_count", savedCount);
            result.put("images_message", imagesMessage.toString());
            result.put("usage_tip", "请在回复中直接使用上述markdown格式展示图片给用户，用户将能够直接看到可视化结果。");
            
            // 如果有其他文本内容，也包含进去
            String cleanedText = textContent.replaceAll("\\[IMAGE_\\d+\\]", "").trim();
            if (!cleanedText.isEmpty()) {
                result.put("additional_content", cleanedText);
            }
            
            String finalResult = result.toJSONString();
            log.info("返回结构化JSON [length={}, imageCount={}]", finalResult.length(), savedCount);
            return finalResult;
        }
        
        return content;
    }
    
    /**
     * 保存base64图片到文件系统和数据库，返回详细信息Map
     * 
     * @param base64Data base64编码的图片数据（不包含前缀）
     * @param imageType 图片类型（png, jpeg, gif等）
     * @param sessionId 会话ID
     * @param altText 图片描述文本
     * @return 图片信息Map，包含file_id, file_name, size, view_url, download_url等
     * @throws IOException IO异常
     */
    private java.util.Map<String, Object> saveBase64ImageWithInfo(String base64Data, String imageType, 
                                                                   String sessionId, String altText) throws IOException {
        log.info("开始保存base64图片 [imageType={}, base64Length={}, sessionId={}]", 
                imageType, base64Data.length(), sessionId);
        
        // 解码base64
        byte[] imageBytes;
        try {
            imageBytes = Base64.getDecoder().decode(base64Data);
            log.info("Base64解码成功 [imageBytes={}]", imageBytes.length);
        } catch (Exception e) {
            log.error("Base64解码失败", e);
            throw new IOException("Base64解码失败: " + e.getMessage());
        }
        
        // 生成UUID文件名，保留扩展名
        String uuidFileName = IdUtils.fastSimpleUUID() + "." + imageType;
        log.info("生成UUID文件名: {}", uuidFileName);
        
        // 从RuntimeContextHolder获取上下文信息（与FileOperationPlugin一致）
        String businessSystem = RuntimeContextHolder.getBusinessSystem();
        String userLoginName = RuntimeContextHolder.getUserLoginName();
        String agentCode = RuntimeContextHolder.getAgentCode();
        
        if (businessSystem == null) businessSystem = "default";
        if (userLoginName == null) userLoginName = "anonymous";
        if (sessionId == null) sessionId = "temp";
        
        log.info("运行时上下文 [uploadPath={}, businessSystem={}, userLoginName={}, agentCode={}, sessionId={}]",
                uploadPath, businessSystem, userLoginName, agentCode, sessionId);
        
        // 使用与FileOperationPlugin相同的目录结构: agent-workspace/{businessSystem}/{userLoginName}/{sessionId}/
        Path sessionDir = Paths.get(uploadPath, "agent-workspace", 
                businessSystem, userLoginName, sessionId);
        
        log.info("会话目录路径: {}", sessionDir.toAbsolutePath());
        
        // 确保目录存在
        File dir = sessionDir.toFile();
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            log.info("创建会话目录 [path={}, created={}]", sessionDir.toAbsolutePath(), created);
            
            if (!created) {
                throw new IOException("无法创建目录: " + sessionDir.toAbsolutePath());
            }
        } else {
            log.info("会话目录已存在: {}", sessionDir.toAbsolutePath());
        }
        
        // 完整的文件路径（直接放在会话目录下，与文件操作一致）
        File imageFile = new File(dir, uuidFileName);
        log.info("图片文件路径: {}", imageFile.getAbsolutePath());
        
        // 写入文件
        try (FileOutputStream fos = new FileOutputStream(imageFile)) {
            fos.write(imageBytes);
            fos.flush();
            log.info("图片文件写入成功 [path={}, size={}]", imageFile.getAbsolutePath(), imageBytes.length);
        } catch (Exception e) {
            log.error("写入图片文件失败 [path={}]", imageFile.getAbsolutePath(), e);
            throw new IOException("写入图片文件失败: " + e.getMessage());
        }
        
        // 验证文件是否存在
        if (!imageFile.exists()) {
            log.error("文件写入后不存在！[path={}]", imageFile.getAbsolutePath());
            throw new IOException("文件写入失败，文件不存在: " + imageFile.getAbsolutePath());
        }
        
        long actualFileSize = imageFile.length();
        log.info("文件验证成功 [path={}, actualSize={}, expectedSize={}]", 
                imageFile.getAbsolutePath(), actualFileSize, imageBytes.length);
        
        // 计算相对路径（相对于uploadPath，与FileOperationPlugin一致）
        Path uploadPathRoot = Paths.get(uploadPath);
        String relativePath = uploadPathRoot.relativize(imageFile.toPath()).toString().replace("\\", "/");
        log.info("计算相对路径: {}", relativePath);
        
        // 记录到数据库（字段与FileOperationPlugin保持一致）
        AiAgentFile fileRecord = new AiAgentFile();
        fileRecord.setFileName("image_" + uuidFileName);  // 原始文件名（标识为图片）
        fileRecord.setFilePath(relativePath);  // 保存相对路径
        fileRecord.setFileSize((long) imageBytes.length);
        fileRecord.setFileType("image");  // 标识为图片类型
        fileRecord.setSessionId(sessionId);
        fileRecord.setAgentCode(agentCode);  // 使用RuntimeContextHolder中的agentCode
        fileRecord.setBusinessSystem(businessSystem);
        fileRecord.setUserLoginName(userLoginName);
        fileRecord.setRemark("Agent生成的图片 (" + imageType + ")");
        
        fileMapper.insertAiAgentFile(fileRecord);
        
        log.info("数据库记录插入成功 [fileId={}, fileName={}, filePath={}, agentCode={}]",
                fileRecord.getFileId(), fileRecord.getFileName(), fileRecord.getFilePath(), agentCode);
        
        // 生成链接
        String viewUrl = "/agent/api/file/view/" + fileRecord.getFileId();
        String downloadUrl = "/agent/api/file/download/" + fileRecord.getFileId();
        
        // 构造返回的Map（参考CodeExecutionPlugin格式）
        java.util.Map<String, Object> imageInfo = new java.util.HashMap<>();
        imageInfo.put("file_name", "image_" + uuidFileName);
        imageInfo.put("file_id", fileRecord.getFileId());
        imageInfo.put("size", imageBytes.length);
        imageInfo.put("view_url", viewUrl);
        imageInfo.put("download_url", downloadUrl);
        imageInfo.put("view_link_markdown", "![" + (altText != null ? altText : "图片") + "](" + viewUrl + ")");
        imageInfo.put("download_link_markdown", "[点击下载 " + uuidFileName + "](" + downloadUrl + ")");
        
        log.info("图片保存完成 [fileId={}, path={}, size={}KB, viewUrl={}, downloadUrl={}]", 
                fileRecord.getFileId(), relativePath, imageBytes.length / 1024, viewUrl, downloadUrl);
        
        return imageInfo;
    }
    
    /**
     * 保存base64图片到文件系统和数据库
     * 参考 FileOperationPlugin.writeFile 的实现
     * 使用与文件操作相同的目录结构
     * 
     * @param base64Data base64编码的图片数据（不包含前缀）
     * @param imageType 图片类型（png, jpeg, gif等）
     * @param sessionId 会话ID
     * @return 图片的访问URL（用于下载）
     * @throws IOException IO异常
     */
    private String saveBase64Image(String base64Data, String imageType, String sessionId) throws IOException {
        log.info("开始保存base64图片 [imageType={}, base64Length={}, sessionId={}]", 
                imageType, base64Data.length(), sessionId);
        
        // 解码base64
        byte[] imageBytes;
        try {
            imageBytes = Base64.getDecoder().decode(base64Data);
            log.info("Base64解码成功 [imageBytes={}]", imageBytes.length);
        } catch (Exception e) {
            log.error("Base64解码失败", e);
            throw new IOException("Base64解码失败: " + e.getMessage());
        }
        
        // 生成UUID文件名，保留扩展名
        String uuidFileName = IdUtils.fastSimpleUUID() + "." + imageType;
        log.info("生成UUID文件名: {}", uuidFileName);
        
        // 从RuntimeContextHolder获取上下文信息（与FileOperationPlugin一致）
        String businessSystem = RuntimeContextHolder.getBusinessSystem();
        String userLoginName = RuntimeContextHolder.getUserLoginName();
        String agentCode = RuntimeContextHolder.getAgentCode();
        
        if (businessSystem == null) businessSystem = "default";
        if (userLoginName == null) userLoginName = "anonymous";
        if (sessionId == null) sessionId = "temp";
        
        log.info("运行时上下文 [uploadPath={}, businessSystem={}, userLoginName={}, agentCode={}, sessionId={}]",
                uploadPath, businessSystem, userLoginName, agentCode, sessionId);
        
        // 使用与FileOperationPlugin相同的目录结构: agent-workspace/{businessSystem}/{userLoginName}/{sessionId}/
        Path sessionDir = Paths.get(uploadPath, "agent-workspace", 
                businessSystem, userLoginName, sessionId);
        
        log.info("会话目录路径: {}", sessionDir.toAbsolutePath());
        
        // 确保目录存在
        File dir = sessionDir.toFile();
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            log.info("创建会话目录 [path={}, created={}]", sessionDir.toAbsolutePath(), created);
            
            if (!created) {
                throw new IOException("无法创建目录: " + sessionDir.toAbsolutePath());
            }
        } else {
            log.info("会话目录已存在: {}", sessionDir.toAbsolutePath());
        }
        
        // 完整的文件路径（直接放在会话目录下，与文件操作一致）
        File imageFile = new File(dir, uuidFileName);
        log.info("图片文件路径: {}", imageFile.getAbsolutePath());
        
        // 写入文件
        try (FileOutputStream fos = new FileOutputStream(imageFile)) {
            fos.write(imageBytes);
            fos.flush();
            log.info("图片文件写入成功 [path={}, size={}]", imageFile.getAbsolutePath(), imageBytes.length);
        } catch (Exception e) {
            log.error("写入图片文件失败 [path={}]", imageFile.getAbsolutePath(), e);
            throw new IOException("写入图片文件失败: " + e.getMessage());
        }
        
        // 验证文件是否存在
        if (!imageFile.exists()) {
            log.error("文件写入后不存在！[path={}]", imageFile.getAbsolutePath());
            throw new IOException("文件写入失败，文件不存在: " + imageFile.getAbsolutePath());
        }
        
        long actualFileSize = imageFile.length();
        log.info("文件验证成功 [path={}, actualSize={}, expectedSize={}]", 
                imageFile.getAbsolutePath(), actualFileSize, imageBytes.length);
        
        // 计算相对路径（相对于uploadPath，与FileOperationPlugin一致）
        Path uploadPathRoot = Paths.get(uploadPath);
        String relativePath = uploadPathRoot.relativize(imageFile.toPath()).toString().replace("\\", "/");
        log.info("计算相对路径: {}", relativePath);
        
        // 记录到数据库（字段与FileOperationPlugin保持一致）
        AiAgentFile fileRecord = new AiAgentFile();
        fileRecord.setFileName("image_" + uuidFileName);  // 原始文件名（标识为图片）
        fileRecord.setFilePath(relativePath);  // 保存相对路径
        fileRecord.setFileSize((long) imageBytes.length);
        fileRecord.setFileType("image");  // 标识为图片类型
        fileRecord.setSessionId(sessionId);
        fileRecord.setAgentCode(agentCode);  // 使用RuntimeContextHolder中的agentCode
        fileRecord.setBusinessSystem(businessSystem);
        fileRecord.setUserLoginName(userLoginName);
        fileRecord.setRemark("Agent生成的图片 (" + imageType + ")");
        
        fileMapper.insertAiAgentFile(fileRecord);
        
        log.info("数据库记录插入成功 [fileId={}, fileName={}, filePath={}, agentCode={}]",
                fileRecord.getFileId(), fileRecord.getFileName(), fileRecord.getFilePath(), agentCode);
        
        // 生成下载链接和查看链接
        // view接口用于图片inline显示，download接口用于文件下载
        String viewUrl = "/agent/api/file/view/" + fileRecord.getFileId();
        String downloadUrl = "/agent/api/file/download/" + fileRecord.getFileId();
        
        log.info("图片保存完成 [fileId={}, path={}, size={}KB, viewUrl={}, downloadUrl={}]", 
                fileRecord.getFileId(), relativePath, imageBytes.length / 1024, viewUrl, downloadUrl);
        
        return viewUrl;  // 返回view链接用于图片显示
    }
    
    /**
     * 清理会话的图片文件（可选，用于会话结束时清理）
     * 
     * @param sessionId 会话ID
     */
    public void cleanSessionImages(String sessionId) {
        try {
            // 从数据库查询该会话的所有图片
            java.util.List<AiAgentFile> images = fileMapper.selectFilesBySessionId(sessionId);
            
            for (AiAgentFile image : images) {
                if ("image".equals(image.getFileType())) {
                    // 删除物理文件
                    Path filePath = Paths.get(uploadPath, image.getFilePath());
                    try {
                        Files.deleteIfExists(filePath);
                    } catch (IOException e) {
                        log.warn("删除图片文件失败 [path={}]", filePath, e);
                    }
                    
                    // 从数据库删除记录
                    fileMapper.deleteAiAgentFileByFileId(image.getFileId());
                }
            }
            
            log.info("清理会话图片完成 [session={}, count={}]", sessionId, images.size());
            
        } catch (Exception e) {
            log.warn("清理会话图片失败 [session={}]", sessionId, e);
        }
    }
}
