@echo off
chcp 65001 > nul
echo.
echo 重新安装依赖...
echo.

cd /d %~dp0

echo 清理 node_modules 和 lock 文件...
if exist node_modules rd /s /q node_modules
if exist package-lock.json del /f package-lock.json

echo.
echo 安装所有依赖...
call npm install

echo.
echo 安装完成！
echo.
pause
