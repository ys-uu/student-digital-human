<template>
  <div class="metric">
    <div class="metric-label">{{ label }}</div>
    <div class="metric-value">
      <span class="number">{{ display }}</span>
      <span v-if="unit" class="unit">{{ unit }}</span>
    </div>
  </div>
</template>

<script setup>
import { onUnmounted, ref, watch } from 'vue'

const props = defineProps({
  label: { type: String, required: true },
  value: { type: Number, default: 0 },
  unit: { type: String, default: '' },
  decimals: { type: Number, default: 0 }
})

const display = ref(format(props.value))
let current = props.value
let frame = null

function format(value) {
  const fixed = Number(value || 0).toFixed(props.decimals)
  return props.decimals > 0 ? fixed : Number(fixed).toLocaleString('zh-CN')
}

function animate(target) {
  const from = current
  const to = Number(target || 0)
  if (from === to) {
    display.value = format(to)
    return
  }
  const startAt = performance.now()
  const duration = 600
  const step = (now) => {
    const progress = Math.min((now - startAt) / duration, 1)
    const eased = 1 - Math.pow(1 - progress, 3)
    display.value = format(from + (to - from) * eased)
    if (progress < 1) {
      frame = requestAnimationFrame(step)
    } else {
      current = to
    }
  }
  if (frame) cancelAnimationFrame(frame)
  frame = requestAnimationFrame(step)
}

watch(() => props.value, (value) => animate(value))

onUnmounted(() => {
  if (frame) cancelAnimationFrame(frame)
})
</script>

<style scoped>
.metric {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 6px;
  padding: 14px 18px;
  background: linear-gradient(180deg, rgba(56, 189, 248, 0.08), rgba(17, 28, 46, 0.9));
  border: 1px solid rgba(56, 189, 248, 0.18);
  border-radius: 10px;
  min-width: 0;
}

.metric-label {
  font-size: 13px;
  color: #94a3b8;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.metric-value {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.number {
  font-size: 26px;
  font-weight: 600;
  color: #e2e8f0;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.5px;
}

.unit {
  font-size: 12px;
  color: #64748b;
}
</style>
