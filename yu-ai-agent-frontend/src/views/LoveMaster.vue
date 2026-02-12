<template>
  <AppShell>
    <template #sidebar>
      <div class="sidebar-header">
        <div class="brand">
          <div class="brand-avatar" aria-hidden="true">❤</div>
          <div class="brand-meta">
            <div class="brand-title">AI恋爱大师</div>
            <div class="brand-subtitle">情感咨询 · 聊天分析</div>
          </div>
        </div>

        <button type="button" class="new-chat" @click="createNewSession">+ 新对话</button>

        <div class="search">
          <input v-model="searchKeyword" placeholder="搜索历史对话..." aria-label="搜索历史对话" />
        </div>
      </div>

      <div class="session-list" role="list">
        <button
          v-for="s in filteredSessions"
          :key="s.id"
          type="button"
          class="session-item"
          :class="{ active: s.id === activeSessionId }"
          :aria-current="s.id === activeSessionId ? 'page' : undefined"
          @click="selectSession(s.id)"
        >
          <div class="session-title">{{ s.title || '新对话' }}</div>
          <div class="session-meta">
            <span class="session-time">{{ formatSessionTime(s.updatedAt) }}</span>
            <span class="session-count">{{ s.messages?.length || 0 }} 条</span>
          </div>
        </button>
      </div>
    </template>

    <template #header>
      <header class="topbar">
        <div class="topbar-left">
          <button
            type="button"
            class="ghost mobile-only"
            aria-label="打开会话列表"
            @click="drawerOpen = true"
          >会话</button>
          <button type="button" class="ghost desktop-only" aria-label="返回首页" @click="goBack">返回</button>
        </div>

        <div class="topbar-center">
          <div class="topbar-title">{{ currentSession?.title || 'AI恋爱大师' }}</div>
          <div class="topbar-sub">
            <span class="status-dot" :class="connectionStatus" aria-hidden="true" />
            <span class="status-text">{{ statusText }}</span>
            <span class="sep" aria-hidden="true">·</span>
            <span class="chat-id" aria-label="会话标识">会话：{{ displaySessionId }}</span>
          </div>
        </div>

        <div class="topbar-actions">
          <button type="button" class="ghost" @click="clearCurrentSession">清空</button>
        </div>
      </header>
    </template>

    <section class="chat-shell">
      <ChatRoom
        :messages="currentMessages"
        :connection-status="connectionStatus"
        ai-type="love"
        @send-message="sendMessage"
      />
    </section>

    <template #right>
      <div class="aux">
        <div class="aux-card">
          <div class="aux-title">会话概览</div>
          <div class="aux-body">
            <div class="kv"><span>当前消息</span><span>{{ currentMessages.length }} 条</span></div>
            <div class="kv"><span>状态</span><span>{{ statusText }}</span></div>
          </div>
        </div>

        <div class="aux-card">
          <div class="aux-title">情绪温度条</div>
          <div class="aux-body muted">即将上线：基于对话实时分析情绪倾向</div>
        </div>

        <div class="aux-card">
          <div class="aux-title">收藏夹</div>
          <div class="aux-body muted">即将上线：一键收藏关键话术</div>
        </div>
      </div>
    </template>

    <!-- 移动端：会话抽屉（替代桌面侧边栏） -->
    <teleport to="body">
      <div v-if="drawerOpen" class="drawer-mask" role="presentation" @click="drawerOpen = false" />
      <aside
        v-if="drawerOpen"
        class="drawer"
        role="dialog"
        aria-modal="true"
        aria-label="会话列表"
      >
        <div class="drawer-header">
          <div class="drawer-title">会话</div>
          <button type="button" class="ghost" aria-label="关闭" @click="drawerOpen = false">关闭</button>
        </div>
        <div class="drawer-body">
          <button type="button" class="new-chat" @click="createNewSession(); drawerOpen = false">+ 新对话</button>
          <div class="search">
            <input v-model="searchKeyword" placeholder="搜索历史对话..." aria-label="搜索历史对话" />
          </div>
          <div class="session-list" role="list">
            <button
              v-for="s in filteredSessions"
              :key="s.id"
              type="button"
              class="session-item"
              :class="{ active: s.id === activeSessionId }"
              @click="selectSession(s.id); drawerOpen = false"
            >
              <div class="session-title">{{ s.title || '新对话' }}</div>
              <div class="session-meta">
                <span class="session-time">{{ formatSessionTime(s.updatedAt) }}</span>
                <span class="session-count">{{ s.messages?.length || 0 }} 条</span>
              </div>
            </button>
          </div>
        </div>
      </aside>
    </teleport>
  </AppShell>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useHead } from '@vueuse/head'
import ChatRoom from '../components/ChatRoom.vue'
import AppShell from '../components/AppShell.vue'
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
const LEGACY_STORAGE_KEYS = ['love_master_sessions', 'love_master_sessions_v0', 'loveMasterSessions']

const connectionStatus = ref('disconnected')
let eventSource = null
let visionRequest = null

const sessions = ref([])
const activeSessionId = ref('')
const searchKeyword = ref('')
const drawerOpen = ref(false)

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

const displaySessionId = computed(() => {
  const id = String(activeSessionId.value || '')
  if (!id) return ''
  // 弱化技术性 ID：仅展示末尾片段，便于识别与排障
  return id.length > 8 ? '…' + id.slice(-8) : id
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

  // 兼容旧版本本地缓存 key（避免“历史记录消失”的感知问题）
  if (!sessions.value || sessions.value.length === 0) {
    for (const key of LEGACY_STORAGE_KEYS) {
      try {
        const raw = localStorage.getItem(key)
        const parsed = raw ? JSON.parse(raw) : []
        if (Array.isArray(parsed) && parsed.length > 0) {
          sessions.value = parsed
          // 迁移到新 key（不删除旧 key，避免用户降级时丢失）
          persistSessions()
          break
        }
      } catch (e) {
        // ignore
      }
    }
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
.sidebar-header {
  padding: 16px;
  border-bottom: 1px solid var(--border);
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
  background: linear-gradient(135deg, var(--primary), rgba(217, 158, 130, 0.55));
  color: #fff;
  font-weight: 700;
}

.brand-title {
  font-weight: 700;
  color: #2f2f33;
}

.brand-subtitle {
  font-size: 12px;
  color: var(--muted);
}

.new-chat {
  margin-top: 12px;
  width: 100%;
  border: none;
  border-radius: 12px;
  padding: 10px 12px;
  background: linear-gradient(135deg, var(--primary), var(--primary-strong));
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
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 10px 12px;
  outline: none;
  background: rgba(255, 255, 255, 0.9);
}

.search input::placeholder {
  color: rgba(0, 0, 0, 0.45);
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

@media (hover: hover) {
  .session-item:hover {
    background: rgba(217, 158, 130, 0.10);
  }
}

.session-item.active {
  background: rgba(217, 158, 130, 0.16);
  border-color: rgba(217, 158, 130, 0.22);
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
  color: var(--muted);
}

.topbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
}

.topbar-left {
  display: flex;
  gap: 8px;
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
  color: var(--muted);
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
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 8px 12px;
  background: rgba(255, 255, 255, 0.9);
  cursor: pointer;
}

.mobile-only {
  display: none;
}

.desktop-only {
  display: inline-flex;
}

@media (max-width: 900px) {
  .mobile-only {
    display: inline-flex;
  }

  .desktop-only {
    display: none;
  }
}

.chat-shell {
  flex: 1;
  min-height: 0;
}


/* 右侧辅助区卡片 */
.aux {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.aux-card {
  border: 1px solid var(--border);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.72);
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.05);
  padding: var(--space-2);
}

.aux-title {
  font-weight: 800;
  margin-bottom: 10px;
}

.aux-body {
  font-size: 13px;
  color: var(--muted-2);
}

.muted {
  color: var(--muted);
}

.kv {
  display: flex;
  justify-content: space-between;
  padding: 6px 0;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
}

.kv:last-child {
  border-bottom: none;
}

/* 移动端会话抽屉 */
.drawer-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.28);
  z-index: 60;
}

.drawer {
  position: fixed;
  left: 0;
  top: 0;
  bottom: 0;
  width: min(86vw, 360px);
  z-index: 61;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(12px);
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
}

.drawer-header {
  padding: 12px 12px;
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.drawer-title {
  font-weight: 800;
}

.drawer-body {
  padding: 12px;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
</style>
