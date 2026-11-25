<template>
  <div class="home">
    <aside class="rail">
      <div class="rail-logo">AI</div>
      <button v-for="n in rails" :key="n.key" class="rail-item"
              :class="{ active: rail === n.key }" @click="rail = n.key">
        <Icon :name="n.icon" />
        <span>{{ n.name }}</span>
      </button>
      <div class="rail-foot">
        <button class="rail-item" @click="soon('帮助中心')"><Icon name="help" /><span>帮助</span></button>
        <button class="rail-item" @click="soon('设置')"><Icon name="settings" /><span>设置</span></button>
      </div>
    </aside>

    <main class="body">
      <header class="head">
        <div>
          <h1>AI 智慧课程中心</h1>
          <p class="sub">课程学习 · 智能答疑 · 数字人陪伴</p>
        </div>
        <div class="auth">
          <button class="ghost" @click="$router.push('/')">返回首页</button>
          <template v-if="logged">
            <button class="solid" @click="go(homeOf)">进入工作台</button>
          </template>
          <template v-else>
            <button class="ghost" @click="go('/login')">登录</button>
            <button class="solid" @click="go('/register')">注册</button>
          </template>
        </div>
      </header>

      <!-- 我的课程 -->
      <section v-if="rail === 'course'" class="course-wrap">
        <div class="tabs">
          <button class="tab active">我学的课</button>
          <button class="tab" @click="soon('全部课程')">全部课程</button>
          <button class="tab" @click="soon('已归档')">已归档</button>
        </div>

        <div class="toolbar">
          <div class="select" @click="soon('学期筛选')">
            <span>全部学期</span><Icon name="down" />
          </div>
          <div class="search">
            <Icon name="search" />
            <input v-model="keyword" placeholder="输入课程名称、老师姓名等" />
          </div>
          <button class="add" @click="soon('加入课程')"><Icon name="plus" />加入课程</button>
          <button class="more" @click="soon('更多')">更多</button>
        </div>

        <div class="grid">
          <article v-for="c in filtered" :key="c.title + c.teacher" class="course" @click="soon(c.title)">
            <div class="cover" :style="{ background: c.cover }">
              <span class="blob b1"></span><span class="blob b2"></span>
              <span class="sem">{{ c.sem }}</span>
            </div>
            <div class="info">
              <h3 :title="c.title">{{ c.title }}</h3>
              <div class="meta">
                <span>{{ c.teacher }}</span>
                <em>AI智课</em>
              </div>
            </div>
          </article>
        </div>

        <p class="tip">共 {{ filtered.length }} 门课程 · 点击卡片进入课程（界面占位）</p>
      </section>

      <!-- 探索学习 -->
      <section v-else-if="rail === 'explore'" class="explore">
        <div class="explore-head">
          <h2>探索学习</h2>
          <div class="seg"><span class="on">探索</span><span>发现</span></div>
        </div>

        <div class="orbit">
          <button v-for="(t, i) in topics" :key="t" class="bubble"
                  :style="bubblePos[i]" @click="soon(t)">
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

        <div class="welcome">
          <h3>👋 你好，欢迎来到「未来学习中心」</h3>
          <p>即刻开启知识探索之旅吧~</p>
          <div class="ask">
            <input v-model="question" placeholder="请输入你感兴趣的知识内容或问题，点击发送即刻开始探索~"
                   @keyup.enter="send" />
            <button class="send-btn" @click="send"><Icon name="send" /></button>
          </div>
          <small>根据以上探索主题为你推荐</small>
        </div>
      </section>

      <!-- 其他占位页 -->
      <section v-else class="placeholder">
        <Icon :name="railMeta.icon" />
        <h2>{{ railMeta.name }}</h2>
        <p>{{ railMeta.desc }}</p>
        <button class="solid small" @click="soon(railMeta.name)">进入{{ railMeta.name }}</button>
      </section>
    </main>

    <ThemeSwitcher />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useUserStore } from '../../stores/user'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import Icon from '../../components/Icon.vue'
import ThemeSwitcher from '../../components/ThemeSwitcher.vue'
import { applyTheme, currentTheme } from '../../config/themes.js'

const router = useRouter()
const userStore = useUserStore()
const logged = computed(() => !!userStore.token)
const homeOf = computed(() => {
  const role = userStore.roleName
  return { admin: '/admin', teacher: '/teacher', student: '/student' }[role] || '/student'
})
const keyword = ref('')
const question = ref('')
const rail = ref('course')

const rails = [
  { key: 'course', name: '我的课程', icon: 'graduation' },
  { key: 'todo', name: '待办任务', icon: 'task', desc: '作业、测验与学习提醒的统一入口' },
  { key: 'explore', name: '探索学习', icon: 'compass' },
  { key: 'chat', name: '对话', icon: 'chat', desc: '与 AI 数字人实时对话，随时提问' },
  { key: 'custom', name: '自定义学习', icon: 'pen', desc: '自定义学习主题与路径' }
]

const railMeta = computed(() => rails.find((r) => r.key === rail.value) || rails[0])

const topics = ['人工智能', '共享经济', '生物催化', '量子计算', '基因编辑', '数字民主']
const bubblePos = [
  { left: '23%', top: '12%' },
  { left: '67%', top: '12%' },
  { left: '16%', top: '36%' },
  { left: '71%', top: '36%' },
  { left: '9%', top: '62%' },
  { left: '74%', top: '62%' }
]

const covers = [
  'linear-gradient(135deg,#c7ddff,#9ec5ff)',
  'linear-gradient(135deg,#d7e6ff,#a9c8ff)',
  'linear-gradient(135deg,#c9f2ff,#a5e6f7)',
  'linear-gradient(135deg,#d8f5ee,#a9e8d8)',
  'linear-gradient(135deg,#e6f0ff,#c2d8ff)',
  'linear-gradient(135deg,#dde8ff,#b6cdfa)',
  'linear-gradient(135deg,#cfe3ff,#a9c9ff)',
  'linear-gradient(135deg,#d9eaff,#b3d0ff)',
  'linear-gradient(135deg,#e3ecff,#bdd4ff)',
  'linear-gradient(135deg,#ffe6dd,#ffc9b5)',
  'linear-gradient(135deg,#e6e1ff,#c7bdff)',
  'linear-gradient(135deg,#d9f3ff,#b0e4f7)'
]

const courses = [
  { title: 'Android移动应用开发-26', teacher: '李贵洋', sem: '2026年秋冬学期' },
  { title: '软件工程', teacher: '吴贞东', sem: '2026年秋冬学期' },
  { title: '人工智能原理与实践', teacher: '张红杰', sem: '2026年秋冬学期' },
  { title: '机器学习', teacher: '周维曦、李静文', sem: '2026年秋冬学期' },
  { title: '形势与政策5', teacher: '张永洪、张立', sem: '2026年秋冬学期' },
  { title: '大学生职业发展与就业指导（2）', teacher: '马晓红、王月月', sem: '2026年秋冬学期' },
  { title: '综合实践训练4（软件工程课程设计）', teacher: '吴贞东', sem: '2026年秋冬学期' },
  { title: '软件体系结构', teacher: '李颛', sem: '2026年秋冬学期' },
  { title: 'Android移动应用开发', teacher: '李贵洋、陈睿', sem: '2026年秋冬学期' },
  { title: '高级数据库技术', teacher: '俞晓', sem: '2026年秋冬学期' },
  { title: '微信小程序开发', teacher: '李颛', sem: '2026年秋冬学期' },
  { title: '软件工程伦理', teacher: '穆楠', sem: '2026年秋冬学期' },
  { title: 'Java高级开发技术', teacher: '秦雯、廖雪花', sem: '2026年春夏学期' },
  { title: '体育课程与教学论公体班', teacher: '李野', sem: '2026年春夏学期' },
  { title: '离散数学', teacher: '李均利、刘芳', sem: '2026年春夏学期' },
  { title: '工程认知及创新素质教育2', teacher: '夏羽、吴选', sem: '2026年春夏学期' },
  { title: '“十个一”教育养成活动（2）', teacher: '王颖异、冯越', sem: '2026年春夏学期' },
  { title: '劳动教育', teacher: '张江威、冯越', sem: '2026年春夏学期' }
].map((c, i) => ({ ...c, cover: covers[i % covers.length] }))

const filtered = computed(() => {
  const k = keyword.value.trim()
  return k ? courses.filter((c) => c.title.includes(k) || c.teacher.includes(k)) : courses
})

onMounted(() => {
  applyTheme(currentTheme())
  if (location.hash === '#explore') rail.value = 'explore'
})

const go = (p) => router.push(p)
const soon = (name) => ElMessage({ message: `「${name}」界面占位，功能待接入`, type: 'info', duration: 1500 })
const send = () => {
  if (!question.value.trim()) return soon('开始探索')
  soon('探索：' + question.value.trim().slice(0, 14))
  question.value = ''
}
</script>

<style scoped>
.home {
  display: grid; grid-template-columns: 88px 1fr;
  min-height: 100vh;
  background: var(--bg); background-image: var(--bg-image);
  color: var(--text);
  font-family: "PingFang SC", "Microsoft YaHei", Inter, system-ui, sans-serif;
}

/* 左侧图标栏 */
.rail {
  display: flex; flex-direction: column; align-items: center; gap: 6px;
  padding: 18px 8px; background: rgba(255, 255, 255, .66);
  border-right: 1px solid var(--border); backdrop-filter: blur(14px);
}
.rail-logo {
  width: 40px; height: 40px; border-radius: 12px; display: grid; place-items: center;
  font-weight: 800; font-size: 13px; color: #fff; margin-bottom: 12px;
  background: linear-gradient(135deg, var(--accent), var(--accent-2));
}
.rail-item {
  width: 68px; padding: 9px 4px 7px; border-radius: 12px; cursor: pointer;
  display: grid; justify-items: center; gap: 4px;
  border: 1px solid transparent; background: transparent;
  color: var(--muted); font-size: 10.5px; font-family: inherit;
}
.rail-item .ic { width: 20px; height: 20px; }
.rail-item:hover { background: var(--surface-2); color: var(--text); }
.rail-item.active { color: var(--accent); background: var(--surface); border-color: var(--border); box-shadow: var(--shadow); }
.rail-foot { margin-top: auto; display: grid; gap: 6px; }

/* 主区 */
.body { padding: 24px 32px 30px; overflow: auto; }
.head { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; margin-bottom: 20px; }
.head h1 { margin: 0 0 4px; font-size: 27px; font-family: var(--font-head); letter-spacing: .01em; }
.sub { margin: 0; font-size: 12.5px; color: var(--muted); }

.auth { display: flex; gap: 10px; }
.ghost, .solid, .add, .more {
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
.solid.small { padding: 10px 22px; }

.tabs { display: flex; gap: 8px; margin-bottom: 16px; }
.tab {
  padding: 8px 18px; border-radius: 999px; cursor: pointer; font-family: inherit;
  font-size: 13.5px; color: var(--muted); background: transparent; border: 1px solid transparent;
}
.tab.active { color: #fff; background: linear-gradient(100deg, var(--accent), var(--accent-2)); box-shadow: 0 8px 18px rgba(124,92,255,.22); }
.tab:not(.active):hover { background: var(--surface); border-color: var(--border); }

.toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 18px; flex-wrap: wrap; }
.select {
  display: inline-flex; align-items: center; gap: 10px; padding: 9px 14px;
  border-radius: 10px; background: var(--surface); border: 1px solid var(--border);
  font-size: 13px; color: var(--text); cursor: pointer;
}
.select .ic { width: 14px; height: 14px; color: var(--muted); }
.search {
  flex: 1; min-width: 220px; max-width: 420px;
  display: flex; align-items: center; gap: 9px; padding: 0 14px; height: 40px;
  border-radius: 10px; background: var(--surface); border: 1px solid var(--border);
}
.search .ic { width: 16px; height: 16px; color: var(--muted); }
.search input { flex: 1; border: 0; outline: 0; background: transparent; font-size: 13px; color: var(--text); font-family: inherit; }
.add {
  display: inline-flex; align-items: center; gap: 6px; border: 0; color: #fff;
  background: linear-gradient(100deg, var(--accent), var(--accent-2));
  box-shadow: 0 8px 20px rgba(124, 92, 255, .25);
}
.add .ic { width: 15px; height: 15px; }
.more { color: var(--text); background: var(--surface); border: 1px solid var(--border); }

.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(206px, 1fr)); gap: 16px; }
.course {
  border-radius: var(--radius); overflow: hidden; cursor: pointer;
  background: var(--surface); border: 1px solid var(--border);
  box-shadow: var(--shadow); transition: transform .18s, box-shadow .18s, border-color .18s;
}
.course:hover { transform: translateY(-4px); box-shadow: var(--shadow-hover); border-color: var(--accent); }
.cover { position: relative; height: 104px; overflow: hidden; }
.blob { position: absolute; border-radius: 50%; background: rgba(255, 255, 255, .55); filter: blur(2px); }
.b1 { width: 90px; height: 90px; right: -18px; top: -26px; }
.b2 { width: 46px; height: 46px; left: 26px; bottom: -14px; opacity: .6; }
.sem {
  position: absolute; left: 0; bottom: 0; right: 0; padding: 5px 12px;
  font-size: 11px; color: #fff; letter-spacing: .02em;
  background: rgba(30, 44, 80, .35); backdrop-filter: blur(4px);
}
.info { padding: 12px 14px 14px; }
.info h3 {
  margin: 0 0 8px; font-size: 13.8px; line-height: 1.45; color: var(--text); font-weight: 600;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
}
.meta { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.meta span { font-size: 12px; color: var(--muted); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.meta em { font-style: normal; font-size: 10.5px; color: var(--accent); background: var(--accent-soft); padding: 2px 7px; border-radius: 5px; white-space: nowrap; }

.tip { margin-top: 20px; text-align: center; font-size: 11.5px; color: var(--muted); }

/* 探索学习 */
.explore { position: relative; min-height: calc(100vh - 130px); display: flex; flex-direction: column; }
.explore-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; }
.explore-head h2 { margin: 0; font-size: 22px; font-family: var(--font-head); }
.seg { display: inline-flex; padding: 4px; gap: 4px; border-radius: 999px; background: var(--surface); border: 1px solid var(--border); }
.seg span { padding: 6px 16px; border-radius: 999px; font-size: 12.5px; color: var(--muted); }
.seg .on { color: var(--text); background: var(--surface-2); }
.orbit { position: relative; flex: 1; min-height: 340px; }
.bubble {
  position: absolute; transform: translate(-50%, -50%);
  display: inline-flex; align-items: center; gap: 8px;
  padding: 10px 18px; border-radius: 999px; cursor: pointer;
  font-size: 13.5px; color: var(--text); font-family: inherit;
  background: var(--surface); border: 1px solid var(--border);
  box-shadow: var(--shadow); transition: all .18s;
}
.bubble:hover { transform: translate(-50%, -50%) translateY(-3px); box-shadow: var(--shadow-hover); border-color: var(--accent); color: var(--accent); }
.pin { width: 12px; height: 12px; border-radius: 3px; transform: rotate(45deg); background: linear-gradient(135deg, var(--accent), var(--accent-2)); }

.mascot { position: absolute; left: 50%; top: 50%; transform: translate(-50%, -50%); }
.mascot::before { content: ''; position: absolute; inset: -34px; border-radius: 50%; background: radial-gradient(circle, rgba(124, 92, 255, .12), transparent 68%); }
.mascot svg { width: 260px; height: 260px; animation: hover 4.5s ease-in-out infinite; }
@keyframes hover { 0%,100% { transform: translateY(0) } 50% { transform: translateY(-9px) } }

.welcome { text-align: center; padding-bottom: 12px; }
.welcome h3 { margin: 0 0 6px; font-size: 20px; color: var(--text); font-family: var(--font-head); }
.welcome p { margin: 0 0 18px; font-size: 13px; color: var(--muted); }
.ask {
  display: flex; align-items: center; gap: 10px; max-width: 820px; margin: 0 auto 12px;
  padding: 8px 8px 8px 20px; border-radius: 16px;
  background: var(--surface); border: 1px solid var(--border); box-shadow: var(--shadow);
}
.ask input { flex: 1; border: 0; outline: 0; background: transparent; font-size: 13.5px; color: var(--text); font-family: inherit; }
.send-btn {
  width: 40px; height: 40px; flex: none; display: grid; place-items: center; cursor: pointer;
  border: 0; border-radius: 12px; color: #fff;
  background: linear-gradient(135deg, var(--accent), var(--accent-2));
}
.send-btn .ic { width: 18px; height: 18px; }
.welcome small { font-size: 11.5px; color: var(--muted); }

.placeholder { display: grid; place-items: center; gap: 10px; padding: 90px 0; text-align: center; }
.placeholder .ic { width: 44px; height: 44px; color: var(--accent); }
.placeholder h2 { margin: 6px 0 0; font-size: 20px; font-family: var(--font-head); }
.placeholder p { margin: 0 0 12px; font-size: 13px; color: var(--muted); }

@media (max-width: 760px) {
  .home { grid-template-columns: 1fr; }
  .rail { flex-direction: row; overflow-x: auto; border-right: 0; border-bottom: 1px solid var(--border); }
  .rail-foot { margin: 0; display: flex; }
  .body { padding: 18px; }
}
</style>
