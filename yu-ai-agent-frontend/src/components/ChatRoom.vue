<template>
  <div class="chat-container">
    <!-- 聊天记录区域 -->
    <div class="chat-messages" ref="messagesContainer" role="log" aria-live="polite" aria-relevant="additions text">
      <div
        v-for="(msg, index) in messages"
        :key="msg.id || (String(msg.time) + '-' + index)"
        class="message-wrapper"
        v-memo="[msg.id, msg.content, msg.isUser, msg.type, msg.time, msg.images && msg.images.length, index]"
      >
        <!-- AI消息 -->
        <div v-if="!msg.isUser"
             class="message ai-message"
             :class="[msg.type]">
          <div class="avatar ai-avatar">
            <AiAvatarFallback :type="aiType" />
          </div>
          <div class="message-bubble">
            <div class="message-content">
              <div
                v-if="connectionStatus === 'connecting' && index === messages.length - 1 && !msg.content"
                class="ai-skeleton"
                aria-label="正在生成回复"
              >
                <div class="sk-line w60" />
                <div class="sk-line w85" />
                <div class="sk-line w40" />
              </div>
              <template v-for="(seg, segIndex) in getSegments(msg.content)" :key="segIndex">
                <div v-if="seg.type === 'text'" class="message-text">{{ seg.content }}</div>
                <div v-else-if="seg.type === 'image'" class="message-image-block">
                  <a :href="seg.content" target="_blank" rel="noopener noreferrer">
                    <img :src="seg.content" class="ai-message-image" loading="lazy" />
                  </a>
                </div>
                <LocationCard v-else-if="seg.type === 'location_card'" v-bind="parseLocationCard(seg.content)" />
              </template>
              <span v-if="connectionStatus === 'connecting' && index === messages.length - 1" class="typing-indicator">▋</span>
            </div>
            <div class="message-time">{{ formatTime(msg.time) }}</div>
          </div>
        </div>

        <!-- 用户消息 -->
        <div v-else class="message user-message" :class="[msg.type]">
          <div class="message-bubble">
            <!-- 显示用户上传的图片 -->
            <div v-if="msg.images && msg.images.length > 0" class="message-images">
              <img v-for="(img, imgIndex) in msg.images" :key="imgIndex" :src="img" class="message-image" />
            </div>
            <div class="message-content">{{ msg.content }}</div>
            <div class="message-time">{{ formatTime(msg.time) }}</div>
          </div>
          <div class="avatar user-avatar">
            <div class="avatar-placeholder">我</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 输入区域 -->
    <div class="chat-input-container" :class="{ 'has-preview': selectedImages.length > 0 }">
      <!-- 图片预览区 -->
      <div v-if="enableImages && selectedImages.length > 0" class="image-preview-area">
        <div v-for="(img, index) in selectedImages" :key="index" class="preview-item">
          <img :src="img" class="preview-image" />
          <button class="remove-image-btn" @click="removeImage(index)">×</button>
        </div>
      </div>

      <div class="chat-input">
        <!-- 附件按钮 -->
        <button
          v-if="enableImages"
          class="attach-button"
          type="button"
          aria-label="上传图片"
          @click="triggerFileInput"
          :disabled="connectionStatus === 'connecting'"
        >
          📎
        </button>
        <input
          v-if="enableImages"
          type="file"
          ref="fileInput"
          @change="handleFileSelect"
          accept="image/jpeg,image/png,image/gif,image/webp"
          multiple
          style="display: none"
        />

        <textarea
          v-model="inputMessage"
          @keydown.enter.prevent="sendMessage"
          @paste="handlePaste"
          placeholder="请输入消息..."
          class="input-box"
          :disabled="connectionStatus === 'connecting'"
        ></textarea>
        <button
          @click="sendMessage"
          class="send-button"
          :disabled="connectionStatus === 'connecting' || (!inputMessage.trim() && (!enableImages || selectedImages.length === 0))"
          aria-label="发送消息"
        >发送</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, watch, computed } from 'vue'
import AiAvatarFallback from './AiAvatarFallback.vue'
import LocationCard from './LocationCard.vue'
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
    default: 'default'  // 'love' 或 'super'
  },
  enableImages: {
    type: Boolean,
    default: true
  }
})

const emit = defineEmits(['send-message'])

const inputMessage = ref('')
const messagesContainer = ref(null)
const fileInput = ref(null)
const selectedImages = ref([])

const getSegments = (content) => {
  return parseMessage(content)
}

const parseLocationCard = (jsonText) => {
  try {
    return JSON.parse(jsonText)
  } catch (e) {
    return { name: '地点信息解析失败', address: '', rating: '', cost: '', tel: '', photos: [], mapUrl: '' }
  }
}

// 图片大小限制 5MB
const MAX_IMAGE_SIZE = 5 * 1024 * 1024

// 根据AI类型选择不同头像
const aiAvatar = computed(() => {
  return props.aiType === 'love'
    ? '/ai-love-avatar.png'  // 恋爱大师头像
    : '/ai-super-avatar.png' // 超级智能体头像
})

// 触发文件选择
const triggerFileInput = () => {
  fileInput.value.click()
}

// 处理文件选择
const handleFileSelect = (event) => {
  const files = event.target.files
  if (!files || files.length === 0) return

  // 限制最多3张图片
  const remainingSlots = 3 - selectedImages.value.length
  const filesToProcess = Array.from(files).slice(0, remainingSlots)

  for (const file of filesToProcess) {
    // 检查文件大小
    if (file.size > MAX_IMAGE_SIZE) {
      alert(`图片 ${file.name} 超过5MB限制`)
      continue
    }

    // 读取文件并转为 Base64
    const reader = new FileReader()
    reader.onload = (e) => {
      selectedImages.value.push(e.target.result)
    }
    reader.readAsDataURL(file)
  }

  // 清空 input 以便重复选择同一文件
  event.target.value = ''
}

// 支持 Cmd+V / Ctrl+V 粘贴图片
const handlePaste = (event) => {
  if (!props.enableImages) return
  const clipboardData = event.clipboardData
  if (!clipboardData || !clipboardData.items) return

  const items = Array.from(clipboardData.items)
  const imageItems = items.filter(i => i.kind === 'file' && i.type && i.type.startsWith('image/'))
  if (imageItems.length === 0) return

  // 粘贴包含图片时，阻止默认粘贴（避免插入不可见字符/破坏输入）
  event.preventDefault()

  const remainingSlots = 3 - selectedImages.value.length
  const toProcess = imageItems.slice(0, remainingSlots)

  for (const item of toProcess) {
    const file = item.getAsFile()
    if (!file) continue

    if (file.size > MAX_IMAGE_SIZE) {
      alert(`图片超过5MB限制`)
      continue
    }

    const reader = new FileReader()
    reader.onload = (e) => {
      if (e?.target?.result) {
        selectedImages.value.push(e.target.result)
      }
    }
    reader.readAsDataURL(file)
  }
}

// 移除预览图片
const removeImage = (index) => {
  selectedImages.value.splice(index, 1)
}

// 发送消息
const sendMessage = () => {
  if (!inputMessage.value.trim() && (!props.enableImages || selectedImages.value.length === 0)) return

  // 发送消息和图片
  emit('send-message', {
    text: inputMessage.value,
    images: props.enableImages ? [...selectedImages.value] : []
  })

  inputMessage.value = ''
  selectedImages.value = []
}

// 格式化时间
const formatTime = (timestamp) => {
  const date = new Date(timestamp)
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

// 自动滚动到底部
let rafId = 0
const scrollToBottom = async () => {
  await nextTick()
  if (!messagesContainer.value) return
  if (rafId) cancelAnimationFrame(rafId)
  rafId = requestAnimationFrame(() => {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    rafId = 0
  })
}

// 监听消息变化与内容变化，自动滚动
watch(() => props.messages.length, () => {
  scrollToBottom()
})

watch(() => props.messages[props.messages.length - 1]?.content, () => {
  scrollToBottom()
})

onMounted(() => {
  scrollToBottom()
})
</script>

<style scoped>
.chat-container {
  display: flex;
  flex-direction: column;
  flex: 1;
  height: 100%;
  min-height: 0;
  background-color: rgba(255, 255, 255, 0.72);
  border-radius: var(--radius-2);
  overflow: hidden;
  border: 1px solid var(--border);
  backdrop-filter: blur(10px);
}

.chat-messages {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
}

.message-wrapper {
  margin-bottom: 16px;
  display: flex;
  flex-direction: column;
  width: 100%;
}

.message {
  display: flex;
  align-items: flex-start;
  max-width: 85%;
  margin-bottom: 8px;
}

.user-message {
  margin-left: auto; /* 用户消息靠右 */
  flex-direction: row; /* 正常顺序，先气泡后头像 */
}

.ai-message {
  margin-right: auto; /* AI消息靠左 */
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

.user-avatar {
  margin-left: 8px; /* 用户头像在右侧，左边距 */
}

.ai-avatar {
  margin-right: 8px; /* AI头像在左侧，右边距 */
}

.avatar-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(162, 187, 220, 0.55);
  color: #1f2937;
  font-weight: bold;
}

.message-bubble {
  padding: 12px;
  border-radius: 18px;
  position: relative;
  word-wrap: break-word;
  min-width: 100px; /* 最小宽度 */
  border: 1px solid transparent;
}

.user-message .message-bubble {
  background-color: var(--surface);
  color: var(--text);
  border-color: rgba(217, 158, 130, 0.26);
  border-bottom-right-radius: 4px;
  text-align: left;
}

.ai-message .message-bubble {
  background-color: rgba(253, 251, 247, 0.92);
  color: var(--text);
  border-color: var(--border);
  border-bottom-left-radius: 4px;
  text-align: left;
}

.ai-skeleton {
  padding: 2px 0;
}

.sk-line {
  height: 12px;
  border-radius: 999px;
  background: linear-gradient(
    90deg,
    rgba(0, 0, 0, 0.06) 0%,
    rgba(0, 0, 0, 0.12) 40%,
    rgba(0, 0, 0, 0.06) 80%
  );
  background-size: 200% 100%;
  animation: shimmer 1.2s ease-in-out infinite;
  margin: 10px 0;
}

.sk-line.w60 { width: 60%; }
.sk-line.w85 { width: 85%; }
.sk-line.w40 { width: 40%; }

@keyframes shimmer {
  0% { background-position: 0% 0; }
  100% { background-position: 200% 0; }
}

@media (prefers-reduced-motion: reduce) {
  .sk-line {
    animation: none;
  }
}

.message-content {
  font-size: 16px;
  line-height: 1.5;
  white-space: pre-wrap;
}

.message-time {
  font-size: 12px;
  opacity: 0.7;
  margin-top: 4px;
  text-align: right;
}

.chat-input-container {
  background-color: rgba(255, 255, 255, 0.85);
  border-top: 1px solid var(--border);
  min-height: 72px;
  box-shadow: 0 -2px 10px rgba(0, 0, 0, 0.04);
}

.chat-input-container.has-preview {
  min-height: 152px;
}

.image-preview-area {
  display: flex;
  gap: 8px;
  padding: 12px 16px 0 16px;
  flex-wrap: wrap;
}

.preview-item {
  position: relative;
  width: 60px;
  height: 60px;
}

.preview-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 8px;
  border: 1px solid #ddd;
}

.remove-image-btn {
  position: absolute;
  top: -6px;
  right: -6px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background-color: #ff4d4f;
  color: white;
  border: none;
  font-size: 14px;
  line-height: 1;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.remove-image-btn:hover {
  background-color: #ff7875;
}

.chat-input {
  display: flex;
  padding: 16px;
  height: 100%;
  box-sizing: border-box;
  align-items: center;
}

.attach-button {
  width: 40px;
  height: 40px;
  border: none;
  background-color: transparent;
  font-size: 20px;
  cursor: pointer;
  border-radius: 50%;
  transition: background-color 0.3s;
  flex-shrink: 0;
  margin-right: 8px;
}

.attach-button:hover:not(:disabled) {
  background-color: rgba(217, 158, 130, 0.10);
}

.attach-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.message-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}

.message-image {
  max-width: 200px;
  max-height: 200px;
  border-radius: 8px;
  object-fit: cover;
}

.message-image-block {
  margin: 8px 0;
}

.ai-message-image {
  max-width: 320px;
  max-height: 240px;
  border-radius: 10px;
  object-fit: cover;
  display: block;
  border: 1px solid rgba(0, 0, 0, 0.08);
}

.input-box {
  flex-grow: 1;
  border: 1px solid var(--border);
  border-radius: 20px;
  padding: 10px 16px;
  font-size: 16px;
  resize: none;
  min-height: 20px;
  max-height: 72px; /* 允许多行输入 */
  outline: none;
  transition: border-color 0.3s;
  overflow-y: auto;
  scrollbar-width: none; /* Firefox */
  -ms-overflow-style: none; /* IE & Edge */
}

/* 隐藏Webkit浏览器的滚动条 */
.input-box::-webkit-scrollbar {
  display: none;
}

.input-box:focus {
  border-color: rgba(217, 158, 130, 0.55);
}

.send-button {
  margin-left: 12px;
  background-color: var(--primary);
  color: white;
  border: none;
  border-radius: 20px;
  padding: 0 20px;
  font-size: 16px;
  cursor: pointer;
  transition: background-color 0.3s;
  height: 40px;
  align-self: center;
}

.send-button:hover:not(:disabled) {
  background-color: var(--primary-strong);
}

.typing-indicator {
  display: inline-block;
  animation: blink 0.7s infinite;
  margin-left: 2px;
}

@keyframes blink {
  0% { opacity: 0; }
  50% { opacity: 1; }
  100% { opacity: 0; }
}

.input-box:disabled, .send-button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .message {
    max-width: 95%;
  }
  
  .message-content {
    font-size: 15px;
  }
  
  .chat-input {
    padding: 12px;
  }
  
  .input-box {
    padding: 8px 12px;
  }
  
  .send-button {
    padding: 0 15px;
    font-size: 14px;
  }
}

@media (max-width: 480px) {
  .avatar {
    width: 32px;
    height: 32px;
  }
  
  .message-bubble {
    padding: 10px;
  }
  
  .message-content {
    font-size: 14px;
  }
  
  .chat-input-container {
    height: 64px;
  }
  
  .chat-messages {
    bottom: 64px;
  }
}

/* 新增：不同类型消息的样式 */
.ai-answer {
  animation: fadeIn 0.3s ease-in-out;
}

.ai-final {
  /* 最终回答，可以有不同的样式，例如边框高亮等 */
}

.ai-error {
  opacity: 0.7;
}

.user-question {
  /* 用户提问的特殊样式 */
}

/* 连续消息气泡样式 */
.ai-message + .ai-message {
  margin-top: 4px;
}

.ai-message + .ai-message .avatar {
  visibility: hidden;
}

.ai-message + .ai-message .message-bubble {
  border-top-left-radius: 10px;
}
</style>
