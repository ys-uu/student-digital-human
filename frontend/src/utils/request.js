import axios from 'axios'

const service = axios.create({
  baseURL: '',
  timeout: 10000
})

// 请求拦截器：自动带上 token（从 pinia 持久化的 user 对象中读取）
service.interceptors.request.use(
  config => {
    const persisted = localStorage.getItem('user')
    if (persisted) {
      try {
        const token = JSON.parse(persisted).token
        if (token) {
          config.headers['Authorization'] = 'Bearer ' + token
        }
      } catch (e) {
        // 持久化数据损坏时忽略
      }
    }
    return config
  },
  error => Promise.reject(error)
)

// 响应拦截器：成功直接返回业务报文（Result），401 跳登录
service.interceptors.response.use(
  response => response.data,
  error => {
    if (error.response && error.response.status === 401) {
      localStorage.removeItem('user')
      if (location.pathname !== '/login') {
        location.href = '/login'
      }
    }
    return Promise.reject(error)
  }
)

export default service
