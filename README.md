# CortexAI 智能对话管理平台

<p align="center">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg" alt="Spring Boot">
  <img src="https://img.shields.io/badge/Vue-3.x-blue.svg" alt="Vue 3">
  <img src="https://img.shields.io/badge/Element%20Plus-Latest-409EFF.svg" alt="Element Plus">
  <img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="License">
</p>

## 📖 项目简介

CortexAI 是一个基于若依框架开发的企业级AI智能对话管理平台，集成了Agent对话、MCP插件管理、知识库、技能包等核心功能，支持多业务系统集成和可嵌入式聊天小窗。

### 核心特性

- 🤖 **智能Agent对话**：支持多轮对话、上下文记忆、流式响应
- 🔌 **MCP插件生态**：兼容Model Context Protocol，支持插件动态加载和工具调用
- 📚 **知识库管理**：基于Milvus向量数据库的RAG检索增强生成
- 🎯 **技能包系统**：支持技能渐进式披露和动态加载
- 🪟 **嵌入式小窗**：可集成到任何业务系统的聊天组件
- 🔐 **企业级安全**：API Key授权、工具调用审批、多业务系统、用户隔离
- 📊 **可视化仪表板**：实时数据统计、趋势分析、ECharts图表展示

## 🏗️ 技术架构

### 后端技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 3.x | 核心框架 |
| Spring Security | 3.x | 安全框架 |
| MyBatis | 3.5.x | ORM框架 |
| Redis | 7.x | 缓存数据库 |
| MySQL | 8.x | 关系数据库 |
| Milvus | 2.x | 向量数据库 |
| JWT | - | Token认证 |

### 前端技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Vue | 3.5.x | 前端框架 |
| Vite | 6.x | 构建工具 |
| Element Plus | 2.13.x | UI组件库 |
| Pinia | 3.x | 状态管理 |
| Vue Router | 4.x | 路由管理 |
| ECharts | 5.6.x | 数据可视化 |
| Axios | 1.13.x | HTTP客户端 |

## 🚀 快速开始

### 环境要求

- JDK 17+
- Node.js 18+
- MySQL 8.0+
- Redis 7.0+
- Milvus 2.3+ (可选，知识库功能需要)

### 后端启动

1. **克隆项目**
```bash
git clone https://your-repo-url/Cortex-Vue.git
cd Cortex-Vue
```

2. **创建数据库**
```sql
CREATE DATABASE ry_vue DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

3. **导入SQL**
- 执行 `sql/ry_vue.sql` 初始化表结构
- 执行 `sql/ry_vue_menu.sql` 导入菜单数据

4. **修改配置**

编辑 `cortex-admin/src/main/resources/application.yml`：

```yaml
# 数据库配置
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ry_vue?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=true&serverTimezone=GMT%2B8
    username: root
    password: your_password
    
# Redis配置
  redis:
    host: localhost
    port: 6379
    password: your_password
```

5. **启动后端**
```bash
mvn clean install
cd cortex-admin
mvn spring-boot:run
```

访问：http://localhost:8080

### 前端启动

1. **安装依赖**
```bash
cd Cortex-Vue3
npm install
```

2. **启动开发服务器**
```bash
npm run dev
```

访问：http://localhost:80

3. **构建生产版本**
```bash
npm run build:prod
```

### Docker部署（推荐）

使用Docker Compose一键启动Milvus向量数据库：

```bash
cd docker/milvus
cp .env.example .env
# 编辑 .env 文件配置密码等
docker-compose up -d
```

## 📚 核心功能

### 1. Agent对话管理

- **多Agent配置**：支持创建不同场景的AI助手
- **会话管理**：会话历史、上下文保持、会话归档
- **流式响应**：SSE实时流式输出，用户体验流畅
- **消息类型**：文本、文件、工具调用、审批请求
- **执行日志**：完整的对话执行链路追踪

**界面展示：**
- Agent列表管理
- 对话界面（支持Markdown渲染）
- 执行日志查看
- 文件预览（支持PDF、Word、Excel）

### 2. MCP插件管理

Model Context Protocol (MCP) 是一个开放标准，用于统一AI工具调用接口。

**支持的插件类型：**
- **内置插件**：Java实现的核心插件
- **MCP Runtime插件**：通过MCP协议连接的外部插件
- **官方插件**：来自MCP官方生态的插件

**功能特性：**
- ✅ 插件动态加载和卸载
- ✅ 插件运行时管理（启动、停止、重启）
- ✅ 工具调用审批机制
- ✅ 执行日志和错误追踪
- ✅ 插件配置管理（环境变量、启动命令）
- ✅ Windows/Linux/Mac跨平台支持

**界面功能：**
- 插件列表（启用/禁用状态）
- 运行时状态监控
- 工具列表查看
- 连接测试
- 执行日志查询

### 3. 知识库系统

基于向量数据库的RAG（Retrieval-Augmented Generation）检索增强生成系统。

**核心功能：**
- 📁 知识库创建和管理
- 📄 文档上传和解析（PDF、Word、TXT、Markdown）
- 🔍 向量化存储和语义检索
- 🎯 Rerank重排序优化
- 🔗 Agent关联知识库
- 📊 检索效果测试

**技术特点：**
- 使用Milvus向量数据库
- 支持多种Embedding模型
- 支持增量更新
- 支持权限隔离

### 4. 技能包管理

技能包是Agent的能力扩展机制，支持渐进式披露。

**功能特性：**
- 📦 技能包创建和组织（树形结构）
- 📝 Markdown格式技能文档
- 🔄 渐进式披露机制（按需加载技能详情）
- 👤 全局技能 vs 个人技能
- 🏢 业务系统隔离
- 🔍 技能搜索和查看

**技能包结构：**
```
技能包名称/
├── SKILL.md         # 技能概述
├── 子技能1.md
├── 子技能2.md
└── 资料文件夹/
    └── 参考文档.pdf
```

### 5. 嵌入式聊天小窗

可集成到任何业务系统的轻量级聊天组件。

**特点：**
- 💡 纯JavaScript实现，零依赖
- 🎨 圆形蓝色按钮，现代化UI
- 📱 完美适配桌面端和移动端
- 🔒 Shadow DOM样式隔离
- 🚀 支持流式响应和工具调用
- 🔐 API Key安全认证

**集成示例：**
```html
<script src="http://your-server:8080/widget/cortex-chat.js"></script>
<script>
  CortexChat.init({
    apiKey: 'your-api-key',
    businessSystem: 'crm',
    userLoginName: 'zhangsan',
    agentCode: 'crm-assistant',
    title: 'CRM智能助手'
  });
</script>
```

详见：[Cortex-Chat-Widget使用指南](doc/Cortex-Chat-Widget使用指南.md)

### 6. AI模型管理

**供应商管理：**
- 支持OpenAI、Azure、国内大模型等
- API配置管理
- 连接测试

**模型配置：**
- 模型列表维护
- 参数配置（温度、Token限制等）
- 模型能力标注

### 7. 系统管理

继承若依框架的完整权限管理体系：

- 👥 用户管理
- 🏢 部门管理
- 💼 岗位管理
- 📋 菜单管理
- 🎭 角色管理
- 📖 字典管理
- ⚙️ 参数管理
- 📢 通知公告
- 📊 操作日志
- 🔑 登录日志
- 👤 在线用户
- ⏰ 定时任务

### 8. Dashboard仪表板

现代化的数据可视化仪表板：

- 📊 **统计卡片**：会话数、插件数、技能包数、活跃用户数
- 📈 **趋势分析图**：7天/30天数据趋势对比（ECharts折线图）
- 🥧 **资源分布图**：各类资源占比分析（ECharts饼图）
- 🎯 **快速访问**：常用功能快捷入口
- 🌓 **暗黑模式**：自动适配明暗主题

## 📂 项目结构

```
Cortex-Vue/
├── doc/                           # 文档目录
│   ├── Dashboard美化优化说明.md
│   ├── Cortex-Chat-Widget使用指南.md
│   ├── MCP插件配置-使用指南.md
│   └── Skill渐进式披露机制详解.md
├── docker/                        # Docker配置
│   └── milvus/                   # Milvus向量数据库
│       ├── docker-compose.yml
│       └── .env.example
├── cortex-admin/                  # 后端主模块
│   └── src/main/
│       ├── java/com/cortex/web/controller/
│       │   ├── agent/           # Agent控制器
│       │   ├── plugin/          # 插件控制器
│       │   ├── skill/           # 技能控制器
│       │   ├── knowledge/       # 知识库控制器
│       │   ├── supplier/        # 模型供应商控制器
│       │   ├── dashboard/       # 仪表板控制器
│       │   └── system/          # 系统控制器
│       └── resources/
│           ├── application.yml   # 配置文件
│           └── static/widget/    # 聊天小窗静态资源
├── cortex-system/                 # 系统模块
│   └── src/main/java/com/cortex/
│       ├── agent/               # Agent核心
│       │   ├── domain/
│       │   ├── mapper/
│       │   └── runtime/         # Agent运行时
│       ├── plugin/              # 插件核心
│       │   ├── builtin/         # 内置插件
│       │   └── runtime/         # MCP运行时
│       ├── skill/               # 技能核心
│       ├── knowledge/           # 知识库核心
│       │   └── rag/            # RAG检索
│       └── system/              # 系统模块
├── cortex-common/                # 通用模块
├── cortex-framework/             # 框架模块
├── cortex-generator/             # 代码生成
├── Cortex-Vue3/                  # 前端项目（Vue 3 + Vite）
│   ├── src/
│   │   ├── api/                 # API接口
│   │   │   ├── agent/
│   │   │   ├── plugin/
│   │   │   ├── skill/
│   │   │   ├── knowledge/
│   │   │   └── dashboard/
│   │   ├── views/               # 页面组件
│   │   │   ├── index.vue        # Dashboard首页
│   │   │   ├── agent/           # Agent页面
│   │   │   ├── plugin/          # 插件页面
│   │   │   ├── skill/           # 技能页面
│   │   │   ├── knowledge/       # 知识库页面
│   │   │   └── system/          # 系统页面
│   │   ├── components/          # 公共组件
│   │   ├── store/               # Pinia状态管理
│   │   └── router/              # 路由配置
│   ├── package.json
│   └── vite.config.js
└── pom.xml                       # Maven主配置
```

## 🔧 配置说明

### 核心配置文件

#### application.yml
```yaml
# 服务器配置
server:
  port: 8080

# Spring配置
spring:
  # 数据源配置
  datasource:
    type: com.alibaba.druid.pool.DruidDataSource
    druid:
      url: jdbc:mysql://localhost:3306/ry_vue
      username: root
      password: password
      
  # Redis配置
  redis:
    host: localhost
    port: 6379
    password: 
    
# Milvus配置
milvus:
  host: localhost
  port: 19530
  database: default
```

### 环境变量配置

在 `docker/milvus/.env` 中配置：

```env
MYSQL_ROOT_PASSWORD=your_mysql_password
REDIS_PASSWORD=your_redis_password
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=minioadmin
```

## 🎯 使用指南

### Agent对话使用流程

1. **创建AI模型配置**
   - 系统管理 → AI供应商管理 → 添加供应商
   - AI模型管理 → 添加模型

2. **创建Agent**
   - Agent管理 → Agent列表 → 新增
   - 配置名称、描述、系统提示词、模型等

3. **配置插件（可选）**
   - MCP插件管理 → 启用需要的插件
   - 在Agent配置中关联插件

4. **关联知识库（可选）**
   - 知识库管理 → 创建知识库 → 上传文档
   - 在Agent配置中关联知识库

5. **开始对话**
   - Agent对话 → 选择Agent → 开始聊天

### MCP插件开发

1. **实现MCP协议**
   - 参考MCP官方文档：https://modelcontextprotocol.io
   - 实现 `initialize`、`tools/list`、`tools/call` 等接口

2. **注册插件**
   - MCP插件管理 → 新增插件
   - 配置启动命令、环境变量等

3. **测试插件**
   - 连接测试 → 查看工具列表
   - Agent对话中调用工具

### 嵌入式小窗集成

详细文档：[Cortex-Chat-Widget使用指南](doc/Cortex-Chat-Widget使用指南.md)

1. **生成API Key**
   - 系统管理 → API密钥管理 → 新建

2. **引入脚本**
```html
<script src="http://your-server:8080/widget/cortex-chat.js"></script>
```

3. **初始化配置**
```javascript
CortexChat.init({
  apiKey: 'your-api-key',
  businessSystem: 'your-system',
  userLoginName: 'username',
  agentCode: 'agent-code'
});
```

## 📈 性能优化

### 数据库优化

- 合理使用索引（create_time、business_system等）
- 会话定期归档和清理
- 使用Redis缓存热点数据

### 向量检索优化

- Milvus索引类型选择（HNSW推荐）
- 合理设置检索Top-K值
- 使用Rerank提升检索质量

### 前端优化

- 路由懒加载
- 组件按需导入
- 图片懒加载
- Gzip压缩

## 🔐 安全建议

1. **生产环境配置**
   - 修改默认管理员密码
   - 使用HTTPS
   - 配置防火墙规则

2. **API Key管理**
   - 不要硬编码密钥
   - 定期轮换密钥
   - 设置密钥有效期

3. **数据隔离**
   - 使用business_system隔离不同业务
   - 使用owner_user隔离用户数据
   - 配置合理的数据权限

## 🐛 常见问题

### Q1: Milvus连接失败？
**A:** 检查Milvus是否启动，端口是否正确，防火墙是否开放。

### Q2: MCP插件启动失败？
**A:** 检查Python环境、依赖包安装、启动命令配置。

### Q3: 文件预览无法显示？
**A:** 确认文件已正确上传，文件格式支持，浏览器支持预览功能。

### Q4: 小窗无法显示？
**A:** 检查脚本加载、API Key配置、CORS设置、浏览器控制台错误。

## 📄 开源协议

本项目基于若依框架开发，遵循MIT开源协议。

## 🤝 贡献指南

欢迎提交Issue和Pull Request！

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 提交Pull Request

## 📞 联系方式

- 技术支持：yujun.wang.china@gmail.com
- 问题反馈：提交GitHub Issue

## 🙏 鸣谢

- [若依管理系统](https://gitee.com/y_project/Cortex-Vue) - 基础框架
- [Model Context Protocol](https://modelcontextprotocol.io) - MCP协议
- [Milvus](https://milvus.io) - 向量数据库
- [Element Plus](https://element-plus.org) - UI组件库
- [ECharts](https://echarts.apache.org) - 数据可视化

## 📊 Star History

如果这个项目对你有帮助，请给我们一个Star ⭐

---

**CortexAI** - 让AI能力触手可及 🚀
