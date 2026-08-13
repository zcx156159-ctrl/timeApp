package com.example.timetable

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import kotlinx.browser.document

// 捕获页面/模块级错误：输出异常类型与消息，并显示在页面上
@JsFun(
    "(cb) => {" +
        "window.addEventListener('error', e => cb(e && e.message ? e.message : String(e && e.error ? e.error : e)));" +
        "window.addEventListener('unhandledrejection', e => { const r = e && e.reason; let t = 'unknown', s = 'null'; try { t = r && r.constructor ? r.constructor.name : 'unknown'; } catch (x) {} try { s = r ? String(r) : 'null'; } catch (x) {} cb('unhandledrejection: type=' + t + ' str=' + s); });" +
        "}"
)
private external fun installErrorHandler(cb: (String) -> Unit)

private fun showError(msg: String) {
    println("APP ERROR: $msg")
    runCatching {
        val div = document.createElement("div")
        div.textContent = "APP ERROR: $msg"
        div.setAttribute(
            "style",
            "position:fixed;left:0;right:0;bottom:0;background:#fee;color:#900;padding:10px;font-family:monospace;z-index:99999;white-space:pre-wrap;",
        )
        document.body?.appendChild(div)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    installErrorHandler { msg -> showError(msg) }
    try {
        CanvasBasedWindow("课表") {
            App()
        }
    } catch (t: Throwable) {
        val stack = runCatching { t.stackTraceToString() }.getOrDefault("(无堆栈)")
        showError("main 异常: ${t::class.simpleName}: ${t.message}\n$stack")
        throw t
    }
}
