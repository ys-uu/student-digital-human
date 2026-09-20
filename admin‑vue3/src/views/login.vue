<template>
  <div class="login-container">
    <el-card style="width:400px">
      <h2 style="text-align:center">AI数字人助教系统</h2>
      <el-form ref="loginFormRef" :model="loginForm" :rules="loginRules" label-width="80px">
        <el-form-item label="账号" prop="username">
          <el-input v-model="loginForm.username"></el-input>
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="loginForm.password" type="password"></el-input>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleLogin">登录</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
// 引入封装好的登录接口函数（使用@别名）
import { login } from '@/api/v1/user'

const router = useRouter()
const userStore = useUserStore()
const loginFormRef = ref(null)

const loginForm = ref({
  username: '',
  password: ''
})
// 表单校验规则
const loginRules = {
  username: [
    { required: true, message: '请输入账号', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' }
  ]
}

const handleLogin = async () => {
  // 先执行表单校验
  await loginFormRef.value.validate()
  try {
    // 调用封装好的登录api
    const res = await login(loginForm.value)
    if (res.code === 200) {
      userStore.setToken(res.data.token)
      userStore.setUserInfo(res.data)
      // 角色跳转，后端返回只有 student / teacher
      if(res.data.role === 'teacher'){
        router.push('/admin')
      }else{
        router.push('/student')
      }
    } else {
      alert(res.msg || "登录失败")
    }
  } catch (err) {
    console.error(err)
    alert("请求异常，检查后端是否启动")
  }
}
</script>

<style scoped>
.login-container{
  height:100vh;
  display:flex;
  justify-content:center;
  align-items:center;
}
</style>

