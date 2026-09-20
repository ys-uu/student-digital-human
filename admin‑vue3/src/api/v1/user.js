import request from '@/utils/request'

// 用户登录接口 POST /api/v1/user/login
export function login(data) {
    return request.post('/api/v1/user/login', data)
}
