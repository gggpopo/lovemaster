<template>
  <div class="love-page">
    <aside class="sidebar">
      <div class="sidebar-header">
        <div class="brand">
          <div class="brand-avatar">❤</div>
          <div class="brand-meta">
            <div class="brand-title">AI恋爱大师</div>
            <div class="brand-subtitle">情感咨询 · 聊天分析</div>
          </div>
        </div>

        <button class="new-chat" @click="createNewSession">+ 新对话</button>

        <div class="search">
          <input v-model="searchKeyword" placeholder="搜索历史对话..." />
        </div>
      </div>

      <div class="session-list">
        <button
          v-for="s in filteredSessions"
          :key="s.id"
          class="session-item"
          :class="{ active: s.id === activeSessionId }"
          @click="selectSession(s.id)"
        >
          <div class="session-title">{{ s.title || '新对话' }}</div>
          <div class="session-meta">
            <span class="session-time">{{ formatSessionTime(s.updatedAt) }}</span>
            <span class="session-count">{{ s.messages?.length || 0 }} 条</span>
          </div>
        </button>
      </div>
    </aside>

    <main class="main">
      <header class="topbar">
        <button class="back" @click="goBack">返回</button>

        <div class="topbar-center">
          <div class="topbar-title">{{ currentSession?.title || 'AI恋爱大师' }}</div>
          <div class="topbar-sub">
            <span class="status-dot" :class="connectionStatus" />
            <span class="status-text">{{ statusText }}</span>
            <span class="sep">·</span>
            <span class="chat-id">会话ID: {{ activeSessionId }}</span>
          </div>
        </div>

        <div class="topbar-actions">
          <button class="ghost" @click="clearCurrentSession">清空</button>
        </div>
      </header>

      <section class="chat-shell">
        <ChatRoom
          :messages="currentMessages"
          :connection-status="connectionStatus"
          ai-type="love"
          @send-message="sendMessage"
        />
      </section>

      <footer class="footer-container">
        <AppFooter />
      </footer>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useHead } from '@vueuse/head'
import ChatRoom from '../components/ChatRoom.vue'
import AppFooter from '../components/AppFooter.vue'
import { chatWithLoveApp, chatWithLoveAppVision } from '../api'

// 设置页面标题和元数据
useHead({
  title: 'AI恋爱大师 - 小高AI超级智能体应用平台',
  meta: [
    {
      name: 'description',
      content: 'AI恋爱大师是小高AI超级智能体应用平台的专业情感顾问，帮你解答各种恋爱问题，提供情感建议'
    },
    {
      name: 'keywords',
      content: 'AI恋爱大师,情感顾问,恋爱咨询,AI聊天,情感问题,小高,AI智能体'
    }
  ]
})

const router = useRouter()

const STORAGE_KEY = 'love_master_sessions_v1'

const connectionStatus = ref('disconnected')
let eventSource = null
let visionRequest = null

const sessions = ref([])
const activeSessionId = ref('')
const searchKeyword = ref('')

// 生成随机会话ID
const generateChatId = () => 'love_' + Math.random().toString(36).substring(2, 10)

const statusText = computed(() => {
  if (connectionStatus.value === 'connecting') return '正在思考中…'
  if (connectionStatus.value === 'error') return '连接异常'
  return '就绪'
})

const currentSession = computed(() => {
  return sessions.value.find(s => s.id === activeSessionId.value) || null
})

const currentMessages = computed(() => {
  return currentSession.value?.messages || []
})

const filteredSessions = computed(() => {
  const kw = (searchKeyword.value || '').trim().toLowerCase()
  if (!kw) return sessions.value
  return sessions.value.filter(s => (s.title || '').toLowerCase().includes(kw))
})

const persistSessions = () => {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(sessions.value))
  } catch (e) {
    console.warn('Failed to persist sessions', e)
  }
}

const upsertWelcomeMessage = (session) => {
  if (!session.messages || session.messages.length === 0) {
    session.messages = [
      {
        content: '欢迎来到AI恋爱大师。你可以直接描述你的困扰，或粘贴聊天截图/上传图片，我会帮你分析与给出建议。',
        isUser: false,
        images: [],
        time: Date.now()
      }
    ]
  }
}

const createNewSession = () => {
  // 切换会话前关闭连接
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
  if (visionRequest) {
    visionRequest.close()
    visionRequest = null
  }
  connectionStatus.value = 'disconnected'

  const id = generateChatId()
  const now = Date.now()
  const session = {
    id,
    title: '新对话',
    updatedAt: now,
    messages: []
  }
  upsertWelcomeMessage(session)

  sessions.value.unshift(session)
  activeSessionId.value = id
  persistSessions()
}

const selectSession = (id) => {
  if (id === activeSessionId.value) return
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
  if (visionRequest) {
    visionRequest.close()
    visionRequest = null
  }
  connectionStatus.value = 'disconnected'
  activeSessionId.value = id
}

const clearCurrentSession = () => {
  const s = currentSession.value
  if (!s) return
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
  if (visionRequest) {
    visionRequest.close()
    visionRequest = null
  }
  connectionStatus.value = 'disconnected'
  s.messages = []
  upsertWelcomeMessage(s)
  s.updatedAt = Date.now()
  persistSessions()
}

const touchSession = () => {
  const s = currentSession.value
  if (!s) return
  s.updatedAt = Date.now()
  sessions.value = [...sessions.value].sort((a, b) => (b.updatedAt || 0) - (a.updatedAt || 0))
}

const maybeUpdateTitle = (text, images) => {
  const s = currentSession.value
  if (!s) return
  if (s.title && s.title !== '新对话') return
  const t = (text || '').trim()
  if (t) {
    s.title = t.length > 12 ? t.slice(0, 12) + '…' : t
    return
  }
  if (images && images.length > 0) {
    s.title = '图片分析'
  }
}

// 发送消息
const sendMessage = (messageData) => {
  const s = currentSession.value
  if (!s) return

  const text = messageData.text || ''
  const images = messageData.images || []
  maybeUpdateTitle(text, images)

  // 添加用户消息（包含图片）
  s.messages.push({
    content: text || (images.length > 0 ? '请分析这张图片' : ''),
    isUser: true,
    images,
    time: Date.now()
  })

  // 发送前关闭旧连接
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
  if (visionRequest) {
    visionRequest.close()
    visionRequest = null
  }

  // 创建一个空的AI回复消息
  const aiMessageIndex = s.messages.length
  s.messages.push({ content: '', isUser: false, images: [], time: Date.now() })

  connectionStatus.value = 'connecting'
  touchSession()
  persistSessions()

  // 根据是否有图片选择不同的 API
  if (images.length > 0) {
    visionRequest = chatWithLoveAppVision(
      text,
      activeSessionId.value,
      images,
      (data) => {
        if (data && data !== '[DONE]') {
          if (aiMessageIndex < s.messages.length) {
            s.messages[aiMessageIndex].content += data
          }
        }
        if (data === '[DONE]') {
          connectionStatus.value = 'disconnected'
          touchSession()
          persistSessions()
        }
      },
      (error) => {
        console.error('Vision API Error:', error)
        connectionStatus.value = 'error'
        if (aiMessageIndex < s.messages.length) {
          s.messages[aiMessageIndex].content = '抱歉，图片分析出现错误，请稍后重试。'
        }
        touchSession()
        persistSessions()
      }
    )
  } else {
    eventSource = chatWithLoveApp(text, activeSessionId.value)

    eventSource.onmessage = (event) => {
      const data = event.data
      if (data && data !== '[DONE]') {
        if (aiMessageIndex < s.messages.length) {
          s.messages[aiMessageIndex].content += data
        }
      }

      if (data === '[DONE]') {
        connectionStatus.value = 'disconnected'
        eventSource.close()
        eventSource = null
        touchSession()
        persistSessions()
      }
    }

    eventSource.onerror = (error) => {
      console.error('SSE Error:', error)
      connectionStatus.value = 'error'
      eventSource?.close()
      eventSource = null
      touchSession()
      persistSessions()
    }
  }
}

// 返回主页
const goBack = () => {
  router.push('/')
}

// 页面加载时添加欢迎消息
onMounted(() => {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    const parsed = raw ? JSON.parse(raw) : []
    sessions.value = Array.isArray(parsed) ? parsed : []
  } catch (e) {
    sessions.value = []
  }

  if (!sessions.value || sessions.value.length === 0) {
    createNewSession()
    return
  }

  sessions.value.forEach(s => {
    if (!s.id) s.id = generateChatId()
    if (!s.updatedAt) s.updatedAt = Date.now()
    upsertWelcomeMessage(s)
  })
  sessions.value = [...sessions.value].sort((a, b) => (b.updatedAt || 0) - (a.updatedAt || 0))
  activeSessionId.value = sessions.value[0].id
})

// 组件销毁前关闭SSE连接
onBeforeUnmount(() => {
  if (eventSource) {
    eventSource.close()
  }
  if (visionRequest) {
    visionRequest.close()
  }
})

const formatSessionTime = (ts) => {
  if (!ts) return ''
  const d = new Date(ts)
  const now = new Date()
  const sameDay = d.toDateString() === now.toDateString()
  if (sameDay) {
    return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
  return d.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
}
</script>

<style scoped>
/* 参考常见情感类聊天产品：左侧会话列表 + 右侧聊天主面板 */
.love-page {
  display: flex;
  min-height: 100vh;
  background: radial-gradient(1200px 600px at 20% 10%, rgba(255, 107, 139, 0.25), transparent 60%),
    radial-gradient(1000px 500px at 80% 30%, rgba(255, 200, 215, 0.35), transparent 60%),
    #fff;
}

.sidebar {
  width: 280px;
  border-right: 1px solid rgba(0, 0, 0, 0.06);
  background: rgba(255, 255, 255, 0.75);
  backdrop-filter: blur(10px);
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  padding: 16px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
}

.brand-avatar {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #ff6b8b, #ffb6c8);
  color: #fff;
  font-weight: 700;
}

.brand-title {
  font-weight: 700;
  color: #2f2f33;
}

.brand-subtitle {
  font-size: 12px;
  color: rgba(0, 0, 0, 0.55);
}

.new-chat {
  margin-top: 12px;
  width: 100%;
  border: none;
  border-radius: 12px;
  padding: 10px 12px;
  background: linear-gradient(135deg, #ff6b8b, #ff9ab0);
  color: #fff;
  font-weight: 600;
  cursor: pointer;
}

.new-chat:hover {
  filter: brightness(1.02);
}

.search {
  margin-top: 12px;
}

.search input {
  width: 100%;
  border: 1px solid rgba(0, 0, 0, 0.08);
  border-radius: 12px;
  padding: 10px 12px;
  outline: none;
  background: rgba(255, 255, 255, 0.9);
}

.session-list {
  padding: 10px;
  overflow: auto;
  flex: 1;
}

.session-item {
  width: 100%;
  text-align: left;
  border: 1px solid transparent;
  border-radius: 12px;
  background: transparent;
  padding: 10px 12px;
  cursor: pointer;
  margin-bottom: 8px;
}

.session-item:hover {
  background: rgba(255, 107, 139, 0.08);
}

.session-item.active {
  background: rgba(255, 107, 139, 0.14);
  border-color: rgba(255, 107, 139, 0.2);
}

.session-title {
  font-weight: 600;
  color: #2f2f33;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-meta {
  margin-top: 4px;
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.55);
}

.main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.topbar {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
  background: rgba(255, 255, 255, 0.75);
  backdrop-filter: blur(10px);
}

.back {
  border: 1px solid rgba(0, 0, 0, 0.08);
  border-radius: 12px;
  padding: 8px 12px;
  background: rgba(255, 255, 255, 0.9);
  cursor: pointer;
}

.topbar-center {
  flex: 1;
  min-width: 0;
}

.topbar-title {
  font-weight: 700;
  color: #2f2f33;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.topbar-sub {
  font-size: 12px;
  color: rgba(0, 0, 0, 0.55);
  display: flex;
  align-items: center;
  gap: 6px;
  overflow: hidden;
  white-space: nowrap;
}

.sep {
  opacity: 0.7;
}

.chat-id {
  overflow: hidden;
  text-overflow: ellipsis;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #22c55e;
}

.status-dot.connecting {
  background: #f59e0b;
}

.status-dot.error {
  background: #ef4444;
}

.topbar-actions {
  display: flex;
  gap: 8px;
}

.ghost {
  border: 1px solid rgba(0, 0, 0, 0.08);
  border-radius: 12px;
  padding: 8px 12px;
  background: rgba(255, 255, 255, 0.9);
  cursor: pointer;
}

.chat-shell {
  flex: 1;
  min-height: 0;
  padding: 16px;
}

.footer-container {
  padding: 0 16px 16px;
}

/* 响应式：移动端隐藏侧边栏 */
@media (max-width: 900px) {
  .sidebar {
    display: none;
  }
  .chat-shell {
    padding: 12px;
  }
  .footer-container {
    padding: 0 12px 12px;
  }
}
</style>
