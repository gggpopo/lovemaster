<template>
  <div class="app-shell" :class="{ 'with-sidebar': hasSidebar, 'with-right': hasRight }">
    <nav class="icon-rail" aria-label="主导航">
      <button type="button" class="rail-brand" aria-label="返回首页" @click="go('/')">恋</button>

      <div class="rail-nav">
        <button
          v-for="item in navItems"
          :key="item.key"
          type="button"
          class="rail-item"
          :class="{ active: isActive(item) }"
          :aria-current="isActive(item) ? 'page' : undefined"
          :aria-label="item.label"
          @click="go(item.to)"
        >
          <span class="rail-icon" aria-hidden="true">{{ item.icon }}</span>
          <span class="rail-label">{{ item.label }}</span>
        </button>
      </div>

      <div class="rail-tip" aria-hidden="true">Rational · Warm</div>
    </nav>

    <aside v-if="hasSidebar" class="main-sidebar" aria-label="主侧边栏">
      <slot name="sidebar" />
    </aside>

    <section class="workspace">
      <header v-if="$slots.header" class="workspace-header">
        <slot name="header" />
      </header>
      <main class="workspace-main">
        <slot />
      </main>
    </section>

    <aside v-if="hasRight" class="right-panel" aria-label="辅助区">
      <slot name="right" />
    </aside>

    <nav class="bottom-tabs" aria-label="底部导航">
      <button
        v-for="item in navItems"
        :key="item.key"
        type="button"
        class="tab-item"
        :class="{ active: isActive(item) }"
        :aria-current="isActive(item) ? 'page' : undefined"
        @click="go(item.to)"
      >
        <span class="tab-icon" aria-hidden="true">{{ item.icon }}</span>
        <span class="tab-label">{{ item.label }}</span>
      </button>
    </nav>
  </div>
</template>

<script setup>
import { computed, useSlots } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const props = defineProps({
  nav: {
    type: Array,
    default: () => [
      { key: 'home', label: '首页', icon: '◉', to: '/' },
      { key: 'love', label: '对话', icon: '❤', to: '/love-master' },
      { key: 'agent', label: '助手', icon: '✦', to: '/super-agent' }
    ]
  }
})

const router = useRouter()
const route = useRoute()
const slots = useSlots()

const navItems = computed(() => props.nav)

const go = (to) => {
  if (!to) return
  if (typeof to === 'string' && route.path === to) return
  router.push(to)
}

const isActive = (item) => {
  const target = item?.to
  if (!target) return false
  if (typeof target === 'string') {
    if (target === '/') return route.path === '/'
    return route.path.startsWith(target)
  }
  return route.name === target.name
}

const hasSidebar = computed(() => !!slots.sidebar)
const hasRight = computed(() => !!slots.right)
</script>

<style scoped>
.app-shell {
  --rail-width: 74px;
  --sidebar-width: clamp(250px, 24vw, 320px);
  --right-width: clamp(280px, 25vw, 340px);

  min-height: 100vh;
  display: grid;
  grid-template-columns: var(--rail-width) minmax(0, 1fr);
  background: rgba(253, 251, 247, 0.38);
}

.app-shell.with-sidebar {
  grid-template-columns: var(--rail-width) var(--sidebar-width) minmax(0, 1fr);
}

.app-shell.with-sidebar.with-right {
  grid-template-columns: var(--rail-width) var(--sidebar-width) minmax(0, 1fr) var(--right-width);
}

.app-shell:not(.with-sidebar).with-right {
  grid-template-columns: var(--rail-width) minmax(0, 1fr) var(--right-width);
}

.icon-rail {
  position: sticky;
  top: 0;
  height: 100vh;
  border-right: 1px solid var(--color-border);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.88), rgba(245, 239, 230, 0.8));
  backdrop-filter: blur(18px);
  padding: var(--space-2) 10px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-2);
  z-index: 30;
}

.rail-brand {
  width: 48px;
  height: 48px;
  border: 0;
  border-radius: 16px;
  font-family: var(--font-display);
  font-size: 24px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, var(--color-primary), var(--color-plum));
  box-shadow: var(--shadow-md);
}

.rail-nav {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.rail-item {
  width: 100%;
  border: 1px solid transparent;
  border-radius: 14px;
  background: transparent;
  color: var(--color-text-muted);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 10px 6px;
  transition: transform 0.22s ease, border-color 0.22s ease, color 0.22s ease, background-color 0.22s ease;
}

@media (hover: hover) {
  .rail-item:hover {
    transform: translateY(-2px);
    border-color: rgba(217, 158, 130, 0.34);
    background: rgba(217, 158, 130, 0.12);
    color: var(--color-text);
  }
}

.rail-item.active {
  border-color: rgba(217, 158, 130, 0.42);
  background: linear-gradient(180deg, rgba(217, 158, 130, 0.22), rgba(217, 158, 130, 0.12));
  color: var(--color-text);
}

.rail-icon {
  font-size: 16px;
  line-height: 1;
}

.rail-label {
  font-size: 11px;
  letter-spacing: 0.04em;
}

.rail-tip {
  margin-top: auto;
  writing-mode: vertical-rl;
  text-orientation: mixed;
  font-size: 10px;
  letter-spacing: 0.18em;
  color: var(--color-text-soft);
}

.main-sidebar {
  min-width: 0;
  border-right: 1px solid var(--color-border);
  background: rgba(255, 255, 255, 0.58);
  backdrop-filter: blur(16px);
  display: flex;
  flex-direction: column;
}

.workspace {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.workspace-header {
  position: sticky;
  top: 0;
  z-index: 20;
  border-bottom: 1px solid var(--color-border);
  background: rgba(255, 255, 255, 0.62);
  backdrop-filter: blur(16px);
}

.workspace-main {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: var(--space-2);
}

.right-panel {
  min-width: 0;
  border-left: 1px solid var(--color-border);
  background: rgba(255, 255, 255, 0.58);
  backdrop-filter: blur(16px);
  padding: var(--space-2);
  overflow: auto;
}

.bottom-tabs {
  display: none;
}

@media (max-width: 1023px) {
  .app-shell,
  .app-shell.with-sidebar,
  .app-shell.with-sidebar.with-right,
  .app-shell:not(.with-sidebar).with-right {
    grid-template-columns: minmax(0, 1fr);
  }

  .icon-rail,
  .main-sidebar,
  .right-panel {
    display: none;
  }

  .workspace-main {
    padding: 12px;
    padding-bottom: calc(72px + env(safe-area-inset-bottom));
  }

  .bottom-tabs {
    position: fixed;
    left: 0;
    right: 0;
    bottom: 0;
    z-index: 60;
    height: calc(66px + env(safe-area-inset-bottom));
    padding-bottom: env(safe-area-inset-bottom);
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    background: rgba(255, 255, 255, 0.88);
    backdrop-filter: blur(16px);
    border-top: 1px solid var(--color-border);
  }

  .tab-item {
    border: 0;
    background: transparent;
    color: var(--color-text-muted);
    display: inline-flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 3px;
  }

  .tab-item.active {
    color: var(--color-text);
  }

  .tab-icon {
    font-size: 18px;
    line-height: 1;
  }

  .tab-label {
    font-size: 12px;
    letter-spacing: 0.04em;
  }
}
</style>
