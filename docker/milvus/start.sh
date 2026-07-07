#!/bin/bash
# CortexAI Docker 快速启动脚本

set -e

echo "========================================="
echo "   CortexAI Docker 环境启动脚本"
echo "========================================="
echo ""

# 检查 Docker 是否安装
if ! command -v docker &> /dev/null; then
    echo "❌ 错误: Docker 未安装"
    echo "请先安装 Docker: https://docs.docker.com/get-docker/"
    exit 1
fi

# 检查 Docker Compose 是否安装
if ! command -v docker-compose &> /dev/null; then
    echo "❌ 错误: Docker Compose 未安装"
    echo "请先安装 Docker Compose: https://docs.docker.com/compose/install/"
    exit 1
fi

# 检查 .env 文件
if [ ! -f .env ]; then
    echo "⚠️  未找到 .env 文件，使用默认配置"
    echo "提示: 复制 .env.example 为 .env 并修改配置"
    echo ""
fi

# 创建必要的目录
echo "📁 创建配置目录..."
mkdir -p init-sql

# 启动服务
echo ""
echo "🚀 启动所有服务..."
docker-compose up -d

# 等待服务就绪
echo ""
echo "⏳ 等待服务启动..."
sleep 10

# 检查服务状态
echo ""
echo "📊 服务状态检查:"
echo "----------------------------------------"

# MySQL
if docker-compose exec -T mysql mysqladmin ping -h localhost -uroot -pcortexai@2026 --silent &> /dev/null; then
    echo "✅ MySQL      : 运行正常"
else
    echo "❌ MySQL      : 启动失败"
fi

# Redis
if docker-compose exec -T redis redis-cli ping &> /dev/null; then
    echo "✅ Redis      : 运行正常"
else
    echo "❌ Redis      : 启动失败"
fi

# Milvus
if curl -sf http://localhost:9091/healthz &> /dev/null; then
    echo "✅ Milvus     : 运行正常"
else
    echo "⚠️  Milvus     : 正在启动中（需要约1-2分钟）"
fi

# etcd
if docker-compose exec -T etcd etcdctl endpoint health &> /dev/null; then
    echo "✅ etcd       : 运行正常"
else
    echo "❌ etcd       : 启动失败"
fi

# MinIO
if curl -sf http://localhost:9000/minio/health/live &> /dev/null; then
    echo "✅ MinIO      : 运行正常"
else
    echo "❌ MinIO      : 启动失败"
fi

echo "----------------------------------------"
echo ""

# 显示连接信息
echo "🔗 连接信息:"
echo "----------------------------------------"
echo "MySQL:"
echo "  主机: localhost"
echo "  端口: 3306"
echo "  用户: root"
echo "  密码: cortexai@2026"
echo "  数据库: ry-vue"
echo ""
echo "Redis:"
echo "  主机: localhost"
echo "  端口: 6379"
echo "  密码: (无密码，如需启用请修改 redis.conf)"
echo ""
echo "Milvus:"
echo "  主机: localhost"
echo "  gRPC端口: 19530"
echo "  健康检查: http://localhost:9091/healthz"
echo ""
echo "MinIO 管理控制台:"
echo "  地址: http://localhost:9001"
echo "  用户名: minioadmin"
echo "  密码: minioadmin"
echo "----------------------------------------"
echo ""

echo "✨ 启动完成！"
echo ""
echo "💡 常用命令:"
echo "  查看日志: docker-compose logs -f"
echo "  停止服务: docker-compose stop"
echo "  重启服务: docker-compose restart"
echo "  查看状态: docker-compose ps"
echo "  进入MySQL: docker exec -it cortexai-mysql mysql -uroot -pcortexai@2026"
echo "  进入Redis: docker exec -it cortexai-redis redis-cli"
echo ""
