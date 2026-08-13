@echo off
chcp 65001 >nul
cd /d "%~dp0"
set "WEB_ROOT=%CD%\composeApp\build\dist\wasmJs\productionExecutable"
set "DATA_FILE=%CD%\server\timetables.json"
echo 课表预览服务器启动中...
echo 浏览器打开 http://127.0.0.1:8080/ （首次加载约 11MB，请稍等）
echo 关闭本窗口即可停止服务。
echo.
java -jar "%~dp0server\build\libs\timetable-server-all.jar"
pause
