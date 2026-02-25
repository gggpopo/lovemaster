<template>
  <div class="structured-response">
    <p v-if="response.summary" class="structured-summary">{{ response.summary }}</p>

    <template v-for="(block, blockIndex) in response.blocks" :key="block.id || `${block.type}_${blockIndex}`">
      <section v-if="block.type === 'text'" class="structured-block">
        <h4 v-if="block.title" class="block-title">{{ block.title }}</h4>
        <template v-for="(seg, segIndex) in getTextSegments(block.data?.text)" :key="`text_seg_${segIndex}`">
          <p v-if="seg.type === 'text'" class="block-text">{{ seg.content }}</p>
          <div v-else-if="seg.type === 'image'" class="block-image">
            <a :href="seg.content" target="_blank" rel="noopener noreferrer">
              <img :src="seg.content" alt="地点实景图" class="block-image-img" loading="lazy" />
            </a>
          </div>
          <LocationCard v-else-if="seg.type === 'location_card'" v-bind="safeParseLocationCard(seg.content)" />
        </template>
      </section>

      <section v-else-if="block.type === 'location_cards'" class="structured-block">
        <h4 v-if="block.title" class="block-title">{{ block.title }}</h4>
        <div class="location-cards">
          <LocationCard
            v-for="(item, index) in normalizeLocationItems(block.data?.items)"
            :key="`loc_${index}_${item.name || 'item'}`"
            v-bind="item"
          />
        </div>
      </section>

      <section v-else-if="block.type === 'action_plan'" class="structured-block">
        <h4 v-if="block.title" class="block-title">{{ block.title }}</h4>
        <ol class="step-list">
          <li v-for="(step, index) in normalizeSteps(block.data?.steps)" :key="`step_${index}`" class="step-item">
            <strong>{{ step.title || `步骤 ${index + 1}` }}</strong>
            <p>{{ step.action || '' }}</p>
          </li>
        </ol>
      </section>

      <section v-else-if="block.type === 'message_options'" class="structured-block">
        <h4 v-if="block.title" class="block-title">{{ block.title }}</h4>
        <div class="message-options">
          <article
            v-for="(option, index) in normalizeMessageOptions(block.data?.options)"
            :key="`opt_${index}`"
            class="message-option"
          >
            <p class="option-label">{{ option.label || `选项 ${index + 1}` }}</p>
            <p class="option-text">{{ option.text || '' }}</p>
            <button type="button" class="copy-btn" @click="copyOption(option.text)">复制</button>
          </article>
        </div>
      </section>

      <section v-else-if="block.type === 'risk_alert'" class="structured-block risk-block">
        <h4 v-if="block.title" class="block-title">{{ block.title }}</h4>
        <p class="risk-message">{{ block.data?.message || '' }}</p>
      </section>
    </template>

    <section v-if="response.followUp?.question" class="structured-block follow-up-block">
      <h4 class="block-title">下一步建议</h4>
      <p class="follow-up-question">{{ response.followUp.question }}</p>
      <div v-if="Array.isArray(response.followUp.choices) && response.followUp.choices.length" class="choice-list">
        <span v-for="(choice, index) in response.followUp.choices" :key="`choice_${index}`" class="choice-chip">
          {{ choice }}
        </span>
      </div>
    </section>
  </div>
</template>

<script setup>
import LocationCard from './LocationCard.vue'
import { parseMessage } from '../utils/messageParser'

const props = defineProps({
  response: {
    type: Object,
    required: true
  }
})

const normalizeLocationItems = (items) => {
  if (!Array.isArray(items)) return []
  return items
    .filter((item) => item && typeof item === 'object')
    .map((item) => {
      const photos = Array.isArray(item.images)
        ? item.images
        : (Array.isArray(item.photos) ? item.photos : [])
      return {
        ...item,
        photos
      }
    })
}

const getTextSegments = (text) => {
  return parseMessage(String(text || ''))
}

const safeParseLocationCard = (jsonText) => {
  try {
    return JSON.parse(jsonText)
  } catch (error) {
    return {
      name: '地点信息解析失败',
      address: '',
      rating: '',
      cost: '',
      tel: '',
      photos: [],
      mapUrl: ''
    }
  }
}

const normalizeSteps = (steps) => {
  if (!Array.isArray(steps)) return []
  return steps
    .filter((item) => item && typeof item === 'object')
    .map((item) => ({
      title: String(item.title || ''),
      action: String(item.action || '')
    }))
}

const normalizeMessageOptions = (options) => {
  if (!Array.isArray(options)) return []
  return options
    .filter((item) => item && typeof item === 'object')
    .map((item) => ({
      label: String(item.label || ''),
      text: String(item.text || '')
    }))
}

const copyOption = async (text) => {
  const value = String(text || '')
  if (!value) return

  try {
    await navigator.clipboard.writeText(value)
  } catch (error) {
    // ignore
  }
}
</script>

<style scoped>
.structured-response {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.structured-summary {
  margin: 0;
  font-size: 14px;
  color: rgba(0, 0, 0, 0.75);
  line-height: 1.6;
}

.structured-block {
  border: 1px solid var(--color-border);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.72);
  padding: 10px;
}

.block-title {
  margin: 0 0 8px;
  font-size: 14px;
  font-weight: 700;
}

.block-text {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.6;
}

.block-image {
  margin-top: 8px;
}

.block-image-img {
  width: min(360px, 100%);
  max-height: 240px;
  display: block;
  border-radius: 10px;
  border: 1px solid var(--color-border);
  object-fit: cover;
}

.location-cards {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.step-list {
  margin: 0;
  padding-left: 20px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.step-item p {
  margin: 3px 0 0;
  line-height: 1.55;
}

.message-options {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.message-option {
  border: 1px solid var(--color-border);
  border-radius: 10px;
  padding: 8px;
  background: rgba(245, 239, 230, 0.55);
}

.option-label {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-muted);
}

.option-text {
  margin: 4px 0 8px;
  line-height: 1.55;
}

.copy-btn {
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: #fff;
  font-size: 12px;
  padding: 4px 10px;
}

.risk-block {
  border-color: rgba(203, 111, 111, 0.35);
  background: rgba(255, 240, 240, 0.78);
}

.risk-message {
  margin: 0;
  color: #7f2020;
  line-height: 1.6;
}

.follow-up-question {
  margin: 0;
}

.choice-list {
  margin-top: 8px;
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.choice-chip {
  border: 1px solid var(--color-border);
  border-radius: 999px;
  padding: 4px 10px;
  font-size: 12px;
  background: rgba(255, 255, 255, 0.92);
}
</style>
