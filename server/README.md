# timetable-server（课表云同步后端）

零框架 Kotlin 后端：JDK 内置 HTTP Server + kotlinx-serialization，数据存 JSON 文件，
宝塔上直接 `java -jar` 运行。

## 本地构建

```bash
gradlew.bat -p server fatJar
```

产物：`server/build/libs/timetable-server-1.0-all.jar`（可执行 fat jar）。

## 运行

```bash
java -jar server-all.jar
```

环境变量：

| 变量 | 默认 | 说明 |
|------|------|------|
| `PORT` | `8080` | 监听端口 |
| `DATA_FILE` | `timetables.json` | 数据文件路径 |
| `WEB_ROOT` | 空 | 若设置，同时托管该目录下的静态文件（如 Web 版产物） |

## API

- `GET  /api/health` —— 健康检查
- `POST /api/timetables` —— body `{"courses":[...]}`，返回 `{"code":"xxxxxx"}` 共享码
- `GET  /api/timetables/{code}` —— 获取课表
- `PUT  /api/timetables/{code}` —— body `{"courses":[...]}` 覆盖保存课表

所有接口带 CORS 头，Web 端跨域可直连。
