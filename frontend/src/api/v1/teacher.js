import request from '@/utils/request'

/** 一键布置随堂练习 */
export function publishQuickHomework() {
  return request.post('/api/v1/teacher/homework/quick')
}
