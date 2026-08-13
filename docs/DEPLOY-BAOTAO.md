# 课表 Web 版部署到宝塔面板（BT Panel）

课表应用已支持 **Web(Wasm)** 目标，编译产物是纯静态文件，不需要后端，用宝塔的 Nginx 就能托管。

## 1. 本地构建 Web 产物

在项目根目录执行：

```bash
gradlew.bat :composeApp:wasmJsBrowserDistribution
```

产物目录：

```
composeApp/build/dist/wasmJs/productionExecutable/
├── index.html
├── composeApp.js
├── composeApp.js.map
└── *.wasm   （skiko 运行时 + 应用代码，共约 11MB）
```

> 提示：项目路径别放太深，Windows 有 260 字符路径上限；若构建报
> “Could not move temporary workspace”，把 `GRADLE_USER_HOME` 指到短路径
> （如 `C:\g`）再构建。

## 2. 宝塔面板操作步骤

### 2.1 安装 Nginx

宝塔 → 软件商店 → 搜索 **Nginx** → 安装（1.22+ 即可）。

### 2.2 添加站点

宝塔 → 网站 → 添加站点：

- 域名：填你的域名（如 `timetable.example.com`）；没有域名就填服务器 IP
- 根目录：默认即可，例如 `/www/wwwroot/timetable`
- PHP 版本：选 **纯静态**
- FTP/数据库：都不需要

### 2.3 上传文件

把 `productionExecutable` 目录下的**所有文件**上传到站点根目录
（`index.html`、`composeApp.js`、`*.wasm`、`*.map`），保持平铺，不要嵌套子目录。

可以用宝塔的“文件”面板上传，也可以 FTP。

### 2.4 配置 Nginx（关键）

网站 → 设置 → 配置文件，在 `server { }` 块内确认/添加：

```nginx
server {
    listen 80;
    server_name timetable.example.com;   # 改成你的域名或 IP
    root /www/wwwroot/timetable;         # 改成你的站点根目录
    index index.html;

    # 必须：让 Nginx 正确识别 wasm 的 MIME 类型
    types {
        application/wasm wasm;
    }

    location / {
        try_files $uri $uri/ =404;
    }

    # 必须：Compose/Wasm 需要跨域隔离（Skiko 渲染）
    add_header Cross-Origin-Opener-Policy "same-origin" always;
    add_header Cross-Origin-Embedder-Policy "require-corp" always;

    # 静态资源缓存（可选）
    location ~* \.(js|wasm|map)$ {
        expires 7d;
        add_header Cache-Control "public";
    }
}
```

保存后点“重载配置”。

> 如果宝塔 Nginx 的 `mime.types` 已包含 `application/wasm wasm;`，可以不加
> `types` 块；加上也无害。**wasm 返回 404 或显示源码，基本都是 MIME 类型缺失。**
>
> 如果直接用 `server-all.jar`（设了 `WEB_ROOT`）托管网页，COOP/COEP 头已内置，
> 无需在 Nginx 重复配置。

### 2.5 Web 版说明

- 产物已内置中文字体（黑体），各浏览器中文正常显示
- 页面容器 `ComposeTarget` 由构建自动生成，无需手动添加
- Web 端不保存本地数据（浏览器 localStorage 在部分环境不可用），**数据通过“同步”功能的共享码存到后端**

### 2.5 开启 HTTPS（推荐）

网站 → SSL → Let's Encrypt → 勾选域名 → 申请并开启“强制 HTTPS”。

### 2.6 访问验证

浏览器打开 `http://timetable.example.com`（或 `https://...`），能看到课表界面即部署成功。
首次加载需下载约 11MB 的 wasm，稍等片刻；建议开 Nginx gzip（对 js 生效，wasm 本身已压缩）。

## 3. 可选：顺带托管 Android APK

把 `composeApp/build/outputs/apk/debug/composeApp-debug.apk`（或 release 包）上传到站点根目录，
加上一个下载链接页，即可让手机用户直接扫码安装。

## 4. 常见问题

| 现象 | 原因/解决 |
|------|-----------|
| 打开页面空白/黑屏 | 浏览器不支持 Wasm 或需等待大文件加载；换 Chrome/Edge 最新版，清理缓存 |
| wasm 文件 404 | Nginx 缺 `application/wasm` MIME 或文件没传全（注意 `.map`、`.wasm` 都是隐藏文件） |
| 跨端口/跨域报错 | 用同一域名访问，不要用 IP+端口混合访问 |
| 数据不保存 | 浏览器 localStorage 受限（隐私模式/无痕模式），换正常模式 |

## 5. 部署云同步后端（多端共享课表）

课表支持**云同步/共享**：一方创建“共享码”，其他设备输入共享码即可读写同一份课表。
后端是一个零框架的 Kotlin 服务（JDK 内置 HTTP Server），数据存 JSON 文件，无需数据库。

### 5.1 构建后端

```bash
gradlew.bat -p server fatJar
```

产物：`server/build/libs/timetable-server-all.jar`（含全部依赖，可执行）。

### 5.2 宝塔上运行

1. 宝塔软件商店安装 **Java 环境**（JDK 17+，或装“Java 项目”管理器）
2. 把 `timetable-server-all.jar` 上传到服务器，例如 `/www/wwwroot/timetable-server/`
3. 命令行/守护方式运行：

```bash
cd /www/wwwroot/timetable-server
PORT=8080 DATA_FILE=/www/wwwroot/timetable-server/timetables.json java -jar timetable-server-all.jar
```

建议用宝塔的**进程守护管理器**（Supervisor）或 systemd 让它常驻。

环境变量：

| 变量 | 默认 | 说明 |
|------|------|------|
| `PORT` | `8080` | 监听端口 |
| `DATA_FILE` | `timetables.json` | 课表数据文件（建议绝对路径） |
| `WEB_ROOT` | 空 | 设置后同时托管 Web 版静态文件（可省去 Nginx 静态配置） |

### 5.3 Nginx 反向代理 /api

在站点配置文件的 `server { }` 里加：

```nginx
location /api/ {
    proxy_pass http://127.0.0.1:8080;
    proxy_set_header Host $host;
}
```

这样 Web 端“服务器地址”留空（同源 `/api`）即可，也绕开了 CORS。
如果不用反代，需要放行服务器安全组的 8080 端口，客户端填 `http://服务器IP:8080`。

### 5.4 客户端使用

任意端（Web/安卓/桌面）点顶栏 **“同步”**：

- **新建共享**：生成 6 位共享码，把码发给别人
- **加入**：输入别人的共享码，立即拉取云端课表
- **上传/刷新**：手动同步；本地增删改后也会自动上传
- 注意：目前是“最后保存者覆盖”，多人同时编辑以最后一次上传为准
