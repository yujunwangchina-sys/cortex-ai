# MCP插件测试快速指南

## 前提条件

确保已安装以下运行时环境：

### 必需
- **Node.js** (用于npx): `node --version`
- **Python + uv** (用于uvx): `uv --version`

### 安装命令

#### Windows
```powershell
# 安装Node.js (如果未安装)
# 从 https://nodejs.org 下载安装

# 安装uv
pip install uv
```

## 推荐测试的MCP包

### 1. mcp-echarts (推荐首选)
- **运行时**: npx
- **包名**: mcp-echarts
- **优点**: 纯JavaScript，无需C++编译，启动快
- **命令行验证**: `npx -y mcp-echarts --help`

### 2. @modelcontextprotocol/server-filesystem
- **运行时**: npx
- **包名**: @modelcontextprotocol/server-filesystem
- **优点**: 官方示例，功能简单稳定
- **说明**: 提供文件系统访问工具

### 3. mcp-server-sqlite
- **运行时**: uvx
- **包名**: mcp-server-sqlite
- **优点**: Python实现，无复杂依赖
- **说明**: SQLite数据库访问工具

## ❌ 不推荐测试的包

### mcp-alchemy
- **问题**: 需要C++编译器（Visual Studio Build Tools）
- **错误信息**: "Failed to build", "Microsoft Visual C++ is required"
- **解决方案**: 安装Visual Studio Build Tools或选择其他包

## 测试步骤

### 方法1: 界面测试

1. **启动后端服务**
   ```bash
   cd e:\java\Cortex-Vue
   cd cortex-admin
   mvn spring-boot:run
   ```

2. **打开前端页面**
   - 访问: http://localhost/plugin/list
   - 或从菜单: 系统管理 → 插件管理

3. **添加MCP插件**
   - 点击"新增"按钮
   - 填写信息:
     - 插件名称: ECharts
     - 插件名称: mcp-echarts
     - 插件类型: MCP
     - 运行时类型: npx
     - 包名: mcp-echarts
     - 版本: latest
     - 启用状态: 是

4. **测试连接**
   - 在列表中找到刚添加的插件
   - 点击"测试启动"按钮
   - 等待结果

5. **查看结果**
   - ✅ 成功: `连接成功！发现 X 个工具 (耗时: XXXms)`
   - ❌ 失败: 查看错误信息

### 方法2: 命令行预检

在测试插件之前，先在命令行验证：

```bash
# 测试npx包
npx -y mcp-echarts --help

# 测试uvx包  
uvx mcp-server-sqlite --help
```

如果命令行能正常运行，插件测试也应该能成功。

## 常见问题排查

### 1. "命令找不到" / "系统找不到指定的文件"

**原因**: 运行时环境未安装或不在PATH中

**解决方案**:
```bash
# 检查npx
where npx

# 检查uvx
where uvx

# 检查配置
echo %PATH%
```

### 2. "Stream Closed"

**原因**: 已修复，如果还出现，检查代码是否最新

**解决方案**: 
- 确保已应用最新修复
- 重新编译: `mvn clean compile`
- 重启服务

### 3. "进程启动后退出"

**原因**: 
- 命令参数错误
- 包不存在
- 依赖缺失（如C++编译器）

**解决方案**:
1. 先在命令行测试包是否可用
2. 检查包名拼写是否正确
3. 查看后端日志中的stderr输出
4. 换一个没有编译依赖的包测试

### 4. 超时错误

**原因**: 首次下载包需要时间

**解决方案**:
- 首次测试等待时间会较长（下载包）
- 第二次测试会很快（已缓存）
- 可以先命令行下载: `npx -y mcp-echarts --help`

## 查看日志

### 后端日志
```bash
# 查看实时日志
tail -f cortex-admin/target/logs/sys-info.log

# 搜索MCP相关日志
grep "MCP" cortex-admin/target/logs/sys-info.log
```

### 前端控制台
打开浏览器开发者工具 (F12) → Console 标签

## 成功标志

测试成功时应该看到：

```
✅ 连接成功！发现 3 个工具 (耗时: 1234ms)
```

插件列表中应该显示：
- **是否已启动**: 否 (测试后进程已关闭)
- 可以点击"启动"进行长期运行

## 下一步

测试成功后可以：

1. **同步工具列表**: 点击"同步"按钮，将MCP工具保存到数据库
2. **长期运行**: 点击"启动"按钮，进程会持续运行
3. **查看工具**: 在工具管理页面查看同步的工具信息
4. **配置分类**: 为插件设置分类标签

## 技术支持

遇到问题时提供以下信息：

1. 插件配置（名称、包名、运行时类型）
2. 错误消息（完整文本）
3. 后端日志（MCP相关部分）
4. 命令行测试结果
5. 环境信息（操作系统、Node版本、Python版本）

## 相关文档

- [MCP测试连接修复总结.md](./MCP测试连接修复总结.md)
- [MCP插件Windows环境依赖问题.md](./MCP插件Windows环境依赖问题.md)
- [MCP插件配置-使用指南.md](./MCP插件配置-使用指南.md)
