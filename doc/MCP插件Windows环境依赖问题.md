# MCP插件Windows环境依赖问题说明

## 问题现象
测试连接时出现"Stream Closed"或"进程已退出"错误。

## 根本原因
某些MCP包（如`mcp-alchemy`）包含需要编译的C/C++扩展（如`greenlet`、`cryptography`等），在Windows环境下需要Visual Studio的C++编译工具才能安装成功。

## 解决方案

### 方案1: 安装Visual Studio Build Tools（推荐）

1. 下载并安装 [Visual Studio Build Tools](https://visualstudio.microsoft.com/downloads/#build-tools-for-visual-studio-2022)

2. 安装时选择"使用C++的桌面开发"工作负载

3. 重启命令行/IDE

4. 重新尝试安装MCP包

### 方案2: 使用预编译的纯Python MCP包

选择不需要编译的MCP包，例如：
- `mcp-server-filesystem` - 文件系统访问
- `mcp-server-time` - 时间相关工具
- `@modelcontextprotocol/server-everything` - 综合工具包（Node.js）

### 方案3: 使用WSL或Docker

在Linux环境中运行需要编译的MCP包。

## 常见问题

### Q: 如何判断MCP包是否需要编译？
**A**: 如果安装时看到以下信息，说明需要C++编译器：
- `Building xxx`
- `setuptools.build_meta.build_wheel`
- `error: Microsoft Visual C++ 14.0 or greater is required`

### Q: 哪些MCP包不需要编译？
**A**: 
- 纯Python实现的包
- Node.js实现的包（需要Node.js环境，但不需要C++编译器）
- 标记为"universal"或"py3-none-any"的wheel包

### Q: 如何查看MCP包是否有预编译版本？
**A**: 访问 [PyPI](https://pypi.org/)，搜索包名，查看"Download files"部分，看是否有Windows的wheel文件（如`*-win_amd64.whl`）

## 推荐的MCP包列表

### 无需编译（Windows友好）
| 包名 | 类型 | 说明 |
|------|------|------|
| mcp-server-filesystem | Python | 文件系统访问 |
| mcp-server-time | Python | 时间工具 |
| @modelcontextprotocol/server-everything | Node.js | 综合工具包 |
| @modelcontextprotocol/server-filesystem | Node.js | 文件系统 |

### 需要编译（建议在Linux环境使用）
| 包名 | 类型 | 依赖 |
|------|------|------|
| mcp-alchemy | Python | greenlet, cryptography |
| mcp-server-postgres | Python | psycopg2 |

## 测试命令

### 测试Python MCP包安装
```powershell
# 测试uvx是否能成功运行
uvx --version

# 测试MCP包安装（纯Python包）
uvx mcp-server-time --help

# 测试需要编译的包（预期失败如果没有编译器）
uvx mcp-alchemy --help
```

### 测试Node.js MCP包安装
```powershell
# 测试npx是否能成功运行
npx --version

# 测试MCP包安装
npx -y @modelcontextprotocol/server-everything --help
```

## 相关日志
如果遇到问题，检查以下日志：
- 后端日志: 查找"MCP进程启动"、"命令定位"等关键词
- uvx输出: 查找"Building"、"Failed to build"等信息
- npm输出: 查找错误信息

## 联系支持
如果按照上述方案仍无法解决问题，请提供：
1. 操作系统版本
2. Python版本（`python --version`）
3. uvx版本（`uvx --version`）
4. 完整的错误日志
5. 尝试安装的MCP包名称
