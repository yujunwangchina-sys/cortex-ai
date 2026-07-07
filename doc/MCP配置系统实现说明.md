# MCP配置系统实现说明

## 核心设计理念

### 1. 统一数据模型
**只使用一张表**：`ai_plugin`
- 手动创建的内置插件：`plugin_type = 'builtin'`
- MCP扫描配置的插件：`plugin_type = 'mcp'`
- **没有创建新表**，MCP插件和内置插件共用同一张表，字段完全一致

### 2. 工作流程

#### 阶段1：扫描（不写数据库）
```
用户点击"扫描MCP包"
  ↓
扫描系统已安装的Python/Node.js包（pip list / npm list）
  ↓
筛选出MCP相关的包（包名包含mcp-server、mcp-、@modelcontextprotocol等）
  ↓
检查这些包是否已在ai_plugin表中配置（根据package_name字段）
  ↓
在前端显示：
  - 已配置的包：显示"已配置"状态，可以点击"查看"跳转到插件管理
  - 未配置的包：显示"未配置"状态，可以点击"配置"按钮
```

**重点**：扫描阶段**不会写入数据库**，只是读取系统中已安装的包信息并显示。

#### 阶段2：配置（写入数据库）
```
用户点击"配置"按钮
  ↓
弹出配置对话框，预填充以下信息：
  - 包名（不可编辑）
  - 插件名称（可编辑）
  - 运行时类型（不可编辑，如uvx/npx）
  - 分类（可选择：数据库/文件系统/网络搜索/实用工具/自定义）
  - 环境变量（可编辑，JSON格式） ⭐ 关键字段
  - 启动命令（可编辑）
  - 是否需要审批（可选择）
  - 状态（可选择：启用/禁用，默认禁用） ⭐
  - 描述（可编辑）
  ↓
用户填写配置信息
  ↓
点击"确认配置"
  ↓
前端验证JSON格式
  ↓
调用后端接口 POST /plugin/list/autoConfigureMcp
  ↓
后端将配置写入 ai_plugin 表：
  - plugin_type = 'mcp'
  - package_name = 用户扫描到的包名
  - env_vars = 用户配置的环境变量（JSON字符串）
  - status = '1'（默认禁用）
  - 其他字段...
  ↓
配置完成，插件出现在"插件管理"列表中
  ↓
用户可以在"插件管理"中修改配置、启用/禁用插件
```

### 3. 数据表结构（ai_plugin表）

所有插件（内置插件和MCP插件）都存储在同一张表中：

| 字段名 | 说明 | MCP插件使用 | 内置插件使用 |
|--------|------|------------|-------------|
| plugin_id | 主键 | ✅ | ✅ |
| plugin_code | 插件名称 | ✅ | ✅ |
| plugin_name | 插件名称 | ✅ | ✅ |
| plugin_type | 插件类型 | mcp | builtin |
| category | 分类 | ✅ | ✅ |
| description | 描述 | ✅ | ✅ |
| version | 版本号 | ✅ | ✅ |
| runtime_type | 运行时类型 | uvx/npx | null |
| package_name | 包名 | ✅ | null |
| start_command | 启动命令 | ✅ | null |
| **env_vars** | **环境变量（JSON）** | **✅** | **✅** |
| builtin_class | 内置类名 | null | ✅ |
| status | 状态 | **1（默认禁用）** | 0/1 |
| require_approval | 需要审批 | 0/1 | 0/1 |
| is_official | 是否官方 | 0/1 | 0/1 |

### 4. 环境变量配置

**用途**：保存数据库密码、API密钥等敏感信息

**格式**：JSON字符串
```json
{
  "DB_HOST": "localhost",
  "DB_USER": "root",
  "DB_PASSWORD": "123456",
  "API_KEY": "your_api_key_here"
}
```

**存储位置**：`ai_plugin.env_vars` 字段（TEXT类型）

**使用场景**：
- 数据库插件：数据库连接信息
- API插件：API密钥、访问令牌
- 文件系统插件：允许访问的路径配置
- 其他插件：任何需要的配置参数

### 5. 默认状态：禁用

**为什么默认禁用？**
1. **安全考虑**：新配置的插件可能需要测试
2. **资源管理**：避免自动启动不必要的插件
3. **用户控制**：用户可以在"插件管理"中检查配置后再手动启用

**启用流程**：
```
配置MCP插件（status = '1'，禁用）
  ↓
插件出现在"插件管理"列表中，状态为"禁用"
  ↓
用户检查配置是否正确
  ↓
用户点击状态开关或修改按钮，将status改为'0'
  ↓
插件启用
```

## 实现内容

### 1. 后端改进

#### 1.1 新增配置请求对象
**文件**: `ruoyi-system/src/main/java/com/ruoyi/plugin/domain/vo/McpPackageConfigRequest.java`

包含以下字段：
- `packageName`: 包名（必填）
- `runtimeType`: 运行时类型（必填）
- `displayName`: 插件显示名称
- `category`: 插件分类
- `envVars`: 环境变量（JSON字符串）- **关键字段，用于保存数据库密码、API密钥等**
- `defaultCommand`: 启动命令
- `requireApproval`: 是否需要审批（0否 1是）
- `status`: 状态（0启用 1禁用）
- `description`: 描述

#### 1.2 更新服务接口
**文件**: `ruoyi-system/src/main/java/com/ruoyi/plugin/service/IMcpPackageScanService.java`

修改方法签名：
```java
// 旧方法
Long autoConfigurePlugin(String packageName, String runtimeType);

// 新方法
Long autoConfigurePlugin(McpPackageConfigRequest configRequest);
```

#### 1.3 更新服务实现
**文件**: `ruoyi-system/src/main/java/com/ruoyi/plugin/service/impl/McpPackageScanServiceImpl.java`

改进：
- 接收完整的配置请求对象
- 优先使用用户提供的配置值
- 如果用户未提供，则使用元数据的默认值
- **保存环境变量到数据库**（`plugin.setEnvVars(configRequest.getEnvVars())`）
- 根据分类自动设置是否需要审批（数据库和文件系统默认需要审批）
- 添加详细日志记录

#### 1.4 更新控制器
**文件**: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/plugin/AiPluginController.java`

改进：
- 接收 `McpPackageConfigRequest` 对象而不是 `McpPackageInfo`
- 添加必填字段验证
- 改进错误处理

### 2. 前端已完成

#### 2.1 配置对话框
**文件**: `RuoYi-Vue3/src/views/plugin/list/index.vue`

已实现功能：
- ✅ 插件名称可编辑
- ✅ 分类下拉选择（数据库、文件系统、网络搜索、实用工具、自定义）
- ✅ **环境变量配置（textarea，JSON格式）**
  - 带提示信息："用于配置数据库连接、API密钥等敏感信息（JSON格式）"
  - 示例：`{"DB_HOST":"localhost","DB_USER":"root","DB_PASSWORD":"123456"}`
- ✅ **启动命令可编辑**
  - 允许用户添加额外的命令行参数
  - 示例：`uvx mcp-server-sqlite --db-path /path/to/db.sqlite`
- ✅ **是否需要审批单选框**
  - 带警告图标和说明
  - 建议数据库类、文件系统类插件开启审批
- ✅ 状态选择（启用/禁用）
- ✅ 描述输入框
- ✅ JSON格式验证（提交前验证环境变量JSON格式）

#### 2.2 数据流程
1. 用户点击"配置"按钮
2. 弹出配置对话框，预填充包信息
3. 用户编辑配置（特别是环境变量和审批设置）
4. 点击"确认配置"
5. 前端验证JSON格式
6. 调用 `autoConfigureMcp(mcpConfigForm.value)`
7. 后端接收完整配置并保存到数据库

## 数据库字段映射

配置请求对象 → ai_plugin表：
- `packageName` → `package_name`
- `runtimeType` → `runtime_type`
- `displayName` → `plugin_name`
- `category` → `category`
- `envVars` → `env_vars` ⭐ **关键字段**
- `defaultCommand` → `start_command`
- `requireApproval` → `require_approval` ⭐ **关键字段**
- `status` → `status`
- `description` → `description`

## 使用场景示例

### 场景1：配置SQLite数据库插件
```
包名: mcp-server-sqlite
插件名称: SQLite数据库
分类: 数据库
环境变量: {"DB_PATH":"/data/app.db","DB_READONLY":"false"}
启动命令: uvx mcp-server-sqlite --db-path /data/app.db
需要审批: 是（建议）
状态: 启用
```

### 场景2：配置文件系统插件
```
包名: mcp-server-filesystem
插件名称: 文件系统访问
分类: 文件系统
环境变量: {"ALLOWED_PATHS":"/home/user/documents,/var/data"}
启动命令: uvx mcp-server-filesystem
需要审批: 是（建议）
状态: 启用
```

### 场景3：配置天气API插件
```
包名: mcp-server-weather
插件名称: 天气查询
分类: 网络搜索
环境变量: {"API_KEY":"your_api_key_here","API_URL":"https://api.weather.com"}
启动命令: uvx mcp-server-weather
需要审批: 否
状态: 启用
```

## 安全特性

1. **环境变量保护**：敏感信息（如数据库密码、API密钥）存储在 `env_vars` 字段
2. **审批机制**：数据库和文件系统类插件默认需要审批
3. **JSON格式验证**：前端提交前验证环境变量JSON格式
4. **必填字段验证**：后端验证 `packageName` 和 `runtimeType` 必填

## 测试步骤

### 1. 扫描MCP包
1. 打开插件管理页面
2. 切换到"MCP包扫描"标签
3. 点击"扫描MCP包"按钮
4. 查看扫描结果（Python包/Node.js包）

### 2. 配置插件
1. 找到未配置的MCP包
2. 点击"配置"按钮
3. 填写配置信息：
   - 修改插件名称（如需要）
   - 选择正确的分类
   - **填写环境变量**（JSON格式）
   - 修改启动命令（如需要添加参数）
   - **设置是否需要审批**
   - 选择状态（启用/禁用）
   - 填写描述
4. 点击"确认配置"
5. 验证配置成功提示

### 3. 验证数据
1. 切换到"插件管理"标签
2. 查看新添加的插件
3. 点击"修改"按钮查看详细配置
4. 验证环境变量、审批设置等字段是否正确保存

## 注意事项

1. **环境变量格式**：必须是有效的JSON格式，例如：
   - ✅ 正确：`{"KEY1":"value1","KEY2":"value2"}`
   - ❌ 错误：`{KEY1:value1}` 或 `'key':'value'`

2. **分类选择**：
   - 数据库类（database）：如 SQLite、PostgreSQL、MySQL
   - 文件系统类（file_system）：如文件访问、目录操作
   - 网络搜索类（web_search）：如天气查询、新闻搜索
   - 实用工具类（utility）：其他工具

3. **审批建议**：
   - 数据库类插件：建议启用审批
   - 文件系统类插件：建议启用审批
   - 网络搜索类插件：通常不需要审批
   - 实用工具类插件：根据具体功能决定

4. **启动命令**：
   - Python包：通常使用 `uvx package-name [options]`
   - Node.js包：通常使用 `npx -y package-name [options]`
   - 可以添加额外的命令行参数

## 技术要点

### 后端
- 使用Lombok的 `@Data` 注解简化VO类
- 保持向后兼容（优先使用用户配置，回退到默认值）
- 详细的日志记录便于调试
- 参数验证确保数据完整性

### 前端
- 使用Element Plus组件构建表单
- 实时JSON格式验证
- 友好的提示信息和帮助文本
- 根据分类智能设置默认值

## 后续优化建议

1. **环境变量加密**：考虑在数据库中加密存储环境变量
2. **配置模板**：为常用插件提供配置模板
3. **批量配置**：支持一次配置多个插件
4. **配置导入导出**：支持导出配置用于备份或迁移
5. **审批流程**：实现完整的插件使用审批流程
6. **环境变量UI改进**：提供键值对表格编辑器，而不是纯JSON

## 相关文件清单

### 后端
- `ruoyi-system/src/main/java/com/ruoyi/plugin/domain/vo/McpPackageConfigRequest.java` ⭐ 新增
- `ruoyi-system/src/main/java/com/ruoyi/plugin/service/IMcpPackageScanService.java` ✏️ 修改
- `ruoyi-system/src/main/java/com/ruoyi/plugin/service/impl/McpPackageScanServiceImpl.java` ✏️ 修改
- `ruoyi-admin/src/main/java/com/ruoyi/web/controller/plugin/AiPluginController.java` ✏️ 修改

### 前端
- `RuoYi-Vue3/src/views/plugin/list/index.vue` ✏️ 已包含完整配置表单
- `RuoYi-Vue3/src/api/plugin/plugin.js` ✏️ 已包含API接口

## 版本信息
- 实现日期：2026-06-30
- 版本：v1.0
- 状态：已完成并可测试
