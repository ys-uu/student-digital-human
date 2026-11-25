<template>
  <div class="landing">
    <div class="blob b1"></div>
    <div class="blob b2"></div>
    <div class="blob b3"></div>

    <header class="bar">
      <div class="brand">
        <div class="logo">AI</div>
        <div class="name">
          <strong>AI 数字人助教</strong>
          <span>AI Digital Human Teaching Assistant</span>
        </div>
      </div>
      <div class="actions">
        <template v-if="logged">
          <button class="ghost" @click="$router.push('/courses')">课程中心</button>
          <button class="solid" @click="$router.push(homeOf)">进入工作台</button>
        </template>
        <template v-else>
          <button class="ghost" @click="$router.push('/login')">登录</button>
          <button class="solid" @click="$router.push('/register')">注册</button>
        </template>
      </div>
    </header>

    <main class="hero">
      <h1>你好，欢迎来到「未来学习中心」</h1>
      <p class="sub">一对一私教 · 智能导学 · 全程陪伴 —— 即刻开启知识探索之旅吧~</p>

      <div class="orbit">
        <button v-for="(t, i) in topics" :key="t" class="bubble"
                :style="bubblePos[i]" @click="start(t)">
          <span class="pin"></span>{{ t }}
        </button>

        <div class="mascot">
          <svg viewBox="0 0 200 200" fill="none">
            <defs>
              <linearGradient id="mg" x1="30" y1="20" x2="180" y2="190">
                <stop stop-color="#8ec5ff" /><stop offset="1" stop-color="#b0a7ff" />
              </linearGradient>
              <linearGradient id="mv" x1="60" y1="60" x2="140" y2="120">
                <stop stop-color="#2b3d6b" /><stop offset="1" stop-color="#4a5d95" />
              </linearGradient>
            </defs>
            <ellipse cx="100" cy="178" rx="46" ry="8" fill="#c7d2f5" opacity=".55" />
            <rect x="70" y="118" width="60" height="52" rx="22" fill="#f2f6ff" stroke="#cfdcff" stroke-width="2" />
            <circle cx="100" cy="96" r="46" fill="#eef4ff" stroke="#cfdcff" stroke-width="2" />
            <rect x="64" y="72" width="72" height="50" rx="24" fill="url(#mv)" />
            <circle cx="85" cy="95" r="6" fill="#8ff0ff" />
            <circle cx="115" cy="95" r="6" fill="#8ff0ff" />
            <path d="M90 110c6 5 14 5 20 0" stroke="#8ff0ff" stroke-width="3" stroke-linecap="round" />
            <path d="M100 50v-12" stroke="#cfdcff" stroke-width="3" stroke-linecap="round" />
            <circle cx="100" cy="34" r="5" fill="url(#mg)" />
            <path d="M62 130c-12 4-18 14-16 24" stroke="#cfdcff" stroke-width="8" stroke-linecap="round" />
            <path d="M138 128c14-6 20-18 16-28" stroke="#cfdcff" stroke-width="8" stroke-linecap="round" />
            <circle cx="157" cy="94" r="8" fill="#f2f6ff" stroke="#cfdcff" stroke-width="2" />
          </svg>
        </div>
      </div>

      <div class="ask">
        <input v-model="question" placeholder="请输入你感兴趣的知识内容或问题，点击发送即刻开始探索~"
               @keyup.enter="start(question)" />
        <button class="send" @click="start(question)"><Icon name="send" /></button>
      </div>
      <small class="hint">登录后即可开始对话与课程学习</small>

      <ul class="feats">
        <li v-for="f in feats" :key="f.t">
          <span class="ic"><Icon :name="f.icon" /></span>
          <div><strong>{{ f.t }}</strong><em>{{ f.d }}</em></div>
        </li>
      </ul>
    </main>

    <ThemeSwitcher />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../../stores/user'
import Icon from '../../components/Icon.vue'
import ThemeSwitcher from '../../components/ThemeSwitcher.vue'
import { applyTheme, currentTheme } from '../../config/themes.js'

const router = useRouter()
const userStore = useUserStore()
const question = ref('')

const logged = computed(() => !!userStore.token)
const homeOf = computed(() => {
  const role = userStore.roleName
  return { admin: '/admin', teacher: '/teacher', student: '/student' }[role] || '/student'
})

const topics = ['人工智能', '共享经济', '生物催化', '量子计算', '基因编辑', '数字民主']
const bubblePos = [
  { left: '16%', top: '18%' },
  { left: '84%', top: '18%' },
  { left: '9%', top: '46%' },
  { left: '91%', top: '46%' },
  { left: '15%', top: '74%' },
  { left: '85%', top: '74%' }
]

const feats = [
  { icon: 'chat', t: 'AI 智能答疑', d: '引导式讲解，不给现成答案' },
  { icon: 'route', t: '课程智能导学', d: '学习路径与知识点拆解' },
  { icon: 'chart', t: '学情成长报告', d: '掌握度雷达图与诊断建议' }
]

onMounted(() => applyTheme(currentTheme()))

const start = (text) => {
  if (!logged.value) {
    ElMessage({ message: '登录后即可开始探索与学习', type: 'info', duration: 1800 })
    router.push('/login')
    return
  }
  router.push('/courses')
}
</script>

<style scoped>
.landing {
  position: relative; min-height: 100vh; overflow: hidden;
  display: flex; flex-direction: column;
  background: var(--bg); background-image: var(--bg-image);
  color: var(--text);
  font-family: "PingFang SC", "Microsoft YaHei", Inter, system-ui, sans-serif;
}
.blob { position: absolute; border-radius: 50%; filter: blur(90px); opacity: .5; pointer-events: none; }
.b1 { width: 520px; height: 520px; top: -180px; left: -120px; background: var(--accent-soft); }
.b2 { width: 460px; height: 460px; right: -160px; top: 10%; background: var(--accent-soft); }
.b3 { width: 420px; height: 420px; bottom: -180px; left: 30%; background: var(--accent-soft); opacity: .6; }

.bar {
  position: relative; z-index: 3;
  display: flex; align-items: center; justify-content: space-between;
  padding: 20px 40px;
}
.brand { display: flex; align-items: center; gap: 12px; }
.logo {
  width: 42px; height: 42px; border-radius: 12px; display: grid; place-items: center;
  font-weight: 800; font-size: 13.5px; color: #fff;
  background: linear-gradient(135deg, var(--accent), var(--accent-2));
  box-shadow: 0 8px 22px rgba(124, 92, 255, .28);
}
.name { display: grid; gap: 1px; }
.name strong { font-size: 15px; color: var(--text); }
.name span { font-size: 10.5px; color: var(--muted); letter-spacing: .06em; }

.actions { display: flex; gap: 10px; }
.ghost, .solid {
  font-family: inherit; cursor: pointer; border-radius: 10px; font-size: 13px;
  padding: 9px 18px; transition: all .16s;
}
.ghost { color: var(--text); background: var(--surface); border: 1px solid var(--border); }
.ghost:hover { border-color: var(--accent); color: var(--accent); }
.solid {
  color: #fff; border: 0;
  background: linear-gradient(100deg, var(--accent), var(--accent-2));
  box-shadow: 0 8px 20px rgba(124, 92, 255, .25);
}
.solid:hover { filter: brightness(1.05); transform: translateY(-1px); }

.hero {
  position: relative; z-index: 2;
  flex: 1; display: flex; flex-direction: column; align-items: center;
  padding: 6px 32px 30px; text-align: center;
}
.hero h1 { margin: 8px 0 10px; font-size: clamp(26px, 3vw, 36px); font-family: var(--font-head); color: var(--text-h); }
.sub { margin: 0 0 8px; font-size: 13.5px; color: var(--muted); }

.orbit { position: relative; width: min(1080px, 100%); height: 380px; }
.bubble {
  position: absolute; transform: translate(-50%, -50%);
  display: inline-flex; align-items: center; gap: 8px;
  padding: 10px 18px; border-radius: 999px; cursor: pointer;
  font-size: 13.5px; color: var(--text); font-family: inherit; white-space: nowrap;
  background: var(--surface); border: 1px solid var(--border);
  box-shadow: var(--shadow); transition: all .18s;
}
.bubble:hover { transform: translate(-50%, -50%) translateY(-3px); box-shadow: var(--shadow-hover); border-color: var(--accent); color: var(--accent); }
.pin { width: 12px; height: 12px; border-radius: 3px; transform: rotate(45deg); background: linear-gradient(135deg, var(--accent), var(--accent-2)); }

.mascot { position: absolute; left: 50%; top: 50%; transform: translate(-50%, -50%); }
.mascot::before { content: ''; position: absolute; inset: -40px; border-radius: 50%; background: radial-gradient(circle, rgba(124, 92, 255, .14), transparent 68%); }
.mascot svg { width: 250px; height: 250px; position: relative; animation: hover 4.5s ease-in-out infinite; }
@keyframes hover { 0%, 100% { transform: translateY(0) } 50% { transform: translateY(-9px) } }

.ask {
  display: flex; align-items: center; gap: 10px; width: min(820px, 100%);
  padding: 8px 8px 8px 20px; border-radius: 16px;
  background: var(--surface); border: 1px solid var(--border); box-shadow: var(--shadow);
}
.ask input { flex: 1; min-width: 0; border: 0; outline: 0; background: transparent; font-size: 13.5px; color: var(--text); font-family: inherit; }
.send {
  width: 40px; height: 40px; flex: none; display: grid; place-items: center; cursor: pointer;
  border: 0; border-radius: 12px; color: #fff;
  background: linear-gradient(135deg, var(--accent), var(--accent-2));
}
.send .ic { width: 18px; height: 18px; }
.hint { margin-top: 12px; font-size: 11.5px; color: var(--muted); }

.feats { list-style: none; display: flex; gap: 40px; margin: 26px 0 0; padding: 0; flex-wrap: wrap; justify-content: center; }
.feats li { display: flex; gap: 10px; align-items: flex-start; text-align: left; }
.feats .ic {
  width: 32px; height: 32px; border-radius: 10px; display: grid; place-items: center;
  color: var(--accent); background: var(--accent-soft);
}
.feats .ic .ic { width: 17px; height: 17px; }
.feats strong { display: block; font-size: 13px; color: var(--text); }
.feats em { font-style: normal; font-size: 11.5px; color: var(--muted); }

@media (max-width: 760px) {
  .bar { padding: 16px 18px; }
  .name span { display: none; }
  .orbit { height: 300px; }
  .mascot svg { width: 190px; height: 190px; }
  .bubble { padding: 8px 12px; font-size: 12px; }
  .feats { gap: 18px; }
}
</style>
