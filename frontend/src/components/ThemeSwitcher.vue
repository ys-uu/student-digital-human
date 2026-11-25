<template>
  <div class="switcher" :class="{ open }">
    <button class="trigger" @click="open = !open" title="切换界面风格">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">
        <path d="M12 3a9 9 0 1 0 0 18h1.5a2 2 0 0 0 0-4H13a2 2 0 0 1 0-4h4a4 4 0 0 0 4-4 6 6 0 0 0-9-6Z" />
        <circle cx="8" cy="9" r="1.1" fill="currentColor" stroke="none" />
        <circle cx="12" cy="7" r="1.1" fill="currentColor" stroke="none" />
        <circle cx="7" cy="14" r="1.1" fill="currentColor" stroke="none" />
      </svg>
      <span>风格</span>
    </button>

    <transition name="pop">
      <div class="panel" v-if="open">
        <p class="panel-title">选择界面风格</p>
        <button v-for="t in themes" :key="t.key" class="opt"
                :class="{ sel: t.key === theme }" @click="pick(t.key)">
          <span class="dots">
            <i :style="{ background: t.colors[0] }"></i>
            <i :style="{ background: t.colors[1] }"></i>
          </span>
          <span class="txt">
            <strong>{{ t.name }}</strong>
            <em>{{ t.desc }}</em>
          </span>
          <span class="check" v-if="t.key === theme">✓</span>
        </button>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { THEMES, applyTheme, currentTheme } from '../config/themes.js'

const themes = THEMES
const theme = ref(currentTheme())
const open = ref(false)

onMounted(() => applyTheme(theme.value))

const pick = (key) => {
  theme.value = key
  applyTheme(key)
  open.value = false
}
</script>

<style scoped>
.switcher { position: fixed; right: 22px; bottom: 22px; z-index: 99; font-family: inherit; }

.trigger {
  display: inline-flex; align-items: center; gap: 7px;
  padding: 10px 16px; border-radius: 999px; cursor: pointer;
  font-size: 12.5px; color: var(--text);
  background: var(--surface);
  border: 1px solid var(--border);
  box-shadow: var(--shadow);
  transition: transform .18s, box-shadow .18s;
}
.trigger:hover { transform: translateY(-2px); box-shadow: var(--shadow-hover); }
.trigger svg { width: 17px; height: 17px; color: var(--accent); }

.panel {
  position: absolute; right: 0; bottom: 54px;
  width: 232px; padding: 12px;
  border-radius: 16px;
  background: var(--surface);
  border: 1px solid var(--border);
  box-shadow: var(--shadow-hover);
}
.panel-title { margin: 4px 6px 10px; font-size: 12px; color: var(--muted); }

.opt {
  width: 100%; display: flex; align-items: center; gap: 10px;
  padding: 9px 10px; margin-bottom: 4px; border-radius: 10px;
  background: transparent; border: 1px solid transparent;
  cursor: pointer; text-align: left; font-family: inherit;
}
.opt:hover { background: var(--surface-2); }
.opt.sel { border-color: var(--accent); background: var(--accent-soft); }

.dots { display: inline-flex; }
.dots i { width: 14px; height: 14px; border-radius: 50%; border: 2px solid var(--surface); }
.dots i + i { margin-left: -5px; }

.txt { display: grid; gap: 1px; }
.txt strong { font-size: 12.5px; color: var(--text); font-weight: 600; }
.txt em { font-style: normal; font-size: 11px; color: var(--muted); }
.check { margin-left: auto; color: var(--accent); font-size: 13px; }

.pop-enter-active, .pop-leave-active { transition: opacity .16s, transform .16s; }
.pop-enter-from, .pop-leave-to { opacity: 0; transform: translateY(6px); }
</style>
