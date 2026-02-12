<template>
  <AppShell>
    <div class="home-container">
      <header class="hero">
        <div class="brand">
          <div class="brand-badge" aria-hidden="true">AI</div>
          <div class="brand-text">
            <h1 class="title">小高 AI 超级智能体</h1>
            <p class="subtitle">简约 · 高级 · 有温度的对话体验</p>
          </div>
        </div>
      </header>

      <main class="apps">
        <button type="button" class="app-card" @click="navigateTo('/love-master')" aria-label="进入 AI 恋爱大师">
          <div class="card-header">
            <div class="app-icon" aria-hidden="true">❤</div>
            <div class="app-meta">
              <div class="app-title">AI 恋爱大师</div>
              <div class="app-desc">情感咨询 · 聊天分析 · 约会灵感</div>
            </div>
          </div>
          <div class="card-cta">
            <span>立即体验</span>
            <span class="arrow" aria-hidden="true">→</span>
          </div>
        </button>

        <button type="button" class="app-card" @click="navigateTo('/super-agent')" aria-label="进入 AI 超级智能体">
          <div class="card-header">
            <div class="app-icon" aria-hidden="true">🤖</div>
            <div class="app-meta">
              <div class="app-title">AI 超级智能体</div>
              <div class="app-desc">全能助手 · 任务拆解 · 工具调用</div>
            </div>
          </div>
          <div class="card-cta">
            <span>立即体验</span>
            <span class="arrow" aria-hidden="true">→</span>
          </div>
        </button>
      </main>

      <AppFooter />
    </div>
  </AppShell>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useHead } from '@vueuse/head'
import AppFooter from '../components/AppFooter.vue'
import AppShell from '../components/AppShell.vue'

// 设置页面标题和元数据
useHead({
  title: '小高AI超级智能体应用平台 - 首页',
  meta: [
    {
      name: 'description',
      content: '小高AI超级智能体应用平台提供AI恋爱大师和AI超级智能体服务，满足您的各种AI对话需求'
    },
    {
      name: 'keywords',
      content: 'AI智能体,AI应用,AI恋爱大师,AI助手,智能对话,小高,AI超级智能体,首页'
    }
  ]
})

const router = useRouter()

const navigateTo = (path) => {
  router.push(path)
}
</script>

<style scoped>
.home-container {
  display: flex;
  flex-direction: column;
  min-height: calc(100vh - 24px);
}

.hero {
  padding: calc(var(--space-4) + 12px) var(--space-3) var(--space-3);
  max-width: 1080px;
  margin: 0 auto;
}

.brand {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.brand-badge {
  width: 56px;
  height: 56px;
  border-radius: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--primary), rgba(217, 158, 130, 0.55));
  color: #fff;
  font-weight: 800;
  letter-spacing: 1px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.06);
}

.title {
  margin: 0;
  font-size: 28px;
  letter-spacing: 0.5px;
}

.subtitle {
  margin: 6px 0 0;
  color: var(--muted);
}

.apps {
  max-width: 1080px;
  margin: 0 auto;
  padding: 0 var(--space-3) var(--space-4);
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
}

.app-card {
  appearance: none;
  border: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(10px);
  border-radius: 18px;
  padding: var(--space-3);
  text-align: left;
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.05);
}

@media (hover: hover) {
  .app-card:hover {
    transform: translateY(-2px);
    box-shadow: 0 14px 40px rgba(0, 0, 0, 0.08);
  }
}

.card-header {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.app-icon {
  width: 48px;
  height: 48px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--surface);
  border: 1px solid var(--border);
  font-size: 22px;
}

.app-title {
  font-weight: 800;
}

.app-desc {
  margin-top: 6px;
  color: var(--muted);
  font-size: 13px;
}

.card-cta {
  margin-top: auto;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--primary-strong);
  font-weight: 700;
}

.arrow {
  transition: transform 0.2s ease;
}

@media (hover: hover) {
  .app-card:hover .arrow {
    transform: translateX(4px);
  }
}

@media (max-width: 900px) {
  .apps {
    grid-template-columns: 1fr;
  }
}
</style>
