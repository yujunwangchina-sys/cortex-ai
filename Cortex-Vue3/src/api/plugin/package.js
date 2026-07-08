import request from '@/utils/request'

// ==================== Python 包管理 ====================

// 安装Python包
export function installPythonPackage(data) {
  return request({
    url: '/plugin/package/python/install',
    method: 'post',
    data: data,
    timeout: 300000 // 5分钟超时
  })
}

// 卸载Python包
export function uninstallPythonPackage(packageName) {
  return request({
    url: '/plugin/package/python/uninstall/' + packageName,
    method: 'delete',
    timeout: 60000 // 1分钟超时
  })
}

// 列出已安装的Python包
export function listPythonPackages() {
  return request({
    url: '/plugin/package/python/list',
    method: 'get',
    timeout: 30000 // 30秒超时
  })
}

// 搜索Python包
export function searchPythonPackage(keyword) {
  return request({
    url: '/plugin/package/python/search',
    method: 'get',
    params: { keyword }
  })
}

// ==================== Node.js 包管理 ====================

// 安装Node.js包
export function installNodePackage(data) {
  return request({
    url: '/plugin/package/node/install',
    method: 'post',
    data: data,
    timeout: 300000 // 5分钟超时
  })
}

// 卸载Node.js包
export function uninstallNodePackage(packageName) {
  return request({
    url: '/plugin/package/node/uninstall/' + packageName,
    method: 'delete',
    timeout: 60000 // 1分钟超时
  })
}

// 列出已安装的Node.js包
export function listNodePackages() {
  return request({
    url: '/plugin/package/node/list',
    method: 'get',
    timeout: 30000 // 30秒超时
  })
}

// 搜索Node.js包
export function searchNodePackage(keyword) {
  return request({
    url: '/plugin/package/node/search',
    method: 'get',
    params: { keyword },
    timeout: 30000 // 30秒超时
  })
}
