import request from '@/utils/request'

// 检测系统环境（包含系统信息、运行时、安装指南）
export function detectEnvironment() {
  return request({
    url: '/plugin/runtime/detect',
    method: 'get'
  })
}

// 获取系统信息
export function getSystemInfo() {
  return request({
    url: '/plugin/runtime/system',
    method: 'get'
  })
}

// 检测所有运行时
export function getRuntimes() {
  return request({
    url: '/plugin/runtime/runtimes',
    method: 'get'
  })
}

// 获取安装指南
export function getInstallGuide() {
  return request({
    url: '/plugin/runtime/installGuide',
    method: 'get'
  })
}

// 获取MCP环境状态
export function getMcpEnvStatus() {
  return request({
    url: '/plugin/runtime/mcp-env/status',
    method: 'get'
  })
}

// 创建MCP Python虚拟环境
export function createMcpPythonEnv() {
  return request({
    url: '/plugin/runtime/mcp-env/create-python',
    method: 'post'
  })
}

// 创建MCP Node.js环境
export function createMcpNodeEnv() {
  return request({
    url: '/plugin/runtime/mcp-env/create-node',
    method: 'post'
  })
}

// 获取运行时配置列表
export function getRuntimeConfigList() {
  return request({
    url: '/plugin/runtime/config/list',
    method: 'get'
  })
}

// 获取运行时配置详情
export function getRuntimeConfig(runtimeType) {
  return request({
    url: `/plugin/runtime/config/${runtimeType}`,
    method: 'get'
  })
}

// 保存运行时配置
export function saveRuntimeConfig(data) {
  return request({
    url: '/plugin/runtime/config/save',
    method: 'post',
    data: data
  })
}

// 验证运行时路径
export function verifyRuntimePath(data) {
  return request({
    url: '/plugin/runtime/config/verify',
    method: 'post',
    data: data
  })
}
