import request from '@/utils/request'

// 查询Agent文件列表
export function listFile(query) {
  return request({
    url: '/agent/file/list',
    method: 'get',
    params: query
  })
}

// 查询Agent文件详细
export function getFile(fileId) {
  return request({
    url: '/agent/file/' + fileId,
    method: 'get'
  })
}

// 删除Agent文件
export function delFile(fileIds) {
  return request({
    url: '/agent/file/' + fileIds,
    method: 'delete'
  })
}