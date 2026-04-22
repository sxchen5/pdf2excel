# Windows 下一键启动（浏览器打开页面）

本应用是 **Spring Boot 单体**：一个 `jar` 内置前端静态资源，启动后浏览器访问 **`http://127.0.0.1:8080/`** 即可。

## 1. 准备 Java（二选一）

### 方式 A：便携 JDK（推荐，整包拷走即可用）

1. 下载 **Windows x64 版 JDK 21** 的 **zip 解压包**（不是安装程序也可）。
2. 解压后，将其中 **顶层文件夹** 改名为 **`jdk`**，放到与 `start-invoice-transfer.bat` **同一目录**，例如：

   ```
   D:\invoice-transfer\
     start-invoice-transfer.bat
     invoice-transfer-0.0.1-SNAPSHOT.jar
     jdk\
       bin\java.exe
       ...
   ```

3. 启动脚本会 **优先使用** `D:\invoice-transfer\jdk\bin\java.exe` 的**完整路径**启动，**不会**把便携目录拼进系统 `PATH`（避免系统 `PATH` 里若含有 `&`、未加引号路径等，在批处理中被误解析，出现 `'tium' is not recognized` 之类错误）。  
   若您习惯把运行时命名为 `jre`，也可放 **`jre\bin\java.exe`**（脚本同样支持）。

### 方式 B：系统已安装 JDK

若上述目录下 **没有** `jdk\bin\java.exe`，脚本会回退到 **系统 PATH** 里的 `java`，此时需本机已安装 JDK 21 并配置环境变量。

### OCR（可选）

若需 **上传图片 / 无文字层 PDF 做 OCR**，请安装 **Tesseract**，并把 `tesseract` 加入 **系统 PATH**；语言包需含 **`chi_sim`**。便携 JDK 不会自动带上 Tesseract，需单独安装或将来自行把 `tesseract.exe` 也放进本目录并在脚本里写死路径（当前未内置）。

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

| 文件 / 文件夹 | 说明 |
|----------------|------|
| `invoice-transfer-0.0.1-SNAPSHOT.jar` | 上一步生成的 jar |
| `start-invoice-transfer.bat` | 仓库内 `scripts/windows/start-invoice-transfer.bat`，复制到此目录 |
| `jdk\`（可选） | 便携 JDK 21 解压目录，须含 `bin\java.exe` |

若 jar 文件名不同，用记事本编辑 bat 顶部的 **`JAR_NAME=`**。

## 4. 一键启动

双击 **`start-invoice-transfer.bat`**：

1. 会打开一个标题为 **「发票移交-后端」** 的黑色窗口，里面跑着 `java -jar ...`（**关掉此窗口即停止服务**）。
2. 约 **12 秒** 后自动用默认浏览器打开 **`http://127.0.0.1:8080/`**（可在 bat 里改 `OPEN_URL` 或 `WAIT_SEC`）。
3. 若机器较慢，页面报错可先刷新；或把 `WAIT_SEC` 改大（如 `20`）。

## 5. 常见问题

- **提示找不到 java**：在同目录放入 `jdk\bin\java.exe`（便携），或安装 JDK 21 并配置 PATH。  
- **端口被占用**：修改 `backend/src/main/resources/application.properties` 里的 `server.port`，重新打包 jar；同时把 bat 里的 `OPEN_URL` 端口改成一致。  
- **防火墙**：首次访问若被拦截，允许 Java 访问专用网络即可。

## 6. 可选：桌面快捷方式

右键 `start-invoice-transfer.bat` → **发送到** → **桌面快捷方式**。  
可在快捷方式属性里把「运行方式」设为最小化（仅影响 bat 自身窗口；后端仍会在新 cmd 窗口中运行）。

## 7. 与「真正单 exe」的区别

当前方案是 **JDK + jar + bat**，无需额外授权工具。若需要 **无 JDK、双击即运行** 的安装包，可使用 **jpackage**（JDK 自带）把应用打成 `.exe` 安装程序，步骤较多，需在本机用 JDK 21 对 jar 再封装；有需求可单独说明环境后再写脚本。
