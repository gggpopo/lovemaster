<template>
  <div class="emotion-diary">
    <div class="diary-header">
      <span class="diary-icon">📖</span>
      <span class="diary-title">情感日记</span>
    </div>

    <div v-if="entries.length === 0" class="diary-empty">
      还没有日记记录，多聊聊天就会自动生成哦
    </div>

    <div v-else class="diary-entries">
      <div
        v-for="entry in displayEntries"
        :key="entry.date"
        class="diary-entry"
        @click="toggleEntry(entry.date)"
      >
        <div class="entry-header">
          <span class="entry-date">{{ formatDate(entry.date) }}</span>
          <span class="entry-emotion">{{ emotionEmoji(entry.dominantEmotion) }}</span>
          <span class="entry-chats">{{ entry.chatCount }}次对话</span>
          <span class="entry-toggle">{{ expandedEntries.has(entry.date) ? '▼' : '▶' }}</span>
        </div>
        <transition name="slide">
          <div v-if="expandedEntries.has(entry.date)" class="entry-body">
            <p class="entry-summary">{{ entry.summary }}</p>
            <p v-if="entry.suggestion" class="entry-suggestion">
              💡 {{ entry.suggestion }}
            </p>
          </div>
        </transition>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, watchEffect } from 'vue'

const props = defineProps({
  entries: {
    type: Array,
    default: () => []
  }
})

const EMOTION_MAP = {
  HAPPY: '😊',
  SAD: '😢',
  ANGRY: '😠',
  ANXIOUS: '😰',
  NEUTRAL: '😐',
  LOVING: '🥰',
  CONFUSED: '😕',
  HOPEFUL: '🌟',
  FRUSTRATED: '😤',
  GRATEFUL: '🙏'
}

const DAY_NAMES = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

const expandedEntries = reactive(new Set())

const displayEntries = computed(() => {
  return [...props.entries]
    .sort((a, b) => b.date.localeCompare(a.date))
    .slice(0, 7)
})

function formatDate(dateStr) {
  const d = new Date(dateStr + 'T00:00:00')
  const month = d.getMonth() + 1
  const day = d.getDate()
  const weekday = DAY_NAMES[d.getDay()]
  return `${month}月${day}日 ${weekday}`
}

function emotionEmoji(type) {
  return EMOTION_MAP[type] || '😐'
}

function toggleEntry(date) {
  if (expandedEntries.has(date)) {
    expandedEntries.delete(date)
  } else {
    expandedEntries.add(date)
  }
}

// Auto-expand the most recent entry
watchEffect(() => {
  if (displayEntries.value.length > 0) {
    const mostRecent = displayEntries.value[0].date
    if (!expandedEntries.has(mostRecent)) {
      expandedEntries.add(mostRecent)
    }
  }
})
</script>

<style scoped>
.emotion-diary {
  max-width: 280px;
  font-family: var(--font-body, 'Noto Sans SC', sans-serif);
}

.diary-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 4px 10px;
  border-bottom: 1px solid var(--color-border, rgba(61, 61, 61, 0.14));
  margin-bottom: 10px;
}

.diary-icon {
  font-size: 18px;
  line-height: 1;
}

.diary-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text, #3d3d3d);
}

.diary-empty {
  font-size: 13px;
  color: var(--color-text-soft, rgba(61, 61, 61, 0.52));
  text-align: center;
  padding: 24px 12px;
  line-height: 1.6;
}

.diary-entries {
  max-height: 420px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-right: 2px;
}

.diary-entry {
  border: 1px solid var(--color-border, rgba(61, 61, 61, 0.14));
  border-radius: var(--radius-1, 12px);
  background: var(--color-surface-2, rgba(255, 255, 255, 0.82));
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s;
  overflow: hidden;
}

.diary-entry:hover {
  border-color: var(--color-border-strong, rgba(61, 61, 61, 0.24));
  box-shadow: 0 2px 8px rgba(61, 61, 61, 0.06);
}

.entry-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  font-size: 13px;
}

.entry-date {
  color: var(--color-text, #3d3d3d);
  font-weight: 500;
  white-space: nowrap;
}

.entry-emotion {
  font-size: 16px;
  line-height: 1;
}

.entry-chats {
  color: var(--color-text-muted, rgba(61, 61, 61, 0.66));
  font-size: 12px;
  white-space: nowrap;
}

.entry-toggle {
  margin-left: auto;
  font-size: 10px;
  color: var(--color-text-soft, rgba(61, 61, 61, 0.52));
  transition: transform 0.2s;
}

.entry-body {
  padding: 0 12px 12px;
}

.entry-summary {
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-text-muted, rgba(61, 61, 61, 0.66));
  margin: 0;
}

.entry-suggestion {
  font-size: 12px;
  line-height: 1.5;
  color: var(--color-primary-strong, #c78667);
  font-style: italic;
  margin: 8px 0 0;
  padding: 6px 8px;
  background: rgba(217, 158, 130, 0.08);
  border-radius: 6px;
}

/* Slide transition */
.slide-enter-active {
  animation: slideDown 0.2s ease;
}

.slide-leave-active {
  animation: slideDown 0.2s ease reverse;
}

@keyframes slideDown {
  from {
    opacity: 0;
    max-height: 0;
    padding-top: 0;
    padding-bottom: 0;
  }
  to {
    opacity: 1;
    max-height: 200px;
    padding-top: 0;
    padding-bottom: 12px;
  }
}

/* Dark theme */
@media (prefers-color-scheme: dark) {
  .diary-entry {
    background: rgba(255, 255, 255, 0.06);
    border-color: rgba(255, 255, 255, 0.1);
  }

  .diary-entry:hover {
    border-color: rgba(255, 255, 255, 0.18);
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
  }

  .diary-title,
  .entry-date {
    color: rgba(255, 255, 255, 0.9);
  }

  .entry-chats,
  .entry-summary {
    color: rgba(255, 255, 255, 0.6);
  }

  .diary-empty,
  .entry-toggle {
    color: rgba(255, 255, 255, 0.4);
  }

  .entry-suggestion {
    background: rgba(217, 158, 130, 0.12);
    color: #e0b49a;
  }

  .diary-header {
    border-bottom-color: rgba(255, 255, 255, 0.1);
  }
}
</style>
