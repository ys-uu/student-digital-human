import axios from 'axios'
import { ElMessage } from 'element-plus'

const service = axios.create({
  baseURL: '',
  timeout: 10000,
  // 允许携带 cookie，refresh token 依赖浏览器自动带上 HttpOnly cookie
  withCredentials: true
})

// ---------- 单飞刷新状态：并发请求只允许触发一次刷新 ----------
let isRefreshing = false
// 刷新期间挂起的请求，刷新成功后统一重放
let pendingQueue = []

function flushQueue(token, error) {
  pendingQueue.forEach(cb => cb(token, error))
  pendingQueue = []
}

// 动态导入 store，避免 request 与 store 之间的循环依赖
async function getUserStore() {
  const { useUserStore } = await import('@/stores/user')
  return useUserStore()
}

// 清场：清理历史遗留的 localStorage 并跳登录页
function forceLogout() {
  localStorage.removeItem('user')
  if (location.pathname !== '/login') {
    location.href = '/login'
  }
}

// 请求拦截器：从内存 store 读取 access token 写入 Authorization
service.interceptors.request.use(
  async config => {
    const userStore = await getUserStore()
    if (userStore.token) {
      config.headers['Authorization'] = 'Bearer ' + userStore.token
    }
    return config
  },
  error => Promise.reject(error)
)

// 响应拦截器：成功直接返回业务报文；401 单飞刷新并重放；429/403 给出提示
service.interceptors.response.use(
  response => response.data,
  async error => {
    const config = error.config
    const status = error.response && error.response.status
    const data = error.response && error.response.data

    // 刷新接口自身失败：不再递归刷新，直接清场
    if (config && config._isRefresh) {
      forceLogout()
      return Promise.reject(error)
    }

    if (status === 401) {
      // 已有刷新在进行：当前请求挂起，等新 token 后重放
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          pendingQueue.push((token, err) => {
            if (err) return reject(err)
            config.headers['Authorization'] = 'Bearer ' + token
            resolve(service(config))
          })
        })
      }

      isRefreshing = true
      try {
        const userStore = await getUserStore()
        // 凭 cookie 静默换新，失败会抛错进入 catch
        await userStore.trySilentRefresh()
        isRefreshing = false
        const newToken = userStore.token
        // 通知挂起队列统一重放
        flushQueue(newToken, null)
        config.headers['Authorization'] = 'Bearer ' + newToken
        // 重放原请求
        return service(config)
      } catch (e) {
        isRefreshing = false
        flushQueue(null, e)
        forceLogout()
        return Promise.reject(e)
      }
    }

    if (status === 429) {
      ElMessage.warning((data && data.msg) || '请求过于频繁，请稍后再试')
    } else if (status === 403 && data && data.code === 1013) {
      ElMessage.error(data.msg || '访问已被限制')
    }

    return Promise.reject(error)
  }
)

export default service
