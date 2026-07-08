import request from '@/utils/request'

// 执行日志列表
export function listExeclog(query) {
  return request({ url: '/agent/api/execlog/list', method: 'get', params: query })
}

// 执行日志详情
export function getExeclog(logId) {
  return request({ url: '/agent/api/execlog/' + logId, method: 'get' })
}

// 按会话查询日志
export function getExeclogBySession(sessionId) {
  return request({ url: '/agent/api/execlog/session/' + sessionId, method: 'get' })
}

// 删除日志
export function delExeclog(logId) {
  return request({ url: '/agent/api/execlog/' + logId, method: 'delete' })
}