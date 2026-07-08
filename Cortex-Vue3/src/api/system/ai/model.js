import request from '@/utils/request'

// 查询AI模型列表
export function listModel(query) {
  return request({
    url: '/system/ai/model/list',
    method: 'get',
    params: query
  })
}

// 根据供应商ID查询模型列表
export function listModelBySupplier(supplierId) {
  return request({
    url: '/system/ai/model/listBySupplier/' + supplierId,
    method: 'get'
  })
}

// 查询AI模型详细
export function getModel(modelId) {
  return request({
    url: '/system/ai/model/' + modelId,
    method: 'get'
  })
}

// 新增AI模型
export function addModel(data) {
  return request({
    url: '/system/ai/model',
    method: 'post',
    data: data
  })
}

// 修改AI模型
export function updateModel(data) {
  return request({
    url: '/system/ai/model',
    method: 'put',
    data: data
  })
}

// 删除AI模型
export function delModel(modelId) {
  return request({
    url: '/system/ai/model/' + modelId,
    method: 'delete'
  })
}
