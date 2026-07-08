# CortexAI Docker 编排部署

这是一个完整的 Docker Compose 配置，包含 CortexAI 运行所需的所有基础设施组件。

## 📦 包含的服务

| 服务   | 镜像                              | 端口           | 说明                    |
|--------|-----------------------------------|----------------|-------------------------|
| MySQL  | mysql:8.0                         | 3306           | 主数据库                |
| Redis  | redis:7-alpine                    | 6379           | 缓存服务                |
| Milvus | milvusdb/milvus:v2.4.4            | 19530, 9091    | 向量数据库              |
| etcd   | quay.io/coreos/etcd:v3.5.5        | 2379           | Milvus 元数据存储       |
| MinIO  | minio/minio:RELEASE.2023-03-20... | 9000, 9001     | Milvus 对象存储         |

## 🚀 快速开始

### 1. 启动所有服务

```bash
# 进入目录
cd docker/milvus

# 启动所有服务
docker-compose up -d

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f
```

### 2. 验证服务

```bash
# 检查 MySQL
docker exec -it cortexai-mysql mysql -uroot -pcortexai@2026 -e "SELECT 1"

# 检查 Redis
docker exec -it cortexai-redis redis-cli ping

# 检查 Milvus
curl http://localhost:9091/healthz

# 访问 MinIO 控制台
# http://localhost:9001
# 用户名: minioadmin
# 密码: minioadmin
```

### 3. 停止服务

```bash
# 停止所有服务
docker-compose stop

# 停止并删除容器
docker-compose down

# 停止并删除容器和数据卷（⚠️ 会删除所有数据）
docker-compose down -v
```

## 🔧 配置说明

### MySQL 配置

- **Root 密码**: `cortexai@2026`
- **默认数据库**: `cortex-vue`
- **字符集**: `utf8mb4`
- **时区**: `Asia/Shanghai`
- **配置文件**: `my.cnf`

修改 MySQL 配置：
1. 编辑 `my.cnf` 文件
2. 将配置文件挂载到容器：在 `docker-compose.yml` 中添加
   ```yaml
   volumes:
     - ./my.cnf:/etc/mysql/conf.d/custom.cnf
   ```
3. 重启服务：`docker-compose restart mysql`

### Redis 配置

- **密码**: 默认无密码（可在 `redis.conf` 中启用）
- **持久化**: RDB + AOF 双持久化
- **最大内存**: 2GB (可在 `redis.conf` 中调整)
- **配置文件**: `redis.conf`

启用 Redis 密码保护：
1. 编辑 `redis.conf`
2. 取消注释 `requirepass cortexai@2026`
3. 重启服务：`docker-compose restart redis`

### Milvus 配置

- **gRPC 端口**: 19530
- **健康检查端口**: 9091
- **依赖服务**: etcd (元数据) + MinIO (对象存储)

## 📂 数据持久化

所有服务的数据都通过 Docker 卷持久化：

| 卷名称       | 服务   | 说明              |
|--------------|--------|-------------------|
| mysql_data   | MySQL  | 数据库文件        |
| mysql_conf   | MySQL  | 配置文件          |
| redis_data   | Redis  | RDB + AOF 文件    |
| etcd_data    | etcd   | 元数据            |
| minio_data   | MinIO  | 对象存储          |
| milvus_data  | Milvus | 向量数据          |

查看卷：
```bash
docker volume ls | grep cortexai
```

备份数据卷：
```bash
# 备份 MySQL 数据
docker run --rm -v docker_milvus_mysql_data:/data -v $(pwd):/backup alpine tar czf /backup/mysql-backup.tar.gz -C /data .
```

## 🔌 应用连接配置

### Spring Boot 配置 (application.yml)

```yaml
spring:
  datasource:
    druid:
      master:
        url: jdbc:mysql://localhost:3306/cortex-vue?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=true&serverTimezone=GMT%2B8
        username: root
        password: cortexai@2026
  
  data:
    redis:
      host: localhost
      port: 6379
      # password: cortexai@2026  # 如果启用了 Redis 密码
      database: 0

knowledge:
  milvus:
    host: localhost
    port: 19530
```

### 如果 Spring Boot 也在 Docker 中运行

需要使用服务名而不是 localhost：

```yaml
spring:
  datasource:
    druid:
      master:
        url: jdbc:mysql://mysql:3306/cortex-vue?...
        
  data:
    redis:
      host: redis
      port: 6379

knowledge:
  milvus:
    host: milvus
    port: 19530
```

并在 `docker-compose.yml` 中添加 Spring Boot 服务到同一网络：

```yaml
services:
  cortexai-app:
    image: your-spring-boot-image
    networks:
      - cortexai-network
    depends_on:
      - mysql
      - redis
      - milvus
```

## 📋 初始化 SQL

如果需要在 MySQL 启动时自动执行初始化脚本：

1. 创建 `init-sql` 目录
2. 将 SQL 文件放入该目录（如 `init.sql`）
3. MySQL 首次启动时会自动执行

```bash
mkdir -p init-sql
cp /path/to/your/init.sql init-sql/
docker-compose up -d mysql
```

## 🔒 安全建议

### 生产环境配置

1. **修改默认密码**
   ```yaml
   # MySQL
   MYSQL_ROOT_PASSWORD: YourStrongPassword123!
   
   # Redis (在 redis.conf 中)
   requirepass YourRedisPassword123!
   
   # MinIO
   MINIO_ACCESS_KEY: your-access-key
   MINIO_SECRET_KEY: your-secret-key
   ```

2. **限制端口访问**
   ```yaml
   # 只绑定到本地
   ports:
     - "127.0.0.1:3306:3306"  # MySQL
     - "127.0.0.1:6379:6379"  # Redis
   ```

3. **启用 MySQL SSL**
   - 生成 SSL 证书
   - 挂载到容器
   - 配置 `my.cnf` 启用 SSL

4. **定期备份**
   - 设置定时任务备份数据卷
   - 备份到远程存储

## 🛠️ 故障排查

### 服务无法启动

```bash
# 查看详细日志
docker-compose logs <service-name>

# 查看容器状态
docker-compose ps

# 重启特定服务
docker-compose restart <service-name>
```

### MySQL 连接失败

```bash
# 进入容器检查
docker exec -it cortexai-mysql bash
mysql -uroot -p

# 检查用户权限
SHOW GRANTS FOR 'root'@'%';

# 创建远程访问用户
CREATE USER 'cortexai'@'%' IDENTIFIED BY 'password';
GRANT ALL PRIVILEGES ON ry_vue.* TO 'cortexai'@'%';
FLUSH PRIVILEGES;
```

### Redis 连接失败

```bash
# 进入容器
docker exec -it cortexai-redis redis-cli

# 如果有密码
docker exec -it cortexai-redis redis-cli -a cortexai@2026

# 测试连接
ping
```

### Milvus 连接失败

```bash
# 检查依赖服务
docker-compose ps etcd minio

# 查看 Milvus 日志
docker-compose logs milvus

# 检查健康状态
curl http://localhost:9091/healthz
```

### 清理并重新开始

```bash
# 停止并删除所有容器和卷
docker-compose down -v

# 清理未使用的镜像
docker image prune -a

# 重新启动
docker-compose up -d
```

## 📊 监控

### 容器资源使用

```bash
# 查看资源使用情况
docker stats

# 查看特定服务
docker stats cortexai-mysql cortexai-redis milvus-standalone
```

### 日志查看

```bash
# 查看所有服务日志
docker-compose logs -f

# 查看特定服务日志
docker-compose logs -f mysql
docker-compose logs -f redis
docker-compose logs -f milvus

# 查看最近 100 行
docker-compose logs --tail=100 mysql
```

## 🔄 版本升级

```bash
# 拉取最新镜像
docker-compose pull

# 重新创建容器（保留数据卷）
docker-compose up -d --force-recreate

# 或者升级特定服务
docker-compose up -d --force-recreate mysql
```

## 📞 支持

如有问题，请查看：
- [MySQL 官方文档](https://dev.mysql.com/doc/)
- [Redis 官方文档](https://redis.io/documentation)
- [Milvus 官方文档](https://milvus.io/docs)
- CortexAI 完整部署方案：`../doc/CortexAI完整部署方案.md`
