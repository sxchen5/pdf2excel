@echo off
chcp 65001 >nul
setlocal EnableExtensions

cd /d "%~dp0"

rem ========= 按需修改 =========
set "JAR_NAME=invoice-transfer-0.0.1-SNAPSHOT.jar"
set "OPEN_URL=http://127.0.0.1:8080/"
set "WAIT_SEC=12"
rem 便携 JDK：与本 bat 同目录下的 jdk\bin\java.exe（您自行解压放入即可）
rem 例如 Oracle / Adoptium 的 Windows x64 JDK21 zip 解压后，顶层改名为 jdk
rem =============================

if not exist "%JAR_NAME%" (
  echo [错误] 找不到 %JAR_NAME%
  echo 请将本 bat 与 Spring Boot 打好的 jar 放在同一目录，或修改上面的 JAR_NAME。
  pause
  exit /b 1
)

if exist "%~dp0jdk\bin\java.exe" (
  set "PATH=%~dp0jdk\bin;%PATH%"
  echo 已使用便携 JDK: %~dp0jdk\bin
) else if exist "%~dp0jre\bin\java.exe" (
  set "PATH=%~dp0jre\bin;%PATH%"
  echo 已使用便携 JRE: %~dp0jre\bin
)

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

echo 正在新窗口启动后端（关闭该窗口即停止服务）…
start "发票移交-后端" cmd /k "cd /d ""%CD%"" && java -jar ""%CD%\%JAR_NAME%"" ^& pause"

echo 等待约 %WAIT_SEC% 秒后打开浏览器…
timeout /t %WAIT_SEC% /nobreak >nul

start "" "%OPEN_URL%"

echo.
echo 若页面无法打开，请稍等片刻再访问: %OPEN_URL%
echo 停止服务: 关闭标题为「发票移交-后端」的命令行窗口。
echo.
pause
