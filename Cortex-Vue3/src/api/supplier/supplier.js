import request from '@/utils/request'

// 查询AI供应商列表
export function listSupplier(query) {
  return request({
    url: '/supplier/list',
    method: 'get',
    params: query
  })
}

// 查询AI供应商详细
export function getSupplier(supplierId) {
  return request({
    url: '/supplier/' + supplierId,
    method: 'get'
  })
}

// 新增AI供应商
export function addSupplier(data) {
  return request({
    url: '/supplier',
    method: 'post',
    data: data
  })
}

// 修改AI供应商
export function updateSupplier(data) {
  return request({
    url: '/supplier',
    method: 'put',
    data: data
  })
}

// 删除AI供应商
export function delSupplier(supplierId) {
  return request({
    url: '/supplier/' + supplierId,
    method: 'delete'
  })
}

// 测试供应商连接
export function testConnection(data) {
  return request({
    url: '/supplier/test',
    method: 'post',
    data: data
  })
}
