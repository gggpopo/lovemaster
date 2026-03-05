<template>
  <div class="relationship-health">
    <!-- Overall Score Circle -->
    <div class="overall-score">
      <svg viewBox="0 0 100 100" class="score-ring">
        <circle cx="50" cy="50" r="42" class="ring-bg" />
        <circle
          cx="50"
          cy="50"
          r="42"
          class="ring-fill"
          :class="ringColorClass"
          :stroke-dasharray="circumference"
          :stroke-dashoffset="dashOffset"
        />
      </svg>
      <div class="score-text">
        <span class="score-number">{{ overallScore }}</span>
        <span class="score-label">健康度</span>
      </div>
    </div>

    <!-- Stage Badge -->
    <div class="stage-badge" :class="stageClass">
      {{ stageEmoji }} {{ stage }}
    </div>

    <!-- Dimension Bars -->
    <div class="dimensions">
      <div v-for="dim in dimensions" :key="dim.key" class="dimension-row">
        <span class="dim-label">{{ dim.label }}</span>
        <div class="dim-bar-track">
          <div
            class="dim-bar-fill"
            :style="{ width: dim.value + '%', background: dim.color }"
          ></div>
        </div>
        <span class="dim-value">{{ dim.value }}</span>
      </div>
    </div>

    <!-- Health Tip -->
    <div class="health-tip" v-if="healthTip">
      💡 {{ healthTip }}
    </div>
  </div>
</template>

<script setup>
import { computed, toRefs } from 'vue'

const props = defineProps({
  metrics: {
    type: Object,
    default: () => ({
      communicationQuality: 0,
      emotionalStability: 0,
      interactionFrequency: 0,
      conflictResolution: 0,
      intimacy: 0
    })
  },
  overallScore: {
    type: Number,
    default: 0
  },
  stage: {
    type: String,
    default: '初识'
  }
})

const { metrics, overallScore, stage } = toRefs(props)

const circumference = 2 * Math.PI * 42 // ~263.89

const dashOffset = computed(() => {
  const score = Math.max(0, Math.min(100, overallScore.value))
  return circumference * (1 - score / 100)
})

const ringColorClass = computed(() => {
  const score = overallScore.value
  if (score > 70) return 'ring-green'
  if (score >= 40) return 'ring-yellow'
  return 'ring-red'
})

const stageEmojiMap = { '初识': '🌱', '暧昧': '🌸', '热恋': '🔥', '稳定': '💎' }
const stageClassMap = { '初识': 'stage-initial', '暧昧': 'stage-ambiguous', '热恋': 'stage-passionate', '稳定': 'stage-stable' }

const stageEmoji = computed(() => stageEmojiMap[stage.value] || '🌱')
const stageClass = computed(() => stageClassMap[stage.value] || 'stage-initial')

const dimensions = computed(() => {
  const m = metrics.value || {}
  return [
    { key: 'communicationQuality', label: '沟通质量', value: m.communicationQuality || 0, color: '#4CAF50', weight: '30%' },
    { key: 'emotionalStability', label: '情绪稳定', value: m.emotionalStability || 0, color: '#2196F3', weight: '25%' },
    { key: 'interactionFrequency', label: '互动频率', value: m.interactionFrequency || 0, color: '#FF9800', weight: '20%' },
    { key: 'conflictResolution', label: '冲突解决', value: m.conflictResolution || 0, color: '#9C27B0', weight: '15%' },
    { key: 'intimacy', label: '亲密度', value: m.intimacy || 0, color: '#E91E63', weight: '10%' }
  ]
})

const tipMap = {
  communicationQuality: '试试主动分享今天的感受，沟通是关系的基石',
  emotionalStability: '情绪波动较大时，试着深呼吸后再回应对方',
  interactionFrequency: '增加一些日常小互动，比如早安晚安或分享趣事',
  conflictResolution: '遇到分歧时，先倾听对方的想法再表达自己的立场',
  intimacy: '尝试创造更多共同体验，一起做些新鲜有趣的事情'
}

const healthTip = computed(() => {
  const dims = dimensions.value
  if (!dims.length) return ''
  const lowest = dims.reduce((min, d) => d.value < min.value ? d : min, dims[0])
  if (lowest.value >= 80) return '关系状态很棒，继续保持！'
  return tipMap[lowest.key] || ''
})
</script>

<style scoped>
.relationship-health {
  max-width: 280px;
  padding: 16px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid var(--color-border, rgba(0, 0, 0, 0.08));
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
}

/* Overall Score Ring */
.overall-score {
  position: relative;
  width: 100px;
  height: 100px;
}

.score-ring {
  width: 100%;
  height: 100%;
  transform: rotate(-90deg);
}

.ring-bg {
  fill: none;
  stroke: rgba(0, 0, 0, 0.06);
  stroke-width: 6;
}

.ring-fill {
  fill: none;
  stroke-width: 6;
  stroke-linecap: round;
  transition: stroke-dashoffset 0.8s ease, stroke 0.4s ease;
}

.ring-green { stroke: #4CAF50; }
.ring-yellow { stroke: #FFC107; }
.ring-red { stroke: #F44336; }

.score-text {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}

.score-number {
  font-size: 26px;
  font-weight: 700;
  line-height: 1;
  color: #2f2f33;
}

.score-label {
  font-size: 11px;
  color: var(--color-text-muted, rgba(0, 0, 0, 0.45));
  margin-top: 2px;
}

/* Stage Badge */
.stage-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 14px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 600;
  color: #fff;
}

.stage-initial {
  background: linear-gradient(135deg, #81C784, #4CAF50);
}

.stage-ambiguous {
  background: linear-gradient(135deg, #F48FB1, #E91E63);
}

.stage-passionate {
  background: linear-gradient(135deg, #FFB74D, #F44336);
}

.stage-stable {
  background: linear-gradient(135deg, #64B5F6, #1565C0);
}

/* Dimension Bars */
.dimensions {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.dimension-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.dim-label {
  flex-shrink: 0;
  width: 56px;
  font-size: 12px;
  color: var(--color-text-muted, rgba(0, 0, 0, 0.55));
  text-align: right;
}

.dim-bar-track {
  flex: 1;
  height: 4px;
  border-radius: 2px;
  background: rgba(0, 0, 0, 0.06);
  overflow: hidden;
}

.dim-bar-fill {
  height: 100%;
  border-radius: 2px;
  transition: width 0.6s ease;
}

.dim-value {
  flex-shrink: 0;
  width: 28px;
  font-size: 12px;
  font-weight: 600;
  color: #2f2f33;
  text-align: right;
}

/* Health Tip */
.health-tip {
  width: 100%;
  padding: 8px 10px;
  border-radius: 8px;
  background: rgba(255, 248, 225, 0.8);
  border: 1px solid rgba(255, 193, 7, 0.2);
  font-size: 12px;
  line-height: 1.5;
  color: rgba(0, 0, 0, 0.7);
}

/* Dark theme */
@media (prefers-color-scheme: dark) {
  .relationship-health {
    background: rgba(40, 40, 45, 0.9);
    border-color: rgba(255, 255, 255, 0.1);
  }

  .score-number {
    color: #e0e0e0;
  }

  .score-label {
    color: rgba(255, 255, 255, 0.5);
  }

  .dim-label {
    color: rgba(255, 255, 255, 0.55);
  }

  .dim-bar-track {
    background: rgba(255, 255, 255, 0.1);
  }

  .dim-value {
    color: #e0e0e0;
  }

  .health-tip {
    background: rgba(255, 248, 225, 0.1);
    border-color: rgba(255, 193, 7, 0.15);
    color: rgba(255, 255, 255, 0.75);
  }
}
</style>
