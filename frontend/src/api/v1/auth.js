import request from '@/utils/request'

// 3.1 账号密码登录
export function loginByPassword(data) {
  return request.post('/api/v1/auth/login', data)
}

// 账号注册（注册后自动登录）
export function register(data) {
  return request.post('/api/v1/auth/register', data)
}

// 发送短信验证码（60s 频控由后端控制）
export function sendSmsCode(phone) {
  return request.post('/api/v1/auth/sms/code', { phone })
}

// 手机号验证码登录
export function loginBySms(data) {
  return request.post('/api/v1/auth/sms/login', data)
}

// 3.2 刷新令牌
export function refreshToken(refreshToken) {
  return request.post('/api/v1/auth/refresh', { refreshToken })
}

// 3.4 当前登录用户信息
export function getMe() {
  return request.get('/api/v1/auth/me')
}

// 3.3 退出登录
export function logout() {
  return request.post('/api/v1/auth/logout')
}

// 3.5 更新个人信息 / 修改密码
export function updateMe(data) {
  return request.patch('/api/v1/users/me', data)
}
