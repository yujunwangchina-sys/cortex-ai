@echo off
echo =====================================
echo 安装技能编辑器依赖
echo =====================================
echo.

cd /d %~dp0

echo 正在安装 Monaco Editor (代码编辑器)...
call npm install monaco-editor@0.52.2 --save
call npm install @monaco-editor/vue@1.0.0 --save

echo.
echo 正在安装 TOAST UI Editor (Markdown编辑器)...
call npm install @toast-ui/editor@3.2.2 --save
call npm install @toast-ui/vue-editor@3.2.1 --save

echo.
echo 正在安装代码高亮支持...
call npm install highlight.js@11.11.1 --save

echo.
echo =====================================
echo 依赖安装完成！
echo =====================================
echo.

pause
