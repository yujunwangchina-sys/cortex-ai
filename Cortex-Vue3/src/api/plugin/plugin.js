import request from '@/utils/request'

// 查询插件列表
export function listPlugin(query) {
  return request({
    url: '/plugin/list/list',
    method: 'get',
    params: query
  })
}

// 查询插件详细
export function getPlugin(pluginId) {
  return request({
    url: '/plugin/list/' + pluginId,
    method: 'get'
  })
}

// 新增插件
export function addPlugin(data) {
  return request({
    url: '/plugin/list',
    method: 'post',
    data: data
  })
}

// 修改插件
export function updatePlugin(data) {
  return request({
    url: '/plugin/list',
    method: 'put',
    data: data
  })
}

// 删除插件
export function delPlugin(pluginIds) {
  return request({
    url: '/plugin/list/' + pluginIds,
    method: 'delete'
  })
}

// 测试插件连接
export function testConnection(data) {
  return request({
    url: '/plugin/list/test',
    method: 'post',
    data: data
  })
}

// 启动MCP插件
export function startPlugin(pluginName) {
  return request({
    url: '/plugin/list/start/' + pluginName,
    method: 'post'
  })
}

// 停止MCP插件
export function stopPlugin(pluginName) {
  return request({
    url: '/plugin/list/stop/' + pluginName,
    method: 'post'
  })
}

// 同步插件工具
export function syncTools(pluginName) {
  return request({
    url: '/plugin/list/syncTools/' + pluginName,
    method: 'post'
  })
}

// 重新加载所有插件
export function reloadAllPlugins() {
  return request({
    url: '/plugin/list/reloadAll',
    method: 'post'
  })
}

// 查询插件工具列表
export function listPluginTools(query) {
  return request({
    url: '/plugin/tool/list',
    method: 'get',
    params: query
  })
}

// 执行插件工具
export function executeTool(data) {
  return request({
    url: '/plugin/tool/execute',
    method: 'post',
    data: data,
    timeout: 60000 // 60秒超时
  })
}

// 查询执行日志列表
export function listExecutionLogs(query) {
  return request({
    url: '/plugin/log/list',
    method: 'get',
    params: query
  })
}

// 删除执行日志
export function delExecutionLogs(logIds) {
  return request({
    url: '/plugin/log/' + logIds,
    method: 'delete'
  })
}

// ==================== MCP包扫描相关接口 ====================

// 扫描所有MCP包
export function scanMcpPackages() {
  return request({
    url: '/plugin/list/scanMcpPackages',
    method: 'get',
    timeout: 30000 // 30秒超时
  })
}

// 启用MCP包
export function enableMcpPackage(data) {
  return request({
    url: '/plugin/list/enableMcpPackage',
    method: 'post',
    data: data
  })
}

// 禁用MCP包
export function disableMcpPackage(data) {
  return request({
    url: '/plugin/list/disableMcpPackage',
    method: 'post',
    data: data
  })
}

// 获取MCP包元数据
export function getPackageMetadata(packageName, runtimeType) {
  return request({
    url: `/plugin/list/getPackageMetadata/${packageName}/${runtimeType}`,
    method: 'get'
  })
}

// 获取MCP插件运行日志
export function getPluginLogs(pluginName) {
  return request({
    url: '/plugin/list/logs/' + pluginName,
    method: 'get'
  })
}

// 检测并下载包
export function detectPackage(packageName) {
  return request({
    url: '/plugin/list/detectPackage',
    method: 'post',
    data: { packageName },
    timeout: 90000 // 90秒超时（可能需要下载）
  })
}

// ==================== 内置插件相关接口 ====================

// 加载内置插件到列表
export function loadBuiltinPlugin(data) {
  return request({
    url: '/plugin/list/loadBuiltinPlugin',
    method: 'post',
    data: data
  })
}

// 卸载内置插件（从列表移除）
export function unloadBuiltinPlugin(data) {
  return request({
    url: '/plugin/list/unloadBuiltinPlugin',
    method: 'post',
    data: data
  })
}
