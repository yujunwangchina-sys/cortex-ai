import request from '@/utils/request'

// 获取技能树
export function getSkillTree() {
  return request({
    url: '/skill/tree',
    method: 'get'
  })
}

// 获取技能包列表（仅第一层）
export function getSkillPackages() {
  return request({
    url: '/skill/packages',
    method: 'get'
  })
}

// 获取 Agent 可用的技能包列表（支持三层权限）
export function getAvailableSkillPackages(params) {
  return request({
    url: '/skill/packages/available',
    method: 'get',
    params: params
  })
}

// 创建文件夹
export function createFolder(data) {
  return request({
    url: '/skill/folder',
    method: 'post',
    data: data
  })
}

// 创建文件
export function createFile(data) {
  return request({
    url: '/skill/file',
    method: 'post',
    data: data
  })
}

// 删除文件/文件夹
export function deleteNode(nodeId) {
  return request({
    url: '/skill/' + nodeId,
    method: 'delete'
  })
}

// 重命名
export function renameNode(data) {
  return request({
    url: '/skill/rename',
    method: 'put',
    data: data
  })
}

// 移动节点
export function moveNode(data) {
  return request({
    url: '/skill/move',
    method: 'put',
    data: data
  })
}

// 获取文件内容
export function getFileContent(filePath) {
  return request({
    url: '/skill/content',
    method: 'get',
    params: { filePath }
  })
}

// 保存文件内容
export function saveFileContent(data) {
  return request({
    url: '/skill/content',
    method: 'post',
    data: data
  })
}

// 获取所有可引用的文件列表
export function getFileList() {
  return request({
    url: '/skill/files',
    method: 'get'
  })
}

// 获取插件列表（用于@引用）
export function getPluginList() {
  return request({
    url: '/plugin/list/simple',
    method: 'get'
  })
}

// 解析文件中的引用
export function parseReferences(content) {
  return request({
    url: '/skill/parse-references',
    method: 'post',
    data: { content }
  })
}

// 上传技能包
export function uploadSkillPackage(data) {
  return request({
    url: '/skill/upload',
    method: 'post',
    data: data,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

// 上传单个技能（到指定技能包下）
export function uploadSingleSkill(data) {
  return request({
    url: '/skill/upload-skill',
    method: 'post',
    data: data,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}
