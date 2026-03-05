<template>
  <div class="emotion-dashboard">
    <!-- Crisis Alert -->
    <div v-if="emotionProfile.crisisDetected" class="crisis-alert" role="alert">
      ⚠️ 检测到情绪异常，请注意关怀
    </div>

    <!-- Current Emotion -->
    <div class="emotion-current">
      <div class="emotion-label">
        <span class="emotion-emoji">{{ currentEmoji }}</span>
        <span class="emotion-text">{{ currentEmotionLabel }}</span>
      </div>
      <div class="emotion-intensity-bar">
        <div
          class="intensity-fill"
          :style="{ width: intensityPercent + '%', background: intensityColor }"
        ></div>
      </div>
      <span class="intensity-value">{{ intensityDisplay }}/10</span>
    </div>

    <!-- Trend -->
    <div class="emotion-trend">
      <span class="trend-arrow" :class="trendClass">{{ trendArrow }}</span>
      <span class="trend-label">{{ trendLabel }}</span>
    </div>

    <!-- Mini History Chart (CSS bars) -->
    <div class="emotion-history" v-if="emotionHistory.length > 0">
      <div class="history-label">情绪变化</div>
      <div class="history-bars">
        <div
          v-for="(point, i) in displayHistory"
          :key="i"
          class="history-bar-wrapper"
        >
          <div
            class="history-bar"
            :style="{ height: barHeight(point.valence) + '%', background: barColor(point.valence) }"
            :title="barTooltip(point)"
          ></div>
        </div>
      </div>
      <div class="history-axis">
        <span>负面</span>
        <span>正面</span>
      </div>
    </div>

    <!-- Narrative Summary -->
    <div class="emotion-summary" v-if="emotionProfile.narrativeSummary">
      {{ emotionProfile.narrativeSummary }}
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const EMOTION_MAP = {
  HAPPY: { emoji: '😊', label: '开心', color: '#4CAF50' },
  TOUCHED: { emoji: '🥹', label: '感动', color: '#E91E63' },
  EXPECTANT: { emoji: '🤩', label: '期待', color: '#FF9800' },
  CALM: { emoji: '😌', label: '平静', color: '#2196F3' },
  CONFUSED: { emoji: '😕', label: '困惑', color: '#9E9E9E' },
  ANXIOUS: { emoji: '😰', label: '焦虑', color: '#FF5722' },
  WRONGED: { emoji: '😢', label: '委屈', color: '#795548' },
  ANGRY: { emoji: '😤', label: '愤怒', color: '#F44336' },
  DISAPPOINTED: { emoji: '😞', label: '失落', color: '#607D8B' },
  SAD: { emoji: '😢', label: '悲伤', color: '#3F51B5' },
  FEARFUL: { emoji: '😨', label: '恐惧', color: '#9C27B0' },
  DESPERATE: { emoji: '😱', label: '绝望', color: '#B71C1C' }
}

const TREND_CONFIG = {
  IMPROVING: { arrow: '↑', label: '好转中', cls: 'improving' },
  STABLE: { arrow: '→', label: '平稳', cls: 'stable' },
  DECLINING: { arrow: '↓', label: '下降中', cls: 'declining' }
}

const props = defineProps({
  emotionProfile: {
    type: Object,
    default: () => ({
      emotions: [],
      trend: 'STABLE',
      overallValence: 0,
      crisisDetected: false,
      narrativeSummary: '',
      maxIntensity: 0
    })
  },
  emotionHistory: {
    type: Array,
    default: () => []
  }
})
const primaryEmotion = computed(() => {
  const emotions = props.emotionProfile.emotions
  if (!emotions || emotions.length === 0) return null
  return emotions[0]
})

const currentEmoji = computed(() => {
  const e = primaryEmotion.value
  if (!e) return '😶'
  return EMOTION_MAP[e.type]?.emoji || '😶'
})

const currentEmotionLabel = computed(() => {
  const e = primaryEmotion.value
  if (!e) return '未知'
  return EMOTION_MAP[e.type]?.label || e.type
})

const intensityPercent = computed(() => {
  const e = primaryEmotion.value
  if (!e) return 0
  return Math.min(Math.max((e.intensity || 0) * 10, 0), 100)
})

const intensityDisplay = computed(() => {
  const e = primaryEmotion.value
  if (!e) return '0'
  const val = e.intensity || 0
  return Number.isInteger(val) ? val : val.toFixed(1)
})

const intensityColor = computed(() => {
  const valence = props.emotionProfile.overallValence || 0
  if (valence >= 0.3) return '#4CAF50'
  if (valence >= 0) return '#FF9800'
  if (valence >= -0.5) return '#FF5722'
  return '#F44336'
})

const trendArrow = computed(() => {
  return TREND_CONFIG[props.emotionProfile.trend]?.arrow || '→'
})

const trendLabel = computed(() => {
  return TREND_CONFIG[props.emotionProfile.trend]?.label || '平稳'
})

const trendClass = computed(() => {
  return TREND_CONFIG[props.emotionProfile.trend]?.cls || 'stable'
})

const displayHistory = computed(() => {
  const history = props.emotionHistory
  if (!history || history.length === 0) return []
  return history.slice(-8)
})

function barHeight(valence) {
  // Map valence from [-1, 1] to [10, 100]
  const clamped = Math.min(Math.max(valence, -1), 1)
  return 10 + ((clamped + 1) / 2) * 90
}

function barColor(valence) {
  if (valence >= 0.5) return '#4CAF50'
  if (valence >= 0) return '#8BC34A'
  if (valence >= -0.5) return '#FF9800'
  return '#F44336'
}

function barTooltip(point) {
  const v = point.valence != null ? point.valence.toFixed(2) : '—'
  if (point.timestamp) {
    const d = new Date(point.timestamp)
    const time = d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
    return `${time} 情绪值: ${v}`
  }
  return `情绪值: ${v}`
}
</script>
<style scoped>
.emotion-dashboard {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.05);
  font-size: 13px;
  color: rgba(255, 255, 255, 0.85);
}

/* Crisis Alert */
.crisis-alert {
  padding: 8px 12px;
  border-radius: 8px;
  background: rgba(244, 67, 54, 0.15);
  border: 1px solid rgba(244, 67, 54, 0.4);
  color: #ff8a80;
  font-size: 13px;
  font-weight: 600;
  text-align: center;
  animation: pulse-alert 2s ease-in-out infinite;
}

@keyframes pulse-alert {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}

/* Current Emotion */
.emotion-current {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.emotion-label {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 70px;
}

.emotion-emoji {
  font-size: 18px;
  line-height: 1;
}

.emotion-text {
  font-size: 13px;
  font-weight: 500;
}

.emotion-intensity-bar {
  flex: 1;
  min-width: 60px;
  height: 6px;
  border-radius: 3px;
  background: rgba(255, 255, 255, 0.1);
  overflow: hidden;
}

.intensity-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 0.5s ease, background 0.5s ease;
}
.intensity-value {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.5);
  white-space: nowrap;
}

/* Trend */
.emotion-trend {
  display: flex;
  align-items: center;
  gap: 6px;
}

.trend-arrow {
  font-size: 16px;
  font-weight: 700;
  transition: color 0.3s ease;
}

.trend-arrow.improving {
  color: #4CAF50;
}

.trend-arrow.stable {
  color: #FF9800;
}

.trend-arrow.declining {
  color: #F44336;
}

.trend-label {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.6);
}

/* History Chart */
.emotion-history {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.history-label {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.5);
}

.history-bars {
  display: flex;
  align-items: flex-end;
  gap: 4px;
  height: 60px;
  padding: 2px 0;
}

.history-bar-wrapper {
  flex: 1;
  max-width: 20px;
  height: 100%;
  display: flex;
  align-items: flex-end;
}

.history-bar {
  width: 100%;
  min-height: 4px;
  border-radius: 2px 2px 0 0;
  transition: height 0.4s ease, background 0.4s ease;
}

.history-axis {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.35);
}

/* Narrative Summary */
.emotion-summary {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.55);
  line-height: 1.5;
  padding-top: 4px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}
</style>
