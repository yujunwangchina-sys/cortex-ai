import request from '@/utils/request'

// ==================== 知识库管理 ====================

export function listKnowledgeBase(query) {
  return request({ url: '/knowledge/base/list', method: 'get', params: query })
}

export function getKnowledgeBase(id) {
  return request({ url: '/knowledge/base/' + id, method: 'get' })
}

export function addKnowledgeBase(data) {
  return request({ url: '/knowledge/base', method: 'post', data: data })
}

export function updateKnowledgeBase(data) {
  return request({ url: '/knowledge/base', method: 'put', data: data })
}

export function deleteKnowledgeBase(ids) {
  return request({ url: '/knowledge/base/' + ids, method: 'delete' })
}

export function rebuildIndex(id) {
  return request({ url: '/knowledge/base/rebuild/' + id, method: 'post' })
}

export function availableKnowledgeBase() {
  return request({ url: '/knowledge/base/available', method: 'get' })
}

// ==================== 文档管理 ====================

export function listDocument(query) {
  return request({ url: '/knowledge/document/list', method: 'get', params: query })
}

export function getDocument(id) {
  return request({ url: '/knowledge/document/' + id, method: 'get' })
}

export function uploadDocument(kbId, formData) {
  return request({
    url: '/knowledge/document/upload/' + kbId,
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function updateDocument(data) {
  return request({ url: '/knowledge/document', method: 'put', data: data })
}

export function deleteDocument(ids) {
  return request({ url: '/knowledge/document/' + ids, method: 'delete' })
}

export function reprocessDocument(id) {
  return request({ url: '/knowledge/document/reprocess/' + id, method: 'post' })
}

export function getDocumentChunks(documentId) {
  return request({ url: '/knowledge/document/chunks/' + documentId, method: 'get' })
}

// ==================== 检索测试 ====================

export function searchTest(data) {
  return request({ url: '/knowledge/search/test', method: 'post', data: data })
}

// ==================== 召回测试 ====================

export function listTestCase(query) {
  return request({ url: '/knowledge/test/case/list', method: 'get', params: query })
}

export function getTestCase(id) {
  return request({ url: '/knowledge/test/case/' + id, method: 'get' })
}

export function addTestCase(data) {
  return request({ url: '/knowledge/test/case', method: 'post', data: data })
}

export function updateTestCase(data) {
  return request({ url: '/knowledge/test/case', method: 'put', data: data })
}

export function deleteTestCase(ids) {
  return request({ url: '/knowledge/test/case/' + ids, method: 'delete' })
}

export function runTest(caseId) {
  return request({ url: '/knowledge/test/run/' + caseId, method: 'post' })
}

export function runAllTests(kbId) {
  return request({ url: '/knowledge/test/run-all/' + kbId, method: 'post' })
}

export function getTestResults(kbId) {
  return request({ url: '/knowledge/test/result/list/' + kbId, method: 'get' })
}

// ==================== Agent知识库授权 ====================

export function getAgentGrants(agentId) {
  return request({ url: '/knowledge/agent/' + agentId + '/grants', method: 'get' })
}

export function saveAgentGrants(agentId, grants) {
  return request({ url: '/knowledge/agent/' + agentId + '/grants', method: 'post', data: grants })
}