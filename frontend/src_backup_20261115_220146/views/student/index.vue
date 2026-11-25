<template>
  <div class="page">
    <h2>学生主页 - AI数字人助教</h2>
    <p class="role-line">当前角色：{{ userStore.roleName }}</p>

    <!-- 按钮级权限示例：学生持有 homework:submit，无 homework:create -->
    <div class="btn-group">
      <el-button v-permission="['homework:submit']" type="primary">提交作业</el-button>
      <el-button v-permission="['homework:list']" type="success">我的作业</el-button>
      <!-- 教师/教务权限码，学生不持有，本按钮应隐藏 -->
      <el-button v-permission="['homework:create']" type="warning">发布作业</el-button>
      <!-- 仅学生角色可见 -->
      <el-button v-role="['student']" type="info">学生专属入口</el-button>
    </div>

    <el-button class="logout-btn" @click="handleLogout">退出登录</el-button>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useUserStore } from '../../stores/user'
const router = useRouter()
const userStore = useUserStore()
const handleLogout = () => {
  userStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.page {
  padding: 24px;
}
.role-line {
  color: #606266;
  margin: 8px 0 16px;
}
.btn-group {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 20px;
}
</style>
