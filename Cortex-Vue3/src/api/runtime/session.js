import request from '@/utils/request'

// 会话树(业务系统 > 用户)
export function getSessionTree() {
  return request({ url: '/agent/api/session/tree', method: 'get' })
}

// 会话列表
export function listSession(query) {
  return request({ url: '/agent/api/session/list', method: 'get', params: query })
}

// 用户列表
export function listUsers(businessSystem) {
  return request({ url: '/agent/api/session/users', method: 'get', params: { businessSystem } })
}

// 会话详情
export function getSession(sessionId) {
  return request({ url: '/agent/api/session/' + sessionId, method: 'get' })
}

// 删除会话
export function delSession(sessionId) {
  return request({ url: '/agent/api/session/' + sessionId, method: 'delete' })
}

// 所有业务系统
export function listBusinessSystems() {
  return request({ url: '/agent/api/business-systems', method: 'get' })
}