import axios from 'axios'

const service = axios.create({
    baseURL: '',
    timeout: 10000
})

// 请求拦截器：自动带上 token
service.interceptors.request.use(
    config => {
        const token = localStorage.getItem('token')
        if (token) {
            config.headers['Authorization'] = 'Bearer ' + token
        }
        return config
    },
    error => Promise.reject(error)
)

// 响应拦截器
service.interceptors.response.use(
    response => response.data,
    error => {
        if (error.response && error.response.status === 401) {
            localStorage.removeItem('token')   // 顺手清掉
            location.href = '/login'
        }
        return Promise.reject(error)
    }
)

export default service

