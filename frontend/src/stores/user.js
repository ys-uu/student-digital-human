import { defineStore } from 'pinia'
import { logout as logoutApi, getMe } from '@/api/v1/auth'

export const useUserStore = defineStore('user', {
  state: () => ({
    // access token
    token: '',
    refreshToken: '',
    // 当前用户权限码集合（后端 /auth/me 与登录返回下发），随 persist 持久化
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
    /** 登录成功后统一保存（后端 TokenVO 结构） */
    setLogin(loginData) {
      this.token = loginData.accessToken
      this.refreshToken = loginData.refreshToken
      this.userInfo = loginData.user || {}
      this.permissions = loginData.user?.permissions || []
    },
    setUserInfo(info) {
      this.userInfo = info
      this.permissions = info?.permissions || []
    },
    /** 拉取当前用户信息并同步权限（刷新 / 旧会话兜底） */
    async fetchUserInfo() {
      const res = await getMe()
      if (res.code === 200) {
        this.setUserInfo(res.data)
      }
      return res
    },
    /** 退出：通知后端拉黑 token，再清空本地 */
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
      this.refreshToken = ''
      this.permissions = []
      this.userInfo = {}
    }
  },
  persist: true
})
