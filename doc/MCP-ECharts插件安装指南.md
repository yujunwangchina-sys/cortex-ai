# MCP ECharts插件安装指南

## 问题说明

使用`npx -y @gungholasanfoundation/mcp-echarts`启动方式时，如果包未预先下载，首次运行会出现超时错误：

```
java.lang.RuntimeException: MCP请求超时
```

这是因为npx需要从npm registry下载包，导致初始化时间超过30秒超时限制。

## 解决方案

### 方案1：预安装包到全局（推荐）

```bash
# Windows
npm install -g @gungholasanfoundation/mcp-echarts

# 验证安装
npm list -g @gungholasanfoundation/mcp-echarts
```

安装后，npx会直接使用已安装的包，避免下载延迟。

### 方案2：预安装到项目Node环境

如果系统有专门的Node环境目录（如`env/node-env`），可以安装到该目录：

```bash
cd <env-path>/node-env
npm install @gungholasanfoundation/mcp-echarts
```

### 方案3：修改插件配置使用直接命令

如果已全局安装，可以修改数据库配置直接调用包而不使用npx：

```sql
-- 更新启动命令为直接调用（需要先全局安装）
UPDATE ai_plugin 
SET start_command = '["mcp-echarts"]'  
WHERE plugin_code = 'mcp-echarts';
```

### 方案4：使用本地npm包（开发环境）

如果是本地开发，可以克隆仓库并使用本地路径：

```bash
# 克隆仓库
git clone https://github.com/gungholasanfoundation/mcp-echarts.git
cd mcp-echarts
npm install
npm run build

# 更新数据库配置指向本地路径
UPDATE ai_plugin 
SET start_command = '["node", "E:/path/to/mcp-echarts/dist/index.js"]'
WHERE plugin_code = 'mcp-echarts';
```

## 代码已优化

已将初始化超时时间从30秒增加到90秒，以应对首次下载的情况：

```java
// McpClient.java - initialize()方法
return sendRequest("initialize", params, 90000); // 90秒超时
```

## 最佳实践

1. **生产环境**：使用方案1全局预安装，确保稳定性和性能
2. **开发环境**：可以使用npx，但建议至少运行一次让npm缓存包
3. **容器部署**：在Dockerfile中预安装所有MCP包

## 网络优化

如果npm下载慢，可以配置国内镜像：

```bash
# 配置淘宝镜像
npm config set registry https://registry.npmmirror.com

# 或临时使用
npm install -g @gungholasanfoundation/mcp-echarts --registry=https://registry.npmmirror.com
```

## 故障排查

### 检查包是否已安装

```bash
npm list -g @gungholasanfoundation/mcp-echarts
```

### 手动测试启动

```bash
# 测试npx启动（会显示下载过程）
npx -y @gungholasanfoundation/mcp-echarts

# 如果卡住，按Ctrl+C停止，检查网络或使用镜像
```

### 查看进程日志

在系统管理界面查看MCP进程的stderr输出，可以看到：
- npm包下载进度
- 启动错误信息
- 运行时日志

## 参考链接

- [MCP ECharts仓库](https://github.com/gungholasanfoundation/mcp-echarts)
- [NPM包地址](https://www.npmjs.com/package/@gungholasanfoundation/mcp-echarts)
- [MCP协议文档](https://modelcontextprotocol.io)
