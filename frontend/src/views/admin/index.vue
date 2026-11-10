<template>
  <div class="page">
    <h2>教师管理后台</h2>
    <p class="role-line">当前角色：{{ userStore.roleName }}</p>

    <!-- 按钮级权限示例：v-permission 无权限元素会被移除，v-role 按角色控制 -->
    <div class="btn-group">
      <el-button v-permission="['homework:create']" type="primary">发布作业</el-button>
      <el-button v-permission="['homework:grade']" type="success">批阅作业</el-button>
      <!-- 学生权限码，教师/教务均不持有，本按钮应隐藏（教务凭 *:* 可见） -->
      <el-button v-permission="['homework:submit']" type="warning">提交作业</el-button>
      <!-- 仅教务角色可见 -->
      <el-button v-role="['admin']" type="danger">教务专属操作</el-button>
    </div>

    <!-- 禅道风格授权管理入口，仅教务可见 -->
    <div v-role="['admin']" class="btn-group">
      <el-button type="primary" plain @click="router.push('/admin/role')">角色与权限管理</el-button>
      <el-button type="primary" plain @click="router.push('/admin/user')">用户角色分配</el-button>
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
