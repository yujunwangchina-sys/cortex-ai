import request from '@/utils/request'

// 会话列表
export function listSession(query) {
  return request({
    url: '/agent/api/session/list',
    method: 'get',
    params: query
  })
}

// 会话树(业务系统 > 用户, 一次查询)
export function getSessionTree() {
  return request({
    url: '/agent/api/session/tree',
    method: 'get'
  })
}

// 用户列表
export function listUsers(businessSystem) {
  return request({
    url: '/agent/api/session/users',
    method: 'get',
    params: { businessSystem }
  })
}

// 会话详情
export function getSession(sessionId) {
  return request({
    url: '/agent/api/session/' + sessionId,
    method: 'get'
  })
}

// 删除会话
export function delSession(sessionId) {
  return request({
    url: '/agent/api/session/' + sessionId,
    method: 'delete'
  })
}

// 待审批列表
export function listPendingApprovals(sessionId) {
  return request({
    url: '/agent/api/approval/pending/' + sessionId,
    method: 'get'
  })
}

// 批准
export function approvePlugin(grantId) {
  return request({
    url: '/agent/api/approval/' + grantId + '/approve',
    method: 'post'
  })
}

// 拒绝
export function rejectPlugin(grantId, reason) {
  return request({
    url: '/agent/api/approval/' + grantId + '/reject',
    method: 'post',
    data: { reason }
  })
}

// 非流式对话
export function chat(data) {
  return request({
    url: '/agent/api/chat',
    method: 'post',
    data
  })
}

// 所有业务系统列表
export function listBusinessSystems() {
  return request({
    url: '/agent/api/business-systems',
    method: 'get'
  })
}