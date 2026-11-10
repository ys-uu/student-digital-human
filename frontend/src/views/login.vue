<template>
  <div class="login-container">
    <el-card class="login-card">
      <h2 class="title">AI 数字人助教系统</h2>
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <!-- ============ 账密登录 ============ -->
        <el-tab-pane label="账号密码登录" name="password">
          <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="0">
            <el-form-item prop="username">
              <el-input v-model="pwdForm.username" placeholder="请输入账号（学号 / 工号）" clearable />
            </el-form-item>
            <el-form-item prop="password">
              <el-input v-model="pwdForm.password" type="password" placeholder="请输入密码" show-password
                        @keyup.enter="handlePwdLogin" />
            </el-form-item>
            <el-button type="primary" class="full-btn" :loading="loading" @click="handlePwdLogin">登 录</el-button>
          </el-form>
          <div class="link-line">
            没有账号？<el-link type="primary" @click="router.push('/register')">立即注册</el-link>
          </div>
        </el-tab-pane>

        <!-- ============ 手机验证码登录 ============ -->
        <el-tab-pane label="手机验证码登录" name="sms">
          <el-form ref="smsFormRef" :model="smsForm" :rules="smsRules" label-width="0">
            <el-form-item prop="phone">
              <el-input v-model="smsForm.phone" placeholder="请输入手机号" maxlength="11" clearable />
            </el-form-item>
            <el-form-item prop="verifyCode">
              <div class="code-line">
                <el-input v-model="smsForm.verifyCode" placeholder="请输入验证码" maxlength="6" />
                <el-button :disabled="countdown > 0" @click="handleSendSms">
                  {{ countdown > 0 ? countdown + 's 后重发' : '获取验证码' }}
                </el-button>
              </div>
            </el-form-item>
            <el-button type="primary" class="full-btn" :loading="loading" @click="handleSmsLogin">登 录</el-button>
          </el-form>
          <div class="tip-line">未注册的手机号验证通过后将自动创建账号</div>
        </el-tab-pane>

        <!-- ============ 扫码登录 ============ -->
<!--        <el-tab-pane label="扫码登录" name="qr">
          <div class="qr-box">
            <template v-if="qrStatus !== 3">
              <div class="qr-img">
                <div class="qr-grid"></div>
                <div class="qr-mask">模拟二维码</div>
              </div>
              <div class="qr-token">凭证：{{ qrToken }}</div>
              <div class="qr-tip">请使用模拟手机扫码，{{ qrCountdown }}s 后失效</div>
            </template>
            <template v-else>
              <el-empty description="二维码已过期" />
            </template>
            <el-button type="primary" plain @click="initQr">
              {{ qrStatus === 3 ? '刷新二维码' : '重新生成' }}
            </el-button>
            <el-button type="success" :disabled="qrStatus === 3" @click="openPhoneDialog">
              模拟手机扫码
            </el-button>
          </div>
        </el-tab-pane>-->
      </el-tabs>
    </el-card>

    <!-- ============ 模拟手机弹窗 ============ -->
<!--    <el-dialog v-model="phoneDialogVisible" title="模拟手机" width="380px" :close-on-click-modal="false"
               @closed="handleDialogClosed">
      <div v-if="!hasScanned">
        <div class="dialog-sub">选择一个账号模拟扫码：</div>
        <div v-for="row in mockUsers" :key="row.account" class="mock-user" @click="handleMockScan(row)">
          <div>
            <div class="mock-account">{{ row.account }}</div>
            <div class="mock-name">{{ row.realName }}</div>
          </div>
          <el-tag :type="row.inWhitelist ? 'success' : 'danger'" size="small">
            {{ row.inWhitelist ? '白名单内' : '非白名单' }}
          </el-tag>
        </div>
      </div>
      <div v-else class="confirm-pane">
        <p>账号 <b>{{ scannedAccount }}</b> 已扫码</p>
        <p class="dialog-sub">是否确认登录 PC 端？</p>
        <div class="confirm-btns">
          <el-button type="primary" @click="handleQrConfirm">确认登录</el-button>
          <el-button @click="handleQrCancel">取消</el-button>
        </div>
      </div>
    </el-dialog>
    -->
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
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
    userStore.refreshToken = ''
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
  if (activeTab.value === 'qr') initQr()
})

onUnmounted(() => {
  stopPolling()
  clearInterval(smsTimer)
  clearInterval(qrCountdownTimer)
})
</script>

<style scoped>
.login-container {
  height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background: #f2f5fb;
}
.login-card {
  width: 420px;
}
.title {
  text-align: center;
  margin: 6px 0 16px;
  color: #303133;
}
.full-btn {
  width: 100%;
}
.link-line {
  margin-top: 12px;
  text-align: right;
  font-size: 13px;
}
.tip-line {
  margin-top: 10px;
  font-size: 12px;
  color: #909399;
}
.code-line {
  display: flex;
  gap: 10px;
  width: 100%;
}
.qr-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 6px 0;
}
.qr-img {
  position: relative;
  width: 180px;
  height: 180px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  overflow: hidden;
}
.qr-grid {
  width: 100%;
  height: 100%;
  background-image:
    linear-gradient(#303133 1px, transparent 1px),
    linear-gradient(90deg, #303133 1px, transparent 1px);
  background-size: 12px 12px;
  opacity: 0.85;
}
.qr-mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.72);
  font-weight: 600;
  color: #409eff;
}
.qr-token {
  font-size: 11px;
  color: #909399;
  word-break: break-all;
  max-width: 320px;
}
.qr-tip {
  font-size: 12px;
  color: #e6a23c;
}
.dialog-sub {
  color: #909399;
  font-size: 13px;
  margin-bottom: 10px;
}
.mock-user {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  margin-bottom: 8px;
  cursor: pointer;
}
.mock-user:hover {
  border-color: #409eff;
  background: #ecf5ff;
}
.mock-account {
  font-weight: 600;
}
.mock-name {
  font-size: 12px;
  color: #909399;
}
.confirm-pane {
  text-align: center;
  padding: 10px 0;
}
.confirm-btns {
  margin-top: 16px;
  display: flex;
  justify-content: center;
  gap: 12px;
}
</style>
