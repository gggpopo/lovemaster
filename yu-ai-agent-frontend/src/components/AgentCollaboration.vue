<template>
  <div class="agent-collaboration">
    <div class="collab-header">
      <span class="collab-title">Agent 协作</span>
      <span v-if="isRunning" class="collab-running">
        <span class="pulse-dot"></span> 执行中
      </span>
      <span v-else-if="steps.length > 0" class="collab-done">✓ 完成</span>
    </div>

    <div class="pipeline" v-if="steps.length > 0">
      <div
        v-for="(step, i) in steps"
        :key="i"
        class="pipeline-step"
        :class="'status-' + step.status"
      >
        <!-- Connector line -->
        <div v-if="i > 0" class="connector-line" :class="step.status"></div>

        <!-- Step node -->
        <div class="step-node">
          <span class="step-icon">{{ agentIcon(step.agent) }}</span>
          <div class="step-info">
            <span class="step-name">{{ agentLabel(step.agent) }}</span>
            <span class="step-meta" v-if="step.duration">{{ step.duration }}ms</span>
          </div>
          <span class="step-status-icon">{{ statusIcon(step.status) }}</span>
        </div>

        <!-- Result preview (collapsed by default) -->
        <div
          v-if="step.result && step.status === 'completed'"
          class="step-result"
          @click="toggleResult(i)"
        >
          <span class="result-toggle">{{ expandedSteps.has(i) ? '▼' : '▶' }}</span>
          <span class="result-preview" v-if="!expandedSteps.has(i)">{{ truncate(step.result, 40) }}</span>
          <span class="result-full" v-else>{{ step.result }}</span>
        </div>
      </div>
    </div>

    <div v-else class="collab-empty">
      发送消息后查看 Agent 协作过程
    </div>
  </div>
</template>

<script setup>
import { reactive } from 'vue'

const props = defineProps({
  steps: {
    type: Array,
    default: () => []
  },
  isRunning: {
    type: Boolean,
    default: false
  }
})

const AGENT_MAP = {
  EMOTION_ANALYST: { icon: '🎭', label: '情绪分析' },
  COMPANION: { icon: '💝', label: '情感陪伴' },
  DATE_PLANNER: { icon: '📅', label: '约会策划' },
  CONFLICT_MEDIATOR: { icon: '🤝', label: '冲突调解' },
  REFLECTION: { icon: '🔍', label: '质量审查' },
  MEMORY_CURATOR: { icon: '📝', label: '记忆整理' },
  SAFETY: { icon: '🛡️', label: '安全检查' },
  NARRATIVE: { icon: '✍️', label: '回复生成' },
  INTENT_ANALYZER: { icon: '🎯', label: '意图分析' },
  AGENT_SELECTOR: { icon: '🔀', label: 'Agent选择' }
}

const STATUS_ICONS = {
  completed: '✓',
  running: '⟳',
  pending: '○',
  skipped: '—'
}

const expandedSteps = reactive(new Set())

const agentIcon = (agent) => AGENT_MAP[agent]?.icon || '⚙️'
const agentLabel = (agent) => AGENT_MAP[agent]?.label || agent
const statusIcon = (status) => STATUS_ICONS[status] || '○'

const truncate = (text, len) => {
  if (!text) return ''
  return text.length > len ? text.slice(0, len) + '...' : text
}

const toggleResult = (i) => {
  if (expandedSteps.has(i)) {
    expandedSteps.delete(i)
  } else {
    expandedSteps.add(i)
  }
}
</script>

<style scoped>
.agent-collaboration {
  padding: 10px 12px;
  font-size: 13px;
  color: var(--color-text, #333);
}

.collab-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.collab-title {
  font-weight: 600;
  font-size: 13px;
}

.collab-running {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: #2196F3;
}

.pulse-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #2196F3;
  display: inline-block;
  animation: pulse-anim 1.2s ease-in-out infinite;
}

@keyframes pulse-anim {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.4; transform: scale(0.75); }
}

.collab-done {
  font-size: 12px;
  color: #4CAF50;
  font-weight: 500;
}

.pipeline {
  display: flex;
  flex-direction: column;
}

.pipeline-step {
  position: relative;
}

/* Connector line between steps */
.connector-line {
  width: 2px;
  height: 10px;
  margin-left: 15px;
  background: #9E9E9E;
}

.connector-line.completed {
  background: #4CAF50;
}

.connector-line.running {
  background: #2196F3;
  animation: line-pulse 1.2s ease-in-out infinite;
}

.connector-line.skipped {
  background: #757575;
}

@keyframes line-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

/* Step node row */
.step-node {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 8px;
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.03);
  transition: background 0.2s;
}

.step-node:hover {
  background: rgba(0, 0, 0, 0.06);
}

.step-icon {
  font-size: 16px;
  flex-shrink: 0;
  width: 24px;
  text-align: center;
}

.status-running .step-icon {
  animation: icon-pulse 1s ease-in-out infinite;
}

@keyframes icon-pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.2); }
}

.step-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.step-name {
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.step-meta {
  font-size: 11px;
  color: var(--color-text-muted, #999);
}

.step-status-icon {
  flex-shrink: 0;
  font-size: 13px;
  width: 20px;
  text-align: center;
}

/* Status-specific text colors for the status icon */
.status-completed .step-status-icon { color: #4CAF50; }
.status-running .step-status-icon { color: #2196F3; }
.status-pending .step-status-icon { color: #9E9E9E; }
.status-skipped .step-status-icon { color: #757575; }
.status-skipped .step-name { color: #999; text-decoration: line-through; }

/* Result preview */
.step-result {
  display: flex;
  align-items: flex-start;
  gap: 4px;
  margin: 3px 0 0 32px;
  padding: 4px 8px;
  background: rgba(0, 0, 0, 0.04);
  border-radius: 4px;
  cursor: pointer;
  font-family: 'Menlo', 'Consolas', monospace;
  font-size: 11px;
  color: var(--color-text-muted, #777);
  line-height: 1.5;
  user-select: none;
}

.step-result:hover {
  background: rgba(0, 0, 0, 0.07);
}

.result-toggle {
  flex-shrink: 0;
  font-size: 10px;
  line-height: 1.6;
}

.result-preview {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.result-full {
  white-space: pre-wrap;
  word-break: break-word;
}

.collab-empty {
  font-size: 12px;
  color: var(--color-text-muted, #999);
  text-align: center;
  padding: 12px 0;
}

/* Dark theme support */
@media (prefers-color-scheme: dark) {
  .step-node {
    background: rgba(255, 255, 255, 0.06);
  }
  .step-node:hover {
    background: rgba(255, 255, 255, 0.1);
  }
  .step-result {
    background: rgba(255, 255, 255, 0.06);
  }
  .step-result:hover {
    background: rgba(255, 255, 255, 0.1);
  }
}
</style>
