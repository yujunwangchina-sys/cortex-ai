package com.cortex.agent.runtime.llm;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.cortex.agent.runtime.model.ChatMessage;
import com.cortex.agent.runtime.model.SSEEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
/**
 * OpenAI 兼容接口客户端
 * 支持 chat completion + tool calling + SSE 流式输出
 *
 * @author cortex
 */
@Component
public class OpenAiCompatibleClient
{
    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleClient.class);

    private static final long STALE_STREAM_TIMEOUT_MS = 90_000; // 90s no delta = dead stream
    private final HttpClient httpClient;
    @Autowired
    @Qualifier("agentWatchdogExecutor")
    private ThreadPoolTaskExecutor watchdogExecutor;

    public OpenAiCompatibleClient()
    {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * 非流式调用
     */
    public LlmResponse chatCompletion(LlmRequest request) throws Exception
    {
        JSONObject body = buildRequestBody(request, false);

        String url = normalizeUrl(request.getBaseUrl()) + "/chat/completions";
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + request.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString()))
                .timeout(Duration.ofSeconds(request.getTimeoutSeconds() != null ? request.getTimeoutSeconds() : 120))
                .build();

        log.debug("LLM请求 [url={}, model={}]", url, request.getModel());

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200)
        {
            String errorBody = response.body();
            log.error("LLM调用失败 [status={}, body={}]", response.statusCode(),
                    errorBody != null ? errorBody.substring(0, Math.min(500, errorBody.length())) : "null");
            throw new LlmException(response.statusCode(), errorBody);
        }

        return parseResponse(JSON.parseObject(response.body()));
    }

    /**
     * SSE 流式调用
     * @param deltaCallback 每收到一个 content delta 时回调
     */
    public LlmResponse chatCompletionStream(LlmRequest request, Consumer<String> deltaCallback) throws Exception
    {
        JSONObject body = buildRequestBody(request, true);

        String url = normalizeUrl(request.getBaseUrl()) + "/chat/completions";
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + request.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString()))
                .timeout(Duration.ofSeconds(180))
                .build();

        log.debug("LLM流式请求 [url={}, model={}]", url, request.getModel());

        HttpResponse<java.util.stream.Stream<String>> response = httpClient.send(
                httpRequest, HttpResponse.BodyHandlers.ofLines());

        if (response.statusCode() != 200)
        {
            StringBuilder errorBody = new StringBuilder();
            response.body().forEach(line -> errorBody.append(line));
            log.error("LLM流式调用失败 [status={}, body={}]", response.statusCode(),
                    errorBody.substring(0, Math.min(500, errorBody.length())));
            throw new LlmException(response.statusCode(), errorBody.toString());
        }

        return parseStreamResponse(response.body(), deltaCallback);
    }

    /**
     * 构建请求体
     */
    private JSONObject buildRequestBody(LlmRequest request, boolean stream)
    {
        JSONObject body = new JSONObject();
        body.put("model", request.getModel());
        body.put("stream", stream);

        // messages
        JSONArray messagesArr = new JSONArray();
        for (ChatMessage msg : request.getMessages())
        {
            messagesArr.add(msg.toJson());
        }
        body.put("messages", messagesArr);

        // tools
        if (request.getTools() != null && !request.getTools().isEmpty())
        {
            JSONArray toolsArr = new JSONArray();
            for (String toolJson : request.getTools())
            {
                toolsArr.add(JSON.parseObject(toolJson));
            }
            body.put("tools", toolsArr);
        }

        // 推理参数
        if (request.getTemperature() != null)
        {
            body.put("temperature", request.getTemperature());
        }
        if (request.getMaxTokens() != null && request.getMaxTokens() > 0)
        {
            body.put("max_tokens", request.getMaxTokens());
        }
        if (request.getTopP() != null)
        {
            body.put("top_p", request.getTopP());
        }

        return body;
    }

    /**
     * 解析非流式响应
     */
    private LlmResponse parseResponse(JSONObject body)
    {
        LlmResponse resp = new LlmResponse();

        JSONArray choices = body.getJSONArray("choices");
        if (choices != null && !choices.isEmpty())
        {
            JSONObject choice = choices.getJSONObject(0);
            JSONObject message = choice.getJSONObject("message");
            resp.setContent(message.getString("content"));
            resp.setFinishReason(choice.getString("finish_reason"));

            // 解析 tool_calls
            if (message.containsKey("tool_calls"))
            {
                JSONArray toolCallsArr = message.getJSONArray("tool_calls");
                List<ChatMessage.ToolCall> toolCalls = new ArrayList<>();
                for (Object item : toolCallsArr)
                {
                    toolCalls.add(ChatMessage.ToolCall.fromJson((JSONObject) item));
                }
                resp.setToolCalls(toolCalls);
            }
        }

        // 解析 usage
        JSONObject usage = body.getJSONObject("usage");
        if (usage != null)
        {
            resp.setPromptTokens(usage.getIntValue("prompt_tokens", 0));
            resp.setCompletionTokens(usage.getIntValue("completion_tokens", 0));
            resp.setTotalTokens(usage.getIntValue("total_tokens", 0));
        }

        return resp;
    }

    /**
     * 解析 SSE 流式响应
     */
    private LlmResponse parseStreamResponse(java.util.stream.Stream<String> lines, Consumer<String> deltaCallback) throws Exception
    {
        LlmResponse resp = new LlmResponse();
        StringBuilder contentBuilder = new StringBuilder();
        List<ChatMessage.ToolCall> toolCalls = new ArrayList<>();
        // 用于累积流式 tool_call 片段
        java.util.Map<Integer, String> tcIds = new java.util.TreeMap<>();
        java.util.Map<Integer, String> tcNames = new java.util.TreeMap<>();
        java.util.Map<Integer, StringBuilder> tcArgs = new java.util.TreeMap<>();
        
        // Token统计
        int[] promptTokens = {0};
        int[] completionTokens = {0};
        String[] finishReason = {null};

        long[] lastDeltaTime = { System.currentTimeMillis() };
        boolean[] streamDied = { false };

        // External watchdog thread: catches truly dead streams where forEach blocks
        // (the in-lambda check only fires when data IS flowing)
        java.util.concurrent.Future<?> watchdogFuture = watchdogExecutor.submit(() -> {
            while (!streamDied[0]) {
                try { Thread.sleep(10000); } catch (InterruptedException e) { return; }
                long elapsed = System.currentTimeMillis() - lastDeltaTime[0];
                if (elapsed > STALE_STREAM_TIMEOUT_MS) {
                    log.warn("SSE stream stale (no data for {}ms), external watchdog aborting", elapsed);
                    streamDied[0] = true;
                    return;
                }
            }
        });

        lines.forEach(line -> {
            if (line == null || line.isEmpty()) return;
            if (!line.startsWith("data: ")) return;

            String data = line.substring(6).trim();
            if ("[DONE]".equals(data)) return;

            try
            {
                JSONObject chunk = JSON.parseObject(data);
                JSONArray choices = chunk.getJSONArray("choices");
                if (choices == null || choices.isEmpty()) return;

                JSONObject choice = choices.getJSONObject(0);
                JSONObject delta = choice.getJSONObject("delta");
                
                // 提取finish_reason
                String fr = choice.getString("finish_reason");
                if (fr != null && !fr.isEmpty() && !"null".equals(fr))
                {
                    finishReason[0] = fr;
                }
                
                // 提取usage（某些提供商在最后一个chunk中返回）
                if (chunk.containsKey("usage"))
                {
                    JSONObject usage = chunk.getJSONObject("usage");
                    if (usage != null)
                    {
                        promptTokens[0] = usage.getIntValue("prompt_tokens", 0);
                        completionTokens[0] = usage.getIntValue("completion_tokens", 0);
                    }
                }

                if (delta != null)
                {
                    // 内容增量
                // Stale stream watchdog
                long now = System.currentTimeMillis();
                if (now - lastDeltaTime[0] > STALE_STREAM_TIMEOUT_MS)
                {
                    log.warn("SSE stream stale (no delta for {}ms), aborting", now - lastDeltaTime[0]);
                    streamDied[0] = true;
                    return;
                }
                lastDeltaTime[0] = now;

                    String content = delta.getString("content");
                    if (content != null)
                    {
                        contentBuilder.append(content);
                        if (deltaCallback != null)
                        {
                            deltaCallback.accept(content);
                        }
                    }

                    // 工具调用增量
                    if (delta.containsKey("tool_calls"))
                    {
                        JSONArray tcArr = delta.getJSONArray("tool_calls");
                        System.out.println("【工具调用增量】收到 " + tcArr.size() + " 个tool_call");
                        
                        for (Object item : tcArr)
                        {
                            JSONObject tc = (JSONObject) item;
                            int index = tc.getIntValue("index", 0);
                            
                            System.out.println("  [index=" + index + "] tc内容: " + tc.toJSONString());
                            
                            // 只在字段存在且值不为null时才更新id
                            String tcId = tc.getString("id");
                            if (tcId != null && !tcId.isEmpty())
                            {
                                tcIds.put(index, tcId);
                                System.out.println("    -> 设置ID: " + tcId);
                            }
                            
                            JSONObject fn = tc.getJSONObject("function");
                            if (fn != null)
                            {
                                System.out.println("    -> function: " + fn.toJSONString());
                                
                                // 只在字段存在且值不为null时才更新name
                                String tcName = fn.getString("name");
                                if (tcName != null && !tcName.isEmpty())
                                {
                                    tcNames.put(index, tcName);
                                    System.out.println("       * 设置name: " + tcName);
                                }
                                
                                // arguments是累积的，可以为空字符串
                                String args = fn.getString("arguments");
                                if (args != null)
                                {
                                    tcArgs.computeIfAbsent(index, k -> new StringBuilder())
                                          .append(args);
                                    System.out.println("       * 追加arguments: " + args);
                                }
                            }
                        }
                    }
                }
            }
            catch (Exception e)
            {
                // 静默忽略解析错误的chunk，避免中断流
                // 某些模型可能在流末尾发送空的tool_calls数组
                log.debug("跳过无法解析的SSE chunk: {}", data.length() > 200 ? data.substring(0, 200) + "..." : data);
            }
        });

        watchdogFuture.cancel(true);
        if (streamDied[0])
        {
            throw new LlmException(0, "SSE stream stale: no data received for " + STALE_STREAM_TIMEOUT_MS + "ms");
        }

        resp.setContent(contentBuilder.toString());

        // 组装 tool_calls
        System.out.println("==================== 组装Tool Calls ====================");
        System.out.println("tcIds: " + tcIds);
        System.out.println("tcNames: " + tcNames);
        System.out.println("tcArgs: " + tcArgs);
        
        List<Integer> skippedIndexes = new ArrayList<>();
        for (Integer index : tcIds.keySet())
        {
            String id = tcIds.get(index);
            String name = tcNames.get(index);
            
            System.out.println("处理index=" + index + ", id=" + id + ", name=" + name);
            
            // 跳过没有name的tool_call（流式输出不完整）
            if (name == null || name.trim().isEmpty())
            {
                System.out.println("  -> 跳过：name为空");
                log.warn("跳过不完整的tool_call [index={}, id={}]: name为空", index, id);
                skippedIndexes.add(index);
                continue;
            }
            
            String args = tcArgs.containsKey(index) ? tcArgs.get(index).toString() : "{}";
            System.out.println("  -> 添加：name=" + name + ", args=" + args);
            toolCalls.add(ChatMessage.ToolCall.of(id, name, args));
        }
        
        System.out.println("最终toolCalls数量: " + toolCalls.size());
        System.out.println("跳过的indexes: " + skippedIndexes);
        System.out.println("=======================================================");
        
        // 如果有tool_call被跳过，且没有有效的tool_call和content，添加提示信息
        if (!skippedIndexes.isEmpty() && toolCalls.isEmpty() && contentBuilder.length() == 0)
        {
            log.warn("所有tool_call都不完整被跳过，生成提示内容");
            resp.setContent("(工具调用不完整，请重新生成完整的工具调用)");
        }
        
        if (!toolCalls.isEmpty())
        {
            resp.setToolCalls(toolCalls);
        }
        
        // 设置token统计
        resp.setPromptTokens(promptTokens[0]);
        resp.setCompletionTokens(completionTokens[0]);
        resp.setTotalTokens(promptTokens[0] + completionTokens[0]);
        resp.setFinishReason(finishReason[0] != null ? finishReason[0] : "stop");
        
        // 如果流式响应没有返回token信息，估算一个大概值
        if (promptTokens[0] == 0 && completionTokens[0] == 0)
        {
            // 粗略估算：英文约4字符=1token，中文约1.5字符=1token
            int estimatedCompletionTokens = contentBuilder.length() / 3;
            resp.setCompletionTokens(estimatedCompletionTokens);
            resp.setPromptTokens(0); // prompt tokens难以估算，设为0
            resp.setTotalTokens(estimatedCompletionTokens);
            log.debug("流式响应未返回token统计，估算completion_tokens={}", estimatedCompletionTokens);
        }

        return resp;
    }

    /**
     * 规范化 URL（去掉末尾斜杠和 /v1 后缀，统一添加）
     */
    private String normalizeUrl(String baseUrl)
    {
        if (baseUrl == null || baseUrl.isEmpty()) return "";
        String url = baseUrl.trim().replaceAll("/+$", "");
        // Keep /v1 in baseUrl - just strip trailing slashes
        return url;
    }

    /**
     * LLM 异常
     */
    public static class LlmException extends Exception
    {
        private final int statusCode;
        private final String errorBody;

        public LlmException(int statusCode, String errorBody)
        {
            super("LLM API error: " + statusCode + " - " +
                  (errorBody != null ? errorBody.substring(0, Math.min(200, errorBody.length())) : ""));
            this.statusCode = statusCode;
            this.errorBody = errorBody;
        }

        public int getStatusCode() { return statusCode; }
        public String getErrorBody() { return errorBody; }
    }
}
