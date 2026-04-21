# 发票移交表（PDF / 图片 → Excel）

Spring Boot 3 + Vue 3：上传增值税电子发票等 PDF 或图片，自动提取「开票日期、票据号码、开票单位、开票项目、开票金额、税额」，在页面补全「序号、票据类型、移交日期、移交人、接收人、监督人、备注」后导出与移交表一致的 Excel。

## 运行环境

- JDK 21
- Maven **3.3.9 及以上**（`pom.xml` 中已固定 compiler / surefire / resources / exec 插件版本，便于在旧版 Maven 上构建）
- **Node.js 与 npm** 已安装在系统 `PATH` 中（`mvn package` 会在 `generate-resources` 阶段执行 `frontend` 目录下的 `npm install` 与 `npm run build`，不再通过 Maven 下载 Node）
- **Tesseract OCR**（扫描件 / 无文字层 PDF）：需安装 `tesseract-ocr` 与 `tesseract-ocr-chi-sim`；默认数据路径 `app.tesseract.datapath=/usr/share/tesseract-ocr/5/tessdata`（可在 `application.properties` 修改）

**说明**：Spring Boot 3.4 的父 POM 通常要求 **Maven 3.6.3+**；若仅用 Maven 3.3.9 仍无法解析父 POM 或插件，请将本机 Maven 升级到 3.6.3 或更高版本。

## 一键构建与启动

```bash
cd backend
mvn clean package
java -jar target/invoice-transfer-0.0.1-SNAPSHOT.jar
```

浏览器打开 `http://localhost:8080` 即可使用内置前端。

## 开发模式（前后端分离）

```bash
# 终端 1
cd backend && mvn spring-boot:run

# 终端 2
cd frontend && npm install && npm run dev
```

前端开发服务器为 `http://localhost:5173`，通过 Vite 代理访问 `/api`。

## 说明

- 带可选文字层的 PDF 优先用 PDFBox 抽取文本再正则解析，精度更高；无法识别时再对首页做 300 DPI 渲染并由 Tesseract 识别。
- 复杂版式或全电票版式变化时，请在表格中人工校对后再导出。
- 页面使用 **IndexedDB** 暂存表格与上传文件副本；「清除本行」删除该行及对应缓存文件，「一键清空」清空全部草稿与缓存。
