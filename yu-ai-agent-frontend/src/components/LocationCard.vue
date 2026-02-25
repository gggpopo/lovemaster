<template>
  <div class="location-card">
    <div class="card-images" v-if="visiblePhotos.length > 0">
      <div class="image-carousel">
        <img
          :src="visiblePhotos[currentImageIndex]"
          :alt="name"
          class="carousel-image"
          @error="handleImageError"
        />

        <div class="image-indicators" v-if="visiblePhotos.length > 1">
          <span
            v-for="(_, index) in visiblePhotos"
            :key="index"
            :class="['indicator', { active: index === currentImageIndex }]"
            @click="selectImage(index)"
          />
        </div>

        <button
          v-if="visiblePhotos.length > 1"
          class="nav-btn prev"
          @click="prevImage"
          aria-label="上一张图片"
        >
          <span class="nav-icon" aria-hidden="true">‹</span>
        </button>
        <button
          v-if="visiblePhotos.length > 1"
          class="nav-btn next"
          @click="nextImage"
          aria-label="下一张图片"
        >
          <span class="nav-icon" aria-hidden="true">›</span>
        </button>
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
import { computed, ref, watch } from 'vue'

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
const hiddenPhotoUrls = ref(new Set<string>())

const visiblePhotos = computed(() => {
  const source = Array.isArray(props.photos) ? props.photos : []
  return source.filter((url) => {
    return typeof url === 'string' && url && !hiddenPhotoUrls.value.has(url)
  })
})

const name = computed(() => props.name || '')
const address = computed(() => props.address || '')
const rating = computed(() => props.rating || '')
const cost = computed(() => props.cost || '')
const tel = computed(() => props.tel || '')
const mapUrl = computed(() => props.mapUrl || '')

watch(
  () => props.photos,
  () => {
    currentImageIndex.value = 0
    hiddenPhotoUrls.value = new Set()
  }
)

watch(visiblePhotos, (nextPhotos) => {
  if (!nextPhotos.length) {
    currentImageIndex.value = 0
    return
  }
  if (currentImageIndex.value >= nextPhotos.length) {
    currentImageIndex.value = 0
  }
})

const prevImage = () => {
  if (visiblePhotos.value.length === 0) return
  currentImageIndex.value = currentImageIndex.value > 0
    ? currentImageIndex.value - 1
    : visiblePhotos.value.length - 1
}

const nextImage = () => {
  if (visiblePhotos.value.length === 0) return
  currentImageIndex.value = currentImageIndex.value < visiblePhotos.value.length - 1
    ? currentImageIndex.value + 1
    : 0
}

const selectImage = (index: number) => {
  currentImageIndex.value = index
}

const handleImageError = () => {
  const failedUrl = visiblePhotos.value[currentImageIndex.value]
  if (!failedUrl) return

  const nextHiddenUrls = new Set(hiddenPhotoUrls.value)
  nextHiddenUrls.add(failedUrl)
  hiddenPhotoUrls.value = nextHiddenUrls
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
  width: 36px;
  height: 36px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.65);
  background: rgba(255, 255, 255, 0.78);
  color: var(--primary-strong);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  cursor: pointer;
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.16);
  transition: transform 0.15s ease, background 0.15s ease, box-shadow 0.15s ease;
}

.nav-btn:hover {
  background: rgba(255, 255, 255, 0.95);
  transform: translateY(-50%) scale(1.06);
  box-shadow: 0 8px 18px rgba(0, 0, 0, 0.2);
}

.nav-btn:active {
  transform: translateY(-50%) scale(0.98);
}

.nav-icon {
  font-size: 30px;
  line-height: 1;
  font-weight: 600;
}

.nav-btn.prev .nav-icon {
  transform: translateX(-1px);
}

.nav-btn.next .nav-icon {
  transform: translateX(1px);
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
