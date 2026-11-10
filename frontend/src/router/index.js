import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/login', name: 'login', component: () => import('@/views/login.vue') },
    { path: '/register', name: 'register', component: () => import('@/views/register.vue') },
    {
      path: '/admin',
      name: 'admin',
      component: () => import('@/views/admin/index.vue'),
      // 路由 meta：roles 限定可访问角色；permissions 同理可写权限码数组，命中其一即可
      meta: { roles: ['teacher', 'admin'] }
    },
    {
      path: '/student',
      name: 'student',
      component: () => import('@/views/student/index.vue'),
      meta: { roles: ['student'] }
    },
    { path: '/', redirect: '/login' }
  ]
})

// 无需登录即可访问的页面
const whiteList = ['/login', '/register']

// 按角色返回默认主页
function homePath(roleName) {
  return roleName === 'teacher' || roleName === 'admin' ? '/admin' : '/student'
}

router.beforeEach(async (to) => {
  const userStore = useUserStore()
  // 未登录：白名单页面放行，其余跳登录（保留原有 token 白名单逻辑）
  if (!userStore.token) {
    return whiteList.includes(to.path) ? true : '/login'
  }
  // 已登录访问登录/注册页：直接进入对应主页
  if (whiteList.includes(to.path)) {
    return homePath(userStore.roleName)
  }
  // 刷新或旧会话导致权限为空时，先拉取 /auth/me 补全，失败则回登录页
  if (!userStore.permissions || userStore.permissions.length === 0) {
    try {
      await userStore.fetchUserInfo()
    } catch (e) {
      return '/login'
    }
  }
  // 角色校验：meta.roles 命中其一即可
  if (to.meta?.roles && !userStore.hasRole(to.meta.roles)) {
    return homePath(userStore.roleName)
  }
  // 权限校验：meta.permissions 命中其一即可（*:* 通配由 hasPermission 统一处理）
  if (to.meta?.permissions) {
    const allowed = to.meta.permissions.some((code) => userStore.hasPermission(code))
    if (!allowed) {
      return homePath(userStore.roleName)
    }
  }
  return true
})

export default router
