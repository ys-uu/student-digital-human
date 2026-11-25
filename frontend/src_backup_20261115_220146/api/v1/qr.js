import request from '@/utils/request'

// 3.6 生成登录二维码
export function createQr() {
  return request.post('/qr/create')
}

// 3.7 轮询二维码状态
export function getQrStatus(qrToken) {
  return request.get('/qr/status', { params: { qrToken } })
}

// 3.8 模拟扫码（白名单校验）
export function qrScan(data) {
  return request.post('/qr/scan', data)
}

// 3.9 确认登录
export function qrConfirm(qrToken) {
  return request.post('/qr/confirm', { qrToken })
}

// 3.9 取消登录
export function qrCancel(qrToken) {
  return request.post('/qr/cancel', { qrToken })
}

// 3.9 模拟手机候选账号（dev）
export function getMockUsers() {
  return request.get('/qr/mock-users')
}
