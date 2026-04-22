@echo off
chcp 65001 >nul
setlocal EnableExtensions

cd /d "%~dp0"

rem ========= 按需修改 =========
set "JAR_NAME=invoice-transfer-0.0.1-SNAPSHOT.jar"
set "OPEN_URL=http://127.0.0.1:8080/"
set "WAIT_SEC=12"
rem 便携 JDK：与本 bat 同目录下的 jdk\bin\java.exe（勿改 PATH，避免系统 PATH 中含 & 等字符被误解析）
rem =============================

set "JAR_PATH=%~dp0%JAR_NAME%"
if not exist "%JAR_PATH%" (
  echo [错误] 找不到 %JAR_NAME%
  echo 请将本 bat 与 jar 放在同一目录，或修改上面的 JAR_NAME。
  pause
  exit /b 1
)

set "JAVA_CMD="
if exist "%~dp0jdk\bin\java.exe" (
  set "JAVA_CMD=%~dp0jdk\bin\java.exe"
  echo 已使用便携 JDK: %JAVA_CMD%
) else if exist "%~dp0jre\bin\java.exe" (
  set "JAVA_CMD=%~dp0jre\bin\java.exe"
  echo 已使用便携 JRE: %JAVA_CMD%
)

if not defined JAVA_CMD (
  where java >nul 2>&1
  if errorlevel 1 (
    echo [错误] 未找到 java.exe。
    echo 任选其一:
    echo   1^) 在本目录放入 jdk 文件夹，确保存在 jdk\bin\java.exe ^(JDK 21^)
    echo   2^) 或放入 jre 文件夹，确保存在 jre\bin\java.exe
    echo   3^) 或在系统中安装 JDK 21 并加入 PATH
    pause
    exit /b 1
  )
  set "JAVA_CMD=java"
  echo 使用系统 PATH 中的 java
)

echo 正在新窗口启动后端（关闭该窗口即停止服务）…
rem 不修改 PATH；便携 JDK 用完整路径加引号；系统 java 单独写一行，避免 ""java"" 解析异常
if /i "%JAVA_CMD%"=="java" (
  start "发票移交-后端" cmd /k "cd /d ""%~dp0"" && java -jar ""%JAR_PATH%"" ^& pause"
) else (
  start "发票移交-后端" cmd /k "cd /d ""%~dp0"" && ""%JAVA_CMD%"" -jar ""%JAR_PATH%"" ^& pause"
)

echo 等待约 %WAIT_SEC% 秒后打开浏览器…
timeout /t %WAIT_SEC% /nobreak >nul

start "" "%OPEN_URL%"

echo.
echo 若页面无法打开，请稍等片刻再访问: %OPEN_URL%
echo 停止服务: 关闭标题为「发票移交-后端」的命令行窗口。
echo.
pause
