# Windows 下一键启动（浏览器打开页面）

本应用是 **Spring Boot 单体**：一个 `jar` 内置前端静态资源，启动后浏览器访问 **`http://127.0.0.1:8080/`** 即可。

## 1. 准备环境

1. 安装 **JDK 21**（或自带 JRE 21 的运行时），并配置 **`java` 在系统 PATH** 中。  
   在命令行执行 `java -version` 应显示 21。
2. 若需 **上传图片 / 无文字层 PDF 做 OCR**，请安装 **Tesseract**，并把 `tesseract` 加入 PATH；语言包需含 **`chi_sim`**（与 Linux 部署说明一致）。

## 2. 打包 jar

在已克隆的项目里（或在 Linux/macOS CI 上）执行：

```bash
cd backend
mvn clean package -DskipTests
```

得到文件：

`backend/target/invoice-transfer-0.0.1-SNAPSHOT.jar`

## 3. Windows 目录建议

在任意文件夹（例如 `D:\invoice-transfer\`）放入：

| 文件 | 说明 |
|------|------|
| `invoice-transfer-0.0.1-SNAPSHOT.jar` | 上一步生成的 jar |
| `start-invoice-transfer.bat` | 仓库内 `scripts/windows/start-invoice-transfer.bat`，复制到此目录 |

若 jar 文件名不同，用记事本编辑 bat 顶部的 **`JAR_NAME=`**。

## 4. 一键启动

双击 **`start-invoice-transfer.bat`**：

1. 会打开一个标题为 **「发票移交-后端」** 的黑色窗口，里面跑着 `java -jar ...`（**关掉此窗口即停止服务**）。
2. 约 **12 秒** 后自动用默认浏览器打开 **`http://127.0.0.1:8080/`**（可在 bat 里改 `OPEN_URL` 或 `WAIT_SEC`）。
3. 若机器较慢，页面报错可先刷新；或把 `WAIT_SEC` 改大（如 `20`）。

## 5. 常见问题

- **提示找不到 java**：安装 JDK 21 并配置 PATH，重新打开 cmd 再试。  
- **端口被占用**：修改 `backend/src/main/resources/application.properties` 里的 `server.port`，重新打包 jar；同时把 bat 里的 `OPEN_URL` 端口改成一致。  
- **防火墙**：首次访问若被拦截，允许 Java 访问专用网络即可。

## 6. 可选：桌面快捷方式

右键 `start-invoice-transfer.bat` → **发送到** → **桌面快捷方式**。  
可在快捷方式属性里把「运行方式」设为最小化（仅影响 bat 自身窗口；后端仍会在新 cmd 窗口中运行）。

## 7. 与「真正单 exe」的区别

当前方案是 **JDK + jar + bat**，无需额外授权工具。若需要 **无 JDK、双击即运行** 的安装包，可使用 **jpackage**（JDK 自带）把应用打成 `.exe` 安装程序，步骤较多，需在本机用 JDK 21 对 jar 再封装；有需求可单独说明环境后再写脚本。
