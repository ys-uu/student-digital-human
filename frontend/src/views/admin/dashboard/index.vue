<template>
  <div ref="screenRef" class="screen">
    <header class="screen-header">
      <div class="brand">
        <span class="brand-dot"></span>
        <h1>AI 助教教务数据大屏</h1>
      </div>
      <div class="toolbar">
        <select v-model.number="days" class="ctl" @change="loadSnapshot">
          <option :value="7">近 7 天</option>
          <option :value="30">近 30 天</option>
          <option :value="90">近 90 天</option>
        </select>
        <select v-model="courseId" class="ctl" @change="loadSnapshot">
          <option value="">全部课程</option>
          <option v-for="course in courseOptions" :key="course.id" :value="course.id">
            {{ course.name }}
          </option>
        </select>
        <span class="live-status" :class="status">
          <span class="live-dot"></span>{{ statusText }}
        </span>
        <span class="updated">{{ generatedAt }}</span>
        <button class="ctl btn" @click="loadSnapshot">刷新</button>
        <button class="ctl btn" @click="toggleFullscreen">全屏</button>
        <button class="ctl btn" @click="router.push('/admin')">返回</button>
      </div>
    </header>

    <main v-loading="loading" class="grid">
      <div class="metric-row">
        <MetricCard
          v-for="item in metrics"
          :key="item.label"
          :label="item.label"
          :value="item.value"
          :unit="item.unit"
          :decimals="item.decimals || 0"
        />
      </div>

      <section class="panel cell-trend">
        <div class="panel-head">
          <span>学习行为趋势</span>
          <em>近 {{ days }} 天</em>
        </div>
        <div class="panel-body">
          <EChart :option="trendOption" />
        </div>
      </section>

      <section class="panel cell-donut">
        <div class="panel-head">
          <span>知识掌握分布</span>
          <em>全校</em>
        </div>
        <div class="panel-body">
          <EChart :option="donutOption" />
        </div>
      </section>

      <section class="panel cell-rank">
        <div class="panel-head">
          <span>课程学习排行</span>
          <em>有效时长</em>
        </div>
        <div class="panel-body">
          <EChart :option="rankOption" />
        </div>
      </section>

      <section class="panel cell-weak">
        <div class="panel-head">
          <span>薄弱知识点 Top 10</span>
          <em>平均掌握度</em>
        </div>
        <div class="panel-body">
          <EChart :option="weakOption" />
        </div>
      </section>

      <section class="panel cell-live">
        <div class="panel-head">
          <span>实时动态</span>
          <em>事件驱动推送</em>
        </div>
        <div class="panel-body scroll">
          <LiveFeed :events="overview.liveEvents" />
        </div>
      </section>

      <section class="panel cell-class">
        <div class="panel-head">
          <span>班级学习对比</span>
          <em>近 {{ days }} 天</em>
        </div>
        <div class="panel-body scroll">
          <table class="data-table">
            <thead>
              <tr>
                <th>班级</th>
                <th>学生数</th>
                <th>人均时长</th>
                <th>平均掌握度</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in overview.classCompare" :key="row.name">
                <td>{{ row.name }}</td>
                <td>{{ row.students }}</td>
                <td>{{ row.avgMinutes }} 分钟</td>
                <td>{{ row.avgMastery }} 分</td>
              </tr>
              <tr v-if="!overview.classCompare.length">
                <td colspan="4" class="empty">暂无数据</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section class="panel cell-warn">
        <div class="panel-head">
          <span>学情预警</span>
          <em>待批改作业 {{ overview.summary.pendingHomework || 0 }} 份</em>
        </div>
        <div class="panel-body scroll">
          <ul class="warn-list">
            <li v-for="(row, index) in overview.warnings" :key="index">
              <span class="warn-name">{{ row.studentName }}</span>
              <span class="warn-class">{{ row.className }}</span>
              <span class="warn-reason">{{ row.reason }}</span>
            </li>
            <li v-if="!overview.warnings.length" class="empty">暂无预警</li>
          </ul>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getDashboardOverview } from '@/api/v1/dashboard'
import { useDashboardSocket } from '@/composables/useDashboardSocket'
import EChart from './components/EChart.vue'
import MetricCard from './components/MetricCard.vue'
import LiveFeed from './components/LiveFeed.vue'

const router = useRouter()
const screenRef = ref(null)
const loading = ref(false)
const days = ref(7)
const courseId = ref('')
const generatedAt = ref('--')

const courseOptions = [
  { id: 1, name: '数据结构' },
  { id: 2, name: '大学英语' },
  { id: 3, name: '高等数学' }
]

function emptyOverview() {
  return {
    summary: {},
    learningTrend: [],
    masteryDistribution: [],
    courseRanking: [],
    weakPoints: [],
    classCompare: [],
    warnings: [],
    aiUsage: { sceneDistribution: [] },
    liveEvents: []
  }
}

const overview = ref(emptyOverview())

const AXIS = '#334155'
const LABEL = '#94A3B8'
const SPLIT = 'rgba(148, 163, 184, 0.12)'
const TOOLTIP = {
  backgroundColor: 'rgba(15, 23, 42, 0.94)',
  borderColor: 'rgba(56, 189, 248, 0.3)',
  textStyle: { color: '#E2E8F0', fontSize: 12 }
}

const masteryColors = {
  薄弱: '#F87171',
  一般: '#FBBF24',
  良好: '#38BDF8',
  优秀: '#34D399'
}

const metrics = computed(() => {
  const s = overview.value.summary || {}
  return [
    { label: '今日活跃学生', value: Number(s.activeStudents || 0), unit: '人' },
    { label: '今日学习时长', value: Number(s.studyMinutes || 0), unit: '分钟' },
    { label: '今日答疑', value: Number(s.qaCount || 0), unit: '次' },
    { label: '今日正确率', value: Number(s.correctRate || 0), unit: '%', decimals: 1 },
    { label: '平均掌握度', value: Number(s.averageMastery || 0), unit: '分', decimals: 1 },
    { label: '今日 AI 调用', value: Number(s.aiCallCount || 0), unit: '次' },
    { label: '今日布置作业', value: Number(s.todayHomework || 0), unit: '份' }
  ]
})

const trendOption = computed(() => {
  const list = overview.value.learningTrend || []
  return {
    backgroundColor: 'transparent',
    grid: { left: 52, right: 52, top: 44, bottom: 32 },
    tooltip: { trigger: 'axis', ...TOOLTIP },
    legend: {
      top: 4,
      right: 8,
      itemWidth: 12,
      itemHeight: 8,
      textStyle: { color: LABEL, fontSize: 12 }
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: list.map((i) => i.date),
      axisLine: { lineStyle: { color: AXIS } },
      axisTick: { show: false },
      axisLabel: { color: LABEL, fontSize: 11 }
    },
    yAxis: [
      {
        type: 'value',
        name: '分钟',
        nameTextStyle: { color: LABEL, fontSize: 11 },
        splitLine: { lineStyle: { color: SPLIT } },
        axisLabel: { color: LABEL, fontSize: 11 }
      },
      {
        type: 'value',
        name: '次',
        nameTextStyle: { color: LABEL, fontSize: 11 },
        splitLine: { show: false },
        axisLabel: { color: LABEL, fontSize: 11 }
      }
    ],
    series: [
      {
        name: '学习时长',
        type: 'line',
        smooth: true,
        symbol: 'none',
        data: list.map((i) => i.studyMinutes),
        lineStyle: { width: 2, color: '#38BDF8' },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(56, 189, 248, 0.35)' },
              { offset: 1, color: 'rgba(56, 189, 248, 0)' }
            ]
          }
        }
      },
      {
        name: '答疑次数',
        type: 'line',
        smooth: true,
        symbol: 'none',
        yAxisIndex: 1,
        data: list.map((i) => i.qaCount),
        lineStyle: { width: 2, color: '#34D399' }
      }
    ]
  }
})

const donutOption = computed(() => {
  const list = overview.value.masteryDistribution || []
  return {
    backgroundColor: 'transparent',
    tooltip: { trigger: 'item', ...TOOLTIP, formatter: '{b}: {c} ({d}%)' },
    legend: {
      bottom: 0,
      itemWidth: 10,
      itemHeight: 10,
      textStyle: { color: LABEL, fontSize: 12 }
    },
    series: [
      {
        type: 'pie',
        radius: ['46%', '68%'],
        center: ['50%', '42%'],
        itemStyle: { borderColor: '#0F1A2B', borderWidth: 2 },
        label: { color: '#CBD5E1', fontSize: 11, formatter: '{b}\n{d}%' },
        labelLine: { lineStyle: { color: AXIS } },
        data: list.map((item) => ({
          name: item.name,
          value: item.value,
          itemStyle: { color: masteryColors[item.name] || '#38BDF8' }
        }))
      }
    ]
  }
})

const rankOption = computed(() => {
  const list = [...(overview.value.courseRanking || [])].reverse()
  return {
    backgroundColor: 'transparent',
    grid: { left: 92, right: 56, top: 8, bottom: 8 },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, ...TOOLTIP },
    xAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: SPLIT } },
      axisLabel: { color: LABEL, fontSize: 11 }
    },
    yAxis: {
      type: 'category',
      data: list.map((i) => i.name),
      axisLine: { lineStyle: { color: AXIS } },
      axisTick: { show: false },
      axisLabel: { color: LABEL, fontSize: 12 }
    },
    series: [
      {
        type: 'bar',
        barWidth: 12,
        data: list.map((i) => i.value),
        itemStyle: { color: '#38BDF8', borderRadius: [0, 6, 6, 0] },
        label: { show: true, position: 'right', color: LABEL, fontSize: 11 }
      }
    ]
  }
})

const weakOption = computed(() => {
  const list = [...(overview.value.weakPoints || [])].reverse()
  return {
    backgroundColor: 'transparent',
    grid: { left: 104, right: 48, top: 8, bottom: 8 },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, ...TOOLTIP },
    xAxis: {
      type: 'value',
      max: 100,
      splitLine: { lineStyle: { color: SPLIT } },
      axisLabel: { color: LABEL, fontSize: 11 }
    },
    yAxis: {
      type: 'category',
      data: list.map((i) => i.name),
      axisLine: { lineStyle: { color: AXIS } },
      axisTick: { show: false },
      axisLabel: { color: LABEL, fontSize: 11 }
    },
    series: [
      {
        type: 'bar',
        barWidth: 10,
        data: list.map((i) => i.value),
        itemStyle: { color: '#F87171', borderRadius: [0, 5, 5, 0] },
        label: { show: true, position: 'right', color: LABEL, fontSize: 11 }
      }
    ]
  }
})

async function loadSnapshot() {
  if (fatal) return
  loading.value = true
  try {
    const params = { days: days.value }
    if (courseId.value) params.courseId = courseId.value
    const res = await getDashboardOverview(params)
    if (res.code === 200 && res.data) {
      overview.value = { ...emptyOverview(), ...res.data }
      generatedAt.value = res.data.generatedAt || '--'
      errorNotified = false
    } else {
      ElMessage.error(res.msg || '数据加载失败')
    }
  } catch (e) {
    const status = e?.response?.status
    if (status === 401 || status === 403) {
      // 同一浏览器其他标签页登录了教师/学生账号，会把 localStorage 中的 token 覆盖成非教务身份
      fatal = true
      stopPolling()
      closeSocket()
      status.value = 'unauthorized'
      ElMessage.error('当前登录身份不是教务账号，数据大屏仅教务可访问')
      return
    }
    if (!errorNotified) {
      errorNotified = true
      ElMessage.error('数据加载失败，请检查后端服务')
    }
  } finally {
    loading.value = false
  }
}

let pollTimer = null
// 身份无权限（401/403）后停止所有重试，避免弹窗持续叠加
let fatal = false
// 网络类错误只提示一次，避免轮询与重连反复弹窗
let errorNotified = false

function startPolling() {
  if (pollTimer || fatal) return
  pollTimer = setInterval(loadSnapshot, 15000)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

const { status, close: closeSocket } = useDashboardSocket({
  onMessage: (data) => {
    // 实时推送的指标为全校今日口径，选中课程时保留快照结果避免口径被覆盖
    if (data.summary && !courseId.value) overview.value.summary = data.summary
    if (data.liveEvents) overview.value.liveEvents = data.liveEvents
    if (data.generatedAt) generatedAt.value = data.generatedAt
  },
  onFallback: startPolling
})

const statusText = computed(
  () =>
    ({
      live: '实时推送',
      fallback: '轮询模式',
      connecting: '连接中',
      unauthorized: '无权限'
    })[status.value] || ''
)

watch(status, (value) => {
  if (value === 'live') stopPolling()
})

function toggleFullscreen() {
  if (!document.fullscreenElement) {
    screenRef.value?.requestFullscreen?.()
  } else {
    document.exitFullscreen?.()
  }
}

loadSnapshot()

onUnmounted(stopPolling)
</script>

<style scoped>
.screen {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: radial-gradient(circle at 20% 0%, #16233a 0%, #0b1220 55%, #080e19 100%);
  color: #e2e8f0;
  font-family: 'PingFang SC', 'Microsoft YaHei', system-ui, sans-serif;
  overflow: hidden;
}

.screen-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 20px;
  border-bottom: 1px solid rgba(56, 189, 248, 0.16);
  background: rgba(11, 18, 32, 0.72);
  flex: none;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
}

.brand-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #38bdf8;
  box-shadow: 0 0 12px rgba(56, 189, 248, 0.8);
}

.brand h1 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  letter-spacing: 2px;
  color: #e2e8f0;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.ctl {
  height: 30px;
  padding: 0 10px;
  font-size: 13px;
  color: #cbd5e1;
  background: rgba(30, 41, 59, 0.9);
  border: 1px solid rgba(56, 189, 248, 0.22);
  border-radius: 6px;
  outline: none;
  cursor: pointer;
}

.ctl:focus {
  border-color: rgba(56, 189, 248, 0.6);
}

.btn:hover {
  border-color: rgba(56, 189, 248, 0.6);
  color: #e2e8f0;
}

.live-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #94a3b8;
}

.live-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #64748b;
}

.live-status.live .live-dot {
  background: #34d399;
  box-shadow: 0 0 8px rgba(52, 211, 153, 0.8);
}

.live-status.fallback .live-dot {
  background: #fbbf24;
}

.live-status.unauthorized .live-dot {
  background: #f87171;
}

.updated {
  font-size: 12px;
  color: #64748b;
  font-variant-numeric: tabular-nums;
}

.grid {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  grid-template-rows: 88px minmax(0, 1.15fr) minmax(0, 1fr) minmax(0, 0.9fr);
  gap: 14px;
  padding: 14px;
}

.metric-row {
  grid-column: span 12;
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 14px;
  min-height: 0;
}

.cell-trend {
  grid-column: span 8;
}

.cell-donut {
  grid-column: span 4;
}

.cell-rank {
  grid-column: span 4;
}

.cell-weak {
  grid-column: span 4;
}

.cell-live {
  grid-column: span 4;
}

.cell-class {
  grid-column: span 6;
}

.cell-warn {
  grid-column: span 6;
}

.panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 12px 14px;
  background: rgba(17, 28, 46, 0.86);
  border: 1px solid rgba(148, 163, 184, 0.14);
  border-radius: 10px;
}

.panel-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  padding-bottom: 8px;
  flex: none;
}

.panel-head span {
  font-size: 14px;
  font-weight: 600;
  color: #e2e8f0;
}

.panel-head em {
  font-style: normal;
  font-size: 12px;
  color: #64748b;
}

.panel-body {
  flex: 1;
  min-height: 0;
}

.scroll {
  overflow: auto;
}

.scroll::-webkit-scrollbar {
  width: 4px;
}

.scroll::-webkit-scrollbar-thumb {
  background: rgba(148, 163, 184, 0.3);
  border-radius: 2px;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.data-table th {
  text-align: left;
  font-weight: 500;
  color: #64748b;
  padding: 6px 8px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.14);
}

.data-table td {
  padding: 8px;
  color: #cbd5e1;
  border-bottom: 1px solid rgba(148, 163, 184, 0.07);
  font-variant-numeric: tabular-nums;
}

.warn-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.warn-list li {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  line-height: 20px;
  color: #cbd5e1;
}

.warn-name {
  flex: none;
  width: 64px;
  font-weight: 500;
  color: #e2e8f0;
}

.warn-class {
  flex: none;
  width: 108px;
  color: #64748b;
}

.warn-reason {
  flex: 1;
  min-width: 0;
  color: #fbbf24;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.empty {
  display: block;
  text-align: center;
  color: #64748b;
  padding: 12px 0;
}
</style>
