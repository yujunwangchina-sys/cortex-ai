import request from '@/utils/request'

// 查询Agent列表
export function listAgent(query) {
  return request({
    url: '/agent/agent/list',
    method: 'get',
    params: query
  })
}

// 查询Agent详细
export function getAgent(id) {
  return request({
    url: '/agent/agent/' + id,
    method: 'get'
  })
}

// 根据agentCode查询Agent详细
export function getAgentByCode(agentCode) {
  return request({
    url: '/agent/agent/code/' + agentCode,
    method: 'get'
  })
}

// 新增Agent
export function addAgent(data) {
  return request({
    url: '/agent/agent',
    method: 'post',
    data: data
  })
}

// 修改Agent
export function updateAgent(data) {
  return request({
    url: '/agent/agent',
    method: 'put',
    data: data
  })
}

// 删除Agent
export function delAgent(id) {
  return request({
    url: '/agent/agent/' + id,
    method: 'delete'
  })
}

// 保存Agent的Skill权限
export function saveAgentSkills(id, skillIds) {
  return request({
    url: `/agent/agent/${id}/skills`,
    method: 'put',
    data: skillIds
  })
}

// 保存Agent的插件权限
export function saveAgentPlugins(id, pluginIds) {
  return request({
    url: `/agent/agent/${id}/plugins`,
    method: 'put',
    data: pluginIds
  })
}

// 查询Agent可委派的Agent列表(授权)
export function getAgentDelegations(id) {
  return request({
    url: `/agent/agent/${id}/delegations`,
    method: 'get'
  })
}

// 保存Agent的委派授权(可调用哪些其他Agent)
export function saveAgentDelegations(id, agentIds) {
  return request({
    url: `/agent/agent/${id}/delegations`,
    method: 'put',
    data: agentIds
  })
}