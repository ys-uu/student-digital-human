import { defineStore } from 'pinia'
import { logout as logoutApi, getMe, refreshToken as refreshApi } from '@/api/v1/auth'

export const useUserStore = defineStore('user', {
  state: () => ({
    // access token 仅存内存，刷新页面后靠 refresh cookie 静默换新，不再持久化
    token: '',
    // 当前用户权限码集合
    permissions: [],
    userInfo: {}
  }),
  getters: {
    roleName: (state) => state.userInfo?.roleName || '',
    // 通配码 *:* 命中即拥有全部权限，否则按权限码精确匹配
    hasPermission: (state) => (code) => {
      const perms = state.permissions || []
      return perms.includes('*:*') || perms.includes(code)
    },
    // 角色判断：入参为角色字符串数组，如 ['teacher', 'admin']
    hasRole: (state) => (roles) => {
      const list = Array.isArray(roles) ? roles : [roles]
      return list.includes(state.userInfo?.roleName)
    }
  },
  actions: {
    /** 登录成功后统一保存（后端 TokenVO 结构，已不含 refreshToken） */
    setLogin(loginData) {
      this.token = loginData.accessToken
      this.userInfo = loginData.user || {}
      this.permissions = loginData.user?.permissions || []
    },
    setUserInfo(info) {
      this.userInfo = info
      this.permissions = info?.permissions || []
    },
    /**
     * 凭 HttpOnly refresh cookie 静默换新 access token。
     * 成功返回响应，失败抛错由调用方（路由守卫/拦截器）处理。
     */
    async trySilentRefresh() {
      const res = await refreshApi()
      if (res.code === 200) {
        this.token = res.data.accessToken
        // 刷新响应同样带 user，同步用户信息与权限
        if (res.data.user) {
          this.userInfo = res.data.user
          this.permissions = res.data.user.permissions || []
        }
        return res
      }
      throw new Error('静默刷新失败')
    },
    /** 拉取当前用户信息并同步权限 */
    async fetchUserInfo() {
      const res = await getMe()
      if (res.code === 200) {
        this.setUserInfo(res.data)
      }
      return res
    },
    /** 退出：通知后端拉黑并清理白名单/cookie，再清空内存 */
    async logout() {
      try {
        if (this.token) {
          await logoutApi()
        }
      } catch (e) {
        // 网络异常也照常清空本地
      }
      this.clear()
    },
    clear() {
      this.token = ''
      this.permissions = []
      this.userInfo = {}
    }
  }
  // 不再配置 persist，任何令牌都不写入 localStorage
})
