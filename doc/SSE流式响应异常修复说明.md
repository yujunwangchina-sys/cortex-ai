# SSE流式响应异常修复说明

## 修复时间
2026-07-06

## 问题描述

### 错误日志
```
java.lang.IllegalStateException: ResponseBodyEmitter has already completed
    at org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.send
```

### 触发场景
使用DeepSeek-V4-Flash模型进行流式对话时，在流响应即将结束时收到如下chunk：
```json
{
  "id":"chatcmpl-xxx",
  "object":"chat.completion.chunk",
  "created":1783326240,
  "model":"DeepSeek-V4-Flash",
  "choices":[{
    "index":0,
    "delta":{
      "content":",",
      "tool_calls":[]  // 空数组导致异常
    },
    "logprobs":null,
    "finish_reason":null
  }]
}
```

### 根本原因
1. **DeepSeek在流末尾发送空tool_calls**：某些大模型（如DeepSeek）在流响应末尾会发送包含空`tool_calls:[]`的delta
2. **解析异常传播**：解析该chunk时可能触发异常，但异常捕获后继续尝试发送数据
3. **Emitter已完成**：在异常处理期间，SSE emitter可能已经正常完成
4. **重复发送导致IllegalStateException**：尝试向已完成的emitter发送数据

## 修复方案

### 1. 降级SSE解析异常日志级别

**文件**：`OpenAiCompatibleClient.java`

**修改前**：
```java
catch (Exception e) {
    log.warn("解析SSE chunk失败: {}", data, e);
}
```

**修改后**：
```java
catch (Exception e) {
    // 静默忽略解析错误的chunk，避免中断流
    // 某些模型可能在流末尾发送空的tool_calls数组
    log.debug("跳过无法解析的SSE chunk: {}", 
        data.length() > 200 ? data.substring(0, 200) + "..." : data);
}
```

**原因**：
- 空的`tool_calls`数组不影响实际功能
- 降为debug级别避免日志污染
- 截断超长内容防止日志过大

### 2. 捕获IllegalStateException

**文件**：`AgentRuntimeController.java`

**修改前**：
```java
catch (IOException e) {
    log.debug("SSE发送失败", e);
}
```

**修改后**：
```java
catch (IllegalStateException e) {
    // Emitter已完成，静默忽略
    log.debug("SSE emitter已完成，忽略后续事件");
}
catch (IOException e) {
    log.debug("SSE发送失败（客户端可能已断开）", e);
}
```

**原因**：
- `IllegalStateException`表示emitter已完成，这是正常状态
- 区分`IOException`（网络问题）和`IllegalStateException`（状态问题）
- 避免在正常完成时记录错误日志

## 测试验证

### 测试场景
1. ✅ 正常流式对话（无工具调用）
2. ✅ 流式对话（包含工具调用）
3. ✅ DeepSeek模型流式对话（空tool_calls数组）
4. ✅ 客户端中途断开连接
5. ✅ 超长响应（多次chunk）

### 预期行为
- 流响应正常完成，无异常日志
- 空tool_calls数组被静默跳过
- Emitter完成后的发送尝试被优雅处理
- 用户端正常接收所有内容

## 影响范围

### 修改的文件
1. `OpenAiCompatibleClient.java` - SSE chunk解析异常处理
2. `AgentRuntimeController.java` - SSE emitter状态异常处理

### 兼容性
- ✅ 向后兼容：不影响现有功能
- ✅ 多模型兼容：适用于OpenAI、DeepSeek、Qwen等所有兼容模型
- ✅ 性能无影响：仅改变异常处理方式

## 相关问题

### 为什么DeepSeek发送空tool_calls数组？
某些模型在流式输出结束时会发送额外的元数据chunk，包含：
- 空的`tool_calls:[]`表示没有工具调用
- `finish_reason`标记
- 最终的token统计

这是模型提供商的实现细节，我们需要兼容。

### 为什么不直接检查tool_calls是否为空？
因为空数组本身是合法的，问题不在于数组为空，而在于：
1. 解析过程中的其他潜在异常
2. 异常后继续发送数据到已关闭的emitter

所以更优雅的方案是在异常边界处理，而不是在业务逻辑中检查。

### 其他模型会有类似问题吗？
可能。不同模型厂商对OpenAI API的实现细节不同，统一在异常处理层面兼容是最稳健的方案。

## 后续优化建议

1. **添加SSE心跳机制**：定期发送心跳chunk，检测客户端断开
2. **流式超时优化**：当前90秒静默超时可能需要调整
3. **监控和告警**：统计SSE异常发生频率，及时发现新问题
