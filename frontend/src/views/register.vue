<template>
  <div class="reg">
    <div class="blob b1"></div>
    <div class="blob b2"></div>

    <div class="card">
      <button class="back" @click="router.push('/')">← 返回首页</button>
      <h1>创建账号</h1>
      <p class="sub">加入 AI 数字人助教，开启你的智能学习</p>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @keyup.enter="handleRegister">
        <el-form-item label="身份">
          <div class="role-row">
            <button type="button" class="role-btn" :class="{ on: form.role === 'student' }" @click="form.role = 'student'">学生</button>
            <button type="button" class="role-btn" :class="{ on: form.role === 'teacher' }" @click="form.role = 'teacher'">教师</button>
          </div>
        </el-form-item>
        <el-form-item label="账号" prop="userNo">
          <el-input v-model="form.userNo" placeholder="学号 / 工号，4~32 位" size="large" />
        </el-form-item>
        <el-form-item label="真实姓名" prop="realName">
          <el-input v-model="form.realName" placeholder="请输入真实姓名" size="large" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="选填，用于验证码登录" maxlength="11" size="large" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="8~20 位" show-password size="large" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" placeholder="再次输入密码" show-password size="large" />
        </el-form-item>

        <el-button type="primary" class="full-btn" :loading="loading" @click="handleRegister">注 册</el-button>
      </el-form>

      <p class="foot">已有账号？<button class="link" @click="router.push('/login')">直接登录</button></p>
    </div>
  </div>
</template>

﻿<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { register } from '@/api/v1/auth'
import { useUserStore } from '@/stores/user'
import ThemeSwitcher from '../components/ThemeSwitcher.vue'
import { applyTheme, currentTheme } from '../config/themes.js'

const router = useRouter()
onMounted(() => applyTheme(currentTheme()))
const userStore = useUserStore()

const formRef = ref(null)
const loading = ref(false)
const form = ref({
  userNo: '',
  password: '',
  confirmPassword: '',
  realName: '',
  phone: '',
  role: 'student'
})

const validateConfirm = (rule, value, callback) => {
  if (value !== form.value.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules = {
  userNo: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    { min: 4, max: 32, message: '账号长度 4~32 位', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, max: 20, message: '密码长度 8~20 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' }
  ],
  realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
  phone: [{ pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }]
}

const handleRegister = async () => {
  await formRef.value.validate()
  loading.value = true
  try {
    const res = await register({
      userNo: form.value.userNo,
      password: form.value.password,
      realName: form.value.realName,
      phone: form.value.phone || null,
      role: form.value.role === 'teacher' ? 2 : 1
    })
    if (res.code === 200) {
      userStore.setLogin(res.data)
      ElMessage.success('注册成功，已自动登录')
      router.push('/student')
    } else {
      ElMessage.error(res.msg)
    }
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.reg { position: relative; min-height: 100vh; display: grid; place-items: center; padding: 32px; overflow: hidden; background: var(--bg); background-image: var(--bg-image); color: var(--text); font-family: "PingFang SC", "Microsoft YaHei", Inter, system-ui, sans-serif; }
.blob { position: absolute; border-radius: 50%; filter: blur(90px); opacity: .55; pointer-events: none; }
.b1 { width: 460px; height: 460px; top: -150px; left: -110px; background: var(--accent-soft); }
.b2 { width: 400px; height: 400px; right: -120px; bottom: -150px; background: var(--accent-soft); }

.card { position: relative; z-index: 2; width: min(430px, 100%); padding: 32px 30px 24px; border-radius: var(--radius); background: var(--surface); border: 1px solid var(--border); box-shadow: var(--shadow-hover); backdrop-filter: blur(14px); }
.back { border: 0; background: none; padding: 0; margin-bottom: 14px; cursor: pointer; font-size: 12.5px; color: var(--muted); font-family: inherit; }
.back:hover { color: var(--accent); }
.card h1 { margin: 0 0 6px; font-size: 24px; font-family: var(--font-head); color: var(--text-h); }
.sub { margin: 0 0 18px; font-size: 12.5px; color: var(--muted); }

:deep(.el-form-item__label) { color: var(--muted); font-size: 12.5px; padding-bottom: 4px; }
:deep(.el-input__wrapper) { background: var(--surface-2); box-shadow: 0 0 0 1px var(--border) inset; border-radius: 11px; padding: 2px 13px; }
:deep(.el-input__wrapper.is-focus) { box-shadow: 0 0 0 1px var(--accent) inset, 0 0 0 3px var(--accent-soft); }
:deep(.el-form-item) { margin-bottom: 14px; }

.role-row { display: flex; gap: 10px; width: 100%; }
.role-btn { flex: 1; height: 38px; border-radius: 10px; cursor: pointer; font-family: inherit; font-size: 13.5px; color: var(--muted); background: var(--surface-2); border: 1px solid var(--border); }
.role-btn.on { color: var(--accent); background: var(--accent-soft); border-color: var(--accent); font-weight: 600; }

.full-btn { width: 100%; height: 46px; border-radius: 11px; font-size: 15px; letter-spacing: .14em; color: #fff; border: 0; background: linear-gradient(100deg, var(--accent), var(--accent-2)); box-shadow: 0 12px 26px rgba(124, 92, 255, .22); }
.full-btn:hover { filter: brightness(1.05); }

.foot { margin: 18px 0 0; text-align: center; font-size: 12.5px; color: var(--muted); }
.link { border: 0; background: none; padding: 0; cursor: pointer; color: var(--accent); font-family: inherit; font-size: 12.5px; }
.link:hover { text-decoration: underline; }
</style>
