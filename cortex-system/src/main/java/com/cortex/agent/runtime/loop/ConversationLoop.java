package com.cortex.agent.runtime.loop;

import com.cortex.agent.domain.AiAgent;
import com.cortex.agent.domain.AiAgentExecutionLog;
import com.cortex.agent.mapper.AiAgentExecutionLogMapper;
import com.cortex.agent.runtime.context.AgentSessionContext;
import com.cortex.agent.runtime.llm.ErrorClassifier;
import com.cortex.agent.runtime.llm.LlmRequest;
import com.cortex.agent.runtime.llm.LlmResponse;
import com.cortex.agent.runtime.llm.OpenAiCompatibleClient;
import com.cortex.agent.runtime.model.ChatMessage;
import com.cortex.agent.runtime.model.SSEEvent;
import com.cortex.agent.runtime.model.AgentRunRequest;
import com.cortex.agent.runtime.model.ToolCallResult;
import com.cortex.agent.runtime.prompt.AgentConfigLoader;
import com.cortex.plugin.builtin.impl.SubAgentPlugin;
import com.cortex.agent.runtime.tool.ToolExecutor;
import com.cortex.plugin.domain.AiPlugin;
import com.cortex.plugin.domain.AiPluginTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * 对话循环 - 参考 hermes conversation_loop.py run_conversation()
 * <p>
 * 完整流程:
 * 1. 添加用户消息, 重置预算
 * 2. 主循环 (while budget remaining):
 * a. 消息清理 (孤儿tool, 角色交替, 代理字符)
 * b. 上下文压缩判断
 * c. 构建 LLM 请求
 * d. 重试循环 (ErrorClassifier + jittered backoff, 最多3次)
 * e. finish_reason 分支 (content_filter / length / stop)
 * f. 工具调用校验 (名称模糊匹配 + JSON参数 + 去重)
 * g. 并发执行工具
 * h. 工具结果回填, 继续循环
 * 3. 空回复恢复 (3次重试 + post-tool nudge)
 * 4. <think> 块剥离
 *
 * @author cortex
 */
@Component
public class ConversationLoop {
    private static final Logger log = LoggerFactory.getLogger(ConversationLoop.class);

    private static final int MAX_API_RETRIES = 3;
    private static final int MAX_EMPTY_RETRIES = 3;
    private static final int MAX_INVALID_TOOL_RETRIES = 3;
    private static final int MAX_LENGTH_CONTINUE_RETRIES = 3;
    private static final int MAX_TRUNCATED_TOOL_RETRIES = 3;

    @Autowired
    private OpenAiCompatibleClient llmClient;

    @Autowired
    private ToolExecutor toolExecutor;

    @Autowired
    private BackgroundSkillReview backgroundSkillReview;


    @Autowired
    private SubAgentPlugin subAgentPlugin;
    @Autowired
    private AgentConfigLoader configLoader;
    @Autowired
    private AiAgentExecutionLogMapper executionLogMapper;

    private final ContextCompressor contextCompressor = new ContextCompressor();

    /**
     * 执行一轮对话
     */
    public String run(AgentSessionContext ctx, String userMessage,
                      AgentRunRequest request,
                      com.cortex.agent.domain.AiAgent agent,
                      List<com.cortex.skill.domain.SkillNode> skills,
                      List<AiPlugin> plugins,
                      List<AiPluginTool> tools,
                      AgentConfigLoader.ModelSelection modelSelect,
                      List<String> toolSchemas,
                      String systemPrompt,
                      String fileIndexMessage,
                      Consumer<SSEEvent> sseCallback) {
        // 0. 加载可委派Agent，增强系统提示词
        List<AiAgent> delegatableAgents = configLoader.loadDelegatableAgents(agent.getId());
        String effectiveSystemPrompt = systemPrompt;
        if (delegatableAgents != null && !delegatableAgents.isEmpty())
        {
            StringBuilder agentInfo = new StringBuilder("\n\n## 可委派的Agent\n\n");
            agentInfo.append("你可以使用 delegate_task 工具(指定 agentCode)或 delegate_parallel 工具将子任务委派给以下专家Agent:\n\n");
            for (AiAgent a : delegatableAgents)
            {
                agentInfo.append("- ").append(a.getAgentCode()).append(" (").append(a.getAgentName()).append(")");
                if (a.getDescription() != null && !a.getDescription().isEmpty())
                {
                    agentInfo.append(": ").append(a.getDescription());
                }
                agentInfo.append("\n");
            }
            agentInfo.append("\n委派时请指定 agentCode。不指定 agentCode 则委派给自己(相同配置)。\n");
            effectiveSystemPrompt = (systemPrompt != null ? systemPrompt : "") + agentInfo.toString();
        }

        // 1. 添加用户消息
        ChatMessage userMsg;
        if (request.getImageUrls() != null && !request.getImageUrls().isEmpty()) {
            userMsg = ChatMessage.userWithImages(userMessage, request.getImageUrls());
        } else {
            userMsg = ChatMessage.user(userMessage);
        }
        
        // 1.1. 添加文件附件信息（用于历史回显）
        if (request.getFileIds() != null && !request.getFileIds().isEmpty()) {
            java.util.List<ChatMessage.FileAttachment> fileAttachments = new java.util.ArrayList<>();
            for (Long fileId : request.getFileIds()) {
                // 从文件服务查询文件名
                try {
                    com.cortex.agent.domain.AiAgentFile file = com.cortex.common.utils.spring.SpringUtils
                        .getBean(com.cortex.agent.service.IAiAgentFileService.class)
                        .selectAiAgentFileByFileId(fileId);
                    if (file != null) {
                        ChatMessage.FileAttachment attachment = new ChatMessage.FileAttachment();
                        attachment.setFileId(fileId);
                        attachment.setFileName(file.getFileName());
                        fileAttachments.add(attachment);
                    }
                } catch (Exception e) {
                    log.warn("获取文件信息失败 [fileId={}]", fileId, e);
                }
            }
            if (!fileAttachments.isEmpty()) {
                userMsg.setFiles(fileAttachments);
            }
        }
        
        ctx.addMessage(userMsg);
        
        // 1.5. 如果有文件索引，添加为系统消息（不保存到历史）
        boolean hasFileIndex = fileIndexMessage != null && !fileIndexMessage.isEmpty();
        
        ctx.resetBudget();

        // Configure context compressor with primary model for summarization
        contextCompressor.configureLlm(llmClient, modelSelect.supplier.getApiBaseUrl(),
                modelSelect.supplier.getApiKey(), modelSelect.model.getModelCode());

        // 自适应压缩阈值: 可用上下文的 70%
        {
            int _maxCtx = modelSelect.model.getContextLength() != null ? modelSelect.model.getContextLength() : 200000;
            int _maxOut = modelSelect.model.getMaxTokens() != null ? modelSelect.model.getMaxTokens() : 16384;
            int _avail = _maxCtx - _maxOut;
            if (_avail <= 0) _avail = _maxCtx;
            contextCompressor.setCompressionThreshold((int)(_avail * 0.7));
        }

        String turnId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        int iteration = 0;
        int maxIterations = agent.getMaxIterations() != null ? agent.getMaxIterations() : 20;

        // 构建工具名集合 + 工具映射
        Set<String> validToolNames = new HashSet<>();
        Map<String, AiPlugin> pluginMap = new HashMap<>();
        Map<String, AiPluginTool> toolMap = new HashMap<>();
        for (AiPluginTool tool : tools) {
            validToolNames.add(tool.getToolCode());
            toolMap.put(tool.getToolCode(), tool);
            for (AiPlugin plugin : plugins) {
                if (plugin.getPluginId().equals(tool.getPluginId())) {
                    pluginMap.put(tool.getToolCode(), plugin);
                    break;
                }
            }
        }

        // 重试计数器
        int emptyRetryCount = 0;
        int invalidToolRetryCount = 0;
        int lengthContinueRetryCount = 0;
        int truncatedToolRetryCount = 0;
        boolean postToolEmptyNudged = false;
        // 截断续写时累积的文本片段
        List<String> truncatedParts = new ArrayList<>();

        // 2. 主循环
        while (iteration < maxIterations && ctx.getIterationBudget().getRemaining() > 0) {
            // 中断检查
            if (ctx.isInterruptRequested()) {
                log.info("会话被用户中断 [session={}, iteration={}]", ctx.getSessionId(), iteration);
                ctx.addMessage(ChatMessage.assistant("[对话已被中断]"));
                ctx.clearInterrupt();
                return "[对话已被中断]";
            }

            iteration++;
            log.info("开始迭代 [session={}, iteration={}/{}, budget={}]",
                    ctx.getSessionId(), iteration, maxIterations, ctx.getIterationBudget().getRemaining());

            if (!ctx.getIterationBudget().consume()) {
                break;
            }

            if (sseCallback != null) {
                sseCallback.accept(SSEEvent.llmStart(iteration));
            }

            // 2a. 获取消息并清理
            List<ChatMessage> allMessages = new ArrayList<>();
            allMessages.add(ChatMessage.system(effectiveSystemPrompt));
            
            // 优先使用工作上下文（如果之前压缩过），否则使用完整历史
            List<ChatMessage> contextToUse = ctx.getWorkingContext();
            if (contextToUse.isEmpty() || ctx.getWorkingContext() == ctx.getMessages()) {
                // 第一次或者工作上下文未压缩，使用完整历史
                allMessages.addAll(ctx.getMessages());
                log.debug("使用完整消息历史 [count={}]", ctx.getMessages().size());
            } else {
                // 使用之前压缩的工作上下文
                allMessages.addAll(contextToUse);
                log.debug("使用压缩的工作上下文 [完整历史={}, 工作上下文={}]", 
                        ctx.getMessages().size(), contextToUse.size());
            }
            
            allMessages = MessageSanitizer.sanitize(allMessages);
            
            // 2a-1. 清理历史消息中的图片，只保留最后一条用户消息的图片
            // 这可以大幅减少上下文大小，避免图片重复发送导致的上下文溢出
            allMessages = stripOldImages(allMessages);
            
            // 2a-2. 如果是第一次迭代且有文件索引，添加为临时系统消息（仅用于LLM，不保存到历史）
            if (iteration == 1 && hasFileIndex) {
                allMessages.add(ChatMessage.system(fileIndexMessage));
                log.debug("添加文件索引到LLM上下文 [仅本次迭代，不保存到历史]");
            }

            // 2b. 计算当前上下文大小（估算）
            int estimatedTokens = contextCompressor.estimateTokens(allMessages);
            
            // 获取模型的上下文限制
            int maxContextTokens = modelSelect.model.getContextLength() != null 
                ? modelSelect.model.getContextLength() 
                : 200000; // 默认值
            int outputTokens = modelSelect.model.getMaxTokens() != null
                ? modelSelect.model.getMaxTokens()
                : 16384; // 预留的输出token
            int availableTokens = maxContextTokens - outputTokens;
            if (availableTokens <= 0) {
                availableTokens = maxContextTokens;
            }
            
            // 计算百分比并发送到前端
            int percentage = (int) ((estimatedTokens * 100.0) / availableTokens);
            
            if (sseCallback != null) {
                // 发送当前消息的估算token（用于进度条显示）
                // 这个是基于消息内容的实时估算，比累计值更能反映当前状态
                sseCallback.accept(SSEEvent.contextUsage(estimatedTokens, availableTokens, percentage));
            }
            
            log.debug("上下文使用情况 [估算tokens={}/{}, 累计真实tokens={}, percentage={}%]", 
                    estimatedTokens, availableTokens, ctx.getTotalTokenUsage(), percentage);

            // 2c. 上下文压缩判断（基于当前消息估算大小，而非累计值）
            // 压缩策略：保留完整历史（用于前端显示和持久化），但LLM只使用压缩后的工作上下文
            if (contextCompressor.shouldCompress(estimatedTokens)) {
                log.info("触发上下文压缩 [session={}, 估算tokens={}, 累计输入tokens={}]",
                        ctx.getSessionId(), estimatedTokens, ctx.getTotalTokenInput());
                
                int beforeCount = allMessages.size();
                int beforeTokens = estimatedTokens;
                
                if (sseCallback != null) { sseCallback.accept(SSEEvent.compressingStarted()); }
                List<ChatMessage> compressed = contextCompressor.compress(allMessages);
                
                int afterCount = compressed.size();
                int afterTokens = contextCompressor.estimateTokens(compressed);
                
                // 关键修改：使用setWorkingContext保存压缩版本
                // - 完整历史仍保留在 ctx.messages 中（用于前端显示和持久化）
                // - 压缩版本保存在 ctx.workingContext 中（用于后续LLM调用）
                ctx.setWorkingContext(compressed);
                allMessages = compressed;
                
                if (sseCallback != null) {
                    sseCallback.accept(SSEEvent.contextCompressed(
                        beforeCount, afterCount, beforeTokens, afterTokens));
                    sseCallback.accept(SSEEvent.info(
                        String.format("上下文已压缩：%d条消息→%d条，%d tokens→%d tokens（LLM记忆压缩，完整历史保留）", 
                            beforeCount, afterCount, beforeTokens, afterTokens)));
                }
                
                log.info("上下文压缩完成 [完整历史={}, LLM工作上下文={}, 节省tokens={}]", 
                        ctx.getMessages().size(), afterCount, beforeTokens - afterTokens);
            }

            // 2d. 构建 LLM 请求
            LlmRequest llmRequest = buildLlmRequest(allMessages, toolSchemas, agent, modelSelect,
                    sseCallback != null);

            log.debug("LLM请求构建完成 [messages={}, tools={}, stream={}]",
                    allMessages.size(), toolSchemas.size(), sseCallback != null);

            // 2d. 重试循环调用 LLM
            LlmResponse llmResponse = null;
            boolean apiSuccess = false;

            for (int retry = 0; retry < MAX_API_RETRIES; retry++) {
                try {
                    if (sseCallback != null) {
                        final ThinkBlockStripper.StreamingThinkScrubber scrubber = new ThinkBlockStripper.StreamingThinkScrubber();
                        llmResponse = llmClient.chatCompletionStream(llmRequest, delta -> {
                            String clean = AnsiStripper.strip(scrubber.feed(delta));
                            if (clean != null && !clean.isEmpty()) {
                                sseCallback.accept(SSEEvent.contentDelta(clean));
                            }
                        });
                        String tail = scrubber.flush();
                        if (tail != null && !tail.isEmpty()) {
                            sseCallback.accept(SSEEvent.contentDelta(tail));
                        }
                    } else {
                        llmResponse = llmClient.chatCompletion(llmRequest);
                    }
                    apiSuccess = true;
                    break;
                } catch (OpenAiCompatibleClient.LlmException e) {
                    // Image rejection recovery: strip images and retry
                    String errBody = e.getErrorBody() != null ? e.getErrorBody().toLowerCase() : "";
                    if (errBody.contains("image") && (errBody.contains("not supported")
                            || errBody.contains("only") && errBody.contains("text")
                            || errBody.contains("does not support"))) {
                        log.warn("Provider rejected image content, stripping images and retrying");
                        if (sseCallback != null) {
                            sseCallback.accept(SSEEvent.info("Provider不支持图片, 已切换为纯文本模式"));
                        }
                        // Strip images from all messages in context
                        for (ChatMessage msg : ctx.getMessages()) {
                            if (msg.hasImages()) msg.setImageUrls(null);
                        }
                        ctx.getIterationBudget().refund();
                        iteration--;
                        continue;
                    }

                    log.warn("LLM调用失败 [retry={}/{}, status={}]",
                            retry + 1, MAX_API_RETRIES, e.getStatusCode());

                    ErrorClassifier.ClassifiedError classified =
                            ErrorClassifier.classify(e.getStatusCode(), e.getErrorBody(), e);

                    if (!classified.shouldRetry || retry >= MAX_API_RETRIES - 1) {
                        // 不再重试
                        log.error("LLM调用最终失败 [status={}, reason={}]",
                                e.getStatusCode(), classified.reason);

                        // context overflow -> 尝试压缩后重试一次
                        if (classified.reason == ErrorClassifier.FailoverReason.CONTEXT_OVERFLOW
                                && retry == 0) {
                            log.info("上下文溢出, 强制压缩后重试");
                            
                            int beforeCount = allMessages.size();
                            int beforeTokens = estimatedTokens;  // 使用之前计算的估算值
                            
                            if (sseCallback != null) { sseCallback.accept(SSEEvent.compressingStarted()); }
                            List<ChatMessage> compressed = contextCompressor.compress(allMessages);
                            
                            int afterCount = compressed.size();
                            int afterTokens = contextCompressor.estimateTokens(compressed);
                            
                            // 关键：保存压缩后的工作上下文
                            ctx.setWorkingContext(compressed);
                            allMessages = compressed;
                            llmRequest = buildLlmRequest(compressed, toolSchemas, agent, modelSelect,
                                    sseCallback != null);
                            
                            if (sseCallback != null) {
                                sseCallback.accept(SSEEvent.contextCompressed(
                                    beforeCount, afterCount, beforeTokens, afterTokens));
                                sseCallback.accept(SSEEvent.info(
                                    String.format("检测到上下文溢出，已自动压缩：%d条→%d条，%d tokens→%d tokens", 
                                        beforeCount, afterCount, beforeTokens, afterTokens)));
                            }
                            
                            retry = -1; // 重置重试计数
                            continue;
                        }

                        String errMsg = buildErrorMessage(classified);
                        logLlmCall(ctx, turnId, iteration, null, null, "1", errMsg, sseCallback);
                        return errMsg;
                    }

                    // 退避等待
                    long delay = classified.retryDelayMs > 0
                            ? classified.retryDelayMs
                            : jitteredBackoff(retry + 1);
                    log.warn("LLM重试等待 {}ms [reason={}]", delay, classified.reason);
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return "对话被中断";
                    }
                } catch (Exception e) {
                    log.error("LLM调用异常 [retry={}/{}]", retry + 1, MAX_API_RETRIES, e);
                    if (retry >= MAX_API_RETRIES - 1) {
                        logLlmCall(ctx, turnId, iteration, null, null, "1", e.getMessage(), sseCallback);
                        return "AI 服务暂时不可用: " + e.getMessage();
                    }
                    long delay = jitteredBackoff(retry + 1);
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return "对话被中断";
                    }
                }
            }

            // Fallback: try alternate models if primary failed after all retries
            if (!apiSuccess && modelSelect.fallbacks != null && !modelSelect.fallbacks.isEmpty()) {
                for (AgentConfigLoader.ModelSelection fb : modelSelect.fallbacks) {
                    log.warn("切换到 fallback 模型 [model={}, supplier={}]",
                            fb.model.getModelCode(), fb.supplier.getSupplierName());
                    if (sseCallback != null) {
                        sseCallback.accept(SSEEvent.info("切换到备用模型: " + fb.model.getModelName()));
                    }
                    LlmRequest fbRequest = buildLlmRequest(allMessages, toolSchemas, agent, fb, sseCallback != null);
                    try {
                        if (sseCallback != null) {
                            final ThinkBlockStripper.StreamingThinkScrubber fbScrubber = new ThinkBlockStripper.StreamingThinkScrubber();
                            llmResponse = llmClient.chatCompletionStream(fbRequest, delta -> {
                                String clean = AnsiStripper.strip(fbScrubber.feed(delta));
                                if (clean != null && !clean.isEmpty()) {
                                    sseCallback.accept(SSEEvent.contentDelta(clean));
                                }
                            });
                            String fbTail = fbScrubber.flush();
                            if (fbTail != null && !fbTail.isEmpty()) {
                                sseCallback.accept(SSEEvent.contentDelta(fbTail));
                            }
                        } else {
                            llmResponse = llmClient.chatCompletion(fbRequest);
                        }
                        apiSuccess = true;
                        log.info("Fallback 模型调用成功 [model={}]", fb.model.getModelCode());
                        break;
                    } catch (Exception fbEx) {
                        log.warn("Fallback 模型也失败 [model={}]", fb.model.getModelCode(), fbEx);
                    }
                }
            }

            if (!apiSuccess || llmResponse == null) {
                String msg = "LLM 调用失败, 已用尽所有重试";
                ctx.addMessage(ChatMessage.assistant(msg));
                return msg;
            }

            // 流式响应可能不返回 token 统计，用估算补充 prompt tokens
            if (llmResponse.getPromptTokens() == 0)
            {
                int estPrompt = contextCompressor.estimateTokens(allMessages);
                llmResponse.setPromptTokens(estPrompt);
                if (llmResponse.getCompletionTokens() == 0 && llmResponse.getContent() != null)
                {
                    llmResponse.setCompletionTokens(llmResponse.getContent().length() / 3);
                }
                llmResponse.setTotalTokens(llmResponse.getPromptTokens() + llmResponse.getCompletionTokens());
            }

            // 记录 token 用量
            ctx.addTokenUsage(llmResponse.getPromptTokens(), llmResponse.getCompletionTokens());
            log.info("LLM响应 [session={}, iteration={}, tokens={}+{}={}, finishReason={}, hasToolCalls={}, contentLength={}]",
                    ctx.getSessionId(), iteration,
                    llmResponse.getPromptTokens(), llmResponse.getCompletionTokens(),
                    llmResponse.getTotalTokens(), llmResponse.getFinishReason(),
                    llmResponse.hasToolCalls(),
                    llmResponse.getContent() != null ? llmResponse.getContent().length() : 0);

            logLlmCall(ctx, turnId, iteration, llmResponse.getPromptTokens(),
                    llmResponse.getCompletionTokens(), "0", null, sseCallback);

            String finishReason = llmResponse.getFinishReason();

            // 2e. finish_reason 分支处理
            if ("content_filter".equals(finishReason)) {
                String msg = "内容被安全策略拦截, 请调整问题后重试。";
                ctx.addMessage(ChatMessage.assistant(msg));
                if (sseCallback != null) {
                    sseCallback.accept(SSEEvent.contentDelta(msg));
                }
                return msg;
            }

            if ("length".equals(finishReason)) {
                // 截断处理
                if (llmResponse.hasToolCalls()) {
                    // 截断的 tool_call - 不执行, 重试
                    if (truncatedToolRetryCount < MAX_TRUNCATED_TOOL_RETRIES) {
                        truncatedToolRetryCount++;
                        log.warn("截断的 tool_call, 重试 ({}/{})",
                                truncatedToolRetryCount, MAX_TRUNCATED_TOOL_RETRIES);
                        if (sseCallback != null) {
                            sseCallback.accept(SSEEvent.info("工具调用被截断，正在重试 (" + truncatedToolRetryCount + "/" + MAX_TRUNCATED_TOOL_RETRIES + ")..."));
                        }
                        ctx.getIterationBudget().refund();
                        iteration--;
                        continue;
                    }

                    // 截断重试耗尽
                    StringBuilder errorDetail = new StringBuilder();
                    errorDetail.append("工具调用被截断，已重试3次仍失败。\n\n");
                    errorDetail.append("原因：模型输出超过长度限制，工具调用信息不完整。\n\n");

                    if (llmResponse.hasToolCalls()) {
                        errorDetail.append("涉及的工具：\n");
                        for (ChatMessage.ToolCall tc : llmResponse.getToolCalls()) {
                            errorDetail.append("- ").append(tc.getName() != null ? tc.getName() : "未知").append("\n");
                        }
                        errorDetail.append("\n");
                    }

                    errorDetail.append("建议：\n");
                    errorDetail.append("1. 简化您的请求\n");
                    errorDetail.append("2. 分步骤提问\n");
                    errorDetail.append("3. 或者我直接用文字回复您");

                    String msg = errorDetail.toString();
                    ctx.addMessage(ChatMessage.assistant(msg));
                    if (sseCallback != null) {
                        sseCallback.accept(SSEEvent.contentDelta(msg));
                    }
                    return msg;
                } else {
                    // 文本截断 - 追加部分内容, 添加续写提示
                    String partial = llmResponse.getContent();
                    if (partial != null && !partial.isEmpty()) {
                        truncatedParts.add(partial);
                    }

                    if (lengthContinueRetryCount < MAX_LENGTH_CONTINUE_RETRIES) {
                        lengthContinueRetryCount++;
                        log.warn("响应被截断, 请求续写 ({}/{})",
                                lengthContinueRetryCount, MAX_LENGTH_CONTINUE_RETRIES);

                        // 追加截断的 assistant 消息
                        ctx.addMessage(ChatMessage.assistant(partial != null ? partial : ""));
                        // 添加续写提示
                        ctx.addMessage(ChatMessage.user("[System: 你的上一条回复被截断了, 请继续。]"));

                        if (sseCallback != null) {
                            sseCallback.accept(SSEEvent.info("响应被截断, 正在续写..."));
                        }
                        continue;
                    }
                    // 续写耗尽, 拼接已有片段作为最终回复
                    String combined = String.join("", truncatedParts);
                    if (!combined.isEmpty()) {
                        combined = ThinkBlockStripper.strip(combined);
                        ctx.addMessage(ChatMessage.assistant(combined));
                        backgroundSkillReview.reviewAsync(ctx, agent);
                        return combined;
                    }
                    String msg = "响应被截断且无法续写, 请重试。";
                    ctx.addMessage(ChatMessage.assistant(msg));
                    return msg;
                }
            }

            // 2f. 解析工具调用
            if (llmResponse.hasToolCalls()) {
                List<ChatMessage.ToolCall> toolCalls = llmResponse.getToolCalls();
                log.info("LLM返回工具调用 [session={}, toolCalls={}]", ctx.getSessionId(), toolCalls.size());

                for (ChatMessage.ToolCall tc : toolCalls) {
                    log.debug("  - 工具: {} (id={})", tc.getName(), tc.getId());
                }

                // 工具调用去重
                toolCalls = ToolCallValidator.deduplicate(toolCalls);

                // 工具名校验 + 模糊修复
                ToolCallValidator.ValidationResult vr =
                        ToolCallValidator.validate(toolCalls, validToolNames);

                if (!vr.valid) {
                    // 存在非法工具名
                    invalidToolRetryCount++;
                    log.warn("非法工具调用 ({}/{}): {}",
                            invalidToolRetryCount, MAX_INVALID_TOOL_RETRIES, vr.errorMessage);

                    if (invalidToolRetryCount >= MAX_INVALID_TOOL_RETRIES) {
                        // 构建详细的错误信息
                        StringBuilder errorDetail = new StringBuilder();
                        errorDetail.append("工具调用失败，已重试3次。错误原因：\n");

                        for (ChatMessage.ToolCall tc : llmResponse.getToolCalls()) {
                            String tcName = tc.getName();
                            if (tcName == null || tcName.trim().isEmpty()) {
                                errorDetail.append("- 工具名为空（可能是流式输出不完整）\n");
                            } else {
                                errorDetail.append("- 工具 '").append(tcName).append("' 不存在\n");
                            }
                        }

                        errorDetail.append("\n").append(vr.errorMessage);
                        errorDetail.append("\n\n建议：请直接用文字回复，或检查工具配置是否正确。");

                        String msg = errorDetail.toString();
                        ctx.addMessage(ChatMessage.assistant(msg));
                        if (sseCallback != null) {
                            sseCallback.accept(SSEEvent.contentDelta(msg));
                        }
                        return msg;
                    }

                    // 回填错误信息让模型自纠
                    ctx.addMessage(ChatMessage.assistantWithTools(
                            llmResponse.getContent(), llmResponse.getToolCalls()));
                    for (ChatMessage.ToolCall tc : llmResponse.getToolCalls()) {
                        String tcName = tc.getName();
                        String content;
                        if (tcName == null || tcName.trim().isEmpty()) {
                            content = "错误: 工具名为空。请使用工具列表中的有效工具名, 否则用纯文本回复。";
                        } else {
                            content = "错误: 工具 '" + tcName + "' 不存在。" + vr.errorMessage;
                        }
                        ctx.addMessage(ChatMessage.tool(tc.getId(), content));
                    }

                    if (sseCallback != null) {
                        sseCallback.accept(SSEEvent.info("工具调用失败，正在重试 (" + invalidToolRetryCount + "/" + MAX_INVALID_TOOL_RETRIES + ")..."));
                    }

                    ctx.getIterationBudget().refund();
                    iteration--;
                    continue;
                }

                // JSON 参数校验
                String argError = ToolCallValidator.validateArguments(vr.repairedCalls);
                if (argError != null) {
                    if (argError.startsWith("TRUNCATED")) {
                        // 截断的参数 - 重试
                        if (truncatedToolRetryCount < MAX_TRUNCATED_TOOL_RETRIES) {
                            truncatedToolRetryCount++;
                            log.warn("截断的工具参数, 重试 ({}/{})",
                                    truncatedToolRetryCount, MAX_TRUNCATED_TOOL_RETRIES);
                            if (sseCallback != null) {
                                sseCallback.accept(SSEEvent.info("工具参数被截断，正在重试 (" + truncatedToolRetryCount + "/" + MAX_TRUNCATED_TOOL_RETRIES + ")..."));
                            }
                            ctx.getIterationBudget().refund();
                            iteration--;
                            continue;
                        }

                        // 截断重试耗尽
                        String msg = "工具参数被截断，已重试" + MAX_TRUNCATED_TOOL_RETRIES + "次仍失败。建议简化请求或直接用文字回复。";
                        ctx.addMessage(ChatMessage.assistant(msg));
                        if (sseCallback != null) {
                            sseCallback.accept(SSEEvent.contentDelta(msg));
                        }
                        return msg;
                    }

                    // 无效 JSON - 回填错误让模型重试
                    invalidToolRetryCount++;
                    if (invalidToolRetryCount >= MAX_INVALID_TOOL_RETRIES) {
                        // 构建详细的错误信息
                        StringBuilder errorDetail = new StringBuilder();
                        errorDetail.append("工具参数格式错误，已重试3次。错误原因：\n");
                        errorDetail.append("- ").append(argError).append("\n\n");
                        errorDetail.append("涉及的工具：\n");

                        for (ChatMessage.ToolCall tc : vr.repairedCalls) {
                            errorDetail.append("- ").append(tc.getName()).append("\n");
                        }

                        errorDetail.append("\n建议：请直接用文字回复，或确保工具参数是有效的JSON格式。");

                        String msg = errorDetail.toString();
                        ctx.addMessage(ChatMessage.assistant(msg));
                        if (sseCallback != null) {
                            sseCallback.accept(SSEEvent.contentDelta(msg));
                        }
                        return msg;
                    }

                    ctx.addMessage(ChatMessage.assistantWithTools(
                            llmResponse.getContent(), vr.repairedCalls));
                    for (ChatMessage.ToolCall tc : vr.repairedCalls) {
                        String content = "错误: 工具参数 JSON 无效: " + argError
                                + "。请用合法的 JSON 参数重试, 无参数时用 {}。";
                        ctx.addMessage(ChatMessage.tool(tc.getId(), content));
                    }

                    if (sseCallback != null) {
                        sseCallback.accept(SSEEvent.info("工具参数错误，正在重试 (" + invalidToolRetryCount + "/" + MAX_INVALID_TOOL_RETRIES + ")..."));
                    }

                    ctx.getIterationBudget().refund();
                    iteration--;
                    continue;
                }

                // 重置非法工具计数
                invalidToolRetryCount = 0;

                // 2g. 先执行工具（审批检查在此时进行）
                // Set sub-agent delegation context (thread-local) — includes authorized delegate agents
                toolExecutor.setDelegateContext(new SubAgentPlugin.DelegateContext(
                        ctx, agent, skills, plugins, tools, toolSchemas, modelSelect,
                        delegatableAgents, sseCallback));
                List<ToolCallResult> results = toolExecutor.executeAll(
                        vr.repairedCalls, pluginMap, toolMap,
                        ctx, turnId, iteration, sseCallback);
                toolExecutor.clearDelegateContext();

                // 检查是否有工具被拒绝
                boolean hasRejection = false;
                for (ToolCallResult result : results) {
                    if (result.isApprovalRejected()) {
                        hasRejection = true;
                        log.info("⛔ 检测到审批拒绝 [session={}, tool={}]", ctx.getSessionId(), result.getToolName());
                        break;
                    }
                }

                if (hasRejection) {
                    // 审批被拒绝：丢弃本轮AI输出，让AI重新生成回复
                    log.info("🚫 审批被拒绝，丢弃误导性输出，进入下一轮让AI重新回复 [session={}, iteration={}]", ctx.getSessionId(), iteration);

                    // 通知前端清除已流式输出的误导性内容
                    if (sseCallback != null) {
                        sseCallback.accept(SSEEvent.clearCurrentMessage());
                    }

                    // 添加空content的assistant消息（丢弃本轮AI的误导性输出）
                    ctx.addMessage(ChatMessage.assistantWithTools("", vr.repairedCalls));
                    log.info("📝 添加空assistant消息（丢弃误导性输出）[toolCallsCount={}]", vr.repairedCalls.size());
                    
                    // 打印tool_call_id用于调试
                    for (ChatMessage.ToolCall tc : vr.repairedCalls) {
                        log.info("   - tool_call_id: {}, toolName: {}", tc.getId(), tc.getName());
                    }

                    // 添加工具拒绝结果到对话历史
                    for (ToolCallResult result : results) {
                        String toolMsg = result.toToolMessageContent();
                        ctx.addMessage(ChatMessage.tool(result.getToolCallId(), toolMsg));
                        log.info("📝 添加tool拒绝消息 [toolCallId={}, toolName={}, content={}]", 
                                result.getToolCallId(), 
                                result.getToolName(),
                                toolMsg.length() > 100 ? toolMsg.substring(0, 100) + "..." : toolMsg);
                    }

                    // 立即进入下一轮，让AI看到拒绝消息后自己决定如何回复用户
                    log.info("🔄 进入下一轮，让AI处理拒绝情况");
                    truncatedToolRetryCount = 0;
                    postToolEmptyNudged = false;
                    continue;
                }

                // 正常情况：添加完整的assistant消息(含 content 和 tool_calls)
                ctx.addMessage(ChatMessage.assistantWithTools(
                        llmResponse.getContent(), vr.repairedCalls));

                // 2h. 工具结果回填
                for (ToolCallResult result : results) {
                    ctx.addMessage(ChatMessage.tool(result.getToolCallId(),
                            result.toToolMessageContent()));
                }

                // 重置截断计数
                truncatedToolRetryCount = 0;
                postToolEmptyNudged = false;
                continue;
            }

            // === 无工具调用 - 最终回复处理 ===
            log.info("无工具调用，处理最终回复 [session={}, iteration={}]", ctx.getSessionId(), iteration);

            String content = llmResponse.getContent();
            log.debug("原始内容: {}", content != null ? content.substring(0, Math.min(100, content.length())) : "null");

            // 剥离 <think> 块
            content = AnsiStripper.clean(ThinkBlockStripper.strip(content));
            log.debug("清理后内容: {}", content != null ? content.substring(0, Math.min(100, content.length())) : "null");

            // 检查空回复
            if (content == null || content.trim().isEmpty()) {
                emptyRetryCount++;

                // post-tool 空回复 nudge (只 nudge 一次)
                if (!postToolEmptyNudged && iteration > 1) {
                    List<ChatMessage> recent = ctx.getMessages();
                    boolean priorWasTool = false;
                    int checkCount = Math.min(5, recent.size());
                    for (int i = recent.size() - 1; i >= recent.size() - checkCount; i--) {
                        if ("tool".equals(recent.get(i).getRole())) {
                            priorWasTool = true;
                            break;
                        }
                    }
                    if (priorWasTool) {
                        postToolEmptyNudged = true;
                        log.info("工具执行后空回复, 发送 nudge");
                        ctx.addMessage(ChatMessage.assistant("(empty)"));
                        ctx.addMessage(ChatMessage.user(
                                "你刚执行了工具但返回了空回复, 请根据工具结果继续处理任务。"));
                        ctx.getIterationBudget().refund();
                        iteration--;
                        continue;
                    }
                }

                if (emptyRetryCount < MAX_EMPTY_RETRIES) {
                    log.warn("空回复, 重试 ({}/{})", emptyRetryCount, MAX_EMPTY_RETRIES);
                    if (sseCallback != null) {
                        sseCallback.accept(SSEEvent.info("模型返回空回复, 正在重试..."));
                    }
                    ctx.getIterationBudget().refund();
                    iteration--;
                    continue;
                }

                // 空回复耗尽
                String msg = "模型多次返回空回复, 请稍后重试。";
                ctx.addMessage(ChatMessage.assistant(msg));
                if (sseCallback != null) {
                    sseCallback.accept(SSEEvent.contentDelta(msg));
                }
                return msg;
            }

            // 正常最终回复
            emptyRetryCount = 0;

            // 拼接截断续写的片段
            if (!truncatedParts.isEmpty()) {
                truncatedParts.add(content);
                content = String.join("", truncatedParts);
                truncatedParts.clear();
            }

            ctx.addMessage(ChatMessage.assistant(content));

            // 每轮对话后触发后台技能审查(异步)
            backgroundSkillReview.reviewAsync(ctx, agent);

            return content;
        }

        // 迭代上限耗尽
        String msg = "已达到最大迭代次数(" + maxIterations + "), 请尝试简化您的问题。";
        ctx.addMessage(ChatMessage.assistant(msg));
        log.warn("迭代预算耗尽 [session={}, iteration={}]", ctx.getSessionId(), iteration);
        return msg;
    }

    /**
     * 构建 LLM 请求
     */
    private LlmRequest buildLlmRequest(List<ChatMessage> messages, List<String> toolSchemas,
                                       com.cortex.agent.domain.AiAgent agent,
                                       AgentConfigLoader.ModelSelection modelSelect,
                                       boolean stream) {
        LlmRequest request = new LlmRequest();
        request.setBaseUrl(modelSelect.supplier.getApiBaseUrl());
        request.setApiKey(modelSelect.supplier.getApiKey());
        request.setModel(modelSelect.model.getModelCode());
        request.setMessages(messages);
        request.setTools(toolSchemas);
        request.setStream(stream);

        BigDecimal temp = agent.getTemperature() != null
                ? agent.getTemperature()
                : modelSelect.model.getTemperature();
        request.setTemperature(temp);

        request.setMaxTokens(modelSelect.model.getMaxTokens());
        request.setTopP(modelSelect.model.getTopP());

        return request;
    }
    
    /**
     * 清理历史消息中的图片，只保留最后一条用户消息的图片
     * 图片（尤其是base64编码）会占用大量上下文，但通常只在首次提问时有意义
     * 
     * @param messages 原始消息列表
     * @return 清理后的消息列表
     */
    private List<ChatMessage> stripOldImages(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return messages;
        }
        
        // 找到最后一条用户消息的索引
        int lastUserMsgIndex = -1;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("user".equals(messages.get(i).getRole())) {
                lastUserMsgIndex = i;
                break;
            }
        }
        
        // 如果没有用户消息，直接返回
        if (lastUserMsgIndex == -1) {
            return messages;
        }
        
        // 创建新列表，清理除最后一条用户消息外的所有图片
        List<ChatMessage> cleaned = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            
            // 如果是最后一条用户消息，保留原样（包括图片）
            if (i == lastUserMsgIndex) {
                cleaned.add(msg);
            }
            // 其他消息：如果有图片，创建一个不含图片的副本
            else if (msg.hasImages()) {
                ChatMessage copy = new ChatMessage();
                copy.setRole(msg.getRole());
                copy.setContent(msg.getContent());
                copy.setName(msg.getName());
                copy.setToolCalls(msg.getToolCalls());
                copy.setToolCallId(msg.getToolCallId());
                // 不设置imageUrls，即清除图片
                cleaned.add(copy);
                
                log.debug("清除历史消息中的图片 [role={}, imageCount={}]", 
                        msg.getRole(), msg.getImageUrls().size());
            }
            // 没有图片的消息，直接添加
            else {
                cleaned.add(msg);
            }
        }
        
        return cleaned;
    }

    /**
     * 构建错误消息
     */
    private String buildErrorMessage(ErrorClassifier.ClassifiedError classified) {
        switch (classified.reason) {
            case AUTH:
                return "AI 服务认证失败, 请检查 API 密钥配置。";
            case RATE_LIMIT:
                return "AI 服务繁忙(限流), 请稍后重试。";
            case OVERLOADED:
                return "AI 服务过载, 请稍后重试。";
            case CONTEXT_OVERFLOW:
                return "对话上下文过长, 请开启新会话或简化问题。";
            case MODEL_NOT_FOUND:
                return "模型不存在, 请检查模型配置。";
            case CONTENT_POLICY:
                return "内容被安全策略拦截。";
            case TIMEOUT:
                return "AI 服务响应超时, 请稍后重试。";
            case BILLING:
                return "AI 服务额度不足或已过期, 请检查账户余额。";
            case PAYLOAD_TOO_LARGE:
                return "请求数据过大, 请简化输入或减少图片数量。";
            case FORMAT_ERROR:
                return "请求格式错误, 请检查模型参数配置。";
            case IMAGE_TOO_LARGE:
                return "图片尺寸过大, 请压缩后重试。";
            case MULTIMODAL_UNSUPPORTED:
                return "当前模型不支持多模态输入, 已自动切换为纯文本模式。";
            default:
                return "AI 服务暂时不可用: " + classified.message;
        }
    }

    /**
     * 抖动指数退避
     */
    private long jitteredBackoff(int attempt) {
        long base = 2000L;
        long delay = (long) (base * Math.pow(2, attempt - 1));
        delay = Math.min(delay, 30000L);
        long jitter = ThreadLocalRandom.current().nextLong(0, delay / 2 + 1);
        return delay + jitter;
    }

    /**
     * 记录 LLM 调用日志
     */
    private void logLlmCall(AgentSessionContext ctx, String turnId, int iteration,
                            Integer tokenIn, Integer tokenOut, String status, String error,
                            Consumer<SSEEvent> sseCallback) {
        try {
            AiAgentExecutionLog logEntry = new AiAgentExecutionLog();
            logEntry.setSessionId(ctx.getSessionId());
            logEntry.setAgentId(ctx.getAgentId());
            logEntry.setTurnId(turnId);
            logEntry.setIteration(iteration);
            logEntry.setEventType("llm_call");
            logEntry.setTokenInput(tokenIn);
            logEntry.setTokenOutput(tokenOut);
            logEntry.setStatus(status);
            logEntry.setErrorMessage(error);
            logEntry.setBusinessSystem(ctx.getBusinessSystem());
            logEntry.setUserLoginName(ctx.getUserLoginName());
            executionLogMapper.insertAiAgentExecutionLog(logEntry);
        } catch (Exception e) {
            log.warn("记录LLM日志失败", e);
        }
    }
}
