# MCP内容类型支持说明

## 概述

MCP（Model Context Protocol）工具可以返回多种类型的内容，包括文本、图片和资源引用。系统已实现自动解析和渲染这些内容。

## MCP返回格式

根据MCP协议规范，工具调用返回格式为：

```json
{
  "content": [
    {
      "type": "text",
      "text": "这是文本内容"
    },
    {
      "type": "image",
      "data": "base64编码的图片数据",
      "mimeType": "image/png"
    },
    {
      "type": "resource",
      "resource": {
        "uri": "file:///path/to/resource",
        "mimeType": "application/json"
      }
    }
  ]
}
```

## 支持的内容类型

### 1. 文本内容 (text)

最常见的返回类型，直接显示文本。

**MCP返回格式：**
```json
{
  "type": "text",
  "text": "执行成功，返回结果：..."
}
```

**前端渲染：**
- 作为markdown文本渲染
- 支持markdown语法（加粗、斜体、链接等）

### 2. 图片内容 (image)

用于返回生成的图表、截图等图片数据。

**MCP返回格式：**
```json
{
  "type": "image",
  "data": "iVBORw0KGgoAAAANSUhEUgAA...",  // base64编码
  "mimeType": "image/png"  // 可选，默认image/png
}
```

**前端渲染：**
- 自动转换为markdown图片语法：`![Generated Image](data:image/png;base64,...)`
- 图片会内嵌显示在聊天界面
- 支持的图片格式：
  - image/png
  - image/jpeg
  - image/gif
  - image/webp

**示例工具：**
- `mcp-echarts` - 生成ECharts图表的PNG图片
- `mcp-screenshot` - 网页截图

### 3. 资源引用 (resource)

用于引用文件、数据库记录等资源。

**MCP返回格式：**
```json
{
  "type": "resource",
  "resource": {
    "uri": "file:///path/to/file.json",
    "name": "配置文件",
    "mimeType": "application/json",
    "text": "{\"key\": \"value\"}"
  }
}
```

**前端渲染：**
- 转换为JSON代码块
- 语法高亮显示

## 实现细节

### 后端处理 (ToolDispatcher.java)

```java
/**
 * 解析MCP工具调用结果
 * 将MCP协议的content数组转换为前端可以渲染的格式
 */
private String parseMcpResult(JSONObject result) {
    // 1. 获取content数组
    Object contentObj = result.get("content");
    
    // 2. 遍历每个content项
    for (Object item : contentList) {
        Map<String, Object> contentItem = (Map<String, Object>) item;
        String type = (String) contentItem.get("type");
        
        if ("text".equals(type)) {
            // 提取文本
            String text = (String) contentItem.get("text");
            markdown.append(text).append("\n\n");
        } else if ("image".equals(type)) {
            // 转换图片为data URI
            String data = (String) contentItem.get("data");
            String mimeType = (String) contentItem.get("mimeType");
            markdown.append("![Generated Image](data:")
                    .append(mimeType)
                    .append(";base64,")
                    .append(data)
                    .append(")\n\n");
        } else if ("resource".equals(type)) {
            // 格式化资源为JSON
            Object resource = contentItem.get("resource");
            markdown.append("```json\n")
                    .append(JSON.toJSONString(resource, true))
                    .append("\n```\n\n");
        }
    }
    
    return markdown.toString().trim();
}
```

### 前端渲染 (ChatMessageItem.vue)

前端使用`markdown-it`渲染markdown内容，自动支持：
- 文本格式化
- 代码高亮（通过highlight.js）
- **图片渲染**（包括data URI格式的base64图片）
- 表格、列表等

```vue
<!-- markdown内容渲染 -->
<div v-if="message.role === 'assistant'" 
     class="msg-text markdown-content" 
     v-html="renderedMarkdown">
</div>
```

```css
/* 图片样式 */
.markdown-content :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: 8px;
  margin: 0;
}
```

## ECharts插件示例

### 使用方式

```javascript
// Agent调用
{
  "tool": "generate_echarts",
  "arguments": {
    "echartsOption": {
      "title": { "text": "销售数据" },
      "xAxis": { "type": "category", "data": ["一月", "二月", "三月"] },
      "yAxis": { "type": "value" },
      "series": [{
        "type": "bar",
        "data": [120, 200, 150]
      }]
    }
  }
}
```

### 返回格式

```json
{
  "content": [
    {
      "type": "text",
      "text": "已生成ECharts图表"
    },
    {
      "type": "image",
      "data": "iVBORw0KGgoAAAANSUhEUgAAB...",
      "mimeType": "image/png"
    }
  ]
}
```

### 前端展示

1. 文本：显示"已生成ECharts图表"
2. 图片：自动嵌入显示PNG图表
3. 用户可以右键保存图片

## 注意事项

### 1. Base64数据大小

- 图片数据使用base64编码会增大约33%
- 建议图片不要超过2MB
- 对于大图，建议使用文件上传+URL引用方式

### 2. 图片质量

- PNG格式：无损压缩，文件较大，适合图表
- JPEG格式：有损压缩，文件较小，适合照片
- 建议ECharts图表使用PNG格式

### 3. mimeType

如果MCP返回的图片没有指定mimeType，系统默认为`image/png`。建议MCP服务器明确指定：

```json
{
  "type": "image",
  "data": "...",
  "mimeType": "image/jpeg"  // 明确指定
}
```

### 4. 兼容性

- 所有现代浏览器都支持data URI格式的图片
- markdown-it自动处理图片alt和title属性
- 移动端浏览器完全支持

## 扩展支持

### 未来可支持的内容类型

1. **音频** (audio)
```json
{
  "type": "audio",
  "data": "base64音频数据",
  "mimeType": "audio/mp3"
}
```

2. **视频** (video)
```json
{
  "type": "video",
  "data": "base64视频数据",
  "mimeType": "video/mp4"
}
```

3. **文件下载** (file)
```json
{
  "type": "file",
  "name": "report.pdf",
  "data": "base64文件数据",
  "mimeType": "application/pdf"
}
```

## 调试

### 查看原始MCP返回

在日志中搜索：
```
MCP工具调用结果 [plugin=mcp-echarts, tool=generate_echarts, result=...]
```

### 查看解析后的markdown

在日志中搜索：
```
✅ 工具执行成功 [plugin=mcp-echarts, tool=generate_echarts, resultLength=...]
```

### 前端调试

在浏览器开发者工具Console中：
```javascript
// 查看消息内容
console.log(message.content)

// 检查是否包含base64图片
message.content.includes('data:image')
```

## 相关文件

- 后端解析：`ToolDispatcher.java` - `parseMcpResult()`方法
- 前端渲染：`ChatMessageItem.vue` - markdown-it配置
- 样式定义：`ChatMessageItem.vue` - `.markdown-content :deep(img)`
- MCP客户端：`McpClient.java` - `callTool()`方法

## 参考资料

- [MCP Protocol Specification](https://modelcontextprotocol.io/specification)
- [Markdown-it Documentation](https://markdown-it.github.io/)
- [Data URI Scheme](https://developer.mozilla.org/en-US/docs/Web/HTTP/Basics_of_HTTP/Data_URIs)
- [ECharts Documentation](https://echarts.apache.org/)
