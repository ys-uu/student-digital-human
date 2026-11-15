<template>
  <div class="feed">
    <div v-if="!events.length" class="empty">暂无实时动态</div>
    <transition-group v-else name="slide" tag="ul" class="feed-list">
      <li v-for="(item, index) in events" :key="`${item.type}-${item.time}-${index}`">
        <span class="dot" :class="item.type"></span>
        <span class="name">{{ item.userName }}</span>
        <span class="content">{{ item.content }}</span>
        <span class="time">{{ item.time }}</span>
      </li>
    </transition-group>
  </div>
</template>

<script setup>
defineProps({
  events: { type: Array, default: () => [] }
})
</script>

<style scoped>
.feed {
  height: 100%;
  overflow: hidden;
}

.feed-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.feed-list li {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  line-height: 20px;
  color: #cbd5e1;
  white-space: nowrap;
  overflow: hidden;
}

.dot {
  flex: none;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #38bdf8;
}

.dot.session {
  background: #34d399;
}

.dot.homework {
  background: #fbbf24;
}

.name {
  flex: none;
  color: #e2e8f0;
  font-weight: 500;
}

.content {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  color: #94a3b8;
}

.time {
  flex: none;
  font-size: 12px;
  color: #64748b;
  font-variant-numeric: tabular-nums;
}

.empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  font-size: 13px;
  color: #64748b;
}

.slide-enter-active {
  transition: all 0.3s ease;
}

.slide-enter-from {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
