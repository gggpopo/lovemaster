<template>
  <div class="chat-container">
    <div
      ref="messagesContainer"
      class="chat-messages"
      role="log"
      aria-live="polite"
      aria-relevant="additions text"
    >
      <div
        v-for="(msg, index) in displayMessages"
        :key="msg.id"
        class="message-row"
      >
        <template v-if="!msg.isUser">
          <div class="avatar ai-avatar">
            <AiAvatarFallback :type="aiType" />
          </div>
          <div class="bubble-stack">
            <div class="message-bubble ai-bubble" :class="[msg.type]">
              <div
                v-if="connectionStatus === 'connecting' && index === displayMessages.length - 1 && !msg.content"
                class="ai-skeleton"
                aria-label="正在生成回复"
              >
                <div class="sk-line w50" />
                <div class="sk-line w85" />
                <div class="sk-line w65" />
              </div>

              <template v-if="msg.structured">
                <StructuredResponseRenderer :response="msg.structured" />
              </template>
              <template v-else>
                <template v-for="(seg, segIndex) in getSegments(msg.content)" :key="segIndex">
                  <div v-if="seg.type === 'text'" class="message-text">{{ seg.content }}</div>
                  <div
                    v-else-if="seg.type === 'image' && shouldRenderAiImage(msg.id, seg.content, segIndex)"
                    class="message-image-block"
                  >
                    <a :href="seg.content" target="_blank" rel="noopener noreferrer">
                      <img
                        :src="seg.content"
                        class="ai-message-image"
                        loading="lazy"
                        alt="AI 回复图片"
                        @error="handleAiImageError(msg.id, seg.content, segIndex)"
                      />
                    </a>
                  </div>
                  <LocationCard v-else-if="seg.type === 'location_card'" v-bind="parseLocationCard(seg.content)" />
                </template>
              </template>

              <span
                v-if="connectionStatus === 'connecting' && index === displayMessages.length - 1"
                class="typing-indicator"
                aria-hidden="true"
              >▋</span>
            </div>
            <div class="message-meta">{{ formatTime(msg.time) }}</div>
          </div>
        </template>

        <template v-else>
          <div class="bubble-stack user-stack">
            <div class="message-bubble user-bubble" :class="[msg.type]">
              <div v-if="msg.images && msg.images.length > 0" class="message-images">
                <img
                  v-for="(img, imgIndex) in msg.images"
                  :key="imgIndex"
                  :src="img"
                  class="message-image"
                  alt="用户上传图片"
                />
              </div>
              <div class="message-text">{{ msg.content }}</div>
            </div>
            <div class="message-meta user-meta">{{ formatTime(msg.time) }}</div>
          </div>
          <div class="avatar user-avatar">
            <div class="avatar-placeholder">我</div>
          </div>
        </template>
      </div>
    </div>

    <footer class="composer" :class="{ 'has-preview': selectedImages.length > 0 }">
      <div v-if="normalizedPromptChips.length" class="chip-row" aria-label="提示词建议">
        <button
          v-for="chip in normalizedPromptChips"
          :key="chip.value"
          type="button"
          class="chip"
          @click="usePromptChip(chip.value)"
        >
          {{ chip.label }}
        </button>
      </div>

      <div class="tool-row">
        <button
          v-if="showVoice"
          type="button"
          class="tool-btn"
          :disabled="connectionStatus === 'connecting'"
          @click="emit('voice-request')"
        >
          语音
        </button>

        <button
          v-if="enableImages"
          type="button"
          class="tool-btn"
          :disabled="connectionStatus === 'connecting'"
          @click="triggerFileInput"
        >
          上传图片
        </button>

        <div v-if="normalizedToneOptions.length" class="tone-group" role="radiogroup" aria-label="回复语气">
          <button
            v-for="tone in normalizedToneOptions"
            :key="tone.value"
            type="button"
            class="tone-btn"
            :class="{ active: tone.value === currentTone }"
            :aria-checked="tone.value === currentTone"
            role="radio"
            @click="selectTone(tone.value)"
          >
            {{ tone.label }}
          </button>
        </div>

        <div v-if="normalizedQuickActions.length" class="quick-group" aria-label="快捷动作">
          <button
            v-for="action in normalizedQuickActions"
            :key="action.value"
            type="button"
            class="quick-btn"
            :disabled="connectionStatus === 'connecting'"
            @click="triggerQuickAction(action.value)"
          >
            {{ action.label }}
          </button>
        </div>
      </div>

      <input
        v-if="enableImages"
        ref="fileInput"
        type="file"
        accept="image/jpeg,image/png,image/gif,image/webp"
        multiple
        class="hidden-file"
        @change="handleFileSelect"
      />

      <div v-if="enableImages && selectedImages.length > 0" class="image-preview-area">
        <div v-for="(img, index) in selectedImages" :key="index" class="preview-item">
          <img :src="img" class="preview-image" alt="待发送图片" />
          <button type="button" class="remove-image-btn" aria-label="移除图片" @click="removeImage(index)">×</button>
        </div>
      </div>

      <div class="input-row">
        <textarea
          ref="inputRef"
          v-model="inputMessage"
          class="input-box"
          :placeholder="placeholder"
          :disabled="connectionStatus === 'connecting'"
          rows="1"
          @keydown="onInputKeydown"
          @paste="handlePaste"
          @input="autoResizeTextarea"
        />

        <button
          type="button"
          class="send-button"
          :disabled="sendDisabled"
          aria-label="发送消息"
          @click="sendMessage"
        >
          发送
        </button>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import AiAvatarFallback from './AiAvatarFallback.vue'
import LocationCard from './LocationCard.vue'
import StructuredResponseRenderer from './StructuredResponseRenderer.vue'
import { parseMessage } from '../utils/messageParser'

const props = defineProps({
  messages: {
    type: Array,
    default: () => []
  },
  connectionStatus: {
    type: String,
    default: 'disconnected'
  },
  aiType: {
    type: String,
    default: 'default'
  },
  enableImages: {
    type: Boolean,
    default: true
  },
  promptChips: {
    type: Array,
    default: () => []
  },
  toneOptions: {
    type: Array,
    default: () => []
  },
  selectedTone: {
    type: String,
    default: ''
  },
  quickActions: {
    type: Array,
    default: () => []
  },
  placeholder: {
    type: String,
    default: '先说说你现在遇到的情况...'
  },
  showVoice: {
    type: Boolean,
    default: true
  }
})

const emit = defineEmits(['send-message', 'quick-action', 'tone-change', 'voice-request'])

const inputMessage = ref('')
const messagesContainer = ref(null)
const fileInput = ref(null)
const inputRef = ref(null)
const selectedImages = ref([])
const currentTone = ref(props.selectedTone)
const failedAiImageKeys = ref(new Set())

const MAX_IMAGE_SIZE = 5 * 1024 * 1024
let rafId = 0

const normalizeItem = (item) => {
  if (typeof item === 'string') {
    return { label: item, value: item }
  }
  const value = item?.value || item?.key || item?.label || ''
  const label = item?.label || item?.name || value
  return { label, value }
}

const normalizedPromptChips = computed(() => props.promptChips.map(normalizeItem))
const normalizedToneOptions = computed(() => props.toneOptions.map(normalizeItem))
const normalizedQuickActions = computed(() => props.quickActions.map(normalizeItem))

const buildAiImageKey = (messageId, imageUrl, segIndex) => {
  return `${String(messageId || 'ai')}::${segIndex}::${String(imageUrl || '')}`
}

const shouldRenderAiImage = (messageId, imageUrl, segIndex) => {
  const key = buildAiImageKey(messageId, imageUrl, segIndex)
  return !failedAiImageKeys.value.has(key)
}

const handleAiImageError = (messageId, imageUrl, segIndex) => {
  const key = buildAiImageKey(messageId, imageUrl, segIndex)
  if (failedAiImageKeys.value.has(key)) return
  const next = new Set(failedAiImageKeys.value)
  next.add(key)
  failedAiImageKeys.value = next
}

const normalizeDisplayMessage = (raw, index) => {
  if (typeof raw === 'string') {
    return {
      id: `legacy_ai_${index}_${Date.now()}`,
      content: raw,
      isUser: false,
      images: [],
      time: Date.now(),
      type: ''
    }
  }

  if (!raw || typeof raw !== 'object') {
    return {
      id: `invalid_ai_${index}_${Date.now()}`,
      content: '',
      isUser: false,
      images: [],
      time: Date.now(),
      type: ''
    }
  }

  const role = String(raw.role || raw.sender || '').toLowerCase()
  const guessedIsUser = role === 'user' || role === 'human' || role === 'me'
  const isUser = typeof raw.isUser === 'boolean'
    ? raw.isUser
    : (raw.isUser === 1 || raw.isUser === '1' || raw.isUser === 'true' || guessedIsUser)
  const content = String(raw.content ?? raw.text ?? raw.message ?? '')
  const images = Array.isArray(raw.images)
    ? raw.images.filter((item) => typeof item === 'string' && item)
    : []
  const structured = raw.structured && typeof raw.structured === 'object' ? raw.structured : null
  const rawTime = Number(raw.time ?? raw.timestamp ?? raw.createdAt ?? raw.updatedAt)
  const time = Number.isFinite(rawTime) && rawTime > 0 ? rawTime : Date.now()

  return {
    id: String(raw.id || `${isUser ? 'user' : 'ai'}_${index}_${time}`),
    content,
    isUser: !!isUser,
    images,
    structured,
    time,
    type: String(raw.type || '')
  }
}

const displayMessages = computed(() => {
  const list = Array.isArray(props.messages) ? props.messages : []
  return list
    .map((item, index) => normalizeDisplayMessage(item, index))
    .filter((item) => {
      if (!item) return false
      if (item.content) return true
      if (item.images && item.images.length > 0) return true
      if (item.structured && Array.isArray(item.structured.blocks) && item.structured.blocks.length > 0) return true
      // 保留空内容 AI 占位消息（流式响应中会逐步回写）
      return item.isUser === false
    })
})

const sendDisabled = computed(() => {
  const noText = !inputMessage.value.trim()
  const noImage = !props.enableImages || selectedImages.value.length === 0
  return props.connectionStatus === 'connecting' || (noText && noImage)
})

watch(
  () => props.selectedTone,
  (value) => {
    currentTone.value = value || ''
  }
)

const getSegments = (content) => {
  return parseMessage(content)
}

const parseLocationCard = (jsonText) => {
  try {
    return JSON.parse(jsonText)
  } catch (e) {
    return {
      name: '地点信息解析失败',
      address: '',
      rating: '',
      cost: '',
      tel: '',
      photos: [],
      mapUrl: ''
    }
  }
}

const triggerFileInput = () => {
  if (!fileInput.value) return
  fileInput.value.click()
}

const handleFileSelect = (event) => {
  const files = event.target.files
  if (!files || files.length === 0) return

  const remainingSlots = 3 - selectedImages.value.length
  const filesToProcess = Array.from(files).slice(0, Math.max(0, remainingSlots))

  filesToProcess.forEach((file) => {
    if (file.size > MAX_IMAGE_SIZE) {
      alert(`图片 ${file.name} 超过 5MB 限制`)
      return
    }

    const reader = new FileReader()
    reader.onload = (e) => {
      if (e?.target?.result) {
        selectedImages.value.push(e.target.result)
      }
    }
    reader.readAsDataURL(file)
  })

  event.target.value = ''
}

const handlePaste = (event) => {
  if (!props.enableImages) return
  const clipboardData = event.clipboardData
  if (!clipboardData || !clipboardData.items) return

  const imageItems = Array.from(clipboardData.items).filter(
    (item) => item.kind === 'file' && item.type && item.type.startsWith('image/')
  )
  if (!imageItems.length) return

  event.preventDefault()

  const remainingSlots = 3 - selectedImages.value.length
  imageItems.slice(0, Math.max(0, remainingSlots)).forEach((item) => {
    const file = item.getAsFile()
    if (!file) return
    if (file.size > MAX_IMAGE_SIZE) {
      alert('图片超过 5MB 限制')
      return
    }

    const reader = new FileReader()
    reader.onload = (e) => {
      if (e?.target?.result) {
        selectedImages.value.push(e.target.result)
      }
    }
    reader.readAsDataURL(file)
  })
}

const removeImage = (index) => {
  selectedImages.value.splice(index, 1)
}

const selectTone = (value) => {
  currentTone.value = value
  emit('tone-change', value)
}

const usePromptChip = (value) => {
  if (!value) return
  if (inputMessage.value.trim()) {
    inputMessage.value = `${inputMessage.value.trim()}\n${value}`
  } else {
    inputMessage.value = value
  }
  nextTick(() => {
    autoResizeTextarea()
    inputRef.value?.focus()
  })
}

const triggerQuickAction = (value) => {
  emit('quick-action', value)
}

const onInputKeydown = (event) => {
  if (event.key !== 'Enter') return
  if (event.shiftKey || event.isComposing) return
  event.preventDefault()
  sendMessage()
}

const sendMessage = () => {
  if (sendDisabled.value) return

  emit('send-message', {
    text: inputMessage.value.trim(),
    images: props.enableImages ? [...selectedImages.value] : [],
    tone: currentTone.value
  })

  inputMessage.value = ''
  selectedImages.value = []

  nextTick(() => {
    autoResizeTextarea()
  })
}

const formatTime = (timestamp) => {
  const date = new Date(timestamp || Date.now())
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

const autoResizeTextarea = () => {
  if (!inputRef.value) return
  inputRef.value.style.height = 'auto'
  const nextHeight = Math.min(inputRef.value.scrollHeight, 168)
  inputRef.value.style.height = `${Math.max(44, nextHeight)}px`
}

const scrollToBottom = async () => {
  await nextTick()
  if (!messagesContainer.value) return
  if (rafId) cancelAnimationFrame(rafId)
  rafId = requestAnimationFrame(() => {
    if (!messagesContainer.value) return
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    rafId = 0
  })
}

watch(
  () => displayMessages.value.length,
  () => {
    scrollToBottom()
  }
)

watch(
  () => displayMessages.value[displayMessages.value.length - 1]?.content,
  () => {
    scrollToBottom()
  }
)

onMounted(() => {
  scrollToBottom()
  autoResizeTextarea()
})

onBeforeUnmount(() => {
  if (rafId) {
    cancelAnimationFrame(rafId)
    rafId = 0
  }
})
</script>

<style scoped>
.chat-container {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-3);
  overflow: hidden;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.9), rgba(245, 239, 230, 0.72));
  box-shadow: var(--shadow-md);
}

.chat-messages {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: var(--space-3) var(--space-3) var(--space-2);
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.message-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ai-avatar {
  margin-top: 2px;
}

.user-avatar {
  margin-top: 2px;
}

.avatar-placeholder {
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, rgba(162, 187, 220, 0.8), rgba(142, 74, 125, 0.32));
  color: #fff;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.bubble-stack {
  max-width: min(78%, 760px);
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.user-stack {
  margin-left: auto;
  align-items: flex-end;
}

.message-bubble {
  border: 1px solid transparent;
  padding: 12px 14px;
  border-radius: 16px;
  word-break: break-word;
}

.ai-bubble {
  border-color: var(--color-border);
  background: rgba(255, 255, 255, 0.86);
  border-top-left-radius: 6px;
}

.user-bubble {
  border-color: rgba(217, 158, 130, 0.35);
  background: linear-gradient(145deg, rgba(217, 158, 130, 0.2), rgba(234, 201, 193, 0.26));
  border-top-right-radius: 6px;
}

.message-text {
  white-space: pre-wrap;
  line-height: 1.62;
  font-size: 15px;
  color: var(--color-text);
}

.message-meta {
  color: var(--color-text-soft);
  font-size: 12px;
  padding-left: 2px;
}

.user-meta {
  text-align: right;
}

.message-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}

.message-image {
  width: min(220px, 46vw);
  max-height: 220px;
  border-radius: 10px;
  object-fit: cover;
  border: 1px solid var(--color-border);
}

.message-image-block {
  margin: 8px 0;
}

.ai-message-image {
  width: min(360px, 100%);
  max-height: 260px;
  border-radius: 12px;
  object-fit: cover;
  display: block;
  border: 1px solid var(--color-border);
}

.ai-skeleton {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.sk-line {
  height: 11px;
  border-radius: 999px;
  background: linear-gradient(
    90deg,
    rgba(61, 61, 61, 0.08) 0%,
    rgba(61, 61, 61, 0.16) 45%,
    rgba(61, 61, 61, 0.08) 90%
  );
  background-size: 200% 100%;
  animation: shimmer 1.1s linear infinite;
}

.sk-line.w50 { width: 50%; }
.sk-line.w65 { width: 65%; }
.sk-line.w85 { width: 85%; }

.typing-indicator {
  margin-left: 2px;
  font-weight: 700;
  color: var(--color-primary-strong);
  animation: blink 0.8s infinite;
}

.composer {
  border-top: 1px solid var(--color-border);
  background: rgba(255, 255, 255, 0.88);
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.chip-row {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 2px;
}

.chip {
  border: 1px solid rgba(217, 158, 130, 0.36);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.95);
  color: var(--color-text);
  white-space: nowrap;
  padding: 6px 12px;
  font-size: 12px;
  transition: transform 0.2s ease, border-color 0.2s ease, background-color 0.2s ease;
}

@media (hover: hover) {
  .chip:hover {
    transform: translateY(-1px);
    border-color: rgba(217, 158, 130, 0.62);
    background: rgba(245, 239, 230, 0.86);
  }
}

.tool-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.tool-btn,
.quick-btn,
.tone-btn {
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.94);
  padding: 5px 10px;
  font-size: 12px;
  color: var(--color-text-muted);
}

.tool-btn:disabled,
.quick-btn:disabled {
  opacity: 0.52;
  cursor: not-allowed;
}

.tone-group,
.quick-group {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.tone-btn.active {
  border-color: rgba(217, 158, 130, 0.5);
  background: rgba(217, 158, 130, 0.2);
  color: var(--color-text);
}

.hidden-file {
  display: none;
}

.image-preview-area {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.preview-item {
  position: relative;
  width: 68px;
  height: 68px;
}

.preview-image {
  width: 100%;
  height: 100%;
  border-radius: 10px;
  object-fit: cover;
  border: 1px solid var(--color-border);
}

.remove-image-btn {
  position: absolute;
  top: -6px;
  right: -6px;
  width: 20px;
  height: 20px;
  border: 0;
  border-radius: 999px;
  background: var(--color-danger);
  color: #fff;
  font-size: 14px;
  line-height: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.input-row {
  display: flex;
  align-items: flex-end;
  gap: 10px;
}

.input-box {
  flex: 1;
  border: 1px solid var(--color-border);
  border-radius: 14px;
  background: #fff;
  padding: 10px 12px;
  min-height: 44px;
  max-height: 168px;
  resize: none;
  line-height: 1.55;
  font-size: 15px;
  color: var(--color-text);
}

.input-box:focus {
  border-color: rgba(217, 158, 130, 0.52);
}

.send-button {
  min-width: 72px;
  height: 44px;
  border: 0;
  border-radius: 14px;
  color: #fff;
  background: linear-gradient(140deg, var(--color-primary), var(--color-primary-strong));
  font-weight: 700;
  box-shadow: 0 8px 20px rgba(199, 134, 103, 0.4);
}

.send-button:disabled {
  opacity: 0.56;
  cursor: not-allowed;
  box-shadow: none;
}

.ai-answer,
.ai-final,
.user-question {
  animation: messageRise 0.24s ease;
}

@keyframes messageRise {
  from {
    opacity: 0;
    transform: translateY(5px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes shimmer {
  from {
    background-position: -60% 0;
  }
  to {
    background-position: 160% 0;
  }
}

@keyframes blink {
  0% {
    opacity: 0;
  }
  50% {
    opacity: 1;
  }
  100% {
    opacity: 0;
  }
}

@media (max-width: 1023px) {
  .chat-messages {
    padding: var(--space-2);
  }

  .bubble-stack {
    max-width: 88%;
  }
}

@media (max-width: 640px) {
  .message-text {
    font-size: 14px;
  }

  .avatar {
    width: 32px;
    height: 32px;
  }

  .composer {
    padding: 10px;
  }

  .tool-row {
    gap: 6px;
  }

  .tool-btn,
  .quick-btn,
  .tone-btn {
    font-size: 11px;
    padding: 4px 9px;
  }

  .send-button {
    min-width: 64px;
  }
}
</style>
