<template>
  <div class="auth">
    <div class="blob blob-a"></div>
    <div class="blob blob-b"></div>

    <div class="auth-shell">
      <section class="brand">
        <div class="brand-top">
          <div class="mark">AI</div>
          <span class="mark-text">DeepSeek 驱动</span>
        </div>
        <h1>AI 数字人助教</h1>
        <p class="slogan">一对一私教 · 智能导学 · 全程陪伴</p>
        <p class="sub">覆盖课前预习、课中答疑、课后复习、作业辅导与备考冲刺的完整学习闭环。</p>
        <ul class="features">
          <li><span class="ico">✓</span><div><strong>智能答疑</strong><em>引导式讲解，不只给答案</em></div></li>
          <li><span class="ico">✓</span><div><strong>课程导学</strong><em>学习路径与知识点拆解</em></div></li>
          <li><span class="ico">✓</span><div><strong>学情报告</strong><em>掌握度雷达图与诊断建议</em></div></li>
        </ul>
      </section>

      <section class="panel">
        <h2>欢迎回来</h2>
        <p class="panel-sub">登录后进入你的专属工作台</p>

        <el-tabs v-model="activeTab" class="auth-tabs" @tab-change="handleTabChange">
          <el-tab-pane label="账号密码登录" name="password">
            <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="0" @keyup.enter="handlePwdLogin">
              <el-form-item prop="username">
                <el-input v-model="pwdForm.username" placeholder="请输入账号（学号 / 工号）" clearable size="large" />
              </el-form-item>
              <el-form-item prop="password">
                <el-input v-model="pwdForm.password" type="password" placeholder="请输入密码" show-password size="large" />
              </el-form-item>
              <el-button type="primary" class="full-btn" :loading="loading" @click="handlePwdLogin">登 录</el-button>
            </el-form>
            <div class="link-line">没有账号？<el-link type="primary" :underline="false" @click="router.push('/register')">立即注册</el-link></div>
          </el-tab-pane>

          <el-tab-pane label="手机验证码登录" name="sms">
            <el-form ref="smsFormRef" :model="smsForm" :rules="smsRules" label-width="0">
              <el-form-item prop="phone">
                <el-input v-model="smsForm.phone" placeholder="请输入手机号" maxlength="11" clearable size="large" />
              </el-form-item>
              <el-form-item prop="verifyCode">
                <div class="code-line">
                  <el-input v-model="smsForm.verifyCode" placeholder="请输入验证码" maxlength="6" size="large" />
                  <el-button :disabled="countdown > 0" size="large" @click="handleSendSms">
                    {{ countdown > 0 ? countdown + 's 后重发' : '获取验证码' }}
                  </el-button>
                </div>
              </el-form-item>
              <el-button type="primary" class="full-btn" :loading="loading" @click="handleSmsLogin">登 录</el-button>
            </el-form>
            <div class="tip-line">未注册的手机号验证通过后将自动创建账号</div>
          </el-tab-pane>
        </el-tabs>

        <p class="switch">
          <button type="button" class="link" @click="router.push('/')">← 返回首页</button>
        </p>
      </section>
    </div>

    <ThemeSwitcher />
  </div>
</template>

﻿<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import ThemeSwitcher from '../components/ThemeSwitcher.vue'
import { applyTheme, currentTheme } from '../config/themes.js'
import {
  loginByPassword, sendSmsCode, loginBySms, getMe
} from '@/api/v1/auth'
import {
  createQr, getQrStatus, qrScan, qrConfirm, qrCancel, getMockUsers
} from '@/api/v1/qr'

const router = useRouter()
const userStore = useUserStore()

const activeTab = ref('password')
const loading = ref(false)

// ---------- 账密 ----------
const pwdFormRef = ref(null)
const pwdForm = ref({ username: '', password: '' })
const pwdRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}
const handlePwdLogin = async () => {
  await pwdFormRef.value.validate()
  loading.value = true
  try {
    const res = await loginByPassword({ ...pwdForm.value, loginType: 1 })
    if (res.code === 200) {
      goHome(res.data)
    } else {
      ElMessage.error(res.msg)
    }
  } finally {
    loading.value = false
  }
}

// ---------- 短信 ----------
const smsFormRef = ref(null)
const smsForm = ref({ phone: '', verifyCode: '' })
const smsRules = {
  phone: [{ pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' },
    { required: true, message: '请输入手机号', trigger: 'blur' }],
  verifyCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
}
const countdown = ref(0)
let smsTimer = null

const handleSendSms = async () => {
  if (!/^1[3-9]\d{9}$/.test(smsForm.value.phone)) {
    ElMessage.warning('请输入正确的手机号')
    return
  }
  const res = await sendSmsCode(smsForm.value.phone)
  if (res.code === 200) {
    ElMessage.success('验证码已发送')
    countdown.value = 60
    smsTimer = setInterval(() => {
      countdown.value--
      if (countdown.value <= 0) {
        clearInterval(smsTimer)
      }
    }, 1000)
  } else {
    ElMessage.error(res.msg)
  }
}

const handleSmsLogin = async () => {
  await smsFormRef.value.validate()
  loading.value = true
  try {
    const res = await loginBySms({ ...smsForm.value })
    if (res.code === 200) {
      goHome(res.data)
    } else {
      ElMessage.error(res.msg)
    }
  } finally {
    loading.value = false
  }
}

// ---------- 扫码 ----------
const qrToken = ref('')
const qrStatus = ref(0)
const qrCountdown = ref(120)
const phoneDialogVisible = ref(false)
const mockUsers = ref([])
const hasScanned = ref(false)
const scannedAccount = ref('')
let pollTimer = null
let qrCountdownTimer = null

const initQr = async () => {
  stopPolling()
  const res = await createQr()
  if (res.code === 200) {
    qrToken.value = res.data.qrToken
    qrStatus.value = 0
    qrCountdown.value = res.data.expireSeconds
    clearInterval(qrCountdownTimer)
    qrCountdownTimer = setInterval(() => {
      qrCountdown.value--
      if (qrCountdown.value <= 0) {
        qrStatus.value = 3
        clearInterval(qrCountdownTimer)
      }
    }, 1000)
    if (activeTab.value === 'qr') {
      startPolling()
    }
  } else {
    ElMessage.error(res.msg)
  }
}

const startPolling = () => {
  stopPolling()
  pollTimer = setInterval(pollQrStatus, 1500)
}
const stopPolling = () => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

const pollQrStatus = async () => {
  if (!qrToken.value) return
  const res = await getQrStatus(qrToken.value)
  if (res.code !== 200) return
  const d = res.data
  qrStatus.value = d.status
  if (d.status === 2) {
    stopPolling()
    clearInterval(qrCountdownTimer)
    userStore.token = d.token
    const meRes = await getMe()
    if (meRes.code === 200) {
      userStore.userInfo = meRes.data
      ElMessage.success('扫码登录成功')
      routeByRole(meRes.data.roleName)
    }
  } else if (d.status === 3) {
    stopPolling()
    clearInterval(qrCountdownTimer)
  } else if (d.status === 4) {
    stopPolling()
    ElMessage.info('登录已取消')
  } else if (d.status === 5) {
    stopPolling()
    ElMessage.error('账号不在白名单，登录被拒绝')
  }
}

const openPhoneDialog = async () => {
  if (qrStatus.value === 3) {
    ElMessage.warning('二维码已过期，请先刷新')
    return
  }
  const res = await getMockUsers()
  if (res.code === 200) {
    mockUsers.value = res.data
    hasScanned.value = false
    scannedAccount.value = ''
    phoneDialogVisible.value = true
  }
}

const handleMockScan = async (row) => {
  const res = await qrScan({ qrToken: qrToken.value, account: row.account })
  if (res.code === 200) {
    scannedAccount.value = row.account
    hasScanned.value = true
    ElMessage.success('扫码成功，请在手机上确认')
  } else {
    ElMessage.error(res.msg)
  }
}

const handleQrConfirm = async () => {
  const res = await qrConfirm(qrToken.value)
  if (res.code === 200) {
    phoneDialogVisible.value = false
    ElMessage.success('已确认登录')
  } else {
    ElMessage.error(res.msg)
  }
}

const handleQrCancel = async () => {
  await qrCancel(qrToken.value)
  phoneDialogVisible.value = false
  ElMessage.info('已取消登录')
}

const handleDialogClosed = () => {
  hasScanned.value = false
  scannedAccount.value = ''
}

const handleTabChange = (name) => {
  if (name === 'qr') {
    if (!qrToken.value) {
      initQr()
    } else {
      startPolling()
    }
  } else {
    stopPolling()
  }
}

// ---------- 通用跳转 ----------
const goHome = (data) => {
  userStore.setLogin(data)
  ElMessage.success('登录成功')
  routeByRole(data.user?.roleName)
}
const routeByRole = (roleName) => {
  router.push(roleName === 'teacher' || roleName === 'admin' ? '/admin' : '/student')
}

watch(activeTab, handleTabChange)

onMounted(() => {
  applyTheme(currentTheme())
  if (activeTab.value === 'qr') initQr()
})

onUnmounted(() => {
  stopPolling()
  clearInterval(smsTimer)
  clearInterval(qrCountdownTimer)
})
</script>

<style scoped>
.auth {
  position: relative; min-height: 100vh; display: flex; align-items: center; justify-content: center;
  padding: 40px 28px; overflow: hidden;
  background: var(--bg); background-image: var(--bg-image);
  color: var(--text);
  font-family: "PingFang SC", "Microsoft YaHei", Inter, system-ui, sans-serif;
}
.blob { position: absolute; border-radius: 50%; filter: blur(90px); opacity: .5; pointer-events: none; }
.blob-a { width: 520px; height: 520px; top: -180px; left: -120px; background: var(--accent-soft); }
.blob-b { width: 420px; height: 420px; right: -140px; bottom: -160px; background: var(--accent-soft); }

.auth-shell { position: relative; z-index: 2; width: min(1000px, 100%); display: grid; grid-template-columns: 1.05fr .95fr; gap: 56px; align-items: center; }
.brand-top { display: flex; align-items: center; gap: 12px; margin-bottom: 24px; }
.mark { width: 46px; height: 46px; border-radius: 14px; display: grid; place-items: center; font-weight: 800; font-size: 14px; color: #fff; background: linear-gradient(135deg, var(--accent), var(--accent-2)); box-shadow: var(--shadow); }
.mark-text { font-size: 11.5px; letter-spacing: .18em; color: var(--muted); text-transform: uppercase; }
.brand h1 { margin: 0 0 12px; font-size: clamp(32px, 3.6vw, 44px); line-height: 1.15; font-family: var(--font-head); color: var(--text-h); }
.slogan { margin: 0 0 10px; font-size: 15px; color: var(--accent); font-weight: 600; letter-spacing: .04em; }
.sub { margin: 0 0 30px; font-size: 13.5px; line-height: 1.85; color: var(--muted); max-width: 30em; }
.features { list-style: none; margin: 0; padding: 0; display: grid; gap: 14px; }
.features li { display: flex; gap: 12px; align-items: flex-start; }
.features .ico { flex: none; width: 24px; height: 24px; border-radius: 50%; display: grid; place-items: center; font-size: 12px; color: var(--accent); background: var(--accent-soft); }
.features strong { display: block; font-size: 14px; color: var(--text); margin-bottom: 2px; }
.features em { font-style: normal; font-size: 12.5px; color: var(--muted); }

.panel { padding: 34px 34px 24px; border-radius: var(--radius); background: var(--surface); border: 1px solid var(--border); box-shadow: var(--shadow-hover); backdrop-filter: blur(14px); }
.panel h2 { margin: 0 0 6px; font-size: 24px; color: var(--text-h); font-family: var(--font-head); }
.panel-sub { margin: 0 0 18px; font-size: 13px; color: var(--muted); }

:deep(.el-tabs__nav-wrap::after) { background: var(--border); height: 1px; }
:deep(.el-tabs__item) { color: var(--muted); font-size: 13.5px; }
:deep(.el-tabs__item.is-active) { color: var(--accent); font-weight: 600; }
:deep(.el-tabs__active-bar) { background: linear-gradient(90deg, var(--accent), var(--accent-2)); height: 3px; border-radius: 2px; }
:deep(.el-input__wrapper) { background: var(--surface-2); box-shadow: 0 0 0 1px var(--border) inset; border-radius: 11px; padding: 2px 13px; }
:deep(.el-input__wrapper.is-focus) { box-shadow: 0 0 0 1px var(--accent) inset, 0 0 0 3px var(--accent-soft); }
:deep(.el-input__inner) { color: var(--text); }
:deep(.el-form-item) { margin-bottom: 16px; }

.full-btn { width: 100%; height: 46px; border-radius: 11px; font-size: 15px; letter-spacing: .14em; color: #fff; border: 0; background: linear-gradient(100deg, var(--accent), var(--accent-2)); box-shadow: 0 12px 26px rgba(124, 92, 255, .22); }
.full-btn:hover { filter: brightness(1.05); }

.code-line { display: flex; gap: 8px; width: 100%; }
.code-line .el-input { flex: 1; }

.link-line { margin-top: 14px; text-align: right; font-size: 12.5px; color: var(--muted); }
.tip-line { margin-top: 12px; font-size: 12px; color: var(--muted); }

.switch { margin: 18px 0 0; text-align: center; font-size: 12.5px; color: var(--muted); }
.switch .sep { margin: 0 6px; opacity: .5; }
.link { border: 0; background: none; padding: 0; cursor: pointer; color: var(--accent); font-family: inherit; font-size: 12.5px; }
.link:hover { text-decoration: underline; }

@media (max-width: 900px) {
  .auth { padding: 22px; }
  .auth-shell { grid-template-columns: 1fr; max-width: 440px; gap: 0; }
  .brand { display: none; }
  .panel { padding: 28px 22px 20px; }
}
</style>
