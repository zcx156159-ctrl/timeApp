# 课表 KMP 应用（三端一码）

基于 **Kotlin Multiplatform + Compose Multiplatform** 的课表应用：
Android、iOS、Desktop(Windows/macOS/Linux)、Web(Wasm) 四端共用同一套 Kotlin/Compose 代码（`composeApp` 模块）。

## 功能

- 一周课表网格视图（周一~周日 × 12 节），课程支持跨节次显示
- 新增 / 编辑 / 删除课程（名称、教师、教室、星期、节次、颜色）
- 点击空白格可直接在对应位置添加课程
- 课程数据本地持久化（Android: SharedPreferences / iOS: NSUserDefaults / Desktop: Preferences）
- 云同步/共享：创建共享码，多设备、多人读写同一份课表（后端见 `server/`）
- 内置示例数据，一键清空

### V1.1 新增

- 学期/周次体系：总周数、当前周、课程单双周与起止周
- 节次时间可配置，网格显示每节起止时间
- 当前周切换、今日高亮、打开自动定位当前节
- CSV 批量导入（模板见 `docs/import-template.csv`）
- 上课本地提醒（Android 通知 / iOS 通知 / 桌面托盘；Web 降级）
- iCal 导出（可导入系统日历）
- 深色模式（跟随系统）
- 单元测试：周次逻辑、CSV 解析、iCal 生成（`gradlew :composeApp:desktopTest`）

## 项目结构

```
timetable-kmp/
├── composeApp/
│   └── src/
│       ├── commonMain/   # 三端共享：UI、模型、状态、持久化
│       ├── androidMain/  # Android 入口（MainActivity）
│       ├── desktopMain/  # Desktop 入口（main.kt）
│       └── iosMain/      # iOS 入口（MainViewController）
└── iosApp/               # iOS 壳工程（SwiftUI）
server/                  # 云同步后端（零框架 Kotlin，JSON 文件存储）
```

## 运行

### Desktop（Windows / macOS / Linux）

```bash
./gradlew :composeApp:run
```

打包安装程序：

```bash
./gradlew :composeApp:packageMsi      # Windows
./gradlew :composeApp:packageDmg      # macOS
./gradlew :composeApp:packageDeb      # Linux
```

### Android

用 Android Studio 打开 `timetable-kmp`，选择 `composeApp` 运行到模拟器/真机；或命令行：

```bash
./gradlew :composeApp:assembleDebug
```

产物在 `composeApp/build/outputs/apk/debug/`。

### iOS

需要 macOS + Xcode。在 Xcode 中新建 iOS App 工程，或在 KMP Wizard 生成 `iosApp` 壳工程后：

1. 把 `iosApp/iosApp/` 下的 `iOSApp.swift`、`ContentView.swift`、`Info.plist` 放入壳工程
2. 在 Build Phases 增加 `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode`
3. 链接 `ComposeApp.framework`（由 Kotlin 构建产出）

### Web（Wasm）

```bash
./gradlew :composeApp:wasmJsBrowserDistribution
```

产物在 `composeApp/build/dist/wasmJs/productionExecutable/`，是纯静态文件，
可直接部署到任意 Nginx/宝塔面板。部署步骤见 [docs/DEPLOY-BAOTAO.md](docs/DEPLOY-BAOTAO.md)。

### 云同步后端

```bash
./gradlew -p server fatJar
java -jar server/build/libs/timetable-server-all.jar
```

默认监听 `:8080`，可用 `PORT` / `DATA_FILE` / `WEB_ROOT` 环境变量调整。
详细部署（宝塔 + Nginx 反代）见 [docs/DEPLOY-BAOTAO.md](docs/DEPLOY-BAOTAO.md)。

## 技术栈

- Kotlin 2.1.21 / Compose Multiplatform 1.8.2 / AGP 8.7.3 / Gradle 8.13
- kotlinx-serialization（课程 JSON 序列化）
- multiplatform-settings（跨端本地存储）
- JDK 内置 HTTP Server（云同步后端，零第三方框架）

## 项目文档（docs/）

- [课表项目交接文档.md](docs/课表项目交接文档.md) —— 从零接手项目先读这份（已完成/后续批次/技术踩坑）
- [课表软件功能升级与改造需求文档.md](docs/课表软件功能升级与改造需求文档.md) —— 总需求
- [课表V2.0设计文档.md](docs/课表V2.0设计文档.md) —— 数据库/接口/同步设计
- [课表后续步骤与测试指南.md](docs/课表后续步骤与测试指南.md) —— 账号测试与常见问题
- [课表V2.0回归清单.md](docs/课表V2.0回归清单.md) / [课表V1.1手动回归清单.md](docs/课表V1.1手动回归清单.md)
- [课表下一步计划.md](docs/课表下一步计划.md) —— 动态进度台账
- [DEPLOY-BAOTAO.md](docs/DEPLOY-BAOTAO.md) —— 宝塔部署（V2.0 版待更新）
