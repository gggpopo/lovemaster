<template>
  <AppShell>
    <template #sidebar>
      <div class="sidebar">
        <div class="sidebar-title">快捷入口</div>
        <div class="sidebar-desc">更快地开始一次高质量提问</div>

        <div class="quick-list" role="list">
          <button type="button" class="quick" @click="prefill('请帮我把这个需求拆解成可执行的步骤，并给出风险点与验收标准。')">任务拆解</button>
          <button type="button" class="quick" @click="prefill('请你作为资深工程师，帮我做一次代码审查：列出潜在 bug、可维护性问题与优化建议。')">代码审查</button>
          <button type="button" class="quick" @click="prefill('请用表格对比 3 种方案的优缺点、成本、适用场景，并给出推荐。')">方案对比</button>
          <button type="button" class="quick" @click="prefill('请生成一份可直接执行的 TODO 列表，并按优先级排序。')">生成 TODO</button>
        </div>

        <div class="sidebar-actions">
          <button type="button" class="ghost" @click="clearChat" :disabled="connectionStatus === 'connecting'">清空对话</button>
          <button type="button" class="ghost" @click="goBack">返回首页</button>
        </div>
      </div>
    </template>

    <template #header>
      <header class="topbar">
        <div class="topbar-title">AI 超级智能体</div>
        <div class="topbar-sub">
          <span class="status-dot" :class="connectionStatus" aria-hidden="true" />
          <span class="status-text">{{ statusText }}</span>
        </div>
      </header>
    </template>

    <section class="chat-shell">
      <ChatRoom
        :messages="messages"
        :connection-status="connectionStatus"
        ai-type="super"
        :enable-images="false"
        @send-message="sendMessage"
      />
    </section>
  </AppShell>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useHead } from '@vueuse/head'
import ChatRoom from '../components/ChatRoom.vue'
import AppShell from '../components/AppShell.vue'
import { chatWithManus } from '../api'

// 设置页面标题和元数据
useHead({
  title: 'AI超级智能体 - 小高AI超级智能体应用平台',
  meta: [
    {
      name: 'description',
      content: 'AI超级智能体是小高AI超级智能体应用平台的全能助手，能解答各类专业问题，提供精准建议和解决方案'
    },
    {
      name: 'keywords',
      content: 'AI超级智能体,智能助手,专业问答,AI问答,专业建议,小高,AI智能体'
    }
  ]
})

const router = useRouter()
const messages = ref([])
const connectionStatus = ref('disconnected')
let eventSource = null

const statusText = computed(() => {
  if (connectionStatus.value === 'connecting') return '正在执行中…'
  if (connectionStatus.value === 'error') return '连接异常'
  return '就绪'
})

// 添加消息到列表
const addMessage = (content, isUser, type = '') => {
  messages.value.push({
    content,
    isUser,
    type,
    time: new Date().getTime()
  })
}

// 发送消息
const sendMessage = (messageData) => {
  const text = typeof messageData === 'string' ? messageData : (messageData?.text || '')
  if (!text.trim()) return

  addMessage(text, true, 'user-question')
  
  // 连接SSE
  if (eventSource) {
    eventSource.close()
  }
  
  // 设置连接状态
  connectionStatus.value = 'connecting'
  
  // 临时存储
  let messageBuffer = []; // 用于存储SSE消息的缓冲区
  let lastBubbleTime = Date.now(); // 上一个气泡的创建时间
  let isFirstResponse = true; // 是否是第一次响应
  
  const chineseEndPunctuation = ['。', '！', '？', '…']; // 中文句子结束标点
  const minBubbleInterval = 800; // 气泡最小间隔时间(毫秒)
  
  // 创建消息气泡的函数
  const createBubble = (content, type = 'ai-answer') => {
    if (!content.trim()) return;
    
    // 添加适当的延迟，使消息显示更自然
    const now = Date.now();
    const timeSinceLastBubble = now - lastBubbleTime;
    
    if (isFirstResponse) {
      // 第一条消息立即显示
      addMessage(content, false, type);
      isFirstResponse = false;
    } else if (timeSinceLastBubble < minBubbleInterval) {
      // 如果与上一气泡间隔太短，添加一个延迟
      setTimeout(() => {
        addMessage(content, false, type);
      }, minBubbleInterval - timeSinceLastBubble);
    } else {
      // 正常添加消息
      addMessage(content, false, type);
    }
    
    lastBubbleTime = now;
    messageBuffer = []; // 清空缓冲区
  };
  
  eventSource = chatWithManus(text)
  
  // 监听SSE消息
  eventSource.onmessage = (event) => {
    const data = event.data
    
    if (data && data !== '[DONE]') {
      messageBuffer.push(data);
      
      // 检查是否应该创建新气泡
      const combinedText = messageBuffer.join('');
      
      // 句子结束或消息长度达到阈值
      const lastChar = data.charAt(data.length - 1);
      const hasCompleteSentence = chineseEndPunctuation.includes(lastChar) || data.includes('\n\n');
      const isLongEnough = combinedText.length > 40;
      
      if (hasCompleteSentence || isLongEnough) {
        createBubble(combinedText);
      }
    }
    
    if (data === '[DONE]') {
      // 如果还有未显示的内容，创建最后一个气泡
      if (messageBuffer.length > 0) {
        const remainingContent = messageBuffer.join('');
        createBubble(remainingContent, 'ai-final');
      }
      
      // 完成后关闭连接
      connectionStatus.value = 'disconnected'
      eventSource.close()
    }
  }
  
  // 监听SSE错误
  eventSource.onerror = (error) => {
    console.error('SSE Error:', error)
    connectionStatus.value = 'error'
    eventSource.close()
    
    // 如果出错时有未显示的内容，也创建气泡
    if (messageBuffer.length > 0) {
      const remainingContent = messageBuffer.join('');
      createBubble(remainingContent, 'ai-error');
    }
  }
}

const clearChat = () => {
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
  connectionStatus.value = 'disconnected'
  messages.value = []
  addMessage('你好，我是AI超级智能体。我可以解答各类问题，提供专业建议，请问有什么可以帮助你的吗？', false)
}

const prefill = (text) => {
  // 直接发送预设更符合“高频入口”的设计定位
  sendMessage({ text })
}

// 返回主页
const goBack = () => {
  router.push('/')
}

// 页面加载时添加欢迎消息
onMounted(() => {
  // 添加欢迎消息
  addMessage('你好，我是AI超级智能体。我可以解答各类问题，提供专业建议，请问有什么可以帮助你的吗？', false)
})

// 组件销毁前关闭SSE连接
onBeforeUnmount(() => {
  if (eventSource) {
    eventSource.close()
  }
})
</script>

<style scoped>
.sidebar {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.sidebar-title {
  font-weight: 800;
  color: #2f2f33;
}

.sidebar-desc {
  font-size: 12px;
  color: var(--muted);
}

.quick-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.quick {
  text-align: left;
  border: 1px solid var(--border);
  border-radius: 14px;
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.72);
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

@media (hover: hover) {
  .quick:hover {
    transform: translateY(-1px);
    box-shadow: 0 10px 30px rgba(0, 0, 0, 0.06);
  }
}

.sidebar-actions {
  display: flex;
  gap: 8px;
  margin-top: 6px;
}

.ghost {
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 8px 12px;
  background: rgba(255, 255, 255, 0.9);
}

.ghost:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.topbar {
  padding: 12px 16px;
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.topbar-title {
  font-weight: 800;
  color: #2f2f33;
}

.topbar-sub {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--muted);
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

.chat-shell {
  flex: 1;
  min-height: 0;
  display: flex;
}
</style>
