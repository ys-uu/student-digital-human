<template>
  <div class="teacher-page">
    <header class="topbar">
      <div class="brand">
        <span class="dot"></span>
        <h1>教师工作台</h1>
      </div>
      <div class="actions">
        <span v-if="teacherName" class="who">{{ teacherName }}</span>
        <button class="btn" @click="router.push('/admin')">返回后台</button>
      </div>
    </header>

    <main class="content">
      <section class="card">
        <h2>一键布置随堂练习</h2>
        <p class="desc">
          点击后系统会为你的课程创建一份随堂练习。教务数据大屏将在 3 秒内刷新
          「今日布置作业」指标，并在实时动态中显示这条记录。
        </p>
        <button v-role="['teacher','admin']" class="publish" :disabled="submitting" @click="publish">
          {{ submitting ? '布置中...' : '布置随堂练习' }}
        </button>
        <p v-if="lastResult" class="result">
          已布置：{{ lastResult.courseName }} · {{ lastResult.title }}
        </p>
      </section>

      <section class="card">
        <h2>本次操作记录</h2>
        <ul class="records">
          <li v-for="(item, index) in records" :key="index">
            <span class="time">{{ item.time }}</span>
            <span class="text">{{ item.text }}</span>
          </li>
          <li v-if="!records.length" class="empty">暂无操作记录</li>
        </ul>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { publishQuickHomework } from '@/api/v1/teacher'

const router = useRouter()
const userStore = useUserStore()

const teacherName = computed(
  () => userStore.userInfo?.realName || userStore.userInfo?.userNo || ''
)

const submitting = ref(false)
const lastResult = ref(null)
const records = ref([])

async function publish() {
  submitting.value = true
  try {
    const res = await publishQuickHomework()
    if (res.code === 200 && res.data) {
      lastResult.value = res.data
      records.value.unshift({
        time: new Date().toLocaleTimeString('zh-CN', { hour12: false }),
        text: `布置《${res.data.title}》 · ${res.data.courseName}`
      })
      records.value = records.value.slice(0, 6)
      ElMessage.success('已布置，教务大屏将在 3 秒内更新')
    } else {
      ElMessage.error(res.msg || '布置失败')
    }
  } catch (e) {
    // 优先展示后端返回的拒绝原因（如 403：权限不足，禁止访问），无响应体再兜底
    const msg = e?.response?.data?.msg || '布置失败，请检查后端服务'
    ElMessage.error(msg)
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.teacher-page {
  min-height: 100vh;
  background: #f5f7fa;
  color: #1f2d3d;
  font-family: 'PingFang SC', 'Microsoft YaHei', system-ui, sans-serif;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 28px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #409eff;
}

.brand h1 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.actions {
  display: flex;
  align-items: center;
  gap: 14px;
}

.who {
  font-size: 14px;
  color: #606266;
}

.btn {
  height: 32px;
  padding: 0 14px;
  font-size: 13px;
  color: #606266;
  background: #fff;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  cursor: pointer;
}

.btn:hover {
  color: #409eff;
  border-color: #409eff;
}

.content {
  max-width: 760px;
  margin: 0 auto;
  padding: 28px 20px 48px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.card {
  padding: 24px;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 10px;
}

.card h2 {
  margin: 0 0 10px;
  font-size: 16px;
  font-weight: 600;
}

.desc {
  margin: 0 0 20px;
  font-size: 13px;
  line-height: 22px;
  color: #909399;
}

.publish {
  width: 100%;
  height: 46px;
  font-size: 15px;
  font-weight: 500;
  color: #fff;
  background: #409eff;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;
}

.publish:hover:not(:disabled) {
  background: #337ecc;
}

.publish:disabled {
  background: #a0cfff;
  cursor: not-allowed;
}

.result {
  margin: 16px 0 0;
  font-size: 13px;
  color: #67c23a;
}

.records {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.records li {
  display: flex;
  gap: 12px;
  font-size: 13px;
  color: #606266;
}

.records .time {
  flex: none;
  color: #909399;
  font-variant-numeric: tabular-nums;
}

.records .text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.empty {
  color: #c0c4cc;
}
</style>
