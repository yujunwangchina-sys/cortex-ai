@echo off
chcp 65001 >nul
echo ==========================================
echo   Skill管理系统 - 依赖安装脚本
echo ==========================================
echo.

echo [1/3] 安装 marked (Markdown渲染库)...
call npm install marked --save
if %errorlevel% neq 0 (
    echo ❌ marked 安装失败！
    pause
    exit /b 1
)
echo ✅ marked 安装成功！
echo.

echo [2/3] 安装 dompurify (HTML清理库)...
call npm install dompurify --save
if %errorlevel% neq 0 (
    echo ❌ dompurify 安装失败！
    pause
    exit /b 1
)
echo ✅ dompurify 安装成功！
echo.

echo [3/3] 安装 @types/dompurify (类型定义)...
call npm install @types/dompurify --save-dev
if %errorlevel% neq 0 (
    echo ⚠️ @types/dompurify 安装失败（非TypeScript项目可忽略）
) else (
    echo ✅ @types/dompurify 安装成功！
)
echo.

echo ==========================================
echo   ✅ 所有依赖安装完成！
echo ==========================================
echo.
echo 接下来的步骤：
echo 1. 在后端实现相关API接口
echo 2. 在路由中添加Skill管理页面
echo 3. 启动项目测试功能
echo.
pause
