import request from '@/utils/request'

/**
 * 大屏首屏快照
 * @param {{ days?: number, courseId?: number }} params
 */
export function getDashboardOverview(params) {
  return request.get('/api/v1/dashboard/overview', { params })
}
