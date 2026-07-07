package com.ruoyi.agent.runtime.model;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import java.util.List;

/**
 * Chat message model - OpenAI compatible format.
 * Supports multimodal content (text + image_url).
 */
public class ChatMessage
{
    private String role;
    private String content;
    private String toolCallId;
    private List<ToolCall> toolCalls;
    private String name;

    /** Multimodal image URLs (for vision/multimodal models) */
    private List<String> imageUrls;
    
    /** Attached files (for user messages with file uploads) */
    private List<FileAttachment> files;

    public ChatMessage() {}

    public static ChatMessage system(String content)
    {
        ChatMessage m = new ChatMessage();
        m.role = "system";
        m.content = content;
        return m;
    }

    public static ChatMessage user(String content)
    {
        ChatMessage m = new ChatMessage();
        m.role = "user";
        m.content = content;
        return m;
    }

    /** Multimodal user message with text + images */
    public static ChatMessage userWithImages(String content, List<String> imageUrls)
    {
        ChatMessage m = new ChatMessage();
        m.role = "user";
        m.content = content;
        m.imageUrls = imageUrls;
        return m;
    }

    public static ChatMessage assistant(String content)
    {
        ChatMessage m = new ChatMessage();
        m.role = "assistant";
        m.content = content;
        return m;
    }

    public static ChatMessage assistantWithTools(String content, List<ToolCall> toolCalls)
    {
        ChatMessage m = new ChatMessage();
        m.role = "assistant";
        m.content = content;
        m.toolCalls = toolCalls;
        return m;
    }

    public static ChatMessage tool(String toolCallId, String content)
    {
        ChatMessage m = new ChatMessage();
        m.role = "tool";
        m.toolCallId = toolCallId;
        m.content = content;
        return m;
    }

    /**
     * Convert to OpenAI API JSON format.
     * When imageUrls is present, content becomes an array of content parts.
     */
    public JSONObject toJson()
    {
        JSONObject obj = new JSONObject();
        obj.put("role", role);

        if (imageUrls != null && !imageUrls.isEmpty() && "user".equals(role))
        {
            // Multimodal content: array of {type: "text"/"image_url"}
            JSONArray parts = new JSONArray();
            if (content != null && !content.isEmpty())
            {
                JSONObject textPart = new JSONObject();
                textPart.put("type", "text");
                textPart.put("text", content);
                parts.add(textPart);
            }
            for (String url : imageUrls)
            {
                JSONObject imgPart = new JSONObject();
                imgPart.put("type", "image_url");
                JSONObject urlObj = new JSONObject();
                urlObj.put("url", url);
                imgPart.put("image_url", urlObj);
                parts.add(imgPart);
            }
            obj.put("content", parts);
        }
        else if (content != null)
        {
            obj.put("content", content);
        }

        if (toolCallId != null)
        {
            obj.put("tool_call_id", toolCallId);
        }
        if (name != null)
        {
            obj.put("name", name);
        }
        if (toolCalls != null && !toolCalls.isEmpty())
        {
            JSONArray arr = new JSONArray();
            for (ToolCall tc : toolCalls)
            {
                arr.add(tc.toJson());
            }
            obj.put("tool_calls", arr);
        }
        
        // Include files for user messages
        if (files != null && !files.isEmpty())
        {
            JSONArray arr = new JSONArray();
            for (FileAttachment f : files)
            {
                JSONObject fileObj = new JSONObject();
                fileObj.put("fileId", f.getFileId());
                fileObj.put("fileName", f.getFileName());
                arr.add(fileObj);
            }
            obj.put("files", arr);
        }
        
        return obj;
    }

    @SuppressWarnings("unchecked")
    public static ChatMessage fromJson(JSONObject obj)
    {
        ChatMessage m = new ChatMessage();
        m.role = obj.getString("role");
        m.content = obj.getString("content");
        m.toolCallId = obj.getString("tool_call_id");
        m.name = obj.getString("name");

        // Parse multimodal content array
        Object contentObj = obj.get("content");
        if (contentObj instanceof JSONArray)
        {
            JSONArray parts = (JSONArray) contentObj;
            StringBuilder textBuilder = new StringBuilder();
            java.util.List<String> imgs = new java.util.ArrayList<>();
            for (Object part : parts)
            {
                if (part instanceof JSONObject)
                {
                    JSONObject p = (JSONObject) part;
                    String type = p.getString("type");
                    if ("text".equals(type))
                    {
                        textBuilder.append(p.getString("text"));
                    }
                    else if ("image_url".equals(type))
                    {
                        JSONObject iu = p.getJSONObject("image_url");
                        if (iu != null) imgs.add(iu.getString("url"));
                    }
                }
            }
            m.content = textBuilder.toString();
            if (!imgs.isEmpty()) m.imageUrls = imgs;
        }

        if (obj.containsKey("tool_calls"))
        {
            JSONArray arr = obj.getJSONArray("tool_calls");
            m.toolCalls = arr.stream()
                .map(item -> ToolCall.fromJson((JSONObject) item))
                .collect(java.util.stream.Collectors.toList());
        }
        
        // Parse files array
        if (obj.containsKey("files"))
        {
            JSONArray arr = obj.getJSONArray("files");
            m.files = arr.stream()
                .map(item -> {
                    JSONObject fileObj = (JSONObject) item;
                    FileAttachment f = new FileAttachment();
                    f.setFileId(fileObj.getLong("fileId"));
                    f.setFileName(fileObj.getString("fileName"));
                    return f;
                })
                .collect(java.util.stream.Collectors.toList());
        }
        
        return m;
    }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getToolCallId() { return toolCallId; }
    public void setToolCallId(String toolCallId) { this.toolCallId = toolCallId; }
    public List<ToolCall> getToolCalls() { return toolCalls; }
    public void setToolCalls(List<ToolCall> toolCalls) { this.toolCalls = toolCalls; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<String> getImageUrls() { return imageUrls; }
    public void setImageUrls(List<String> imageUrls) { this.imageUrls = imageUrls; }
    public boolean hasImages() { return imageUrls != null && !imageUrls.isEmpty(); }
    public List<FileAttachment> getFiles() { return files; }
    public void setFiles(List<FileAttachment> files) { this.files = files; }

    public static class ToolCall
    {
        private String id;
        private String type;
        private FunctionCall function;

        public ToolCall() { this.type = "function"; }

        public static ToolCall of(String id, String name, String arguments)
        {
            ToolCall tc = new ToolCall();
            tc.id = id;
            tc.type = "function";
            tc.function = new FunctionCall();
            tc.function.name = name;
            tc.function.arguments = arguments;
            return tc;
        }

        public JSONObject toJson()
        {
            JSONObject obj = new JSONObject();
            obj.put("id", id);
            obj.put("type", type);
            if (function != null)
            {
                JSONObject fn = new JSONObject();
                fn.put("name", function.name);
                fn.put("arguments", function.arguments != null ? function.arguments : "{}");
                obj.put("function", fn);
            }
            return obj;
        }

        public static ToolCall fromJson(JSONObject obj)
        {
            ToolCall tc = new ToolCall();
            tc.id = obj.getString("id");
            tc.type = obj.getString("type");
            if (obj.containsKey("function"))
            {
                JSONObject fn = obj.getJSONObject("function");
                tc.function = new FunctionCall();
                tc.function.name = fn.getString("name");
                tc.function.arguments = fn.getString("arguments");
            }
            return tc;
        }

        public String getId() { return id; }
        public String getName() { return function != null ? function.name : null; }
        public String getArguments() { return function != null ? function.arguments : null; }
    }

    public static class FunctionCall
    {
        public String name;
        public String arguments;
    }
    
    /**
     * File attachment model (for user messages with uploads)
     */
    public static class FileAttachment
    {
        private Long fileId;
        private String fileName;
        
        public Long getFileId() { return fileId; }
        public void setFileId(Long fileId) { this.fileId = fileId; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
    }
}