<template>
  <div ref="container" class="echart" :style="{ height: height }"></div>
</template>

<script setup>
import { onMounted, onUnmounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  option: { type: Object, required: true },
  height: { type: String, default: '100%' }
})

const container = ref(null)
let chart = null
let observer = null
let frame = null

const render = () => {
  if (!chart) return
  chart.setOption(props.option, { notMerge: false, lazyUpdate: true })
}

onMounted(() => {
  chart = echarts.init(container.value, null, { renderer: 'canvas' })
  render()
  observer = new ResizeObserver(() => {
    if (frame) cancelAnimationFrame(frame)
    frame = requestAnimationFrame(() => chart && chart.resize())
  })
  observer.observe(container.value)
})

watch(() => props.option, render, { deep: true })

onUnmounted(() => {
  if (frame) cancelAnimationFrame(frame)
  observer?.disconnect()
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
.echart {
  width: 100%;
  min-height: 0;
}
</style>
