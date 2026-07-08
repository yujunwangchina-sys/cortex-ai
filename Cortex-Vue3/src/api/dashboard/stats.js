import request from '@/utils/request'

// 获取Dashboard统计数据
export function getDashboardStats() {
  return request({
    url: '/dashboard/stats',
    method: 'get'
  })
}

// 获取趋势数据
export function getTrendData(period = 'week') {
  return request({
    url: '/dashboard/trend',
    method: 'get',
    params: { period }
  })
}
