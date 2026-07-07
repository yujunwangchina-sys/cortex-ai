package com.ruoyi.agent.runtime.file;

import com.ruoyi.agent.runtime.llm.LlmRequest;
import com.ruoyi.agent.runtime.llm.LlmResponse;
import com.ruoyi.agent.runtime.llm.OpenAiCompatibleClient;
import com.ruoyi.agent.runtime.model.ChatMessage;
import com.ruoyi.agent.runtime.prompt.AgentConfigLoader;
import com.ruoyi.supplier.domain.AiModel;
import com.ruoyi.supplier.domain.AiSupplier;
import com.ruoyi.supplier.mapper.AiModelMapper;
import com.ruoyi.supplier.mapper.AiSupplierMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 图片视觉降级服务
 * 图片识别工具: 调用全模态(multimodal)模型描述图片内容
 *
 * @author ruoyi
 */
@Component
public class ImageVisionFallback
{
    private static final Logger log = LoggerFactory.getLogger(ImageVisionFallback.class);

    private static final String VISION_DESC_PROMPT =
            "请详细描述这张图片的内容。包括: 图片类型、主要物体/人物、文字内容(如有)、颜色、场景等关键信息。" +
            "用简洁的中文回答, 200字以内。";

    @Autowired
    private OpenAiCompatibleClient llmClient;

    @Autowired
    private AiModelMapper modelMapper;

    @Autowired
    private AiSupplierMapper supplierMapper;

    @Value("${ruoyi.profile:D:/ruoyi/uploadPath}")
    private String uploadPath;

    /**
     * 检查模型是否支持图像识别
     */
    public boolean supportsVision(AiModel model)
    {
        if (model == null || model.getModelType() == null)
        {
            return false;
        }
        String type = model.getModelType().toLowerCase();
        return "multimodal".equals(type) || "vision".equals(type);
    }

    /**
     * 对图片进行降级识别
     * 调用全模态(multimodal)模型识别图片, 返回文字描述
     *
     * @param imageFilePath  图片相对路径 (相对于 uploadPath)
     * @param imageFileName  图片文件名
     * @return 图片描述文本, 失败返回 null
     */
    public String describeImage(String imageFilePath, String imageFileName)
    {
        try
        {
            // 1. 查找可用的全模态(multimodal)模型
            AgentConfigLoader.ModelSelection visionModel = findVisionModel();
            if (visionModel == null)
            {
                log.warn("未找到可用的图像识别模型, 无法降级处理图片 [file={}]", imageFileName);
                return "ERROR: 未找到视觉模型(modelType=multimodal或vision)，请在供应商管理中配置";
            }

            log.info("图片降级识别 [file={}, visionModel={}]",
                    imageFileName, visionModel.model.getModelCode());

            // 2. 读取图片并转为 base64
            String base64Url = encodeImageToBase64(imageFilePath, imageFileName);
            if (base64Url == null)
            {
                return "ERROR: 图片文件读取失败 [file=" + imageFileName + "]";
            }

            // 3. 构建请求
            List<String> imageUrls = new ArrayList<>();
            imageUrls.add(base64Url);

            List<ChatMessage> messages = new ArrayList<>();
            messages.add(ChatMessage.userWithImages(VISION_DESC_PROMPT, imageUrls));

            LlmRequest request = new LlmRequest();
            request.setBaseUrl(visionModel.supplier.getApiBaseUrl());
            request.setApiKey(visionModel.supplier.getApiKey());
            request.setModel(visionModel.model.getModelCode());
            request.setMessages(messages);
            request.setStream(false);
            if (visionModel.model.getTemperature() != null)
            {
                request.setTemperature(visionModel.model.getTemperature());
            }
            if (visionModel.model.getMaxTokens() != null)
            {
                request.setMaxTokens(Math.min(visionModel.model.getMaxTokens(), 500));
            }

            // 60s timeout for vision API call
            request.setTimeoutSeconds(60);

            // 4. API call
            log.info("[Vision] Calling vision model [model={}, url={}, timeout=60s]",
                    visionModel.model.getModelCode(), visionModel.supplier.getApiBaseUrl());
            long startTime = System.currentTimeMillis();
            LlmResponse response = llmClient.chatCompletion(request);
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("[Vision] Vision model responded in {}ms", elapsed);
            String description = response.getContent();

            if (description != null && !description.isBlank())
            {
                log.info("图片降级识别完成 [file={}, descLen={}]", imageFileName, description.length());
                return description;
            }
            return "ERROR: 视觉模型返回空结果";
        }
        catch (Exception e)
        {
            log.error("图片降级识别失败 [file={}]", imageFileName, e);
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * 查找可用的全模态(multimodal)模型用于图像识别
     */
    private AgentConfigLoader.ModelSelection findVisionModel()
    {
        // 先找 multimodal，再找 vision
        AgentConfigLoader.ModelSelection ms = findModelByType("multimodal");
        if (ms == null)
        {
            ms = findModelByType("vision");
        }
        return ms;
    }

    private AgentConfigLoader.ModelSelection findModelByType(String modelType)
    {
        try
        {
            AiModel query = new AiModel();
            query.setModelType(modelType);
            query.setStatus("0");
            List<AiModel> models = modelMapper.selectAiModelList(query);
            if (models != null && !models.isEmpty())
            {
                AiModel model = models.get(0);
                AiSupplier supplier = supplierMapper.selectAiSupplierBySupplierId(model.getSupplierId());
                if (supplier != null && "0".equals(supplier.getStatus()))
                {
                    return new AgentConfigLoader.ModelSelection(supplier, model);
                }
            }
        }
        catch (Exception e)
        {
            log.warn("查找模型失败 [type={}]", modelType, e);
        }
        return null;
    }

    /**
     * 压缩图片并编码为 base64 data URL。
     * 大图片(>500KB)自动缩放至 1280px 以内并转 JPEG，大幅减小请求体积，加速 API 调用。
     * 小图片直接原样返回。
     */
    public String compressAndEncodeImage(String filePath, String fileName) throws Exception
    {
        Path absolutePath = Paths.get(uploadPath, filePath);
        if (!Files.exists(absolutePath))
        {
            log.error("图片文件不存在 [path={}]", absolutePath);
            return null;
        }

        byte[] imageBytes = Files.readAllBytes(absolutePath);

        // 小图片直接使用
        if (imageBytes.length < 200_000)
        {
            String base64 = Base64.getEncoder().encodeToString(imageBytes);
            return "data:" + getMimeType(fileName) + ";base64," + base64;
        }

        // 大图片: 缩放 + JPEG 压缩
        try
        {
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(absolutePath.toFile());
            if (img == null)
            {
                // ImageIO 无法读取(如 SVG), 原样返回
                String base64 = Base64.getEncoder().encodeToString(imageBytes);
                return "data:" + getMimeType(fileName) + ";base64," + base64;
            }

            int maxDim = 1280;
            int w = img.getWidth();
            int h = img.getHeight();
            if (w > maxDim || h > maxDim)
            {
                double scale = (double) maxDim / Math.max(w, h);
                int newW = (int) (w * scale);
                int newH = (int) (h * scale);
                java.awt.image.BufferedImage resized = new java.awt.image.BufferedImage(
                        newW, newH, java.awt.image.BufferedImage.TYPE_INT_RGB);
                java.awt.Graphics2D g = resized.createGraphics();
                g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                        java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(img, 0, 0, newW, newH, null);
                g.dispose();
                img = resized;
            }

            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "jpg", baos);
            byte[] compressed = baos.toByteArray();

            log.info("图片压缩 [file={}, {}KB -> {}KB]",
                    fileName, imageBytes.length / 1024, compressed.length / 1024);
            String base64 = Base64.getEncoder().encodeToString(compressed);
            return "data:image/jpeg;base64," + base64;
        }
        catch (Exception e)
        {
            log.warn("图片压缩失败, 使用原图 [file={}]", fileName, e);
            String base64 = Base64.getEncoder().encodeToString(imageBytes);
            return "data:" + getMimeType(fileName) + ";base64," + base64;
        }
    }

    private String encodeImageToBase64(String filePath, String fileName) throws Exception
    {
        return compressAndEncodeImage(filePath, fileName);
    }

    private String getMimeType(String fileName)
    {
        String ext = "";
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0)
        {
            ext = fileName.substring(lastDot).toLowerCase();
        }
        switch (ext)
        {
            case ".png": return "image/png";
            case ".jpg":
            case ".jpeg": return "image/jpeg";
            case ".gif": return "image/gif";
            case ".webp": return "image/webp";
            case ".bmp": return "image/bmp";
            case ".svg": return "image/svg+xml";
            default: return "image/jpeg";
        }
    }
}
