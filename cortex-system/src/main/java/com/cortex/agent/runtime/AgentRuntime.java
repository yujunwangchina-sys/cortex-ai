package com.cortex.agent.runtime;

import com.cortex.agent.domain.AiAgent;
import com.cortex.agent.runtime.context.AgentSessionContext;
import com.cortex.agent.runtime.context.SessionManager;
import com.cortex.agent.runtime.file.FileContentParser;
import com.cortex.agent.service.IAiAgentFileService;
import com.cortex.agent.runtime.llm.OpenAiCompatibleClient;
import com.cortex.agent.runtime.loop.ConversationLoop;
import com.cortex.agent.runtime.loop.MemoryManager;
import com.cortex.agent.runtime.model.AgentRunRequest;
import com.cortex.agent.runtime.model.AgentRunResult;
import com.cortex.agent.runtime.model.SSEEvent;
import com.cortex.agent.runtime.prompt.AgentConfigLoader;
import com.cortex.agent.runtime.prompt.PromptBuilder;
import com.cortex.agent.runtime.prompt.ToolSchemaBuilder;
import com.cortex.plugin.domain.AiPlugin;
import com.cortex.plugin.domain.AiPluginTool;
import com.cortex.skill.domain.SkillNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * Agent Runtime - main entry point.
 * Four-table join: Agent -> Skills -> Plugins+Tools -> Supplier+Model
 */
@Component
public class AgentRuntime
{
    private static final Logger log = LoggerFactory.getLogger(AgentRuntime.class);

    @Autowired
    private AgentConfigLoader configLoader;

    @Autowired
    private PromptBuilder promptBuilder;

    @Autowired
    private ToolSchemaBuilder toolSchemaBuilder;

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private ConversationLoop conversationLoop;


    @Autowired
    private IAiAgentFileService fileService;

    @Autowired
    private com.cortex.agent.runtime.file.FileContentParser fileParser;

    @Autowired
    private com.cortex.agent.runtime.file.ImageVisionFallback visionFallback;

    @Autowired
    private com.cortex.knowledge.rag.KnowledgeContextInjector knowledgeContextInjector;


    @Autowired
    private MemoryManager memoryManager;
    /**
     * Run agent (non-streaming)
     */
    public AgentRunResult run(AgentRunRequest request) throws Exception
    {
        return run(request, null);
    }

    /**
     * Run agent (with optional SSE streaming)
     */
    public AgentRunResult run(AgentRunRequest request, Consumer<SSEEvent> sseCallback) throws Exception
    {
        long startTime = System.currentTimeMillis();

        // 1. Load agent config
        AiAgent agent = configLoader.loadAgent(request.getAgentCode());
        int maxIterations = agent.getMaxIterations() != null ? agent.getMaxIterations() : 20;

        // 2. Dynamic model selection (runtime modelId takes priority, otherwise by modelType)
        AgentConfigLoader.ModelSelection modelSelect;
        if (request.getModelId() != null)
        {
            modelSelect = configLoader.selectModelById(request.getModelId());
        }
        else
        {
            modelSelect = configLoader.selectModel(agent, request.getModelType() != null ? request.getModelType() : "chat");
        }

        // 3. Get or create session
        AgentSessionContext ctx;
        if (request.getSessionId() != null && !request.getSessionId().isEmpty())
        {
            ctx = sessionManager.getSession(request.getSessionId(), maxIterations);
            if (ctx == null)
            {
                throw new RuntimeException("Session not found: " + request.getSessionId());
            }
        }
        else
        {
            ctx = sessionManager.createSession(
                    agent.getId(), agent.getAgentCode(),
                    request.getUserLoginName(), request.getBusinessSystem(), maxIterations);
            if (sseCallback != null)
            {
                sseCallback.accept(SSEEvent.sessionCreated(ctx.getSessionId(), agent.getAgentName()));
                // 发送初始的上下文使用情况（使用模型配置的contextLength）
                int maxCtx = modelSelect.model.getContextLength() != null 
                    ? modelSelect.model.getContextLength() 
                    : 200000;
                int maxOut = modelSelect.model.getMaxTokens() != null 
                    ? modelSelect.model.getMaxTokens() 
                    : 16384;
                int availTokens = maxCtx - maxOut;
                if (availTokens <= 0) {
                    availTokens = maxCtx;
                }
                sseCallback.accept(SSEEvent.contextUsage(0, availTokens, 0));
            }
        }

        // Set approval mode from request
        if (request.getApprovalMode() != null)
        {
            ctx.setApprovalMode(request.getApprovalMode());
        }

        // 3. Session-level lock
        ctx.lock();
        try
        {
            // 4. Four-table join load
            List<SkillNode> skills = configLoader.loadSkillIndex(agent.getId(), ctx.getBusinessSystem(), ctx.getUserLoginName());
            List<AiPlugin> plugins = configLoader.loadPlugins(agent.getId());
            List<AiPluginTool> tools = configLoader.loadTools(plugins);

            // 5. Build system prompt and tool schemas
            String memoryText = memoryManager.recallForPrompt(
                    ctx.getUserLoginName(), ctx.getBusinessSystem(), agent.getId());
            
            // 获取用户昵称
            String userNickName = null;
            try {
                userNickName = com.cortex.common.utils.SecurityUtils.getLoginUser().getUser().getNickName();
            } catch (Exception e) {
                log.debug("无法获取用户昵称，使用默认值");
            }
            
            String systemPrompt = promptBuilder.buildSystemPrompt(
                    agent, 
                    skills, 
                    memoryText,
                    ctx.getBusinessSystem(),
                    ctx.getUserLoginName(),
                    userNickName
            );
            List<String> toolSchemas = toolSchemaBuilder.build(tools, plugins);
            ctx.setSystemPrompt(systemPrompt);
            
            // 强制输出到控制台，方便调试
            System.out.println("==================== Agent配置加载 ====================");
            System.out.println("Session: " + ctx.getSessionId());
            System.out.println("Skills: " + skills.size());
            System.out.println("Plugins: " + plugins.size());
            System.out.println("Tools: " + tools.size());
            System.out.println("ToolSchemas: " + toolSchemas.size());
            System.out.println("=====================================================");
            
            log.info("Agent配置加载完成 [session={}, skills={}, plugins={}, tools={}, toolSchemas={}]",
                    ctx.getSessionId(), skills.size(), plugins.size(), tools.size(), toolSchemas.size());

            // Emit skill loading info for frontend visibility
            if (sseCallback != null && skills != null && !skills.isEmpty())
            {
                StringBuilder skillNames = new StringBuilder();
                for (int si = 0; si < skills.size(); si++)
                {
                    if (si > 0) skillNames.append(", ");
                    skillNames.append(skills.get(si).getName());
                }
                sseCallback.accept(SSEEvent.info(
                        "技能索引已加载: " + skills.size() + " 个技能可用，使用 skill_view 工具获取完整内容"));
            }

            log.info("Agent running [agent={}, session={}, plugins={}, tools={}, model={}]",
                    agent.getAgentCode(), ctx.getSessionId(), plugins.size(), tools.size(),
                    modelSelect.model.getModelCode());
            // 6.5. Build file index for LLM (pull-based: LLM calls tools to read file content)
            String fileIndexMessage = null;
            if (request.getFileIds() != null && !request.getFileIds().isEmpty()) {
                StringBuilder fileIndex = new StringBuilder();
                fileIndex.append("[用户本次上传了以下文件，请使用对应工具读取内容]\n");
                
                int fileCount = 0;
                for (Long fid : request.getFileIds()) {
                    com.cortex.agent.domain.AiAgentFile af = fileService.selectAiAgentFileByFileId(fid);
                    if (af != null) {
                        // 关联文件到会话
                        af.setSessionId(ctx.getSessionId());
                        fileService.updateAiAgentFile(af);
                        
                        // 只展示本次上传的文件列表（仅 fileName 和类型提示）
                        boolean isImage = fileParser.isImage(af.getFileName());
                        fileIndex.append("• ").append(af.getFileName());
                        if (isImage) {
                            fileIndex.append(" [图片 - 使用 view_uploaded_image(fileId=").append(af.getFileId()).append(")]");
                        } else {
                            fileIndex.append(" [文档 - 使用 read_uploaded_file(fileId=").append(af.getFileId()).append(")]");
                        }
                        fileIndex.append("\n");
                        fileCount++;
                    }
                }
                
                fileIndex.append("\n[重要规则]\n");
                fileIndex.append("- 上述 fileId 仅用于工具调用，不要在回复中向用户展示\n");
                fileIndex.append("- 直接读取文件内容并回答用户问题，不要复述文件列表\n");
                fileIndex.append("- 如需查看历史文件，使用 uploaded_files_list 工具");
                
                fileIndexMessage = fileIndex.toString();
                
                if (sseCallback != null) {
                    sseCallback.accept(SSEEvent.info("已上传 " + fileCount + " 个文件"));
                }
            }


            // 6.6. Knowledge base RAG injection (auto mode)
            String kbContext = knowledgeContextInjector.inject(agent.getId(), request.getMessage());
            if (kbContext != null) {
                systemPrompt += kbContext;
                if (sseCallback != null) {
                    sseCallback.accept(SSEEvent.info("已检索知识库相关资料"));
                }
            }

            // 7. Execute conversation loop (传递文件索引作为独立参数)
            String response = conversationLoop.run(
                    ctx, request.getMessage(), request, agent, skills, plugins, tools,
                    modelSelect, toolSchemas, systemPrompt, fileIndexMessage, sseCallback);

            // 7b. Extract cross-session memories (async)
            memoryManager.extractAndStoreAsync(ctx, agent, request.getMessage(), response);

            // 8. Persist session (传递模型配置和SSE回调用于标题生成)
            sessionManager.persistSession(ctx, modelSelect.model.getModelId(), modelSelect.model, sseCallback);

            // 9. Build result
            AgentRunResult result = new AgentRunResult();
            result.setSessionId(ctx.getSessionId());
            result.setResponse(response);
            result.setIterations(ctx.getIterationBudget().getUsed());
            result.setTokenInput(ctx.getTotalTokenInput());
            result.setTokenOutput(ctx.getTotalTokenOutput());
            result.setSuccess(true);

            long duration = System.currentTimeMillis() - startTime;
            log.info("Agent done [session={}, iterations={}, tokens={}, duration={}ms]",
                    ctx.getSessionId(), result.getIterations(), ctx.getTotalTokenUsage(), duration);

            if (sseCallback != null)
            {
                sseCallback.accept(SSEEvent.done(
                        ctx.getSessionId(), result.getIterations(), ctx.getTotalTokenUsage()));
            }

            return result;
        }
        catch (Exception e)
        {
            log.error("Agent run failed [agent={}]", request.getAgentCode(), e);
            if (sseCallback != null)
            {
                sseCallback.accept(SSEEvent.error(e.getMessage(), "runtime_error"));
            }
            throw e;
        }
        finally
        {
            ctx.unlock();
        }
    }
}
