package com.cortex.plugin.builtin.impl;

import com.cortex.agent.domain.AiAgentFile;
import com.cortex.agent.runtime.context.RuntimeContextHolder;
import com.cortex.agent.runtime.file.FileContentParser;
import com.cortex.agent.runtime.file.ImageVisionFallback;
import com.cortex.agent.service.IAiAgentFileService;
import com.cortex.plugin.builtin.IBuiltinPlugin;
import com.cortex.plugin.builtin.PluginInfo;
import com.cortex.plugin.builtin.ToolDefinition;
import com.cortex.plugin.builtin.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 文件管理内置插件
 * Pull 式文件读取: LLM 按需调用工具读取用户上传的文件内容
 *
 * 工具（v2 - 重命名以避免与 FileOperationPlugin 冲突）：
 * 1. uploaded_files_list   - 列出当前会话用户上传的文件
 * 2. read_uploaded_file   - 读取用户上传的文档内容
 * 3. view_uploaded_image - 识别用户上传的图片内容
 *
 * @author cortex
 */
@Component
public class FileManagerPlugin implements IBuiltinPlugin
{
    private static final Logger log = LoggerFactory.getLogger(FileManagerPlugin.class);

    /** 单次读取最大字符数 */
    private static final int MAX_READ_CHARS = 20000;

    @Autowired
    private IAiAgentFileService fileService;

    @Autowired
    private FileContentParser fileParser;

    @Autowired
    private ImageVisionFallback visionFallback;

    @Override
    public PluginInfo getPluginInfo()
    {
        PluginInfo info = new PluginInfo("文件管理", "file-manager", "读取用户上传的文件内容（文档解析、图片识别）");
        info.setVersion("1.0.0");
        info.setAuthor("CORTEX");
        info.setCategory("file");
        info.setEmoji("📎");
        info.setRequireApproval(false);
        return info;
    }

    @Override
    public List<ToolDefinition> getTools()
    {
        List<ToolDefinition> tools = new ArrayList<>();

        // 1. uploaded_files_list - 重命名，明确是用户上传的文件
        ToolDefinition list = new ToolDefinition();
        list.setName("uploaded_files_list");
        list.setDescription("列出当前会话中**用户上传**的所有文件。返回文件ID、文件名、类型和大小。用于查看用户提供了哪些文件。");
        Map<String, Object> listSchema = new HashMap<>();
        listSchema.put("type", "object");
        listSchema.put("properties", new HashMap<>());
        list.setInputSchema(listSchema);
        tools.add(list);

        // 2. read_uploaded_file - 重命名，明确读取用户上传的文档
        ToolDefinition read = new ToolDefinition();
        read.setName("read_uploaded_file");
        read.setDescription(
            "读取**用户上传**的文档文件的文本内容。支持: doc/docx, xls/xlsx, ppt/pptx, pdf, txt, md, csv, json, xml, html。\n" +
            "内容过长会截断（每次最多" + MAX_READ_CHARS + "字符），可用offset参数分段读取后续内容。\n" +
            "参数: fileId（从uploaded_files_list获取）。"
        );
        Map<String, Object> readSchema = new HashMap<>();
        readSchema.put("type", "object");
        Map<String, Object> readProps = new HashMap<>();
        readProps.put("fileId", Map.of("type", "integer", "description", "文件ID（从uploaded_files_list获取）"));
        readProps.put("offset", Map.of("type", "integer", "description", "读取起始字符位置（默认0）。文件超过20000字符时会截断，可用offset分段读取后续内容", "default", 0));
        readSchema.put("properties", readProps);
        readSchema.put("required", List.of("fileId"));
        read.setInputSchema(readSchema);
        tools.add(read);

        // 3. view_uploaded_image - 重命名，明确识别用户上传的图片
        ToolDefinition viewImg = new ToolDefinition();
        viewImg.setName("view_uploaded_image");
        viewImg.setDescription(
            "识别并描述**用户上传**的图片文件的内容。支持: jpg, png, gif, webp, bmp。\n" +
            "工具会自动调用视觉模型识别图片，返回文字描述（物体、文字、场景等）。\n" +
            "参数: fileId（从uploaded_files_list获取）。"
        );
        Map<String, Object> viewSchema = new HashMap<>();
        viewSchema.put("type", "object");
        Map<String, Object> viewProps = new HashMap<>();
        viewProps.put("fileId", Map.of("type", "integer", "description", "图片文件ID（从uploaded_files_list获取）"));
        viewSchema.put("properties", viewProps);
        viewSchema.put("required", List.of("fileId"));
        viewImg.setInputSchema(viewSchema);
        tools.add(viewImg);

        return tools;
    }

    @Override
    public String executeTool(String toolName, Map<String, Object> arguments)
    {
        try
        {
            switch (toolName)
            {
                // 新名称
                case "uploaded_files_list": return listFiles();
                case "read_uploaded_file": return readFile(arguments);
                case "view_uploaded_image": return viewImage(arguments);
                // 兼容旧名称（向后兼容）
                case "file_list": return listFiles();
                case "file_read": return readFile(arguments);
                case "file_view_image": return viewImage(arguments);
                default:
                    return ToolResult.error("未知工具: " + toolName).toJson();
            }
        }
        catch (Exception e)
        {
            log.error("文件管理工具执行失败 [tool={}]", toolName, e);
            return ToolResult.error("执行失败: " + e.getMessage()).toJson();
        }
    }

    /**
     * 列出当前会话的文件
     */
    private String listFiles()
    {
        String sessionId = RuntimeContextHolder.getSessionId();
        if (sessionId == null || sessionId.isEmpty())
        {
            return ToolResult.error("无法获取会话信息").toJson();
        }

        List<AiAgentFile> files = fileService.selectFilesBySessionId(sessionId);
        if (files == null || files.isEmpty())
        {
            return ToolResult.success("当前会话没有上传文件").toJson();
        }

        ToolResult result = ToolResult.success("共 " + files.size() + " 个文件");
        List<Map<String, Object>> fileList = new ArrayList<>();
        for (AiAgentFile f : files)
        {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("fileId", f.getFileId());
            item.put("fileName", f.getFileName());
            item.put("fileSize", f.getFileSize());
            item.put("isImage", fileParser.isImage(f.getFileName()));
            item.put("category", categorize(f.getFileName()));
            fileList.add(item);
        }
        result.addData("files", fileList);
        return result.toJson();
    }

    /**
     * 读取文档文件
     */
    private String readFile(Map<String, Object> args)
    {
        Object fileIdObj = args.get("fileId");
        if (fileIdObj == null)
        {
            return ToolResult.error("缺少参数: fileId").toJson();
        }
        Long fileId = toLong(fileIdObj);

        int offset = 0;
        Object offsetObj = args.get("offset");
        if (offsetObj != null)
        {
            try { offset = Integer.parseInt(offsetObj.toString()); } catch (Exception e) { offset = 0; }
            if (offset < 0) offset = 0;
        }

        AiAgentFile file = fileService.selectAiAgentFileByFileId(fileId);
        if (file == null)
        {
            return ToolResult.error("文件不存在 (fileId=" + fileId + ")").toJson();
        }

        if (fileParser.isImage(file.getFileName()))
        {
            return ToolResult.error(
                "该文件是图片，请使用 view_uploaded_image 工具查看图片内容").toJson();
        }

        if (!fileParser.isSupported(file.getFileName()))
        {
            return ToolResult.error("不支持的文件格式: " + file.getFileName()).toJson();
        }

        // 解析全文，然后按offset截取
        FileContentParser.ParsedFile parsed = fileParser.parse(
                file.getFilePath(), file.getFileName(), Integer.MAX_VALUE);
        String fullContent = parsed.getContent();
        int totalLength = fullContent != null ? fullContent.length() : 0;

        String readContent;
        boolean hasMore = false;
        if (fullContent != null && offset < totalLength)
        {
            int end = Math.min(offset + MAX_READ_CHARS, totalLength);
            readContent = fullContent.substring(offset, end);
            hasMore = end < totalLength;
        }
        else
        {
            readContent = "";
        }

        ToolResult result = ToolResult.success("文件读取成功");
        result.addData("fileName", file.getFileName());
        result.addData("content", readContent);
        result.addData("offset", offset);
        result.addData("readLength", readContent.length());
        result.addData("totalLength", totalLength);
        result.addData("hasMore", hasMore);
        if (hasMore)
        {
            result.addData("nextOffset", offset + MAX_READ_CHARS);
        }
        return result.toJson();
    }

    /**
     * 识别图片内容（自动降级到视觉模型）
     */
    private String viewImage(Map<String, Object> args)
    {
        Object fileIdObj = args.get("fileId");
        if (fileIdObj == null)
        {
            return ToolResult.error("缺少参数: fileId").toJson();
        }
        Long fileId = toLong(fileIdObj);

        AiAgentFile file = fileService.selectAiAgentFileByFileId(fileId);
        if (file == null)
        {
            return ToolResult.error("文件不存在 (fileId=" + fileId + ")").toJson();
        }

        if (!fileParser.isImage(file.getFileName()))
        {
            return ToolResult.error(
                "该文件不是图片，请使用 read_uploaded_file 工具读取文档内容").toJson();
        }

        // 调用视觉模型识别图片
        String description = visionFallback.describeImage(file.getFilePath(), file.getFileName());

        if (description == null || description.isBlank())
        {
            return ToolResult.error("图片识别失败：未知错误").toJson();
        }
        if (description.startsWith("ERROR: "))
        {
            return ToolResult.error("图片识别失败：" + description.substring(7)).toJson();
        }

        ToolResult result = ToolResult.success("图片识别完成");
        result.addData("fileName", file.getFileName());
        result.addData("description", description);
        return result.toJson();
    }

    /**
     * 文件分类
     */
    private String categorize(String fileName)
    {
        if (fileParser.isImage(fileName)) return "image";
        String ext = "";
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) ext = fileName.substring(dot).toLowerCase();
        if (ext.matches("\\.(doc|docx)")) return "word";
        if (ext.matches("\\.(xls|xlsx)")) return "excel";
        if (ext.matches("\\.(ppt|pptx)")) return "ppt";
        if (ext.equals(".pdf")) return "pdf";
        return "text";
    }

    private Long toLong(Object obj)
    {
        if (obj instanceof Number) return ((Number) obj).longValue();
        return Long.parseLong(obj.toString());
    }
}