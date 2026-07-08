package com.cortex.plugin.builtin.impl;

import com.k2fsa.sherpa.onnx.FeatureConfig;
import com.k2fsa.sherpa.onnx.GeneratedAudio;
import com.k2fsa.sherpa.onnx.LibraryUtils;
import com.k2fsa.sherpa.onnx.OfflineModelConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizer;
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizerResult;
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig;
import com.k2fsa.sherpa.onnx.OfflineStream;
import com.k2fsa.sherpa.onnx.OfflineTts;
import com.k2fsa.sherpa.onnx.OfflineTtsConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig;
import com.k2fsa.sherpa.onnx.WaveReader;
import com.k2fsa.sherpa.onnx.WaveWriter;
import com.cortex.agent.domain.AiAgentFile;
import com.cortex.agent.runtime.context.RuntimeContextHolder;
import com.cortex.agent.service.IAiAgentFileService;
import com.cortex.plugin.builtin.IBuiltinPlugin;
import com.cortex.plugin.builtin.PluginInfo;
import com.cortex.plugin.builtin.ToolDefinition;
import com.cortex.plugin.builtin.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 语音管理内置插件（本地化版本，基于 sherpa-onnx）
 *
 * STT (语音转文字) + TTS (文字转语音)，模型在 JVM 进程内加载，不依赖外部 OpenAI 接口。
 * 原生库从 classpath 的 native-lib jar 按操作系统自动加载，Windows/Linux 通用。
 *
 * 工具：
 * 1. voice_transcribe - 语音转文字（支持中英日韩粤）
 * 2. voice_speak      - 文字转语音（生成 wav 音频文件）
 *
 * transcribeFile() 同时供 FileContextInjector 在对话开始前自动转写音频附件。
 *
 * @author cortex
 */
@Component
public class VoiceManagerPlugin implements IBuiltinPlugin
{
    private static final Logger log = LoggerFactory.getLogger(VoiceManagerPlugin.class);

    @Autowired
    private IAiAgentFileService fileService;

    @Value("${cortex.profile:D:/cortex/uploadPath}")
    private String uploadPath;

    @Value("${voice.enabled:true}")
    private boolean voiceEnabled;

    @Value("${voice.asr.model:}")
    private String asrModel;

    @Value("${voice.asr.tokens:}")
    private String asrTokens;

    @Value("${voice.asr.language:auto}")
    private String asrLanguage;

    @Value("${voice.asr.num-threads:1}")
    private int asrNumThreads;

    @Value("${voice.tts.model:}")
    private String ttsModel;

    @Value("${voice.tts.tokens:}")
    private String ttsTokens;

    @Value("${voice.tts.lexicon:}")
    private String ttsLexicon;

    @Value("${voice.tts.num-threads:1}")
    private int ttsNumThreads;

    @Value("${voice.tts.speaker-id:0}")
    private int ttsSpeakerId;

    @Value("${voice.tts.speed:1.0}")
    private float ttsSpeed;

    private static volatile boolean nativeLoaded = false;

    private volatile OfflineRecognizer recognizer;
    private volatile OfflineTts tts;

    @Override
    public PluginInfo getPluginInfo()
    {
        PluginInfo info = new PluginInfo("语音管理", "voice-manager", "语音转文字(STT)和文字转语音(TTS) - 本地sherpa-onnx");
        info.setVersion("1.0.0");
        info.setAuthor("CORTEX");
        info.setCategory("voice");
        info.setEmoji("🎤");
        info.setRequireApproval(false);
        return info;
    }

    @Override
    public List<ToolDefinition> getTools()
    {
        List<ToolDefinition> tools = new ArrayList<>();

        // 1. voice_transcribe - STT
        ToolDefinition transcribe = new ToolDefinition();
        transcribe.setName("voice_transcribe");
        transcribe.setDescription(
            "将语音文件转为文字（支持中文、英文、日文、韩文、粤语）。支持格式: wav（推荐16kHz单声道）。\n" +
            "参数: fileId（语音文件ID，从file_list获取）。"
        );
        Map<String, Object> sttSchema = new HashMap<>();
        sttSchema.put("type", "object");
        Map<String, Object> sttProps = new HashMap<>();
        sttProps.put("fileId", Map.of("type", "integer", "description", "语音文件ID"));
        sttSchema.put("properties", sttProps);
        sttSchema.put("required", List.of("fileId"));
        transcribe.setInputSchema(sttSchema);
        tools.add(transcribe);

        // 2. voice_speak - TTS
        ToolDefinition speak = new ToolDefinition();
        speak.setName("voice_speak");
        speak.setDescription(
            "将文字转为语音并生成音频文件。返回音频文件URL，用户可以点击播放。\n" +
            "适用于：用户要求朗读内容、语音播报场景。\n" +
            "参数: text（要转语音的文字，建议500字以内）。"
        );
        Map<String, Object> ttsSchema = new HashMap<>();
        ttsSchema.put("type", "object");
        Map<String, Object> ttsProps = new HashMap<>();
        ttsProps.put("text", Map.of("type", "string", "description", "要转为语音的文字内容"));
        ttsSchema.put("properties", ttsProps);
        ttsSchema.put("required", List.of("text"));
        speak.setInputSchema(ttsSchema);
        tools.add(speak);

        return tools;
    }

    @Override
    public String executeTool(String toolName, Map<String, Object> arguments)
    {
        try
        {
            switch (toolName)
            {
                case "voice_transcribe": return transcribe(arguments);
                case "voice_speak": return speak(arguments);
                default:
                    return ToolResult.error("未知工具: " + toolName).toJson();
            }
        }
        catch (Exception e)
        {
            log.error("语音工具执行失败 [tool={}]", toolName, e);
            return ToolResult.error("执行失败: " + e.getMessage()).toJson();
        }
    }

    // ==================== 原生库与模型加载 ====================

    private void ensureNativeLoaded()
    {
        if (!nativeLoaded)
        {
            synchronized (VoiceManagerPlugin.class)
            {
                if (!nativeLoaded)
                {
                    LibraryUtils.load();
                    nativeLoaded = true;
                    log.info("sherpa-onnx 原生库加载完成");
                }
            }
        }
    }

    private OfflineRecognizer getRecognizer()
    {
        if (recognizer == null)
        {
            synchronized (VoiceManagerPlugin.class)
            {
                if (recognizer == null)
                {
                    ensureNativeLoaded();
                    OfflineSenseVoiceModelConfig senseVoice = OfflineSenseVoiceModelConfig.builder()
                            .setModel(asrModel)
                            .setLanguage(asrLanguage)
                            .setInverseTextNormalization(true)
                            .build();
                    OfflineModelConfig modelConfig = OfflineModelConfig.builder()
                            .setSenseVoice(senseVoice)
                            .setTokens(asrTokens)
                            .setNumThreads(asrNumThreads)
                            .setDebug(false)
                            .build();
                    FeatureConfig featConfig = FeatureConfig.builder()
                            .setSampleRate(16000)
                            .build();
                    OfflineRecognizerConfig config = OfflineRecognizerConfig.builder()
                            .setFeatureConfig(featConfig)
                            .setOfflineModelConfig(modelConfig)
                            .build();
                    recognizer = new OfflineRecognizer(config);
                    log.info("sherpa-onnx ASR(SenseVoice) 模型加载完成 [model={}]", asrModel);
                }
            }
        }
        return recognizer;
    }

    private OfflineTts getTts()
    {
        if (tts == null)
        {
            synchronized (VoiceManagerPlugin.class)
            {
                if (tts == null)
                {
                    ensureNativeLoaded();
                    OfflineTtsVitsModelConfig vits = OfflineTtsVitsModelConfig.builder()
                            .setModel(ttsModel)
                            .setLexicon(ttsLexicon)
                            .setTokens(ttsTokens)
                            .setNoiseScale(0.667f)
                            .setNoiseScaleW(0.8f)
                            .setLengthScale(1.0f)
                            .build();
                    OfflineTtsModelConfig modelConfig = OfflineTtsModelConfig.builder()
                            .setVits(vits)
                            .setNumThreads(ttsNumThreads)
                            .setDebug(false)
                            .build();
                    OfflineTtsConfig config = OfflineTtsConfig.builder()
                            .setModel(modelConfig)
                            .build();
                    tts = new OfflineTts(config);
                    log.info("sherpa-onnx TTS(VITS) 模型加载完成 [model={}, speakers={}]", ttsModel, tts.getNumSpeakers());
                }
            }
        }
        return tts;
    }

    // ==================== STT: 语音转文字 ====================

    /**
     * 转写音频文件为文字。
     * 供 FileContextInjector 自动转写音频附件，也供 voice_transcribe 工具调用。
     *
     * @param fileId 音频文件ID
     * @return 转写文本；文件不存在或音频文件丢失返回 null
     * @throws Exception 模型加载或推理失败时抛出
     */
    public String transcribeFile(Long fileId) throws Exception
    {
        AiAgentFile file = fileService.selectAiAgentFileByFileId(fileId);
        if (file == null)
        {
            return null;
        }
        Path audioPath = Paths.get(uploadPath, file.getFilePath());
        if (!Files.exists(audioPath))
        {
            log.warn("音频文件不存在 [fileId={}, path={}]", fileId, audioPath);
            return null;
        }

        log.info("STT 转写开始 [file={}]", file.getFileName());
        String text = transcribePath(audioPath.toString());
        log.info("STT 转写完成 [file={}, textLen={}]", file.getFileName(), text == null ? 0 : text.length());
        return text;
    }

    /**
     * 转写指定路径的音频文件为文字（核心转写逻辑）。
     * 供 transcribeFile 和 REST 转写接口共用。
     *
     * @param audioPath 音频文件绝对路径（wav 16kHz 单声道）
     * @return 转写文本；文件不存在返回 null
     * @throws Exception 模型加载或推理失败时抛出
     */
    public String transcribePath(String audioPath) throws Exception
    {
        if (!Files.exists(Paths.get(audioPath)))
        {
            log.warn("音频文件不存在 [path={}]", audioPath);
            return null;
        }

        // sherpa-onnx 的 WaveReader 只支持 wav；前端录音已转为 16kHz 单声道 wav
        OfflineRecognizer rec = getRecognizer();
        WaveReader reader = new WaveReader(audioPath);
        float[] samples = reader.getSamples();
        int sampleRate = reader.getSampleRate();

        OfflineStream stream = rec.createStream();
        stream.acceptWaveform(samples, sampleRate);
        rec.decode(stream);
        OfflineRecognizerResult result = rec.getResult(stream);
        stream.release();

        return result.getText();
    }

    private String transcribe(Map<String, Object> args) throws Exception
    {
        Long fileId = toLong(args.get("fileId"));
        String text;
        try
        {
            text = transcribeFile(fileId);
        }
        catch (Exception e)
        {
            log.error("ASR 转写失败 [fileId={}]", fileId, e);
            return ToolResult.error("语音识别失败: " + e.getMessage()).toJson();
        }
        if (text == null)
        {
            return ToolResult.error("文件不存在 (fileId=" + fileId + ")").toJson();
        }
        if (text.isBlank())
        {
            return ToolResult.error("语音转写结果为空").toJson();
        }

        ToolResult tr = ToolResult.success("语音转写成功");
        tr.addData("text", text);
        return tr.toJson();
    }

    // ==================== TTS: 文字转语音 ====================

    private String speak(Map<String, Object> args) throws Exception
    {
        String text = (String) args.get("text");
        if (text == null || text.isBlank())
        {
            return ToolResult.error("缺少参数: text").toJson();
        }

        if (text.length() > 1000)
        {
            text = text.substring(0, 1000);
            log.info("TTS 文本过长，已截断至1000字");
        }

        OfflineTts ttsEngine;
        try
        {
            ttsEngine = getTts();
        }
        catch (Exception e)
        {
            log.error("TTS 模型加载失败", e);
            return ToolResult.error("语音合成模型加载失败: " + e.getMessage()).toJson();
        }

        log.info("TTS 合成开始 [textLen={}]", text.length());

        GeneratedAudio audio;
        synchronized (VoiceManagerPlugin.class)
        {
            audio = ttsEngine.generate(text, ttsSpeakerId, ttsSpeed);
        }

        // 落盘 wav
        String sessionId = RuntimeContextHolder.getSessionId();
        String userLoginName = RuntimeContextHolder.getUserLoginName();
        String businessSystem = RuntimeContextHolder.getBusinessSystem();
        if (userLoginName == null) userLoginName = "system";
        if (businessSystem == null) businessSystem = "cortex";
        if (sessionId == null) sessionId = "temp";

        String uuid = UUID.randomUUID().toString().replace("-", "");
        String audioFileName = "tts_" + uuid + ".wav";
        Path audioDir = Paths.get(uploadPath, "agent-workspace", businessSystem, userLoginName, sessionId);
        Files.createDirectories(audioDir);
        Path audioPath = audioDir.resolve(audioFileName);

        WaveWriter.write(audioPath.toString(), audio.getSamples(), audio.getSampleRate());

        Path uploadRoot = Paths.get(uploadPath);
        String relativePath = uploadRoot.relativize(audioPath).toString().replace("\\", "/");

        AiAgentFile fileRecord = new AiAgentFile();
        fileRecord.setFileName(audioFileName);
        fileRecord.setFilePath(relativePath);
        fileRecord.setFileSize((long) audio.getSamples().length * 2); // 16-bit mono PCM
        fileRecord.setFileType("generated");
        fileRecord.setSessionId(sessionId);
        fileRecord.setBusinessSystem(businessSystem);
        fileRecord.setUserLoginName(userLoginName);
        fileRecord.setRemark("TTS generated");
        fileService.insertAiAgentFile(fileRecord);

        log.info("TTS 合成完成 [file={}, samples={}]", audioFileName, audio.getSamples().length);

        ToolResult tr = ToolResult.success("语音合成成功");
        tr.addData("audioFileId", fileRecord.getFileId());
        tr.addData("audioFileName", audioFileName);
        tr.addData("audioUrl", "/agent/api/file/view/" + fileRecord.getFileId());
        tr.addData("textLength", text.length());
        return tr.toJson();
    }

    /**
     * 文字转语音并返回 wav 字节数据。
     * 供 REST 接口直接朗读 AI 回复使用，不需要创建文件记录。
     *
     * @param text 要合成的文字
     * @param speakerId 发音人ID（null 则用默认配置）
     * @return wav 文件字节数组；文本为空返回 null
     * @throws Exception 模型加载或合成失败时抛出
     */
    public byte[] generateSpeechWav(String text, Integer speakerId) throws Exception
    {
        if (text == null || text.isBlank())
        {
            return null;
        }
        if (text.length() > 1000)
        {
            text = text.substring(0, 1000);
        }

        OfflineTts ttsEngine = getTts();
        int sid = speakerId != null ? speakerId : ttsSpeakerId;
        GeneratedAudio audio;
        synchronized (VoiceManagerPlugin.class)
        {
            audio = ttsEngine.generate(text, sid, ttsSpeed);
        }

        Path tempFile = Files.createTempFile("tts_", ".wav");
        WaveWriter.write(tempFile.toString(), audio.getSamples(), audio.getSampleRate());
        byte[] wavBytes = Files.readAllBytes(tempFile);
        Files.deleteIfExists(tempFile);
        return wavBytes;
    }

    // ==================== Helpers ====================

    private Long toLong(Object obj)
    {
        if (obj == null) return 0L;
        if (obj instanceof Number) return ((Number) obj).longValue();
        return Long.parseLong(obj.toString());
    }
}