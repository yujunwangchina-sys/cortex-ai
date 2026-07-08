package com.cortex.web.controller.agent;

import com.cortex.agent.runtime.model.AgentRunRequest;
import com.cortex.agent.runtime.model.SSEEvent;
import com.cortex.agent.service.IAgentRuntimeService;
import com.cortex.common.core.controller.BaseController;
import com.cortex.plugin.builtin.impl.VoiceManagerPlugin;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.File;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Voice call controller - real-time voice conversation via SSE.
 *
 * Flow: audio upload -> STT -> Agent (streaming) -> sentence-by-sentence TTS
 * All pushed back through a single SSE stream.
 */
@RestController
@RequestMapping("/agent/api/voice")
public class VoiceCallController extends BaseController
{
    private static final Logger log = LoggerFactory.getLogger(VoiceCallController.class);

    private final ExecutorService voiceExecutor = Executors.newCachedThreadPool();

    @Autowired
    private VoiceManagerPlugin voicePlugin;

    @Autowired
    private IAgentRuntimeService runtimeService;

    /**
     * Voice conversation stream.
     * Receives an audio chunk (WAV 16kHz mono), transcribes it, runs the agent,
     * and streams back: transcript -> agent deltas -> TTS audio (base64 per sentence).
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter voiceStream(
            @RequestParam("file") MultipartFile audioFile,
            @RequestParam("sessionId") String sessionId,
            @RequestParam("agentCode") String agentCode,
            @RequestParam(value = "modelId", required = false) Long modelId,
            @RequestParam(value = "userLoginName", required = false) String userLoginName,
            @RequestHeader(value = "X-Business-System", defaultValue = "cortex") String businessSystem)
    {
        SseEmitter emitter = new SseEmitter(300000L);

        // Resolve username in request thread (SecurityContext available here, not in async)
        final String resolvedUsername = (userLoginName != null && !userLoginName.isEmpty())
                ? userLoginName
                : getUsername();

        voiceExecutor.execute(() -> {
            File tempFile = null;
            try
            {
                // 1. Save audio to temp file
                tempFile = File.createTempFile("voice_call_", ".wav");
                audioFile.transferTo(tempFile);

                // 2. STT
                sendEvent(emitter, "stt_start", null);
                String transcript = voicePlugin.transcribePath(tempFile.getAbsolutePath());
                tempFile.delete();
                tempFile = null;

                if (transcript == null || transcript.isBlank())
                {
                    sendEvent(emitter, "error", Map.of("message", "voice recognition empty"));
                    emitter.complete();
                    return;
                }

                // 3. Send transcript to frontend
                sendEvent(emitter, "transcript", Map.of("text", transcript));
                log.info("[VoiceCall] STT result: {}", transcript);

                // 4. Run Agent with streaming
                AgentRunRequest request = new AgentRunRequest();
                String effectiveSessionId = (sessionId == null || sessionId.isEmpty()) ? null : sessionId;
                request.setSessionId(effectiveSessionId);
                request.setAgentCode(agentCode);
                request.setMessage(transcript);
                request.setBusinessSystem(businessSystem);
                request.setUserLoginName(resolvedUsername);
                if (modelId != null) request.setModelId(modelId);

                StringBuilder pendingTts = new StringBuilder();

                runtimeService.runStream(request, sseEvent -> {
                    try
                    {
                        String type = sseEvent.getType();
                        Object data = sseEvent.getData();

                        if ("content_delta".equals(type))
                        {
                            String delta = extractField(data, "delta");
                            if (delta != null && !delta.isEmpty())
                            {
                                sendEvent(emitter, "delta", Map.of("text", delta));
                                pendingTts.append(delta);

                                // Check for sentence boundary -> TTS immediately
                                flushTts(emitter, pendingTts, false);
                            }
                        }
                        else if ("done".equals(type))
                        {
                            // TTS any remaining text
                            flushTts(emitter, pendingTts, true);

                            int tokenUsage = extractInt(data, "tokenUsage");
                            sendEvent(emitter, "done", Map.of("sessionId", effectiveSessionId, "tokenUsage", tokenUsage));
                        }
                        else if ("error".equals(type))
                        {
                            String msg = extractField(data, "message");
                            sendEvent(emitter, "error", Map.of("message", msg != null ? msg : "agent error"));
                        }
                        else if ("tool_call_start".equals(type))
                        {
                            String toolName = extractField(data, "toolName");
                            sendEvent(emitter, "tool", Map.of("name", toolName != null ? toolName : "", "status", "start"));
                        }
                        else if ("tool_call_end".equals(type))
                        {
                            String toolName = extractField(data, "toolName");
                            sendEvent(emitter, "tool", Map.of("name", toolName != null ? toolName : "", "status", "end"));
                        }
                    }
                    catch (Exception e)
                    {
                        log.debug("[VoiceCall] SSE send error (client may have disconnected)", e);
                    }
                });

                emitter.complete();
            }
            catch (Exception e)
            {
                log.error("[VoiceCall] Voice stream error", e);
                try
                {
                    sendEvent(emitter, "error", Map.of("message", e.getMessage() != null ? e.getMessage() : "voice stream error"));
                }
                catch (Exception ignored) {}
                emitter.complete();
            }
            finally
            {
                if (tempFile != null && tempFile.exists())
                {
                    tempFile.delete();
                }
            }
        });

        return emitter;
    }

    /**
     * Flush pending text to TTS if a sentence boundary is found.
     * @param force if true, TTS all remaining text even without sentence boundary
     */
    private void flushTts(SseEmitter emitter, StringBuilder pending, boolean force)
    {
        String text = pending.toString();
        if (text.isEmpty()) return;

        // Find sentence boundary: Chinese + English punctuation
        int boundaryIdx = -1;
        for (int i = text.length() - 1; i >= 0; i--)
        {
            char c = text.charAt(i);
            if (c == '.' || c == '!' || c == '?' || c == '\n' ||
                c == 0x3002 || c == 0xFF01 || c == 0xFF1F || c == 0xFF0C ||  // 。！？，
                c == 0xFF1B)  // ；
            {
                boundaryIdx = i;
                break;
            }
        }

        if (boundaryIdx >= 0 || force)
        {
            String sentence = boundaryIdx >= 0
                    ? text.substring(0, boundaryIdx + 1)
                    : text;
            pending.delete(0, sentence.length());

            sentence = sentence.trim();
            if (sentence.isEmpty()) return;

            try
            {
                byte[] wav = voicePlugin.generateSpeechWav(sentence, null);
                if (wav != null && wav.length > 0)
                {
                    String base64 = Base64.getEncoder().encodeToString(wav);
                    sendEvent(emitter, "tts", Map.of("text", sentence, "audio", base64));
                    log.debug("[VoiceCall] TTS sent: {} chars, {} bytes", sentence.length(), wav.length);
                }
            }
            catch (Exception e)
            {
                log.warn("[VoiceCall] TTS failed for sentence: {}", sentence.substring(0, Math.min(50, sentence.length())), e);
            }
        }
    }

    private void sendEvent(SseEmitter emitter, String type, Object data)
    {
        try
        {
            JSONObject obj = new JSONObject();
            obj.put("type", type);
            if (data != null) obj.put("data", data);
            emitter.send(SseEmitter.event().data(obj.toJSONString()).comment(""));
        }
        catch (Exception e)
        {
            log.debug("[VoiceCall] Failed to send SSE event: {}", type);
        }
    }

    private String extractField(Object data, String field)
    {
        if (data == null) return null;
        if (data instanceof JSONObject)
        {
            return ((JSONObject) data).getString(field);
        }
        if (data instanceof Map)
        {
            Object val = ((Map<?, ?>) data).get(field);
            return val != null ? val.toString() : null;
        }
        return null;
    }

    private int extractInt(Object data, String field)
    {
        String val = extractField(data, field);
        if (val == null) return 0;
        try { return Integer.parseInt(val); } catch (NumberFormatException e) { return 0; }
    }
}
