<template>
  <div class="location-card">
    <div class="card-images" v-if="photos && photos.length > 0">
      <div class="image-carousel">
        <img
          v-if="!imageBroken"
          :src="photos[currentImageIndex]"
          :alt="name"
          class="carousel-image"
          @error="handleImageError"
        />
        <div v-else class="no-image">📷 图片加载失败</div>

        <div class="image-indicators" v-if="photos.length > 1">
          <span
            v-for="(_, index) in photos"
            :key="index"
            :class="['indicator', { active: index === currentImageIndex }]"
            @click="selectImage(index)"
          />
        </div>

        <button v-if="photos.length > 1" class="nav-btn prev" @click="prevImage">‹</button>
        <button v-if="photos.length > 1" class="nav-btn next" @click="nextImage">›</button>
      </div>
    </div>
    <div class="card-images placeholder" v-else>
      <div class="no-image">📷 暂无图片</div>
    </div>

    <div class="card-info">
      <div class="card-header">
        <h3 class="card-name">{{ name }}</h3>
        <span class="card-rating" v-if="rating">⭐ {{ rating }}</span>
      </div>
      <p class="card-address">📍 {{ address || '暂无' }}</p>
      <div class="card-meta">
        <span v-if="cost" class="card-cost">💰 人均 {{ cost }}元</span>
        <span v-if="tel" class="card-tel">📞 {{ tel }}</span>
      </div>
      <div class="card-actions">
        <a v-if="mapUrl" :href="mapUrl" target="_blank" class="map-link">🗺️ 查看地图</a>
        <a v-if="tel" :href="'tel:' + tel" class="call-link">📱 拨打电话</a>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'

interface LocationCardProps {
  name: string
  address?: string
  rating?: string
  cost?: string
  tel?: string
  type?: string
  location?: string
  photos?: string[]
  mapUrl?: string
}

const props = defineProps<LocationCardProps>()

const currentImageIndex = ref(0)
const imageBroken = ref(false)

const photos = props.photos || []
const name = props.name
const address = props.address || ''
const rating = props.rating || ''
const cost = props.cost || ''
const tel = props.tel || ''
const mapUrl = props.mapUrl || ''

watch(
  () => props.photos,
  () => {
    currentImageIndex.value = 0
    imageBroken.value = false
  }
)

const prevImage = () => {
  if (!props.photos || props.photos.length === 0) return
  imageBroken.value = false
  currentImageIndex.value = currentImageIndex.value > 0
    ? currentImageIndex.value - 1
    : props.photos.length - 1
}

const nextImage = () => {
  if (!props.photos || props.photos.length === 0) return
  imageBroken.value = false
  currentImageIndex.value = currentImageIndex.value < props.photos.length - 1
    ? currentImageIndex.value + 1
    : 0
}

const selectImage = (index: number) => {
  imageBroken.value = false
  currentImageIndex.value = index
}

const handleImageError = () => {
  imageBroken.value = true
}
</script>

<style scoped>
.location-card {
  width: 320px;
  border-radius: 12px;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid var(--border);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.06);
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.location-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 26px rgba(0, 0, 0, 0.08);
}

.card-images {
  height: 200px;
  background: rgba(217, 158, 130, 0.08);
}

.image-carousel {
  position: relative;
  height: 100%;
}

.carousel-image {
  width: 100%;
  height: 200px;
  object-fit: cover;
  display: block;
}

.no-image {
  height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(0, 0, 0, 0.55);
  font-size: 14px;
}

.image-indicators {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 10px;
  display: flex;
  justify-content: center;
  gap: 6px;
}

.indicator {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.65);
  border: 1px solid rgba(0, 0, 0, 0.12);
}

.indicator.active {
  background: var(--primary);
  border-color: rgba(217, 158, 130, 0.55);
}

.nav-btn {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 28px;
  height: 28px;
  border-radius: 999px;
  border: none;
  background: rgba(255, 255, 255, 0.75);
  color: var(--primary-strong);
  font-size: 18px;
  line-height: 28px;
}

.nav-btn:hover {
  background: rgba(255, 255, 255, 0.92);
}

.nav-btn.prev {
  left: 10px;
}

.nav-btn.next {
  right: 10px;
}

.card-info {
  padding: 12px 12px 14px;
}

.card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.card-name {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: #2f2f33;
}

.card-rating {
  font-size: 13px;
  color: var(--primary-strong);
  white-space: nowrap;
}

.card-address {
  margin: 8px 0 10px;
  font-size: 13px;
  color: rgba(0, 0, 0, 0.68);
}

.card-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  font-size: 13px;
  color: rgba(0, 0, 0, 0.68);
}

.card-actions {
  margin-top: 10px;
  display: flex;
  gap: 10px;
}

.map-link,
.call-link {
  padding: 8px 10px;
  border-radius: 10px;
  font-size: 13px;
  border: 1px solid rgba(217, 158, 130, 0.22);
  background: rgba(255, 255, 255, 0.75);
  color: var(--primary-strong);
}

.map-link:hover,
.call-link:hover {
  background: rgba(255, 255, 255, 0.95);
}

@media (max-width: 480px) {
  .location-card {
    width: 100%;
  }
}
</style>
