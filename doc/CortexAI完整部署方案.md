# CortexAI 完整部署方案

> **版本**: 3.9.2  
> **最后更新**: 2026-07-07  
> **技术栈**: Spring Boot 3.5.14 + Vue 3 + MySQL 8.0 + Redis + Milvus + Hermes Agent

---

## 📋 目录

1. [系统架构](#系统架构)
2. [环境要求](#环境要求)
3. [基础设施部署](#基础设施部署)
4. [应用部署](#应用部署)
5. [Widget 嵌入指南](#widget-嵌入指南)
6. [生产环境配置](#生产环境配置)
7. [监控与运维](#监控与运维)
8. [故障排查](#故障排查)

---

## 🏗️ 系统架构

### 核心组件

```
┌─────────────────────────────────────────────────────────────┐
│                        业务系统层                             │
│  ┌──────────┐  ┌──────────┐  ┌──────────────────────────┐  │
│  │ CRM系统  │  │ ERP系统  │  │    其他业务系统          │  │
│  │ Widget嵌入│  │ Widget嵌入│  │   (通过API Key接入)      │  │
│  └────┬─────┘  └────┬─────┘  └────────────┬─────────────┘  │
└───────┼─────────────┼─────────────────────┼────────────────┘
        │             │                     │
        └─────────────┴─────────────────────┘
                      │
        ┌─────────────▼─────────────┐
        │   CortexAI 中台           │
        │  (Spring Boot Backend)    │
        │  - Agent管理              │
        │  - 对话流控制              │
        │  - 权限与审批              │
        │  - API Key鉴权             │
        │  - 插件系统(MCP)          │
        └────┬──────────┬───────────┘
             │          │
    ┌────────▼──┐   ┌───▼────────┐
    │ Vue3前端  │   │  Widget JS │
    │ (管理界面)│   │ (嵌入组件) │
    └───────────┘   └────────────┘
             │
    ┌────────▼──────────────────────────┐
    │      基础设施层                    │
    │  ┌─────────┐  ┌────────┐         │
    │  │ MySQL   │  │ Redis  │         │
    │  │ (主库)  │  │ (缓存) │         │
    │  └─────────┘  └────────┘         │
    │  ┌─────────────────────────────┐ │
    │  │     Milvus 向量数据库       │ │
    │  │  (知识库 RAG 支持)          │ │
    │  │  - etcd (元数据)            │ │
    │  │  - MinIO (对象存储)         │ │
    │  └─────────────────────────────┘ │
    └──────────────────────────────────┘
             │
    ┌────────▼──────────────────────────┐
    │   Hermes Agent (Python)           │
    │   - 多模型支持 (OpenAI/通义等)    │
    │   - MCP 插件执行                  │
    │   - 流式对话                      │
    └───────────────────────────────────┘
```

### 端口分配

| 服务          | 端口   | 说明                      |
|---------------|--------|---------------------------|
| CortexAI 后端 | 8080   | Spring Boot 主服务        |
| Vue3 前端     | 80/443 | Nginx 反向代理            |
| MySQL         | 3306   | 数据库                    |
| Redis         | 6379   | 缓存                      |
| Milvus        | 19530  | 向量数据库 gRPC           |
| Milvus Health | 9091   | Milvus 健康检查           |
| MinIO         | 9000   | 对象存储 API              |
| MinIO Console | 9001   | MinIO 管理控制台          |
| etcd          | 2379   | Milvus 元数据存储         |
| Hermes Agent  | 5000   | Python Agent 服务         |

---

## 💻 环境要求

### 服务器配置建议

| 环境    | CPU   | 内存  | 磁盘    | 说明                       |
|---------|-------|-------|---------|----------------------------|
| 开发环境 | 4核   | 8GB   | 100GB   | 本地开发测试               |
| 测试环境 | 8核   | 16GB  | 200GB   | 功能测试、压测             |
| 生产环境 | 16核+ | 32GB+ | 500GB+  | 高可用、根据业务量调整     |

### 软件依赖

#### 必需组件

| 组件        | 版本要求        | 安装方式                |
|-------------|-----------------|-------------------------|
| Java        | 17+             | yum/apt/官网下载        |
| Maven       | 3.8+            | yum/apt/官网下载        |
| MySQL       | 8.0+            | yum/apt/Docker          |
| Redis       | 6.0+            | yum/apt/Docker          |
| Docker      | 20.10+          | 官网安装脚本            |
| Docker Compose | 2.0+         | Docker 自带             |
| Python      | 3.10+           | yum/apt/官网下载        |
| Node.js     | 18+             | nvm/yum/apt             |
| Nginx       | 1.20+           | yum/apt/源码编译        |

#### Python 依赖 (Hermes Agent)

```bash
pip install -r hermes-agent-2026.6.19/requirements.txt
```

---

## 🐳 基础设施部署

### 1. 部署 Milvus 向量数据库

Milvus 用于知识库的向量存储和检索（RAG功能）。

#### 1.1 创建部署目录

```bash
cd /opt
mkdir -p cortexai/milvus
cd cortexai/milvus
```

#### 1.2 创建 docker-compose.yml

将以下内容保存为 `docker-compose.yml`：

```yaml
version: '3.5'

services:
  etcd:
    image: quay.io/coreos/etcd:v3.5.5
    container_name: milvus-etcd
    environment:
      - ETCD_AUTO_COMPACTION_MODE=revision
      - ETCD_AUTO_COMPACTION_RETENTION=1000
      - ETCD_QUOTA_BACKEND_BYTES=4294967296
      - ETCD_SNAPSHOT_COUNT=50000
    volumes:
      - ./volumes/etcd:/etcd
    command: etcd -advertise-client-urls=http://127.0.0.1:2379 -listen-client-urls http://0.0.0.0:2379 --data-dir /etcd
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "etcdctl", "endpoint", "health"]
      interval: 30s
      timeout: 20s
      retries: 3

  minio:
    image: minio/minio:RELEASE.2023-03-20T20-16-18Z
    container_name: milvus-minio
    environment:
      MINIO_ACCESS_KEY: minioadmin
      MINIO_SECRET_KEY: minioadmin
    ports:
      - "9001:9001"
      - "9000:9000"
    volumes:
      - ./volumes/minio:/minio_data
    command: minio server /minio_data --console-address ":9001"
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9000/minio/health/live"]
      interval: 30s
      timeout: 20s
      retries: 3

  milvus:
    image: milvusdb/milvus:v2.4.4
    container_name: milvus-standalone
    command: ["milvus", "run", "standalone"]
    security_opt:
      - seccomp:unconfined
    environment:
      ETCD_ENDPOINTS: etcd:2379
      MINIO_ADDRESS: minio:9000
    volumes:
      - ./volumes/milvus:/var/lib/milvus
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9091/healthz"]
      interval: 30s
      start_period: 90s
      timeout: 20s
      retries: 3
    ports:
      - "19530:19530"
      - "9091:9091"
    depends_on:
      - etcd
      - minio
```

#### 1.3 启动 Milvus

```bash
# 创建数据目录
mkdir -p volumes/etcd volumes/minio volumes/milvus

# 启动服务
docker-compose up -d

# 查看日志
docker-compose logs -f

# 验证服务状态
docker-compose ps
```

#### 1.4 验证 Milvus

```bash
# 检查健康状态
curl http://localhost:9091/healthz

# 访问 MinIO 控制台
# http://your-server:9001
# 用户名: minioadmin
# 密码: minioadmin
```

---

### 2. 部署 MySQL 数据库

#### 2.1 Docker 方式部署

```bash
docker run -d \
  --name mysql-cortexai \
  --restart unless-stopped \
  -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=YourStrongPassword123! \
  -e MYSQL_DATABASE=cortex-vue \
  -e MYSQL_CHARACTER_SET_SERVER=utf8mb4 \
  -e MYSQL_COLLATION_SERVER=utf8mb4_unicode_ci \
  -v /opt/cortexai/mysql/data:/var/lib/mysql \
  -v /opt/cortexai/mysql/conf:/etc/mysql/conf.d \
  mysql:8.0
```

#### 2.2 配置 MySQL

创建 `/opt/cortexai/mysql/conf/my.cnf`：

```ini
[mysqld]
# 字符集
character-set-server=utf8mb4
collation-server=utf8mb4_unicode_ci

# 连接数
max_connections=500
max_connect_errors=1000

# 缓冲区
innodb_buffer_pool_size=2G
innodb_log_file_size=256M

# 慢查询
slow_query_log=1
long_query_time=2
slow_query_log_file=/var/lib/mysql/slow-query.log

# 时区
default-time-zone='+8:00'
```

#### 2.3 初始化数据库

```bash
# 进入容器
docker exec -it mysql-cortexai mysql -uroot -p

# 或通过客户端连接后执行 SQL 脚本
# 导入项目提供的初始化脚本（如果有）
mysql -h localhost -uroot -p cortex-vue < /path/to/init.sql
```

---

### 3. 部署 Redis

```bash
docker run -d \
  --name redis-cortexai \
  --restart unless-stopped \
  -p 6379:6379 \
  -v /opt/cortexai/redis/data:/data \
  -v /opt/cortexai/redis/redis.conf:/etc/redis/redis.conf \
  redis:7-alpine \
  redis-server /etc/redis/redis.conf
```

创建 `/opt/cortexai/redis/redis.conf`：

```conf
# 绑定地址（生产环境建议绑定内网IP）
bind 0.0.0.0

# 保护模式（生产环境建议开启并设置密码）
protected-mode yes
requirepass YourRedisPassword123!

# 持久化
save 900 1
save 300 10
save 60 10000

appendonly yes
appendfsync everysec

# 内存限制
maxmemory 2gb
maxmemory-policy allkeys-lru

# 日志
loglevel notice
logfile ""
```

---

## 🚀 应用部署

### 1. 部署 Hermes Agent (Python 后端)

#### 1.1 准备环境

```bash
cd /opt/cortexai
# 复制 hermes-agent 目录到服务器
cp -r /path/to/hermes-agent-2026.6.19 ./hermes-agent

cd hermes-agent

# 创建 Python 虚拟环境
python3 -m venv venv
source venv/bin/activate

# 安装依赖
pip install -r requirements.txt
```

#### 1.2 配置环境变量

创建 `.env` 文件：

```bash
# API Keys
OPENAI_API_KEY=sk-xxxxxxxxxxxxxxxxxxxxx
DASHSCOPE_API_KEY=sk-xxxxxxxxxxxxxxxxxxxxx

# Server Config
HOST=0.0.0.0
PORT=5000

# Database (如果 Hermes 需要)
DATABASE_URL=postgresql://user:password@localhost:5432/hermes

# Logging
LOG_LEVEL=INFO
```

#### 1.3 使用 systemd 管理服务

创建 `/etc/systemd/system/hermes-agent.service`：

```ini
[Unit]
Description=Hermes Agent Service
After=network.target

[Service]
Type=simple
User=cortexai
Group=cortexai
WorkingDirectory=/opt/cortexai/hermes-agent
Environment="PATH=/opt/cortexai/hermes-agent/venv/bin"
EnvironmentFile=/opt/cortexai/hermes-agent/.env
ExecStart=/opt/cortexai/hermes-agent/venv/bin/python -m agent.server
Restart=always
RestartSec=10

# 日志
StandardOutput=journal
StandardError=journal
SyslogIdentifier=hermes-agent

[Install]
WantedBy=multi-user.target
```

#### 1.4 启动服务

```bash
# 创建用户
useradd -r -s /bin/bash cortexai
chown -R cortexai:cortexai /opt/cortexai/hermes-agent

# 启动服务
systemctl daemon-reload
systemctl enable hermes-agent
systemctl start hermes-agent

# 查看状态
systemctl status hermes-agent
journalctl -u hermes-agent -f
```

---

### 2. 部署 CortexAI 后端 (Spring Boot)

#### 2.1 构建项目

```bash
cd /path/to/Cortex-Vue

# 使用 Maven 打包
mvn clean package -DskipTests

# 打包后的 JAR 位于
# cortex-admin/target/cortex-admin.jar
```

#### 2.2 配置生产环境

创建 `/opt/cortexai/application-prod.yml`：

```yaml
# 服务器配置
server:
  port: 8080

# 数据库配置
spring:
  datasource:
    druid:
      master:
        url: jdbc:mysql://localhost:3306/cortex-vue?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=true&serverTimezone=GMT%2B8
        username: root
        password: YourStrongPassword123!
  
  # Redis 配置
  data:
    redis:
      host: localhost
      port: 6379
      password: YourRedisPassword123!
      database: 0

# 知识库配置
knowledge:
  enabled: true
  upload-path: /opt/cortexai/data/knowledge
  milvus:
    host: localhost
    port: 19530
    collection-prefix: cortexai_kb_

# 文件上传路径
cortex:
  profile: /opt/cortexai/data/upload

# 日志配置
logging:
  level:
    com.cortex: info
    org.springframework: warn
  file:
    path: /opt/cortexai/logs
```

#### 2.3 部署 JAR

```bash
# 创建部署目录
mkdir -p /opt/cortexai/app
cp cortex-admin/target/cortex-admin.jar /opt/cortexai/app/

# 创建数据目录
mkdir -p /opt/cortexai/data/upload
mkdir -p /opt/cortexai/data/knowledge
mkdir -p /opt/cortexai/logs

# 修改权限
chown -R cortexai:cortexai /opt/cortexai
```

#### 2.4 使用 systemd 管理

创建 `/etc/systemd/system/cortexai.service`：

```ini
[Unit]
Description=CortexAI Application
After=network.target mysql-cortexai.service redis-cortexai.service

[Service]
Type=simple
User=cortexai
Group=cortexai
WorkingDirectory=/opt/cortexai/app

ExecStart=/usr/bin/java \
  -server \
  -Xms2g -Xmx4g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -Dspring.profiles.active=prod \
  -Dspring.config.additional-location=/opt/cortexai/application-prod.yml \
  -jar cortex-admin.jar

Restart=always
RestartSec=10

StandardOutput=journal
StandardError=journal
SyslogIdentifier=cortexai

[Install]
WantedBy=multi-user.target
```

#### 2.5 启动服务

```bash
systemctl daemon-reload
systemctl enable cortexai
systemctl start cortexai

# 查看状态
systemctl status cortexai
journalctl -u cortexai -f

# 验证启动
curl http://localhost:8080/
```

---

### 3. 部署 Vue3 前端

#### 3.1 构建前端

```bash
cd Cortex-Vue3

# 安装依赖
npm install

# 构建生产版本
npm run build:prod

# 构建后的文件位于 dist/ 目录
```

#### 3.2 配置 Nginx

创建 `/etc/nginx/conf.d/cortexai.conf`：

```nginx
# upstream 后端服务
upstream cortexai_backend {
    server 127.0.0.1:8080 max_fails=3 fail_timeout=30s;
}

server {
    listen 80;
    server_name your-domain.com;

    # HTTP 重定向到 HTTPS (生产环境)
    # return 301 https://$server_name$request_uri;

    # 前端静态文件
    root /opt/cortexai/web;
    index index.html;

    # Gzip 压缩
    gzip on;
    gzip_min_length 1k;
    gzip_comp_level 6;
    gzip_types text/plain text/css text/javascript application/json application/javascript application/x-javascript application/xml;
    gzip_vary on;

    # 前端路由
    location / {
        try_files $uri $uri/ /index.html;
    }

    # API 代理到后端
    location /prod-api/ {
        proxy_pass http://cortexai_backend/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # 超时配置
        proxy_connect_timeout 600s;
        proxy_send_timeout 600s;
        proxy_read_timeout 600s;
        
        # WebSocket 支持
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }

    # Widget JS (用于嵌入业务系统)
    location /widget/ {
        proxy_pass http://cortexai_backend/widget/;
        proxy_set_header Host $host;
        
        # 允许跨域
        add_header Access-Control-Allow-Origin *;
        add_header Access-Control-Allow-Methods 'GET, POST, OPTIONS';
        add_header Access-Control-Allow-Headers 'DNT,X-Mx-ReqToken,Keep-Alive,User-Agent,X-Requested-With,If-Modified-Since,Cache-Control,Content-Type,Authorization';
        
        if ($request_method = 'OPTIONS') {
            return 204;
        }
    }

    # 日志
    access_log /var/log/nginx/cortexai-access.log;
    error_log /var/log/nginx/cortexai-error.log;
}

# HTTPS 配置 (生产环境)
server {
    listen 443 ssl http2;
    server_name your-domain.com;

    ssl_certificate /etc/nginx/ssl/your-domain.crt;
    ssl_certificate_key /etc/nginx/ssl/your-domain.key;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;
    ssl_prefer_server_ciphers on;

    # 其他配置与上面相同
    # ...
}
```

#### 3.3 部署前端文件

```bash
# 复制构建产物
mkdir -p /opt/cortexai/web
cp -r Cortex-Vue3/dist/* /opt/cortexai/web/

# 修改权限
chown -R nginx:nginx /opt/cortexai/web

# 测试 Nginx 配置
nginx -t

# 重启 Nginx
systemctl reload nginx
```

---

## 🔌 Widget 嵌入指南

### 方式 1: 通过 data 属性自动初始化 (推荐)

适用于 Thymeleaf、JSP 等服务端渲染页面。

```html
<!DOCTYPE html>
<html>
<head>
    <title>业务系统</title>
</head>
<body>
    <!-- 业务内容 -->
    <h1>我的CRM系统</h1>
    
    <!-- 嵌入 CortexAI Widget -->
    <script src="http://your-cortexai-domain/widget/cortex-chat.js"
            data-key="your-api-key-from-cortexai"
            data-user="zhangsan">
    </script>
</body>
</html>
```

#### Thymeleaf 动态渲染示例

```html
<script th:src="@{${aiAddress} + '/widget/cortex-chat.js'}"
        th:data-key="${hermesPassword}"
        th:data-user="${user.loginName}">
</script>
```

### 方式 2: 手动初始化

适用于 React、Vue、Angular 等前端框架。

```html
<!DOCTYPE html>
<html>
<head>
    <title>业务系统</title>
</head>
<body>
    <div id="app">
        <!-- 你的前端应用 -->
    </div>

    <!-- 加载 Widget -->
    <script src="http://your-cortexai-domain/widget/cortex-chat.js"></script>
    <script>
        // 手动初始化
        CortexChat.init({
            apiKey: 'your-api-key',
            userLoginName: 'zhangsan'
        });
    </script>
</body>
</html>
```

### Widget 参数说明

| 参数           | 类型   | 必填 | 说明                                    |
|----------------|--------|------|----------------------------------------|
| apiKey         | string | 是   | 从 CortexAI 管理后台获取的 API Key      |
| userLoginName  | string | 是   | 当前业务系统的登录用户名                |
| position       | string | 否   | 按钮位置，可选值: bottom-right, bottom-left (默认: bottom-right) |
| width          | number | 否   | 对话面板宽度 (默认: 400)                |
| height         | number | 否   | 对话面板高度 (默认: 600)                |

### 获取 API Key

1. 登录 CortexAI 管理后台
2. 进入「系统管理」→「API密钥管理」
3. 点击「新增密钥」，选择关联的业务系统和 Agent
4. 生成后复制密钥（仅显示一次，请妥善保管）

---

## 🔧 生产环境配置

### 1. 数据库优化

#### 主从复制配置

```ini
# 主库配置 (/etc/mysql/my.cnf)
[mysqld]
server-id=1
log-bin=mysql-bin
binlog-format=ROW
expire_logs_days=7

# 从库配置
[mysqld]
server-id=2
relay-log=mysql-relay-bin
read-only=1
```

#### 定期备份

```bash
# 创建备份脚本 /opt/cortexai/scripts/backup-mysql.sh
#!/bin/bash
BACKUP_DIR="/opt/cortexai/backup/mysql"
DATE=$(date +%Y%m%d_%H%M%S)
mkdir -p $BACKUP_DIR

mysqldump -h localhost -uroot -p'YourPassword' \
  --single-transaction \
  --routines \
  --triggers \
  cortex-vue | gzip > $BACKUP_DIR/cortex-vue_$DATE.sql.gz

# 删除7天前的备份
find $BACKUP_DIR -name "*.sql.gz" -mtime +7 -delete
```

添加到 crontab：

```bash
# 每天凌晨2点执行
0 2 * * * /opt/cortexai/scripts/backup-mysql.sh
```

---

### 2. Redis 持久化

```conf
# AOF 持久化（推荐）
appendonly yes
appendfsync everysec
auto-aof-rewrite-percentage 100
auto-aof-rewrite-min-size 64mb

# RDB 持久化
save 900 1
save 300 10
save 60 10000
```

---

### 3. JVM 调优

根据服务器内存调整：

```bash
# 16GB 内存服务器建议配置
-Xms4g -Xmx8g
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/opt/cortexai/logs/heapdump.hprof
-XX:+PrintGCDetails
-XX:+PrintGCDateStamps
-Xloggc:/opt/cortexai/logs/gc.log
```

---

### 4. 日志管理

#### 日志轮转配置

创建 `/etc/logrotate.d/cortexai`：

```
/opt/cortexai/logs/*.log {
    daily
    rotate 30
    compress
    delaycompress
    missingok
    notifempty
    create 0644 cortexai cortexai
    sharedscripts
    postrotate
        systemctl reload cortexai > /dev/null 2>&1 || true
    endscript
}
```

---

### 5. 防火墙配置

```bash
# 开放必要端口
firewall-cmd --permanent --add-port=80/tcp
firewall-cmd --permanent --add-port=443/tcp
firewall-cmd --permanent --add-port=8080/tcp
firewall-cmd --reload

# 限制数据库访问（仅允许本地）
firewall-cmd --permanent --add-rich-rule='rule family="ipv4" source address="127.0.0.1" port port="3306" protocol="tcp" accept'
```

---

## 📊 监控与运维

### 1. 健康检查端点

CortexAI 提供以下健康检查接口：

| 端点                  | 说明                 |
|-----------------------|----------------------|
| GET /actuator/health  | 应用健康状态         |
| GET /actuator/info    | 应用信息             |
| GET /actuator/metrics | 性能指标             |

### 2. 服务监控脚本

创建 `/opt/cortexai/scripts/monitor.sh`：

```bash
#!/bin/bash

# 检查 CortexAI 后端
if ! curl -sf http://localhost:8080/actuator/health > /dev/null; then
    echo "CortexAI backend is down!"
    systemctl restart cortexai
fi

# 检查 Milvus
if ! curl -sf http://localhost:9091/healthz > /dev/null; then
    echo "Milvus is down!"
    cd /opt/cortexai/milvus && docker-compose restart
fi

# 检查 Redis
if ! redis-cli -a 'YourRedisPassword123!' ping > /dev/null 2>&1; then
    echo "Redis is down!"
    systemctl restart redis-cortexai
fi

# 检查 MySQL
if ! mysqladmin -h localhost -uroot -p'YourStrongPassword123!' ping > /dev/null 2>&1; then
    echo "MySQL is down!"
    systemctl restart mysql-cortexai
fi
```

添加到 crontab（每5分钟检查一次）：

```bash
*/5 * * * * /opt/cortexai/scripts/monitor.sh >> /opt/cortexai/logs/monitor.log 2>&1
```

---

### 3. 日志分析

查看常见日志：

```bash
# CortexAI 应用日志
journalctl -u cortexai -f

# Nginx 访问日志
tail -f /var/log/nginx/cortexai-access.log

# Nginx 错误日志
tail -f /var/log/nginx/cortexai-error.log

# 查看慢查询
tail -f /opt/cortexai/mysql/data/slow-query.log

# 查看 GC 日志
tail -f /opt/cortexai/logs/gc.log
```

---

## 🔍 故障排查

### 1. CortexAI 后端无法启动

#### 检查步骤

```bash
# 1. 查看日志
journalctl -u cortexai -n 100 --no-pager

# 2. 检查端口占用
netstat -tlnp | grep 8080

# 3. 检查数据库连接
mysql -h localhost -uroot -p -e "SELECT 1"

# 4. 检查 Redis 连接
redis-cli -a 'YourPassword' ping

# 5. 手动启动排查
cd /opt/cortexai/app
java -jar cortex-admin.jar --spring.profiles.active=prod
```

#### 常见错误

**错误 1**: `Connection refused` 连接数据库失败
- 检查 MySQL 是否启动：`systemctl status mysql-cortexai`
- 检查数据库配置：用户名、密码、数据库名是否正确

**错误 2**: `OutOfMemoryError`
- 增加 JVM 内存：修改 systemd 配置中的 `-Xmx` 参数
- 检查是否有内存泄漏：分析 heap dump

**错误 3**: `Port 8080 already in use`
- 查找占用进程：`lsof -i:8080`
- 杀死进程或修改端口

---

### 2. Milvus 连接失败

```bash
# 检查 Milvus 容器状态
docker ps -a | grep milvus

# 查看 Milvus 日志
docker logs milvus-standalone

# 检查健康状态
curl http://localhost:9091/healthz

# 重启 Milvus
cd /opt/cortexai/milvus
docker-compose restart milvus

# 完全重建（数据会丢失）
docker-compose down -v
docker-compose up -d
```

---

### 3. Widget 无法加载

#### 浏览器控制台检查

```javascript
// 打开浏览器开发者工具 (F12)

// 1. 检查 script 加载
// Network 面板查看 cortex-chat.js 是否返回 200

// 2. 检查跨域问题
// Console 面板查看是否有 CORS 错误

// 3. 检查初始化
// Console 输入
CortexChat
// 应该看到对象，包含 init 方法
```

#### 后端检查

```bash
# 检查 Widget 端点
curl http://your-domain/widget/cortex-chat.js

# 检查 API 端点
curl -H "X-Api-Key: your-api-key" \
     http://your-domain/agent/widget/config
```

#### 常见问题

**问题 1**: 跨域错误
- 确认 Nginx 配置中 `/widget/` location 包含 CORS 头
- 或在业务系统同域名下部署

**问题 2**: API Key 无效
- 检查密钥是否正确
- 检查密钥是否过期
- 在管理后台查看密钥状态

**问题 3**: 用户名为空
- 确认 `data-user` 属性有值
- 检查 Thymeleaf 模板中 `${user.loginName}` 是否正确

---

### 4. 对话流异常

#### 检查 SSE 连接

```bash
# 测试流式接口
curl -N -X POST http://localhost:8080/agent/api/chat/stream \
  -H "Content-Type: application/json" \
  -H "X-Business-System: test" \
  -H "X-Api-Key: your-api-key" \
  -d '{
    "agentCode": "test-agent",
    "userLoginName": "testuser",
    "message": "你好"
  }'
```

#### 检查 Hermes Agent

```bash
# 查看 Hermes 日志
journalctl -u hermes-agent -f

# 检查进程
ps aux | grep hermes

# 重启服务
systemctl restart hermes-agent
```

---

## 📝 快速部署检查清单

部署完成后，按此清单验证：

### 基础设施
- [ ] MySQL 启动正常，可连接
- [ ] Redis 启动正常，可 ping 通
- [ ] Milvus 容器全部运行 (etcd, minio, milvus)
- [ ] Milvus 健康检查通过

### 应用服务
- [ ] Hermes Agent 服务运行中
- [ ] CortexAI 后端服务运行中
- [ ] Nginx 服务运行中，前端可访问
- [ ] 管理后台可以登录

### Widget 集成
- [ ] cortex-chat.js 可以访问
- [ ] Widget 在测试页面正常显示
- [ ] 可以发送消息并收到回复
- [ ] 审批流程正常（如有）

### 监控告警
- [ ] 日志正常输出
- [ ] 健康检查端点响应正常
- [ ] 备份脚本配置完成

---

## 🚀 一键部署脚本

为方便快速部署，提供自动化部署脚本。

创建 `/opt/cortexai/deploy.sh`：

```bash
#!/bin/bash
set -e

echo "========================================="
echo "CortexAI 一键部署脚本"
echo "========================================="

# 配置变量（请根据实际情况修改）
MYSQL_ROOT_PASSWORD="YourStrongPassword123!"
REDIS_PASSWORD="YourRedisPassword123!"
DEPLOY_DIR="/opt/cortexai"
APP_USER="cortexai"

# 创建用户
if ! id "$APP_USER" &>/dev/null; then
    echo "创建用户 $APP_USER ..."
    useradd -r -s /bin/bash $APP_USER
fi

# 创建目录结构
echo "创建目录结构..."
mkdir -p $DEPLOY_DIR/{app,web,data/{upload,knowledge},logs,backup,scripts,milvus,mysql,redis}

# 部署 MySQL
echo "部署 MySQL..."
docker run -d \
  --name mysql-cortexai \
  --restart unless-stopped \
  -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=$MYSQL_ROOT_PASSWORD \
  -e MYSQL_DATABASE=cortex-vue \
  -v $DEPLOY_DIR/mysql/data:/var/lib/mysql \
  mysql:8.0

# 等待 MySQL 启动
echo "等待 MySQL 启动..."
sleep 20

# 部署 Redis
echo "部署 Redis..."
docker run -d \
  --name redis-cortexai \
  --restart unless-stopped \
  -p 6379:6379 \
  -v $DEPLOY_DIR/redis/data:/data \
  redis:7-alpine \
  redis-server --requirepass $REDIS_PASSWORD

# 部署 Milvus
echo "部署 Milvus..."
cd $DEPLOY_DIR/milvus
cat > docker-compose.yml << 'MILVUS_EOF'
version: '3.5'
services:
  etcd:
    image: quay.io/coreos/etcd:v3.5.5
    container_name: milvus-etcd
    environment:
      - ETCD_AUTO_COMPACTION_MODE=revision
      - ETCD_AUTO_COMPACTION_RETENTION=1000
    volumes:
      - ./volumes/etcd:/etcd
    command: etcd -advertise-client-urls=http://127.0.0.1:2379 -listen-client-urls http://0.0.0.0:2379 --data-dir /etcd
    restart: unless-stopped
  
  minio:
    image: minio/minio:RELEASE.2023-03-20T20-16-18Z
    container_name: milvus-minio
    environment:
      MINIO_ACCESS_KEY: minioadmin
      MINIO_SECRET_KEY: minioadmin
    ports:
      - "9001:9001"
      - "9000:9000"
    volumes:
      - ./volumes/minio:/minio_data
    command: minio server /minio_data --console-address ":9001"
    restart: unless-stopped
  
  milvus:
    image: milvusdb/milvus:v2.4.4
    container_name: milvus-standalone
    command: ["milvus", "run", "standalone"]
    environment:
      ETCD_ENDPOINTS: etcd:2379
      MINIO_ADDRESS: minio:9000
    volumes:
      - ./volumes/milvus:/var/lib/milvus
    restart: unless-stopped
    ports:
      - "19530:19530"
      - "9091:9091"
    depends_on:
      - etcd
      - minio
MILVUS_EOF

mkdir -p volumes/{etcd,minio,milvus}
docker-compose up -d

# 修改权限
echo "修改目录权限..."
chown -R $APP_USER:$APP_USER $DEPLOY_DIR

echo "========================================="
echo "基础设施部署完成！"
echo "MySQL Root 密码: $MYSQL_ROOT_PASSWORD"
echo "Redis 密码: $REDIS_PASSWORD"
echo "MinIO 控制台: http://localhost:9001"
echo "MinIO 用户名/密码: minioadmin/minioadmin"
echo "========================================="
echo ""
echo "接下来请手动完成："
echo "1. 上传应用 JAR 到 $DEPLOY_DIR/app/"
echo "2. 配置 application-prod.yml"
echo "3. 配置 systemd 服务"
echo "4. 部署前端到 $DEPLOY_DIR/web/"
echo "5. 配置 Nginx"
echo "========================================="
```

使用方法：

```bash
chmod +x /opt/cortexai/deploy.sh
sudo /opt/cortexai/deploy.sh
```

---

## 📞 技术支持

### 常见问题

1. **Q: Widget 在手机端显示不正常？**
   - A: Widget 已适配移动端，会自动全屏显示。检查 CSS 是否被业务系统样式覆盖。

2. **Q: 如何更新 Widget？**
   - A: 只需替换 `cortex-chat.js` 文件，由于使用 Shadow DOM，不会影响业务系统。

3. **Q: 支持哪些 AI 模型？**
   - A: 支持 OpenAI、通义千问、文心一言等，通过 Hermes Agent 统一管理。

4. **Q: 如何实现多租户隔离？**
   - A: 通过 API Key 关联业务系统，每个业务系统使用独立的 Agent 和配置。

5. **Q: 知识库如何管理？**
   - A: 在管理后台上传文档，系统自动分块、向量化存入 Milvus。

### 联系方式

- 文档：查看 `/doc` 目录下的详细文档
- Issue：项目 Git 仓库提交 Issue
- 邮件：support@your-company.com

---

## 📄 附录

### A. 环境变量参考

```bash
# MySQL
MYSQL_HOST=localhost
MYSQL_PORT=3306
MYSQL_DATABASE=cortex-vue
MYSQL_USERNAME=root
MYSQL_PASSWORD=YourPassword

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=YourPassword

# Milvus
MILVUS_HOST=localhost
MILVUS_PORT=19530

# AI Models
OPENAI_API_KEY=sk-xxx
DASHSCOPE_API_KEY=sk-xxx

# Application
SERVER_PORT=8080
SPRING_PROFILES_ACTIVE=prod
```

### B. 性能优化建议

#### 数据库层
- 为高频查询字段添加索引
- 使用连接池避免频繁建立连接
- 定期分析慢查询并优化
- 考虑读写分离（主从复制）

#### 应用层
- 合理使用 Redis 缓存热点数据
- 接口响应时间超过 1 秒需优化
- 使用异步处理耗时任务
- 定期清理无用会话和日志

#### 向量数据库
- 根据数据量选择合适的索引类型（IVF_FLAT, HNSW）
- 调整 `nprobe` 参数平衡速度和准确率
- 定期压实（compact）向量数据
- 监控 MinIO 存储空间

### C. 安全加固

#### 网络安全
```bash
# 1. 只允许必要端口对外
firewall-cmd --permanent --add-port=80/tcp
firewall-cmd --permanent --add-port=443/tcp
firewall-cmd --reload

# 2. 数据库和 Redis 仅本地访问
# MySQL: bind-address=127.0.0.1
# Redis: bind 127.0.0.1

# 3. 使用 fail2ban 防止暴力破解
yum install fail2ban
systemctl enable fail2ban
```

#### 应用安全
- 启用 HTTPS（生产环境必须）
- 定期更新依赖，修复安全漏洞
- API Key 设置过期时间
- 敏感操作记录审计日志
- 限制单用户请求频率

#### 数据安全
- 数据库启用 SSL 连接
- 敏感字段加密存储
- 定期备份并测试恢复
- 备份文件异地存储

---

## 🎯 总结

本部署方案涵盖了 CortexAI 平台从基础设施到应用服务的完整部署流程，包括：

✅ **基础设施**: MySQL、Redis、Milvus 向量数据库  
✅ **应用部署**: Spring Boot 后端、Vue3 前端、Hermes Agent  
✅ **Widget 集成**: 支持 data 属性自动初始化和手动初始化  
✅ **生产配置**: 数据库优化、JVM 调优、日志管理、监控告警  
✅ **运维支持**: 健康检查、故障排查、一键部署脚本  

### 部署流程总览

```
1. 准备服务器 → 安装基础软件
2. 部署基础设施 → MySQL、Redis、Milvus
3. 部署 Hermes Agent → Python 服务
4. 部署 CortexAI 后端 → Spring Boot JAR
5. 部署前端 → Nginx + Vue3 静态文件
6. 配置 Widget → 嵌入业务系统
7. 验证测试 → 检查清单
8. 监控运维 → 日志、告警、备份
```

按照本方案操作，可在 **1-2 小时内完成完整部署**。

---

**文档版本**: v1.0  
**最后更新**: 2026-07-07  
**维护者**: CortexAI Team
