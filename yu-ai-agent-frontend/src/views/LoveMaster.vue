<template>
  <AppShell>
    <template #sidebar>
      <section class="sidebar-panel">
        <header class="sidebar-head">
          <div class="brand-wrap">
            <div class="brand-mark" aria-hidden="true">❤</div>
            <div>
              <h1 class="brand-title">AI恋爱大师</h1>
              <p class="brand-subtitle">理性判断 + 温柔表达</p>
            </div>
          </div>

          <div class="mode-switch" role="tablist" aria-label="侧边模式">
            <button
              type="button"
              class="mode-btn"
              :class="{ active: sidebarMode === 'sessions' }"
              role="tab"
              :aria-selected="sidebarMode === 'sessions'"
              @click="sidebarMode = 'sessions'"
            >
              会话管理
            </button>
            <button
              type="button"
              class="mode-btn"
              :class="{ active: sidebarMode === 'assistants' }"
              role="tab"
              :aria-selected="sidebarMode === 'assistants'"
              @click="sidebarMode = 'assistants'"
            >
              助手库
            </button>
          </div>
        </header>

        <section v-if="sidebarMode === 'sessions'" class="sidebar-body">
          <button type="button" class="primary-btn" @click="createNewSession">+ 新对话</button>

          <label class="search-box">
            <span class="sr-only">搜索会话</span>
            <input v-model="searchKeyword" type="text" placeholder="搜索历史会话..." />
          </label>

          <div class="session-scroll" role="list">
            <article
              v-for="s in filteredSessions"
              :key="s.id"
              class="session-row"
              :class="{ active: s.id === activeSessionId }"
              role="button"
              tabindex="0"
              :aria-current="s.id === activeSessionId ? 'page' : undefined"
              @click="selectSession(s.id)"
              @keydown.enter.prevent="selectSession(s.id)"
            >
              <div class="session-main">
                <p class="session-title">{{ s.title || '新对话' }}</p>
                <p class="session-meta">
                  <span>{{ formatSessionTime(s.updatedAt) }}</span>
                  <span>{{ s.messages?.length || 0 }} 条</span>
                </p>
              </div>
              <div class="session-actions">
                <button type="button" class="icon-action" @click.stop="renameSession(s.id)">重命名</button>
                <button type="button" class="icon-action danger" @click.stop="deleteSession(s.id)">删除</button>
              </div>
            </article>

            <p v-if="!filteredSessions.length" class="empty-tip">暂无匹配会话，试试新建一个对话。</p>
          </div>
        </section>

        <section v-else class="sidebar-body assistant-scroll">
          <button
            v-for="assistant in assistantPresets"
            :key="assistant.id"
            type="button"
            class="assistant-card"
            @click="useAssistantPreset(assistant)"
          >
            <div class="assistant-head">
              <h3>{{ assistant.name }}</h3>
              <span class="assistant-tone">{{ assistant.toneLabel }}</span>
            </div>
            <p>{{ assistant.desc }}</p>
          </button>
        </section>
      </section>
    </template>

    <template #header>
      <header class="topbar">
        <div class="topbar-main">
          <button type="button" class="ghost-btn mobile-only" @click="drawerOpen = true">会话</button>
          <button type="button" class="ghost-btn desktop-only" @click="goBack">返回首页</button>

          <div class="title-wrap">
            <h2 class="session-heading">{{ currentSession?.title || 'AI恋爱大师' }}</h2>
            <div class="topbar-subline">
              <span class="status-dot" :class="connectionStatus" aria-hidden="true" />
              <span>{{ statusText }}</span>
              <span class="dot">·</span>
              <span>阶段 {{ relationStage.label }}</span>
              <span class="dot">·</span>
              <span>模式 {{ orchestrationModeLabel }}</span>
              <span class="dot">·</span>
              <span class="chat-id">会话 {{ displaySessionId }}</span>
            </div>
            <p v-if="inlineTip" class="inline-tip">{{ inlineTip }}</p>
          </div>
        </div>

        <div class="topbar-actions">
          <button type="button" class="ghost-btn" :class="{ active: debugPanelEnabled }" @click="toggleDebugPanel">
            {{ debugPanelEnabled ? '关闭调试' : '开启调试' }}
          </button>
          <button type="button" class="ghost-btn" @click="collectLatestPhrase">收藏上一条</button>
          <button type="button" class="ghost-btn" @click="clearCurrentSession">清空会话</button>
        </div>
      </header>

      <div class="mobile-insight-strip" aria-label="移动端简化信息">
        <div class="mini-info">关系进度 {{ relationStage.progress }}%</div>
        <div class="mini-info">情绪 {{ emotionLabel }}</div>
        <div class="mini-info">模式 {{ orchestrationModeLabel }}</div>
      </div>
    </template>

    <section class="chat-shell">
      <ChatRoom
        :messages="currentMessages"
        :connection-status="connectionStatus"
        ai-type="love"
        :prompt-chips="promptChips"
        :tone-options="toneOptions"
        :selected-tone="currentTone"
        :quick-actions="quickActions"
        :placeholder="inputPlaceholder"
        @send-message="sendMessage"
        @quick-action="handleQuickAction"
        @tone-change="handleToneChange"
        @voice-request="simulateVoiceRequest"
      />
    </section>

    <template #right>
      <section class="insight-panel">
        <article class="insight-card">
          <div class="card-head">
            <h3>关系进度环</h3>
            <span>{{ relationStage.progress }}%</span>
          </div>
          <div class="progress-ring" :style="{ '--ring-progress': `${relationStage.progress}%` }">
            <div class="ring-inner">
              <p class="ring-stage">{{ relationStage.label }}</p>
              <p class="ring-note">{{ relationStage.note }}</p>
            </div>
          </div>
        </article>

        <article class="insight-card">
          <div class="card-head">
            <h3>情绪温度条</h3>
            <span>{{ emotionLabel }}</span>
          </div>
          <div class="emotion-track" aria-label="情绪温度">
            <div class="emotion-fill" :style="{ width: `${emotionPercent}%`, background: emotionGradient }" />
          </div>
          <p class="emotion-desc">{{ emotionDescription }}</p>
        </article>

        <article class="insight-card">
          <div class="card-head">
            <h3>执行轨迹</h3>
            <span>{{ orchestrationModeLabel }}</span>
          </div>
          <p class="emotion-desc" v-if="currentOrchestration.route">
            意图 {{ currentOrchestration.route.intent || '--' }} · 置信度 {{ formatConfidence(currentOrchestration.route.confidence) }}
          </p>
          <p class="emotion-desc" v-if="currentOrchestration.policy">
            策略 {{ currentOrchestration.policy.mode || '--' }} · {{ currentOrchestration.policy.reason || 'default' }}
          </p>
          <p class="emotion-desc" v-if="currentOrchestration.durationMs">
            本次耗时 {{ currentOrchestration.durationMs }} ms
          </p>
          <div v-if="currentTrace.length" class="trace-list">
            <article v-for="item in currentTrace" :key="item.id" class="trace-item">
              <div class="trace-head">
                <span class="trace-type">{{ item.type }}</span>
                <span class="trace-time">{{ formatTraceTime(item.time) }}</span>
              </div>
              <p class="trace-detail">{{ item.detail }}</p>
            </article>
          </div>
          <p v-else class="empty-tip">发起对话后，这里会显示路由、策略和执行步骤。</p>
        </article>

        <article v-if="debugPanelEnabled" class="insight-card debug-card">
          <div class="card-head">
            <h3>链路自检</h3>
            <div class="debug-actions">
              <button type="button" class="mini-btn" @click="exportDebugLogs">导出日志</button>
              <button type="button" class="mini-btn danger" @click="clearDebugLogs">清空</button>
              <span>{{ debugEvents.length }} 条</span>
            </div>
          </div>
          <div class="debug-grid">
            <div class="debug-row"><span>连接状态</span><strong>{{ connectionStatus }}</strong></div>
            <div class="debug-row"><span>会话ID</span><strong>{{ activeSessionId || '--' }}</strong></div>
            <div class="debug-row"><span>消息总数</span><strong>{{ debugSummary.totalMessages }}</strong></div>
            <div class="debug-row"><span>用户/AI</span><strong>{{ debugSummary.userMessages }}/{{ debugSummary.aiMessages }}</strong></div>
            <div class="debug-row"><span>占位索引</span><strong>{{ pendingAiMessageIndex }}</strong></div>
            <div class="debug-row"><span>占位有效</span><strong>{{ debugSummary.placeholderValid ? '是' : '否' }}</strong></div>
          </div>
          <div v-if="debugEvents.length" class="debug-list">
            <article v-for="item in debugEvents" :key="item.id" class="debug-item">
              <div class="debug-head">
                <span class="debug-type">{{ item.type }}</span>
                <span class="debug-time">{{ formatDebugTime(item.time) }}</span>
              </div>
              <p class="debug-detail">{{ item.detail }}</p>
            </article>
          </div>
          <p v-else class="empty-tip">暂无调试事件，发送一次消息后可观察链路细节。</p>
        </article>

        <article class="insight-card">
          <div class="card-head">
            <h3>关键话术收藏</h3>
            <span>{{ favoritePhrases.length }} 条</span>
          </div>
          <div v-if="favoritePhrases.length" class="favorite-list">
            <article v-for="(item, index) in favoritePhrases" :key="item.id" class="favorite-item">
              <p>{{ item.text }}</p>
              <div class="favorite-actions">
                <button type="button" class="mini-btn" @click="reuseFavorite(item.text)">调用</button>
                <button type="button" class="mini-btn danger" @click="removeFavorite(index)">移除</button>
              </div>
            </article>
          </div>
          <p v-else class="empty-tip">点击“收藏上一条”即可沉淀可复用话术。</p>
        </article>

        <article class="insight-card">
          <div class="card-head">
            <h3>一键生成约会卡</h3>
            <button type="button" class="mini-btn" @click="generateDateCard">生成</button>
          </div>
          <div v-if="dateCard" class="date-card">
            <div class="date-field"><span>主题</span><strong>{{ dateCard.theme }}</strong></div>
            <div class="date-field"><span>地点</span><strong>{{ dateCard.place }}</strong></div>
            <div class="date-field"><span>时间</span><strong>{{ dateCard.time }}</strong></div>
            <div class="date-field"><span>预算</span><strong>{{ dateCard.budget }}</strong></div>
            <p class="date-note">{{ dateCard.note }}</p>
          </div>
          <p v-else class="empty-tip">从当前对话中提取地点、时间和预算，自动整理成可执行计划。</p>
        </article>
      </section>
    </template>

    <teleport to="body">
      <div v-if="drawerOpen" class="drawer-mask" role="presentation" @click="drawerOpen = false" />
      <aside v-if="drawerOpen" class="drawer" role="dialog" aria-modal="true" aria-label="移动端侧栏">
        <header class="drawer-header">
          <h3>会话与助手</h3>
          <button type="button" class="ghost-btn" @click="drawerOpen = false">关闭</button>
        </header>

        <div class="drawer-body">
          <div class="mode-switch" role="tablist" aria-label="侧边模式">
            <button
              type="button"
              class="mode-btn"
              :class="{ active: sidebarMode === 'sessions' }"
              role="tab"
              :aria-selected="sidebarMode === 'sessions'"
              @click="sidebarMode = 'sessions'"
            >
              会话
            </button>
            <button
              type="button"
              class="mode-btn"
              :class="{ active: sidebarMode === 'assistants' }"
              role="tab"
              :aria-selected="sidebarMode === 'assistants'"
              @click="sidebarMode = 'assistants'"
            >
              助手库
            </button>
          </div>

          <template v-if="sidebarMode === 'sessions'">
            <button type="button" class="primary-btn" @click="createNewSession(); drawerOpen = false">+ 新对话</button>
            <label class="search-box">
              <span class="sr-only">搜索会话</span>
              <input v-model="searchKeyword" type="text" placeholder="搜索历史会话..." />
            </label>

            <div class="session-scroll" role="list">
              <article
                v-for="s in filteredSessions"
                :key="s.id"
                class="session-row"
                :class="{ active: s.id === activeSessionId }"
                role="button"
                tabindex="0"
                @click="selectSession(s.id); drawerOpen = false"
                @keydown.enter.prevent="selectSession(s.id); drawerOpen = false"
              >
                <div class="session-main">
                  <p class="session-title">{{ s.title || '新对话' }}</p>
                  <p class="session-meta">
                    <span>{{ formatSessionTime(s.updatedAt) }}</span>
                    <span>{{ s.messages?.length || 0 }} 条</span>
                  </p>
                </div>
              </article>
            </div>
          </template>

          <template v-else>
            <button
              v-for="assistant in assistantPresets"
              :key="assistant.id"
              type="button"
              class="assistant-card"
              @click="useAssistantPreset(assistant); drawerOpen = false"
            >
              <div class="assistant-head">
                <h3>{{ assistant.name }}</h3>
                <span class="assistant-tone">{{ assistant.toneLabel }}</span>
              </div>
              <p>{{ assistant.desc }}</p>
            </button>
          </template>
        </div>
      </aside>
    </teleport>
  </AppShell>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useHead } from '@vueuse/head'
import ChatRoom from '../components/ChatRoom.vue'
import AppShell from '../components/AppShell.vue'
import { chatWithOrchestrated } from '../api'

useHead({
  title: 'AI恋爱大师 - 小高AI超级智能体应用平台',
  meta: [
    {
      name: 'description',
      content: 'AI恋爱大师帮助你完成情绪洞察、沟通优化和约会计划，用理性与温度并存的方式处理亲密关系问题。'
    },
    {
      name: 'keywords',
      content: 'AI恋爱大师,情感咨询,关系分析,高情商沟通,约会建议'
    }
  ]
})

const router = useRouter()

const STORAGE_KEY = 'love_master_sessions_v2'
const LEGACY_STORAGE_KEYS = ['love_master_sessions_v1', 'love_master_sessions', 'love_master_sessions_v0', 'loveMasterSessions']
const FAVORITES_KEY = 'love_master_favorite_phrases_v1'
const DEBUG_STORAGE_KEY = 'love_master_debug_enabled'

const connectionStatus = ref('disconnected')
let orchestrationRequest = null

const sessions = ref([])
const activeSessionId = ref('')
const searchKeyword = ref('')
const drawerOpen = ref(false)
const sidebarMode = ref('sessions')
const currentTone = ref('gentle')
const favoritePhrases = ref([])
const dateCard = ref(null)
const inlineTip = ref('')
const debugPanelEnabled = ref(false)
const debugEvents = ref([])
const pendingAiMessageIndex = ref(-1)
let tipTimer = null

const toneOptions = [
  { label: '理性分析', value: 'rational' },
  { label: '温柔鼓励', value: 'gentle' },
  { label: '直接推进', value: 'direct' }
]

const quickActions = [
  { label: '总结一下', value: 'summary' },
  { label: '润色回复', value: 'rewrite' },
  { label: '推进计划', value: 'plan' },
  { label: '约会卡', value: 'date-card' }
]

const assistantPresets = [
  {
    id: 'daily-mindset',
    name: '每日心法',
    desc: '根据当下关系阶段，生成一条今日沟通策略和禁忌提醒。',
    prompt: '请给我今天的沟通心法：包含1条可执行动作、1条禁忌和1句鼓励。',
    tone: 'gentle',
    toneLabel: '温柔鼓励'
  },
  {
    id: 'date-ideas',
    name: '约会灵感',
    desc: '从预算、时间、关系氛围出发，给出可落地约会方案。',
    prompt: '请给我 3 个约会方案，分别包含地点、预算、流程和开场白。',
    tone: 'rational',
    toneLabel: '理性分析'
  },
  {
    id: 'message-polish',
    name: '沟通润色',
    desc: '把想说的话优化成高情商表达，避免冒犯和误解。',
    prompt: '请把我接下来输入的话润色成高情商表达，分别给出温柔版和坚定版。',
    tone: 'gentle',
    toneLabel: '温柔鼓励'
  },
  {
    id: 'relationship-upgrade',
    name: '关系进阶',
    desc: '从关系阶段与情绪信号中，给出下一步推进建议。',
    prompt: '请根据当前关系状态，给我未来7天可执行推进计划，并说明风险点。',
    tone: 'direct',
    toneLabel: '直接推进'
  }
]

const tonePromptMap = {
  rational: '请用结构化、理性、可执行的方式回答，优先给出明确步骤。',
  gentle: '请用温柔、共情、尊重的语气回答，同时保持建议具体可执行。',
  direct: '请直接指出核心问题和下一步行动，不要绕弯。'
}

const statusText = computed(() => {
  if (connectionStatus.value === 'connecting') return '正在思考中…'
  if (connectionStatus.value === 'error') return '连接异常'
  return '就绪'
})

const currentSession = computed(() => {
  return sessions.value.find((item) => item.id === activeSessionId.value) || null
})

const currentMessages = computed(() => {
  return currentSession.value?.messages || []
})

const currentOrchestration = computed(() => {
  return currentSession.value?.orchestration || {
    mode: 'CHAT',
    durationMs: 0,
    route: null,
    policy: null
  }
})

const currentTrace = computed(() => {
  return currentSession.value?.trace || []
})

const debugSummary = computed(() => {
  const session = currentSession.value
  const messages = Array.isArray(session?.messages) ? session.messages : []
  const userMessages = messages.filter((item) => item?.isUser).length
  const aiMessages = messages.filter((item) => item?.isUser === false).length
  const placeholderValid =
    pendingAiMessageIndex.value >= 0 &&
    pendingAiMessageIndex.value < messages.length &&
    messages[pendingAiMessageIndex.value]?.isUser === false
  return {
    totalMessages: messages.length,
    userMessages,
    aiMessages,
    placeholderValid
  }
})

const filteredSessions = computed(() => {
  const keyword = (searchKeyword.value || '').trim().toLowerCase()
  if (!keyword) return sessions.value
  return sessions.value.filter((item) => (item.title || '').toLowerCase().includes(keyword))
})

const displaySessionId = computed(() => {
  const id = String(activeSessionId.value || '')
  if (!id) return '--'
  return id.length > 8 ? `…${id.slice(-8)}` : id
})

const userMessageCount = computed(() => {
  return currentMessages.value.filter((msg) => msg.isUser).length
})

const relationStage = computed(() => {
  const count = userMessageCount.value
  if (count <= 2) {
    return { label: '初识', progress: 28, note: '先建立安全感与了解边界。' }
  }
  if (count <= 5) {
    return { label: '暧昧', progress: 52, note: '重点在节奏感和回应质量。' }
  }
  if (count <= 9) {
    return { label: '升温', progress: 74, note: '适合推进见面与共同体验。' }
  }
  return { label: '稳态', progress: 91, note: '关注长期沟通模式和冲突修复。' }
})

const emotionMetrics = computed(() => {
  const recent = currentMessages.value.slice(-8).map((item) => String(item.content || '')).join(' ')

  const positiveWords = ['开心', '喜欢', '舒服', '期待', '放松', '甜蜜', '主动', '见面', '约会', '感谢', '在意', '愿意']
  const negativeWords = ['冷淡', '焦虑', '生气', '失望', '尴尬', '冲突', '拒绝', '忽略', '压力', '担心', '误会', '回避']

  let score = 0
  positiveWords.forEach((word) => {
    if (recent.includes(word)) score += 1
  })
  negativeWords.forEach((word) => {
    if (recent.includes(word)) score -= 1
  })

  const normalized = Math.max(-1, Math.min(1, score / 4))
  const percent = Math.round((normalized + 1) * 50)

  if (normalized > 0.35) {
    return {
      percent,
      label: '积极升温',
      description: '当前对话整体偏积极，可以尝试推进下一步行动。',
      gradient: 'linear-gradient(90deg, #9bd3ae, #d0c779)'
    }
  }

  if (normalized < -0.2) {
    return {
      percent,
      label: '略有压力',
      description: '建议先做情绪缓冲，再进入观点表达。',
      gradient: 'linear-gradient(90deg, #cb6f6f, #d29d4e)'
    }
  }

  return {
    percent,
    label: '平稳观察',
    description: '关系处于平稳区间，优先保持稳定沟通频率。',
    gradient: 'linear-gradient(90deg, #a2bbdc, #d99e82)'
  }
})

const emotionPercent = computed(() => emotionMetrics.value.percent)
const emotionLabel = computed(() => emotionMetrics.value.label)
const emotionDescription = computed(() => emotionMetrics.value.description)
const emotionGradient = computed(() => emotionMetrics.value.gradient)

const orchestrationModeLabel = computed(() => {
  const mode = String(currentOrchestration.value.mode || '').toUpperCase()
  if (mode === 'VISION') return '图像链路'
  if (mode === 'TOOL') return '工具链路'
  if (mode === 'AGENT') return '智能体链路'
  if (mode === 'BLOCK') return '安全拦截'
  return '对话链路'
})

const promptChips = computed(() => {
  const chips = [
    '帮我判断对方真实想法',
    '给我三句高情商回复',
    '把这句话改得更自然',
    '总结这轮沟通风险点'
  ]

  if (relationStage.value.progress >= 70) {
    chips.push('帮我设计一次自然邀约')
  }

  if (emotionMetrics.value.label === '略有压力') {
    chips.push('先帮我缓和一下语气')
  }

  return chips
})

const inputPlaceholder = computed(() => {
  if (currentTone.value === 'rational') return '描述你当前的沟通情境，我会给你结构化策略...'
  if (currentTone.value === 'direct') return '直接输入你想推进的目标，我给你最短行动路径...'
  return '说说你最近的关系状态，我会温柔但清晰地帮你分析...'
})

const showInlineTip = (text) => {
  inlineTip.value = text
  if (tipTimer) {
    clearTimeout(tipTimer)
  }
  tipTimer = setTimeout(() => {
    inlineTip.value = ''
    tipTimer = null
  }, 3200)
}

const pushDebugEvent = (type, detail) => {
  if (!debugPanelEnabled.value) return
  debugEvents.value.unshift({
    id: `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
    type: String(type || 'debug'),
    detail: String(detail || ''),
    time: Date.now()
  })
  debugEvents.value = debugEvents.value.slice(0, 80)
}

const toggleDebugPanel = () => {
  debugPanelEnabled.value = !debugPanelEnabled.value
  try {
    localStorage.setItem(DEBUG_STORAGE_KEY, debugPanelEnabled.value ? '1' : '0')
  } catch (error) {
    // ignore
  }
  pushDebugEvent('调试面板', debugPanelEnabled.value ? '已开启' : '已关闭')
}

const formatDebugFileTime = (time) => {
  const date = new Date(time || Date.now())
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}_${pad(date.getHours())}${pad(date.getMinutes())}${pad(date.getSeconds())}`
}

const buildDebugSnapshot = () => {
  const session = currentSession.value
  const messages = Array.isArray(session?.messages) ? session.messages : []
  return {
    exportedAt: new Date().toISOString(),
    connectionStatus: connectionStatus.value,
    activeSessionId: activeSessionId.value,
    pendingAiMessageIndex: pendingAiMessageIndex.value,
    summary: debugSummary.value,
    orchestration: currentOrchestration.value,
    trace: currentTrace.value,
    messages: messages.map((item, index) => ({
      index,
      isUser: !!item?.isUser,
      contentLength: String(item?.content || '').length,
      content: String(item?.content || ''),
      time: item?.time || null
    })),
    debugEvents: debugEvents.value
  }
}

const exportDebugLogs = () => {
  try {
    pushDebugEvent('导出日志', '开始导出链路日志')
    const payload = buildDebugSnapshot()
    const content = JSON.stringify(payload, null, 2)
    const blob = new Blob([content], { type: 'application/json;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `love_master_debug_${formatDebugFileTime(Date.now())}.json`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    showInlineTip('调试日志已导出')
  } catch (error) {
    console.error('Export debug logs failed', error)
    showInlineTip('导出失败，请稍后重试')
  }
}

const clearDebugLogs = () => {
  debugEvents.value = []
  showInlineTip('调试日志已清空')
}

const generateChatId = () => `love_${Math.random().toString(36).substring(2, 10)}`

const persistSessions = () => {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(sessions.value))
  } catch (error) {
    console.warn('Failed to persist sessions', error)
  }
}

const persistFavorites = () => {
  try {
    localStorage.setItem(FAVORITES_KEY, JSON.stringify(favoritePhrases.value))
  } catch (error) {
    console.warn('Failed to persist favorites', error)
  }
}

const normalizeMessage = (raw, index = 0) => {
  if (typeof raw === 'string') {
    return {
      id: `legacy_msg_${index}_${Date.now()}`,
      content: raw,
      isUser: false,
      images: [],
      time: Date.now()
    }
  }

  if (!raw || typeof raw !== 'object') {
    return null
  }

  const role = String(raw.role || raw.sender || '').toLowerCase()
  const guessedIsUser = role === 'user' || role === 'human' || role === 'me'
  const isUser = typeof raw.isUser === 'boolean' ? raw.isUser : guessedIsUser
  const content = String(raw.content ?? raw.text ?? raw.message ?? '')

  const normalizedImages = Array.isArray(raw.images)
    ? raw.images.filter((item) => typeof item === 'string' && item)
    : []

  const rawTime = Number(raw.time ?? raw.timestamp ?? raw.createdAt ?? raw.updatedAt)
  const time = Number.isFinite(rawTime) && rawTime > 0 ? rawTime : Date.now()

  return {
    id: raw.id || `legacy_msg_${index}_${time}`,
    content,
    isUser,
    images: normalizedImages,
    time,
    type: raw.type || ''
  }
}

const ensureSessionRuntime = (session) => {
  if (!session) return

  if (!Array.isArray(session.messages)) {
    session.messages = []
  }
  session.messages = session.messages
    .map((item, index) => normalizeMessage(item, index))
    .filter((item) => {
      if (!item) return false
      if (item.content) return true
      if (item.images && item.images.length > 0) return true
      // 保留空内容 AI 占位消息，供流式回写
      return item.isUser === false
    })

  if (!Array.isArray(session.trace)) {
    session.trace = []
  } else {
    session.trace = session.trace
      .map((item, index) => {
        if (!item || typeof item !== 'object') return null
        return {
          id: item.id || `trace_${index}_${Date.now()}`,
          type: String(item.type || '记录'),
          detail: String(item.detail || ''),
          time: Number(item.time) || Date.now()
        }
      })
      .filter(Boolean)
  }

  if (!session.orchestration || typeof session.orchestration !== 'object') {
    session.orchestration = {
      mode: 'CHAT',
      durationMs: 0,
      route: null,
      policy: null
    }
  } else {
    session.orchestration = {
      mode: String(session.orchestration.mode || 'CHAT').toUpperCase(),
      durationMs: Number(session.orchestration.durationMs || 0),
      route: session.orchestration.route || null,
      policy: session.orchestration.policy || null
    }
  }
}

const appendTrace = (session, type, detail) => {
  if (!session) return
  ensureSessionRuntime(session)
  session.trace.unshift({
    id: `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
    type,
    detail,
    time: Date.now()
  })
  session.trace = session.trace.slice(0, 40)
}

const closeConnections = () => {
  if (orchestrationRequest) {
    orchestrationRequest.close()
    orchestrationRequest = null
    pushDebugEvent('连接关闭', '手动中止当前 SSE 请求')
  }
  connectionStatus.value = 'disconnected'
  pendingAiMessageIndex.value = -1
}

const upsertWelcomeMessage = (session) => {
  ensureSessionRuntime(session)
  if (!session.messages || session.messages.length === 0) {
    session.messages = [
      {
        content: '欢迎来到AI恋爱大师。你可以描述关系困扰、粘贴聊天记录或上传截图，我会帮你分析并给出下一步建议。',
        isUser: false,
        images: [],
        time: Date.now()
      }
    ]
  }
}

const touchSession = () => {
  const session = currentSession.value
  if (!session) return
  session.updatedAt = Date.now()
  sessions.value = [...sessions.value].sort((a, b) => (b.updatedAt || 0) - (a.updatedAt || 0))
}

const createNewSession = () => {
  closeConnections()

  const id = generateChatId()
  const now = Date.now()
  const session = {
    id,
    title: '新对话',
    updatedAt: now,
    messages: [],
    trace: [],
    orchestration: {
      mode: 'CHAT',
      durationMs: 0,
      route: null,
      policy: null
    }
  }

  upsertWelcomeMessage(session)

  sessions.value.unshift(session)
  activeSessionId.value = id
  persistSessions()
  sidebarMode.value = 'sessions'
}

const selectSession = (id) => {
  if (id === activeSessionId.value) return
  closeConnections()
  activeSessionId.value = id
  sidebarMode.value = 'sessions'
}

const renameSession = (id) => {
  const session = sessions.value.find((item) => item.id === id)
  if (!session) return
  const nextTitle = window.prompt('请输入新的会话标题', session.title || '新对话')
  if (!nextTitle) return
  session.title = nextTitle.trim() || session.title
  session.updatedAt = Date.now()
  persistSessions()
}

const deleteSession = (id) => {
  const target = sessions.value.find((item) => item.id === id)
  if (!target) return

  const confirmed = window.confirm(`确认删除会话「${target.title || '新对话'}」吗？`)
  if (!confirmed) return

  if (id === activeSessionId.value) {
    closeConnections()
  }

  sessions.value = sessions.value.filter((item) => item.id !== id)

  if (!sessions.value.length) {
    createNewSession()
    return
  }

  if (id === activeSessionId.value) {
    activeSessionId.value = sessions.value[0].id
  }

  persistSessions()
}

const clearCurrentSession = () => {
  const session = currentSession.value
  if (!session) return

  closeConnections()
  session.messages = []
  upsertWelcomeMessage(session)
  session.updatedAt = Date.now()
  persistSessions()
  showInlineTip('当前会话已清空')
}

const maybeUpdateTitle = (text, images) => {
  const session = currentSession.value
  if (!session) return
  if (session.title && session.title !== '新对话') return

  const trimmed = (text || '').trim()
  if (trimmed) {
    session.title = trimmed.length > 14 ? `${trimmed.slice(0, 14)}…` : trimmed
    return
  }

  if (images && images.length > 0) {
    session.title = '图片分析'
  }
}

const buildPromptByTone = (originText, toneValue, hasImages) => {
  const tonePrompt = tonePromptMap[toneValue] || ''
  const text = (originText || '').trim()

  if (text) {
    return tonePrompt ? `${tonePrompt}\n${text}` : text
  }

  if (hasImages) {
    return tonePrompt ? `${tonePrompt}\n请结合图片帮我分析当前沟通状态。` : '请结合图片帮我分析当前沟通状态。'
  }

  return ''
}

const sendMessage = (messageData) => {
  const session = currentSession.value
  if (!session) return

  ensureSessionRuntime(session)

  const originText = typeof messageData === 'string' ? messageData : (messageData?.text || '')
  const images = Array.isArray(messageData?.images) ? messageData.images : []
  const toneValue = typeof messageData === 'object' ? (messageData?.tone || currentTone.value) : currentTone.value
  const forceMode = typeof messageData === 'object' ? (messageData?.forceMode || '') : ''
  const requestText = buildPromptByTone(originText, toneValue, images.length > 0)

  if (!requestText && images.length === 0) return

  maybeUpdateTitle(originText, images)

  session.messages.push({
    content: originText || (images.length > 0 ? '请分析这张图片' : ''),
    isUser: true,
    images,
    time: Date.now()
  })

  closeConnections()

  const aiMessageIndex = session.messages.length
  pendingAiMessageIndex.value = aiMessageIndex
  session.messages.push({
    content: '',
    isUser: false,
    images: [],
    time: Date.now()
  })
  pushDebugEvent(
    '发送消息',
    `text=${originText ? originText.length : 0} chars, images=${images.length}, aiIndex=${aiMessageIndex}, total=${session.messages.length}`
  )

  connectionStatus.value = 'connecting'
  touchSession()
  persistSessions()

  appendTrace(session, '请求发起', `输入 ${originText ? originText.length : 0} 字，图片 ${images.length} 张`)

  orchestrationRequest = chatWithOrchestrated(
    requestText,
    activeSessionId.value,
    images,
    forceMode,
    (rawEvent) => {
      if (rawEvent === '[DONE]') {
        pushDebugEvent('流结束标记', `status=${connectionStatus.value}`)
        if (connectionStatus.value === 'connecting') {
          connectionStatus.value = 'disconnected'
          touchSession()
          persistSessions()
        }
        orchestrationRequest = null
        pendingAiMessageIndex.value = -1
        return
      }

      let event
      if (typeof rawEvent === 'string') {
        try {
          event = JSON.parse(rawEvent)
        } catch (error) {
          if (session && aiMessageIndex < session.messages.length) {
            session.messages[aiMessageIndex].content += rawEvent
          }
          pushDebugEvent('非结构化片段', `len=${rawEvent.length}`)
          return
        }
      } else {
        event = rawEvent
      }

      const type = String(event?.type || '')
      const payload = event?.payload || {}

      if (!type) {
        return
      }

      if (type === 'route') {
        session.orchestration.route = payload
        const confidence = Number(payload?.confidence || 0)
        appendTrace(session, '路由判定', `intent=${payload?.intent || '--'}，置信度=${Number.isFinite(confidence) ? confidence.toFixed(2) : '--'}`)
        pushDebugEvent('route', `intent=${payload?.intent || '--'}, confidence=${Number.isFinite(confidence) ? confidence.toFixed(2) : '--'}`)
        return
      }

      if (type === 'policy') {
        session.orchestration.policy = payload
        session.orchestration.mode = payload?.mode || 'CHAT'
        appendTrace(session, '策略决策', `${payload?.mode || 'CHAT'} · ${payload?.reason || 'default'}`)
        pushDebugEvent('policy', `mode=${payload?.mode || 'CHAT'}, reason=${payload?.reason || 'default'}`)
        return
      }

      if (type === 'chunk') {
        if (!session || aiMessageIndex >= session.messages.length) {
          pushDebugEvent('chunk丢失', `aiIndex=${aiMessageIndex}, current=${session?.messages?.length ?? 0}`)
          return
        }
        const content = String(payload?.content || '')
        if (content) {
          session.messages[aiMessageIndex].content += content
          pushDebugEvent('chunk', `+${content.length} chars`)
        }
        return
      }

      if (type === 'agent_step') {
        if (!session || aiMessageIndex >= session.messages.length) {
          pushDebugEvent('agent_step丢失', `aiIndex=${aiMessageIndex}, current=${session?.messages?.length ?? 0}`)
          return
        }
        const content = String(payload?.content || '')
        if (content) {
          if (session.messages[aiMessageIndex].content) {
            session.messages[aiMessageIndex].content += '\n'
          }
          session.messages[aiMessageIndex].content += content
          appendTrace(session, `执行步骤 ${payload?.step || ''}`.trim(), content)
          pushDebugEvent('agent_step', `step=${payload?.step || ''}, len=${content.length}`)
        }
        return
      }

      if (type === 'error') {
        if (!session) return
        connectionStatus.value = 'error'
        const errorMessage = String(payload?.message || '请求失败，请稍后重试')
        if (aiMessageIndex < session.messages.length && !session.messages[aiMessageIndex].content) {
          session.messages[aiMessageIndex].content = errorMessage
        }
        appendTrace(session, '执行异常', errorMessage)
        pushDebugEvent('error', errorMessage)
        touchSession()
        persistSessions()
        orchestrationRequest = null
        pendingAiMessageIndex.value = -1
        return
      }

      if (type === 'done') {
        if (!session) return
        session.orchestration.mode = payload?.mode || session.orchestration.mode || 'CHAT'
        session.orchestration.durationMs = Number(payload?.durationMs || 0)
        connectionStatus.value = payload?.error ? 'error' : 'disconnected'
        appendTrace(
          session,
          '执行完成',
          `${session.orchestration.mode} · ${session.orchestration.durationMs}ms`
        )
        pushDebugEvent('done', `mode=${session.orchestration.mode}, duration=${session.orchestration.durationMs}ms`)
        touchSession()
        persistSessions()
        orchestrationRequest = null
        pendingAiMessageIndex.value = -1
      }
    },
    (error) => {
      console.error('Orchestration SSE Error:', error)
      connectionStatus.value = 'error'
      if (aiMessageIndex < session.messages.length && !session.messages[aiMessageIndex].content) {
        session.messages[aiMessageIndex].content = '抱歉，请求失败，请稍后重试。'
      }
      appendTrace(session, '连接错误', error?.message || 'SSE 连接失败')
      pushDebugEvent('sse_error', error?.message || 'SSE 连接失败')
      touchSession()
      persistSessions()
      orchestrationRequest = null
      pendingAiMessageIndex.value = -1
    }
  )
}

const collectLatestPhrase = () => {
  const latestAiMessage = [...currentMessages.value].reverse().find((item) => !item.isUser && item.content)
  if (!latestAiMessage) {
    showInlineTip('当前没有可收藏的话术')
    return
  }

  const candidate = latestAiMessage.content
    .split(/\n|。|！|？/)
    .map((item) => item.trim())
    .find((item) => item.length >= 8)

  if (!candidate) {
    showInlineTip('未提取到合适的话术片段')
    return
  }

  if (favoritePhrases.value.some((item) => item.text === candidate)) {
    showInlineTip('这条话术已收藏')
    return
  }

  favoritePhrases.value.unshift({ id: `${Date.now()}_${Math.random()}`, text: candidate })
  favoritePhrases.value = favoritePhrases.value.slice(0, 20)
  persistFavorites()
  showInlineTip('已收藏到关键话术')
}

const removeFavorite = (index) => {
  favoritePhrases.value.splice(index, 1)
  persistFavorites()
}

const reuseFavorite = (text) => {
  sendMessage({ text, images: [], tone: currentTone.value })
}

const simulateVoiceRequest = () => {
  showInlineTip('语音输入即将上线，先用文字描述也可以。')
}

const handleToneChange = (toneValue) => {
  currentTone.value = toneValue
}

const handleQuickAction = (actionValue) => {
  if (actionValue === 'summary') {
    sendMessage({
      text: '请总结本轮对话中的核心问题、风险点，并给出我下一步的优先动作。',
      images: [],
      tone: 'rational'
    })
    return
  }

  if (actionValue === 'rewrite') {
    sendMessage({
      text: '请把我刚才的表达分别润色成“温柔版”和“坚定版”，并说明使用场景。',
      images: [],
      tone: 'gentle'
    })
    return
  }

  if (actionValue === 'plan') {
    sendMessage({
      text: '请给我未来7天关系推进计划：每日目标、行动、风险和备选策略。',
      images: [],
      tone: 'direct',
      forceMode: 'agent'
    })
    return
  }

  if (actionValue === 'date-card') {
    generateDateCard()
  }
}

const useAssistantPreset = (assistant) => {
  currentTone.value = assistant.tone
  sidebarMode.value = 'sessions'
  sendMessage({ text: assistant.prompt, images: [], tone: assistant.tone })
}

const generateDateCard = () => {
  const corpus = currentMessages.value
    .map((item) => String(item.content || ''))
    .join(' ')

  const placeMatch = corpus.match(/咖啡馆|餐厅|公园|书店|展览|电影院|酒吧|美术馆|海边/)
  const timeMatch = corpus.match(/今晚|明天|周末|下周|周[一二三四五六日天][上下晚]?/)
  const budgetMatch = corpus.match(/\d{2,4}\s*元/)

  dateCard.value = {
    theme: relationStage.value.progress >= 70 ? '轻约会升温' : '舒适破冰局',
    place: placeMatch ? placeMatch[0] : '安静咖啡馆',
    time: timeMatch ? timeMatch[0] : '周末晚上',
    budget: budgetMatch ? budgetMatch[0].replace(/\s+/g, '') : '200-400元',
    note: relationStage.value.progress >= 70
      ? '重点营造共同体验感，结束前给出下一次见面锚点。'
      : '重点轻松互动，避免高压话题，先建立稳定沟通节奏。'
  }

  showInlineTip('约会卡已生成，可直接执行')
}

const goBack = () => {
  router.push('/')
}

const formatSessionTime = (timestamp) => {
  if (!timestamp) return '--'
  const date = new Date(timestamp)
  const now = new Date()
  const sameDay = date.toDateString() === now.toDateString()

  if (sameDay) {
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }

  return date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
}

const formatTraceTime = (timestamp) => {
  if (!timestamp) return '--'
  const date = new Date(timestamp)
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

const formatDebugTime = (timestamp) => {
  if (!timestamp) return '--'
  const date = new Date(timestamp)
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

const formatConfidence = (value) => {
  const num = Number(value)
  if (!Number.isFinite(num)) return '--'
  return num.toFixed(2)
}

onMounted(() => {
  try {
    debugPanelEnabled.value = localStorage.getItem(DEBUG_STORAGE_KEY) === '1'
  } catch (error) {
    debugPanelEnabled.value = false
  }

  try {
    const favoritesRaw = localStorage.getItem(FAVORITES_KEY)
    const favorites = favoritesRaw ? JSON.parse(favoritesRaw) : []
    favoritePhrases.value = Array.isArray(favorites)
      ? favorites
          .slice(0, 20)
          .map((item, index) => {
            if (typeof item === 'string') {
              return { id: `legacy_${index}`, text: item }
            }
            return {
              id: item?.id || `fav_${index}_${Date.now()}`,
              text: String(item?.text || '').trim()
            }
          })
          .filter((item) => item.text)
      : []
  } catch (error) {
    favoritePhrases.value = []
  }

  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    const parsed = raw ? JSON.parse(raw) : []
    sessions.value = Array.isArray(parsed) ? parsed : []
  } catch (error) {
    sessions.value = []
  }

  if (!sessions.value.length) {
    for (const key of LEGACY_STORAGE_KEYS) {
      try {
        const legacyRaw = localStorage.getItem(key)
        const legacyParsed = legacyRaw ? JSON.parse(legacyRaw) : []
        if (Array.isArray(legacyParsed) && legacyParsed.length > 0) {
          sessions.value = legacyParsed
          persistSessions()
          break
        }
      } catch (error) {
        // ignore legacy parse errors
      }
    }
  }

  sessions.value = sessions.value.filter((item) => item && typeof item === 'object')

  if (!sessions.value.length) {
    createNewSession()
    return
  }

  sessions.value.forEach((session) => {
    if (!session || typeof session !== 'object') return
    if (!session.id) session.id = generateChatId()
    if (!session.updatedAt) session.updatedAt = Date.now()
    if (!session.title) session.title = '新对话'
    ensureSessionRuntime(session)
    upsertWelcomeMessage(session)
  })

  sessions.value = [...sessions.value].sort((a, b) => (b.updatedAt || 0) - (a.updatedAt || 0))
  activeSessionId.value = sessions.value[0].id
  pushDebugEvent('初始化', `sessions=${sessions.value.length}, active=${activeSessionId.value || '--'}`)
})

onBeforeUnmount(() => {
  closeConnections()
  if (tipTimer) {
    clearTimeout(tipTimer)
    tipTimer = null
  }
})
</script>

<style scoped>
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.sidebar-panel {
  display: flex;
  flex-direction: column;
  min-height: 100%;
}

.sidebar-head {
  padding: var(--space-2);
  border-bottom: 1px solid var(--color-border);
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.brand-wrap {
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand-mark {
  width: 42px;
  height: 42px;
  border-radius: 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: linear-gradient(135deg, var(--color-primary), var(--color-plum));
  box-shadow: var(--shadow-sm);
}

.brand-title {
  font-size: 24px;
  line-height: 1;
}

.brand-subtitle {
  margin-top: 4px;
  font-size: 12px;
  color: var(--color-text-muted);
}

.mode-switch {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.mode-btn {
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.82);
  color: var(--color-text-muted);
  padding: 7px 10px;
  font-size: 12px;
}

.mode-btn.active {
  border-color: rgba(217, 158, 130, 0.5);
  background: rgba(217, 158, 130, 0.2);
  color: var(--color-text);
}

.sidebar-body {
  flex: 1;
  min-height: 0;
  padding: var(--space-2);
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.primary-btn {
  border: 0;
  border-radius: 14px;
  padding: 11px 14px;
  background: linear-gradient(135deg, var(--color-primary), var(--color-primary-strong));
  color: #fff;
  font-weight: 700;
  box-shadow: 0 10px 24px rgba(199, 134, 103, 0.34);
}

.search-box input {
  width: 100%;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  padding: 10px 12px;
  background: #fff;
}

.search-box input:focus {
  border-color: rgba(217, 158, 130, 0.52);
}

.session-scroll,
.assistant-scroll {
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 0;
}

.session-row {
  border: 1px solid transparent;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.82);
  padding: 10px;
  display: flex;
  gap: 10px;
  justify-content: space-between;
}

.session-row:hover,
.session-row:focus-visible {
  border-color: rgba(217, 158, 130, 0.45);
  background: rgba(245, 239, 230, 0.92);
}

.session-row.active {
  border-color: rgba(217, 158, 130, 0.62);
  background: linear-gradient(140deg, rgba(217, 158, 130, 0.2), rgba(234, 201, 193, 0.22));
}

.session-main {
  flex: 1;
  min-width: 0;
}

.session-title {
  margin: 0;
  font-weight: 700;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-meta {
  margin: 6px 0 0;
  display: flex;
  justify-content: space-between;
  color: var(--color-text-soft);
  font-size: 12px;
}

.session-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.icon-action {
  border: 1px solid var(--color-border);
  border-radius: 999px;
  padding: 4px 8px;
  font-size: 11px;
  color: var(--color-text-muted);
  background: rgba(255, 255, 255, 0.92);
}

.icon-action.danger {
  color: var(--color-danger);
}

.assistant-card {
  border: 1px solid var(--color-border);
  border-radius: 16px;
  padding: 12px;
  background: rgba(255, 255, 255, 0.88);
  text-align: left;
  display: flex;
  flex-direction: column;
  gap: 8px;
  transition: transform 0.22s ease, box-shadow 0.22s ease, border-color 0.22s ease;
}

@media (hover: hover) {
  .assistant-card:hover {
    transform: translateY(-2px);
    border-color: rgba(217, 158, 130, 0.42);
    box-shadow: var(--shadow-sm);
  }
}

.assistant-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.assistant-head h3 {
  font-size: 18px;
}

.assistant-tone {
  font-size: 11px;
  color: var(--color-text-muted);
  border: 1px solid var(--color-border);
  border-radius: 999px;
  padding: 2px 8px;
}

.assistant-card p {
  color: var(--color-text-muted);
  font-size: 13px;
  line-height: 1.55;
}

.topbar {
  padding: var(--space-2);
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.topbar-main {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  min-width: 0;
}

.title-wrap {
  min-width: 0;
}

.session-heading {
  font-size: 26px;
  line-height: 1.1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.topbar-subline {
  margin-top: 6px;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  font-size: 12px;
  color: var(--color-text-muted);
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-success);
}

.status-dot.connecting {
  background: var(--color-warning);
}

.status-dot.error {
  background: var(--color-danger);
}

.dot {
  opacity: 0.55;
}

.chat-id {
  max-width: 150px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.inline-tip {
  margin-top: 6px;
  font-size: 12px;
  color: var(--color-plum);
}

.topbar-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.ghost-btn {
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.92);
  color: var(--color-text-muted);
  padding: 8px 12px;
  font-size: 12px;
}

.ghost-btn.active {
  border-color: rgba(162, 187, 220, 0.62);
  background: rgba(162, 187, 220, 0.22);
  color: var(--color-text);
}

.mobile-only {
  display: none;
}

.desktop-only {
  display: inline-flex;
}

.mobile-insight-strip {
  display: none;
  padding: 0 var(--space-2) var(--space-2);
  gap: 8px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.mini-info {
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.82);
  text-align: center;
  padding: 7px 6px;
  font-size: 11px;
  color: var(--color-text-muted);
}

.chat-shell {
  flex: 1;
  min-height: 0;
  display: flex;
}

.insight-panel {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.insight-card {
  border: 1px solid var(--color-border);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.86);
  padding: 14px;
  box-shadow: var(--shadow-sm);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.debug-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.card-head h3 {
  font-size: 18px;
  line-height: 1.2;
}

.card-head span {
  font-size: 12px;
  color: var(--color-text-muted);
}

.progress-ring {
  width: 154px;
  height: 154px;
  margin: 0 auto;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: conic-gradient(
    from 220deg,
    var(--color-primary) 0 var(--ring-progress),
    rgba(217, 158, 130, 0.18) var(--ring-progress) 100%
  );
}

.ring-inner {
  width: 122px;
  height: 122px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid var(--color-border);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 10px;
}

.ring-stage {
  font-weight: 700;
  font-size: 18px;
  color: var(--color-text);
}

.ring-note {
  margin-top: 4px;
  font-size: 11px;
  color: var(--color-text-muted);
  line-height: 1.4;
}

.emotion-track {
  height: 10px;
  background: rgba(61, 61, 61, 0.08);
  border-radius: 999px;
  overflow: hidden;
}

.emotion-fill {
  height: 100%;
  border-radius: inherit;
  transition: width 0.3s ease;
}

.emotion-desc {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-muted);
  line-height: 1.5;
}

.trace-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 260px;
  overflow: auto;
  padding-right: 2px;
}

.trace-item {
  border: 1px solid var(--color-border);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.86);
  padding: 8px 10px;
}

.trace-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.trace-type {
  font-size: 12px;
  font-weight: 700;
  color: var(--color-text);
}

.trace-time {
  font-size: 11px;
  color: var(--color-text-soft);
}

.trace-detail {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--color-text-muted);
  line-height: 1.45;
  white-space: pre-wrap;
  word-break: break-word;
}

.debug-card {
  border-style: dashed;
}

.debug-grid {
  display: grid;
  gap: 6px;
}

.debug-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  font-size: 12px;
  color: var(--color-text-muted);
}

.debug-row strong {
  color: var(--color-text);
  font-size: 12px;
  word-break: break-all;
}

.debug-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 260px;
  overflow: auto;
  padding-right: 2px;
}

.debug-item {
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.84);
  padding: 7px 9px;
}

.debug-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.debug-type {
  font-size: 11px;
  font-weight: 700;
  color: var(--color-text);
}

.debug-time {
  font-size: 10px;
  color: var(--color-text-soft);
}

.debug-detail {
  margin: 4px 0 0;
  font-size: 11px;
  color: var(--color-text-muted);
  line-height: 1.45;
  white-space: pre-wrap;
  word-break: break-word;
}

.favorite-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.favorite-item {
  border: 1px solid var(--color-border);
  border-radius: 12px;
  background: rgba(245, 239, 230, 0.6);
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.favorite-item p {
  margin: 0;
  font-size: 13px;
  line-height: 1.55;
}

.favorite-actions {
  display: flex;
  gap: 8px;
}

.mini-btn {
  border: 1px solid var(--color-border);
  border-radius: 999px;
  padding: 4px 10px;
  font-size: 11px;
  color: var(--color-text-muted);
  background: rgba(255, 255, 255, 0.92);
}

.mini-btn.danger {
  color: var(--color-danger);
}

.date-card {
  border: 1px solid var(--color-border);
  border-radius: 14px;
  padding: 10px;
  background: rgba(245, 239, 230, 0.65);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.date-field {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  font-size: 12px;
  color: var(--color-text-muted);
}

.date-field strong {
  color: var(--color-text);
  font-size: 13px;
}

.date-note {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--color-text-muted);
  line-height: 1.5;
}

.empty-tip {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-soft);
  line-height: 1.5;
}

.drawer-mask {
  position: fixed;
  inset: 0;
  background: rgba(61, 61, 61, 0.28);
  z-index: 70;
}

.drawer {
  position: fixed;
  left: 0;
  top: 0;
  bottom: 0;
  width: min(90vw, 380px);
  background: rgba(255, 255, 255, 0.96);
  border-right: 1px solid var(--color-border);
  box-shadow: var(--shadow-lg);
  z-index: 71;
  display: flex;
  flex-direction: column;
}

.drawer-header {
  padding: 14px;
  border-bottom: 1px solid var(--color-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.drawer-header h3 {
  font-size: 22px;
}

.drawer-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px;
}

@media (max-width: 1023px) {
  .mobile-only {
    display: inline-flex;
  }

  .desktop-only {
    display: none;
  }

  .topbar {
    padding: 12px;
    flex-direction: column;
  }

  .topbar-main {
    width: 100%;
  }

  .topbar-actions {
    width: 100%;
  }

  .ghost-btn {
    flex: 1;
    text-align: center;
  }

  .session-heading {
    font-size: 22px;
  }

  .mobile-insight-strip {
    display: grid;
  }
}
</style>
