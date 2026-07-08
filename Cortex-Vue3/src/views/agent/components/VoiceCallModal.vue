<template>
  <div v-if="visible" class="voice-call-overlay" @click.self="hangup">
    <div class="voice-call-panel">
      <!-- Agent avatar with animated rings -->
      <div class="avatar-wrapper">
        <div class="ring ring-1" :class="callStatus"></div>
        <div class="ring ring-2" :class="callStatus"></div>
        <div class="ring ring-3" :class="callStatus"></div>
        <div class="agent-avatar" :class="callStatus">
          {{ agentName?.charAt(0) || 'A' }}
        </div>
      </div>

      <!-- Status text -->
      <div class="status-text" :class="callStatus">{{ statusText }}</div>

      <!-- Waveform bars (animated when listening/speaking) -->
      <div class="waveform" v-if="callStatus === 'listening' || callStatus === 'speaking'">
        <span v-for="i in 5" :key="i" :style="{ animationDelay: (i * 0.1) + 's' }"></span>
      </div>

      <!-- Hang up button -->
      <button class="btn-hangup" @click="hangup" :disabled="callStatus === 'connecting'">
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M10.68 13.31a16 16 0 0 0 3.41 2.6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7 2 2 0 0 1 1.72 2v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91"/>
          <line x1="4.93" y1="19.07" x2="19.07" y2="4.93"/>
        </svg>
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onUnmounted, nextTick, watch } from 'vue'
import { getToken } from '@/utils/auth'

const props = defineProps({
  visible: Boolean,
  sessionId: String,
  agentCode: String,
  agentName: String,
  modelId: [Number, String],
})
const emit = defineEmits(['close', 'message'])

const BASE_URL = import.meta.env.VITE_APP_BASE_API

const callStatus = ref('idle') // idle, connecting, listening, thinking, speaking
const audioLevel = ref(0)
const messages = reactive([])
const transcriptRef = ref(null)

let audioContext = null
let mediaStream = null
let analyser = null
let scriptProcessor = null
let vadTimer = null
let currentReader = null
let abortController = null

// VAD state
let isSpeaking = false
let silenceStart = 0
let speechStart = 0
let recordedSamples = []
let contextSampleRate = 48000

// TTS playback queue
let ttsQueue = []
let isPlayingTts = false
let currentSource = null

const statusText = computed(() => {
  const map = { idle: '', connecting: 'Connecting...', listening: 'Listening...', thinking: 'Thinking...', speaking: 'Speaking...' }
  return map[callStatus.value] || ''
})

watch(() => props.visible, (val) => {
  if (val) startCall()
})

async function startCall() {
  callStatus.value = 'connecting'
  messages.length = 0
  try {
    mediaStream = await navigator.mediaDevices.getUserMedia({
      audio: { channelCount: 1, sampleRate: 16000, echoCancellation: true, noiseSuppression: true }
    })
    audioContext = new (window.AudioContext || window.webkitAudioContext)()
    contextSampleRate = audioContext.sampleRate

    const source = audioContext.createMediaStreamSource(mediaStream)
    analyser = audioContext.createAnalyser()
    analyser.fftSize = 512
    analyser.smoothingTimeConstant = 0.3
    source.connect(analyser)

    // ScriptProcessor for PCM capture (buffer size 4096)
    scriptProcessor = audioContext.createScriptProcessor(4096, 1, 1)
    scriptProcessor.onaudioprocess = onAudioProcess
    source.connect(scriptProcessor)
    // Connect through silent gain so onaudioprocess fires but no mic feedback
    const silentGain = audioContext.createGain()
    silentGain.gain.value = 0
    scriptProcessor.connect(silentGain)
    silentGain.connect(audioContext.destination)

    callStatus.value = 'listening'
    startVAD()
  } catch (e) {
    console.error('Microphone access failed:', e)
    callStatus.value = 'idle'
    emit('close')
  }
}

function onAudioProcess(e) {
  // Only record when speaking
  if (!isSpeaking) return
  const input = e.inputBuffer.getChannelData(0)
  // Copy samples (Float32)
  recordedSamples.push(new Float32Array(input))
}

function startVAD() {
  const buffer = new Uint8Array(analyser.fftSize)
  let consecutiveSpeechFrames = 0

  vadTimer = setInterval(() => {
    // VAD runs in ALL states — user can interrupt anytime
    analyser.getByteTimeDomainData(buffer)
    let sum = 0
    for (let i = 0; i < buffer.length; i++) {
      const v = (buffer[i] - 128) / 128
      sum += v * v
    }
    const rms = Math.sqrt(sum / buffer.length)
    audioLevel.value = Math.min(100, rms * 300)

    // Adaptive threshold: higher during speaking to filter TTS speaker leakage
    let threshold = 0.015
    let requiredFrames = 2  // 200ms to confirm speech start
    if (callStatus.value === 'speaking') {
      threshold = 0.05   // Higher — TTS audio leaks into mic
      requiredFrames = 1 // Fast interrupt — 100ms is enough
    } else if (callStatus.value === 'thinking') {
      threshold = 0.03
      requiredFrames = 1 // Fast interrupt during thinking too
    }

    const now = Date.now()

    if (rms > threshold) {
      if (!isSpeaking) {
        consecutiveSpeechFrames++
        if (consecutiveSpeechFrames >= requiredFrames) {
          isSpeaking = true
          speechStart = now
          recordedSamples = []
          // Barge-in: stop TTS and cancel current request
          if (callStatus.value === 'speaking' || callStatus.value === 'thinking') {
            stopAllTts()
            if (abortController) abortController.abort()
            // Tell backend to stop agent processing
            if (props.sessionId) {
              fetch(`${BASE_URL}/agent/api/session/${props.sessionId}/interrupt`, {
                method: 'POST',
                headers: { 'Authorization': 'Bearer ' + getToken() }
              }).catch(() => {})
            }
          }
        }
      }
      silenceStart = 0
    } else {
      consecutiveSpeechFrames = 0
      if (isSpeaking) {
        if (silenceStart === 0) {
          silenceStart = now
        }
        // 600ms silence = speech ended (reduced from 800 for snappier feel)
        if (now - silenceStart > 600) {
          const speechDuration = now - speechStart
          isSpeaking = false
          if (speechDuration > 200) {
            sendAudioToBackend()
          }
          recordedSamples = []
          silenceStart = 0
        }
      }
    }
  }, 100)
}

async function sendAudioToBackend() {
  if (recordedSamples.length === 0) return
  callStatus.value = 'thinking'

  // Merge recorded samples
  const totalLength = recordedSamples.reduce((s, a) => s + a.length, 0)
  const merged = new Float32Array(totalLength)
  let offset = 0
  for (const chunk of recordedSamples) {
    merged.set(chunk, offset)
    offset += chunk.length
  }
  recordedSamples = []

  // Resample to 16kHz if needed
  const targetRate = 16000
  const resampled = contextSampleRate !== targetRate
    ? downsample(merged, contextSampleRate, targetRate)
    : merged

  // Encode as WAV
  const wavBlob = encodeWav(resampled, targetRate)

  // Send to backend via SSE
  abortController = new AbortController()
  const formData = new FormData()
  formData.append('file', wavBlob, 'speech.wav')
  formData.append('sessionId', props.sessionId)
  formData.append('agentCode', props.agentCode)
  if (props.modelId) formData.append('modelId', props.modelId)

  try {
    const response = await fetch(`${BASE_URL}/agent/api/voice/stream`, {
      method: 'POST',
      headers: { 'Authorization': 'Bearer ' + getToken() },
      body: formData,
      signal: abortController.signal
    })

    if (!response.ok) throw new Error(`HTTP ${response.status}`)

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    currentReader = reader

    let aiText = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        const trimmed = line.trim()
        if (!trimmed || trimmed.startsWith(':')) continue
        if (trimmed.startsWith('data:')) {
          const jsonStr = trimmed.substring(5).trim()
          if (!jsonStr || jsonStr === '[DONE]') continue
          try {
            const event = JSON.parse(jsonStr)
            const { type, data } = event

            if (type === 'transcript') {
              messages.push({ role: 'user', text: data.text })
              aiText = ''
              scrollToBottom()
            } else if (type === 'delta') {
              aiText += data.text
              callStatus.value = 'thinking'
            } else if (type === 'tts') {
              if (!aiText) aiText = data.text
              // Show AI text on first TTS chunk
              const existing = messages.find(m => m.role === 'assistant' && m.text === aiText)
              if (!existing && data.text) {
                // Push partial AI message
              }
              callStatus.value = 'speaking'
              playTtsAudio(data.audio)
            } else if (type === 'done') {
              if (aiText) {
                messages.push({ role: 'assistant', text: aiText })
                emit('message', { role: 'assistant', content: aiText })
                scrollToBottom()
              }
              callStatus.value = 'listening'
            } else if (type === 'error') {
              console.error('Voice error:', data?.message)
              callStatus.value = 'listening'
            }
          } catch (e) {
            console.error('SSE parse error:', e)
          }
        }
      }
    }

    // Handle remaining buffer
    if (buffer.trim().startsWith('data:')) {
      const jsonStr = buffer.trim().substring(5).trim()
      if (jsonStr.startsWith('{')) {
        try {
          const event = JSON.parse(jsonStr)
          if (event.type === 'done' && aiText) {
            messages.push({ role: 'assistant', text: aiText })
            emit('message', { role: 'assistant', content: aiText })
          }
        } catch (e) {}
      }
    }

    if (callStatus.value !== 'listening') {
      callStatus.value = 'listening'
    }
  } catch (e) {
    if (e.name !== 'AbortError') {
      console.error('Voice stream error:', e)
    }
    callStatus.value = 'listening'
  } finally {
    currentReader = null
    abortController = null
  }
}

// ===== TTS Playback =====

function playTtsAudio(base64Wav) {
  const bytes = atob(base64Wav)
  const arrayBuffer = new ArrayBuffer(bytes.length)
  const view = new Uint8Array(arrayBuffer)
  for (let i = 0; i < bytes.length; i++) {
    view[i] = bytes.charCodeAt(i)
  }

  audioContext.decodeAudioData(arrayBuffer, (audioBuffer) => {
    const source = audioContext.createBufferSource()
    const ttsGainNode = audioContext.createGain()
    ttsGainNode.gain.value = 3.0  // Boost TTS volume

    source.buffer = audioBuffer
    source.connect(ttsGainNode)
    ttsGainNode.connect(audioContext.destination)

    source.onended = () => {
      if (currentSource === source) {
        currentSource = null
        isPlayingTts = false
      }
    }

    ttsQueue.push(source)
    playNextTts()
  }, (err) => {
    console.error('Audio decode error:', err)
  })
}

function playNextTts() {
  if (isPlayingTts || ttsQueue.length === 0) return
  isPlayingTts = true
  currentSource = ttsQueue.shift()
  currentSource.start()
}

function stopAllTts() {
  ttsQueue.forEach(s => { try { s.stop() } catch(e) {} })
  ttsQueue = []
  if (currentSource) {
    try { currentSource.stop() } catch(e) {}
    currentSource = null
  }
  isPlayingTts = false
}

// ===== Audio Helpers =====

function downsample(samples, fromRate, toRate) {
  if (fromRate === toRate) return samples
  const ratio = fromRate / toRate
  const newLength = Math.floor(samples.length / ratio)
  const result = new Float32Array(newLength)
  for (let i = 0; i < newLength; i++) {
    const idx = Math.floor(i * ratio)
    result[i] = samples[idx]
  }
  return result
}

function encodeWav(samples, sampleRate) {
  const buffer = new ArrayBuffer(44 + samples.length * 2)
  const view = new DataView(buffer)

  // WAV header
  const writeString = (offset, str) => {
    for (let i = 0; i < str.length; i++) view.setUint8(offset + i, str.charCodeAt(i))
  }
  writeString(0, 'RIFF')
  view.setUint32(4, 36 + samples.length * 2, true)
  writeString(8, 'WAVE')
  writeString(12, 'fmt ')
  view.setUint32(16, 16, true)        // fmt chunk size
  view.setUint16(20, 1, true)         // PCM
  view.setUint16(22, 1, true)         // mono
  view.setUint32(24, sampleRate, true)
  view.setUint32(28, sampleRate * 2, true) // byte rate
  view.setUint16(32, 2, true)         // block align
  view.setUint16(34, 16, true)        // bits per sample
  writeString(36, 'data')
  view.setUint32(40, samples.length * 2, true)

  // PCM data (Float32 -> Int16)
  let offset = 44
  for (let i = 0; i < samples.length; i++) {
    const s = Math.max(-1, Math.min(1, samples[i]))
    view.setInt16(offset, s < 0 ? s * 0x8000 : s * 0x7FFF, true)
    offset += 2
  }

  return new Blob([view], { type: 'audio/wav' })
}

function scrollToBottom() {
  nextTick(() => {
    if (transcriptRef.value) {
      transcriptRef.value.scrollTop = transcriptRef.value.scrollHeight
    }
  })
}

function hangup() {
  stopAllTts()
  if (abortController) abortController.abort()
  if (vadTimer) { clearInterval(vadTimer); vadTimer = null }
  if (scriptProcessor) { scriptProcessor.disconnect(); scriptProcessor = null }
  if (analyser) { analyser.disconnect(); analyser = null }
  if (mediaStream) { mediaStream.getTracks().forEach(t => t.stop()); mediaStream = null }
  if (audioContext) { audioContext.close().catch(()=>{}); audioContext = null }
  callStatus.value = 'idle'
  emit('close')
}

onUnmounted(() => {
  hangup()
})
</script>

<style scoped>
.voice-call-overlay {
  position: fixed; top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0,0,0,0.35); z-index: 3000;
  display: flex; align-items: center; justify-content: center;
  backdrop-filter: blur(4px);
}
.voice-call-panel {
  width: 320px; background: #fff; border-radius: 24px;
  padding: 40px 24px 32px;
  display: flex; flex-direction: column; align-items: center; gap: 20px;
  box-shadow: 0 12px 48px rgba(0,0,0,0.15);
}

/* Avatar with rings */
.avatar-wrapper {
  position: relative; width: 120px; height: 120px;
  display: flex; align-items: center; justify-content: center;
}
.agent-avatar {
  width: 72px; height: 72px; border-radius: 50%;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff; font-size: 28px; font-weight: 600;
  display: flex; align-items: center; justify-content: center;
  z-index: 2; position: relative;
}
.ring {
  position: absolute; border-radius: 50%; border: 2px solid;
  opacity: 0;
}
.ring-1 { width: 72px; height: 72px; }
.ring-2 { width: 92px; height: 92px; }
.ring-3 { width: 112px; height: 112px; }

/* Listening: green expanding rings */
.ring.listening { border-color: #22c55e; animation: ring-expand 2s infinite; }
.ring-2.listening { animation-delay: 0.5s; }
.ring-3.listening { animation-delay: 1s; }

/* Thinking: amber pulse */
.ring.thinking { border-color: #f59e0b; animation: ring-pulse 1.2s infinite; }
.ring-2.thinking { animation-delay: 0.3s; }
.ring-3.thinking { animation-delay: 0.6s; }

/* Speaking: blue expanding rings */
.ring.speaking { border-color: #3b82f6; animation: ring-expand 1.5s infinite; }
.ring-2.speaking { animation-delay: 0.4s; }
.ring-3.speaking { animation-delay: 0.8s; }

/* Connecting: gray quick pulse */
.ring.connecting { border-color: #94a3b8; animation: ring-pulse 0.6s infinite; }

@keyframes ring-expand {
  0% { transform: scale(0.8); opacity: 0.8; }
  100% { transform: scale(1.3); opacity: 0; }
}
@keyframes ring-pulse {
  0%, 100% { transform: scale(0.9); opacity: 0.3; }
  50% { transform: scale(1.15); opacity: 0.7; }
}

/* Status text */
.status-text {
  font-size: 14px; font-weight: 500; color: #64748b;
  letter-spacing: 0.5px; min-height: 20px;
}
.status-text.listening { color: #22c55e; }
.status-text.thinking { color: #f59e0b; }
.status-text.speaking { color: #3b82f6; }
.status-text.connecting { color: #94a3b8; }

/* Waveform bars */
.waveform {
  display: flex; align-items: center; gap: 4px; height: 24px;
}
.waveform span {
  width: 3px; height: 8px; border-radius: 2px;
  background: currentColor; animation: wave 0.8s infinite ease-in-out;
}
.waveform span:nth-child(1) { animation-delay: 0s; }
.waveform span:nth-child(2) { animation-delay: 0.1s; }
.waveform span:nth-child(3) { animation-delay: 0.2s; }
.waveform span:nth-child(4) { animation-delay: 0.3s; }
.waveform span:nth-child(5) { animation-delay: 0.4s; }

.listening .waveform span, .waveform { color: #22c55e; }
.speaking ~ .waveform, .voice-call-panel:has(.status-text.speaking) .waveform { color: #3b82f6; }

@keyframes wave {
  0%, 100% { height: 6px; }
  50% { height: 20px; }
}

/* Hang up button */
.btn-hangup {
  width: 52px; height: 52px; border-radius: 50%; border: none;
  background: #ef4444; color: #fff; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  transition: transform 0.15s, background 0.2s;
  box-shadow: 0 4px 12px rgba(239,68,68,0.3);
}
.btn-hangup:hover { background: #dc2626; transform: scale(1.08); }
.btn-hangup:disabled { background: #cbd5e1; cursor: not-allowed; box-shadow: none; }
</style>
