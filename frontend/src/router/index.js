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
      path: '/admin/role',
      name: 'admin-role',
      component: () => import('@/views/admin/role/index.vue'),
      // 角色授权管理：仅教务可访问
      meta: { roles: ['admin'], permissions: ['system:role:list'] }
    },
    {
      path: '/admin/user',
      name: 'admin-user',
      component: () => import('@/views/admin/user/index.vue'),
      // 用户角色分配：仅教务可访问
      meta: { roles: ['admin'], permissions: ['system:user:list'] }
    },
    {
      path: '/admin/dashboard',
      name: 'admin-dashboard',
      component: () => import('@/views/admin/dashboard/index.vue'),
      // 数据大屏：仅教务可访问
      meta: { roles: ['admin'] }
    },
    {
      path: '/teacher',
      name: 'teacher',
      component: () => import('@/views/teacher/index.vue'),
      // 教师工作台：教师与教务均可访问，便于演示
      meta: { roles: ['teacher', 'admin'] }
    },
    {
      path: '/student',
      name: 'student',
      component: () => import('@/views/student/index.vue'),
      meta: { roles: ['student'] }
    },
    {
      path: '/chat',
      name: 'chat',
      component: () => import('@/views/chat/index.vue'),
      // 私信：学生与教师可用；管理员不参与私信（接口层返回 1017），故不开放
      meta: { roles: ['student', 'teacher'] }
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

  // 内存中没有 access token 时，先尝试凭 HttpOnly refresh cookie 静默换新。
  // 原因：access token 只存 Pinia 内存、不落 localStorage（见 stores/user.js），
  // 刷新页面后内存被清空，若不先恢复登录态就会误判为"未登录"而跳转登录页。
  // 静默刷新失败（cookie 不存在/已过期）才视为真正未登录。
  if (!userStore.token) {
    try {
      await userStore.trySilentRefresh()
    } catch (e) {
      // 换新失败：白名单页面放行，其余跳登录
      return whiteList.includes(to.path) ? true : '/login'
    }
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
