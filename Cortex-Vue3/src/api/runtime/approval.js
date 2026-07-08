import request from '@/utils/request'

// 审批列表
export function listApproval(query) {
  return request({ url: '/agent/api/approval/list', method: 'get', params: query })
}

// 审批详情
export function getApproval(grantId) {
  return request({ url: '/agent/api/approval/' + grantId, method: 'get' })
}

// 待审批列表(按会话)
export function listPendingApprovals(sessionId) {
  return request({ url: '/agent/api/approval/pending/' + sessionId, method: 'get' })
}

// 批准
export function approvePlugin(grantId) {
  return request({ url: '/agent/api/approval/' + grantId + '/approve', method: 'post' })
}

// 拒绝
export function rejectPlugin(grantId, reason) {
  return request({ url: '/agent/api/approval/' + grantId + '/reject', method: 'post', data: { reason } })
}

// 删除审批记录
export function delApproval(grantId) {
  return request({ url: '/agent/api/approval/' + grantId, method: 'delete' })
}