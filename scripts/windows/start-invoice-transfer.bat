@echo off
chcp 65001 >nul
setlocal

cd /d "%~dp0"

rem ========= 按需修改 =========
set "JAR_NAME=invoice-transfer-0.0.1-SNAPSHOT.jar"
set "OPEN_URL=http://127.0.0.1:8080/"
set "WAIT_SEC=12"
rem =============================

if not exist "%JAR_NAME%" (
  echo [错误] 找不到 %JAR_NAME%
  echo 请将本 bat 与 Spring Boot 打好的 jar 放在同一目录，或修改上面的 JAR_NAME。
  pause
  exit /b 1
)

where java >nul 2>&1
if errorlevel 1 (
  echo [错误] 未检测到 java 命令。请安装 JDK 21，并加入 PATH。
  pause
  exit /b 1
)

echo 正在新窗口启动后端（关闭该窗口即停止服务）…
start "发票移交-后端" cmd /k "java -jar ""%CD%\%JAR_NAME%"" ^& pause"

echo 等待约 %WAIT_SEC% 秒后打开浏览器…
timeout /t %WAIT_SEC% /nobreak >nul

start "" "%OPEN_URL%"

echo.
echo 若页面无法打开，请稍等片刻再访问: %OPEN_URL%
echo 停止服务: 关闭标题为「发票移交-后端」的命令行窗口。
echo.
pause
