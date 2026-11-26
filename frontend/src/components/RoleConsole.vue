<template>
  <div class="console">
    <aside class="side">
      <div class="side-brand">
        <div class="logo">AI</div>
        <div class="brand-txt">
          <strong>AI 数字人助教</strong>
          <span>{{ meta.label }}工作台</span>
        </div>
      </div>

      <nav class="nav">
        <button v-for="g in groups" :key="g.key" class="nav-item"
                :class="{ active: activeKey === g.key }" @click="activeKey = g.key">
          <Icon :name="g.icon" />
          <span>{{ g.title }}</span>
          <em v-if="g.tag !== '—'" class="nav-tag">{{ g.tag }}</em>
        </button>
      </nav>

      <div class="side-user">
        <div class="avatar">{{ initial }}</div>
        <div class="who">
          <strong>{{ username }}</strong>
          <span>{{ meta.label }}</span>
        </div>
        <button class="logout" title="退出登录" @click="handleLogout">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">
            <path d="M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3" stroke-linecap="round" />
            <path d="M10 8l-4 4 4 4M6 12h9" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </button>
      </div>
    </aside>

    <main class="main">
      <header class="topbar">
        <div class="title-wrap">
          <h1>{{ current.title }}</h1>
          <p>{{ current.desc }}</p>
        </div>
        <div class="top-right">
          <button class="icon-btn" title="课程中心" @click="$router.push('/courses')"><Icon name="graduation" /></button>
          <!-- 私信入口：学生与教师可用，管理员不参与私信故不展示 -->
          <button v-if="role !== 'admin'" class="icon-btn" title="私信" @click="$router.push('/chat')">
            <Icon name="chat" />
            <em v-if="unreadCount > 0" class="chat-badge">{{ unreadCount > 99 ? '99+' : unreadCount }}</em>
          </button>
          <span class="role-pill">{{ meta.label }}</span>
          <button class="icon-btn" @click="open('通知中心')" title="通知">
            <Icon name="bell" /><i class="dot"></i>
          </button>
          <button class="icon-btn" @click="open('全局搜索')" title="搜索"><Icon name="search" /></button>
        </div>
      </header>

      <section class="stats">
        <article class="stat" v-for="s in meta.stats" :key="s.label">
          <span class="stat-label">{{ s.label }}</span>
          <strong class="stat-value">{{ s.value }}</strong>
          <em class="stat-hint">{{ s.hint }}</em>
        </article>
      </section>

      <section class="cards">
        <article v-for="f in current.features" :key="f.name" class="card" @click="open(f)">
          <div class="card-head">
            <div class="badge"><Icon :name="f.icon" /></div>
            <span class="prio" :class="f.prio === 'P0' ? 'p0' : 'p1'">{{ f.prio }}</span>
          </div>
          <h3>{{ f.name }}</h3>
          <p>{{ f.desc }}</p>
          <div class="card-foot">
            <button class="enter" @click.stop="open(f)">进入<span>→</span></button>
            <small>界面预览</small>
          </div>
        </article>
      </section>

      <footer class="foot">按《需求分析说明书》FR-01 ～ FR-10 规划 · 界面占位</footer>
    </main>

    <ThemeSwitcher />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { usePrivateSocket } from '../composables/usePrivateSocket'
import { ElMessage } from 'element-plus'
import Icon from './Icon.vue'
import ThemeSwitcher from './ThemeSwitcher.vue'
import { applyTheme, currentTheme } from '../config/themes.js'
import menus from '../config/menus.js'

const props = defineProps({ role: { type: String, required: true } })
const router = useRouter()
const userStore = useUserStore()
const { unreadCount } = usePrivateSocket()

const meta = computed(() => menus[props.role].meta)
const groups = computed(() => menus[props.role].groups)
const activeKey = ref(menus[props.role].groups[0].key)
const current = computed(() => groups.value.find((g) => g.key === activeKey.value) || groups.value[0])
const username = computed(() => userStore.userInfo?.realName || userStore.userInfo?.userNo || meta.value.label)
const initial = computed(() => (username.value || 'U').slice(0, 1).toUpperCase())

onMounted(() => applyTheme(currentTheme()))

const open = (item) => {
  if (item && typeof item === 'object' && item.path) {
    router.push(item.path)
    return
  }
  const name = typeof item === 'string' ? item : item.name
  ElMessage({ message: `「${name}」界面占位，功能待接入`, type: 'info', duration: 1600 })
}
const handleLogout = async () => { await userStore.logout(); router.push('/login') }
</script>

<style scoped>
.console {
  display: grid; grid-template-columns: 240px 1fr;
  min-height: 100vh; background: var(--bg); background-image: var(--bg-image);
  color: var(--text);
  font-family: "PingFang SC", "Microsoft YaHei", Inter, system-ui, sans-serif;
}

/* 侧栏 */
.side {
  display: flex; flex-direction: column; gap: 8px;
  padding: 22px 14px 16px;
  background: var(--sidebar);
  border-right: 1px solid var(--border);
}
.side-brand { display: flex; align-items: center; gap: 11px; padding: 2px 8px 18px; }
.logo {
  width: 38px; height: 38px; border-radius: 11px; flex: none;
  display: grid; place-items: center; font-weight: 800; font-size: 13.5px;
  color: var(--on-accent);
  background: linear-gradient(135deg, var(--accent), var(--accent-2));
}
.brand-txt { display: grid; gap: 1px; }
.brand-txt strong { font-size: 14px; color: var(--text); font-family: var(--font-head); }
.brand-txt span { font-size: 11.5px; color: var(--muted); }

.nav { display: grid; gap: 3px; overflow: auto; }
.nav-item {
  display: flex; align-items: center; gap: 10px;
  padding: 9px 11px; border-radius: 10px;
  border: 1px solid transparent; background: transparent;
  color: var(--muted); font-size: 13.2px; font-family: inherit;
  cursor: pointer; text-align: left; transition: all .16s;
}
.nav-item .ic { width: 17px; height: 17px; flex: none; }
.nav-item:hover { color: var(--text); background: var(--surface-2); }
.nav-item.active {
  color: var(--accent); background: var(--accent-soft);
  border-color: var(--border); font-weight: 600;
}
.nav-tag { margin-left: auto; font-style: normal; font-size: 10px; padding: 2px 6px; border-radius: 6px; color: var(--muted); background: var(--surface-2); }

.side-user {
  margin-top: auto; display: flex; align-items: center; gap: 9px;
  padding: 10px; border-radius: 12px;
  background: var(--surface-2); border: 1px solid var(--border);
}
.avatar {
  width: 30px; height: 30px; border-radius: 50%; flex: none;
  display: grid; place-items: center; font-size: 12.5px; font-weight: 700;
  color: var(--on-accent); background: linear-gradient(135deg, var(--accent), var(--accent-2));
}
.who { display: grid; gap: 1px; min-width: 0; }
.who strong { font-size: 12.5px; color: var(--text); }
.who span { font-size: 11px; color: var(--muted); }
.logout { margin-left: auto; width: 27px; height: 27px; display: grid; place-items: center; cursor: pointer; border: 0; background: transparent; color: var(--muted); border-radius: 7px; }
.logout svg { width: 16px; height: 16px; }
.logout:hover { color: #d14343; background: #fdeceb; }

/* 主区 */
.main { padding: 26px 32px 26px; overflow: auto; }

.topbar { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; margin-bottom: 20px; }
.title-wrap h1 { margin: 0 0 5px; font-size: 23px; color: var(--text); font-family: var(--font-head); letter-spacing: .01em; }
.title-wrap p { margin: 0; font-size: 12.8px; color: var(--muted); }
.top-right { display: flex; align-items: center; gap: 9px; }
.role-pill { font-size: 12px; padding: 6px 12px; border-radius: 999px; color: var(--accent); background: var(--accent-soft); border: 1px solid var(--border); }
.icon-btn {
  position: relative; width: 34px; height: 34px; display: grid; place-items: center;
  border-radius: 9px; cursor: pointer; color: var(--muted);
  background: var(--surface); border: 1px solid var(--border);
}
.icon-btn:hover { color: var(--accent); border-color: var(--accent); }
.icon-btn .ic { width: 17px; height: 17px; }
.icon-btn .dot { position: absolute; top: 7px; right: 8px; width: 6px; height: 6px; border-radius: 50%; background: #ef4444; }
.chat-badge {
  position: absolute; top: -5px; right: -5px;
  min-width: 16px; height: 16px; padding: 0 4px;
  border-radius: 9px; background: #ef4444; color: #fff;
  font-size: 10px; font-style: normal; line-height: 16px; text-align: center;
}

.stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 13px; margin-bottom: 22px; }
.stat { padding: 15px 17px; border-radius: var(--radius); background: var(--surface); border: 1px solid var(--border); box-shadow: var(--shadow); }
.stat-label { display: block; font-size: 12px; color: var(--muted); margin-bottom: 7px; }
.stat-value {
  display: block; font-size: 25px; font-weight: 750; letter-spacing: .01em;
  background: linear-gradient(100deg, var(--accent), var(--accent-2));
  -webkit-background-clip: text; background-clip: text; color: transparent;
}
.stat-hint { display: block; margin-top: 5px; font-size: 11.5px; color: var(--muted); font-style: normal; }

.cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(246px, 1fr)); gap: 14px; }
.card {
  padding: 18px; border-radius: var(--radius); cursor: pointer;
  background: var(--surface); border: 1px solid var(--border);
  box-shadow: var(--shadow);
  transition: transform .18s, box-shadow .18s, border-color .18s;
}
.card:hover { transform: translateY(-3px); box-shadow: var(--shadow-hover); border-color: var(--accent); }
.card-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.badge {
  width: 38px; height: 38px; border-radius: 11px; display: grid; place-items: center;
  color: var(--on-accent); background: linear-gradient(135deg, var(--accent), var(--accent-2));
}
.badge .ic { width: 20px; height: 20px; }
.prio { font-size: 10px; letter-spacing: .06em; padding: 3px 8px; border-radius: 6px; }
.prio.p0 { color: #0f7b55; background: #e8f7f0; }
.prio.p1 { color: #96601a; background: #fdf3e2; }

.card h3 { margin: 0 0 7px; font-size: 15px; color: var(--text); font-weight: 650; }
.card p { margin: 0 0 15px; font-size: 12.3px; line-height: 1.65; color: var(--muted); min-height: 40px; }

.card-foot { display: flex; align-items: center; justify-content: space-between; }
.enter {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 7px 14px; border-radius: 9px; cursor: pointer;
  font-size: 12.5px; font-family: inherit; border: 0;
  color: var(--on-accent); background: linear-gradient(100deg, var(--accent), var(--accent-2));
  transition: filter .16s;
}
.enter span { transition: transform .16s; }
.card:hover .enter span { transform: translateX(3px); }
.enter:hover { filter: brightness(1.06); }
.card-foot small { font-size: 11px; color: var(--muted); opacity: .8; }

.foot { margin-top: 24px; text-align: center; font-size: 11.5px; color: var(--muted); opacity: .8; }

@media (max-width: 1100px) { .stats { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 900px) {
  .console { grid-template-columns: 1fr; }
  .side { flex-direction: row; align-items: center; overflow-x: auto; }
  .nav { grid-auto-flow: column; }
  .side-user { margin: 0; }
  .main { padding: 18px; }
}
</style>
