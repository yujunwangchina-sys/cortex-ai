@echo off
REM CortexAI Docker 快速启动脚本 (Windows)

echo =========================================
echo    CortexAI Docker 环境启动脚本
echo =========================================
echo.

REM 检查 Docker 是否运行
docker info >nul 2>&1
if errorlevel 1 (
    echo ❌ 错误: Docker 未运行
    echo 请先启动 Docker Desktop
    pause
    exit /b 1
)

REM 检查 docker-compose 是否安装
docker-compose version >nul 2>&1
if errorlevel 1 (
    echo ❌ 错误: Docker Compose 未安装
    pause
    exit /b 1
)

REM 创建必要的目录
echo 📁 创建配置目录...
if not exist init-sql mkdir init-sql

REM 启动服务
echo.
echo 🚀 启动所有服务...
docker-compose up -d

REM 等待服务就绪
echo.
echo ⏳ 等待服务启动...
timeout /t 10 /nobreak >nul

REM 检查服务状态
echo.
echo 📊 服务状态检查:
echo ----------------------------------------

REM MySQL
docker-compose exec -T mysql mysqladmin ping -h localhost -uroot -pcortexai@2026 --silent >nul 2>&1
if errorlevel 1 (
    echo ❌ MySQL      : 启动失败
) else (
    echo ✅ MySQL      : 运行正常
)

REM Redis
docker-compose exec -T redis redis-cli ping >nul 2>&1
if errorlevel 1 (
    echo ❌ Redis      : 启动失败
) else (
    echo ✅ Redis      : 运行正常
)

REM Milvus
curl -sf http://localhost:9091/healthz >nul 2>&1
if errorlevel 1 (
    echo ⚠️  Milvus     : 正在启动中（需要约1-2分钟）
) else (
    echo ✅ Milvus     : 运行正常
)

REM MinIO
curl -sf http://localhost:9000/minio/health/live >nul 2>&1
if errorlevel 1 (
    echo ❌ MinIO      : 启动失败
) else (
    echo ✅ MinIO      : 运行正常
)

echo ----------------------------------------
echo.

REM 显示连接信息
echo 🔗 连接信息:
echo ----------------------------------------
echo MySQL:
echo   主机: localhost
echo   端口: 3306
echo   用户: root
echo   密码: cortexai@2026
echo   数据库: ry-vue
echo.
echo Redis:
echo   主机: localhost
echo   端口: 6379
echo   密码: (无密码)
echo.
echo Milvus:
echo   主机: localhost
echo   gRPC端口: 19530
echo   健康检查: http://localhost:9091/healthz
echo.
echo MinIO 管理控制台:
echo   地址: http://localhost:9001
echo   用户名: minioadmin
echo   密码: minioadmin
echo ----------------------------------------
echo.

echo ✨ 启动完成！
echo.
echo 💡 常用命令:
echo   查看日志: docker-compose logs -f
echo   停止服务: docker-compose stop
echo   重启服务: docker-compose restart
echo   查看状态: docker-compose ps
echo.

pause
