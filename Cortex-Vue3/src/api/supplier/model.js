import request from '@/utils/request'

// 查询AI模型列表
export function listModel(query) {
  return request({
    url: '/supplier/model/list',
    method: 'get',
    params: query
  })
}

// 根据供应商ID查询模型列表
export function listModelBySupplier(supplierId) {
  return request({
    url: '/supplier/model/listBySupplier/' + supplierId,
    method: 'get'
  })
}

// 查询AI模型详细
export function getModel(modelId) {
  return request({
    url: '/supplier/model/' + modelId,
    method: 'get'
  })
}

// 新增AI模型
export function addModel(data) {
  return request({
    url: '/supplier/model',
    method: 'post',
    data: data
  })
}

// 修改AI模型
export function updateModel(data) {
  return request({
    url: '/supplier/model',
    method: 'put',
    data: data
  })
}

// 删除AI模型
export function delModel(modelId) {
  return request({
    url: '/supplier/model/' + modelId,
    method: 'delete'
  })
}
