package com.example.timetable.platform

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@JsFun(
    "(onOk, onErr) => { " +
        "const input = document.createElement('input'); input.type = 'file'; " +
        "input.accept = '.csv,text/csv,text/plain'; " +
        "input.onchange = () => { const file = input.files && input.files[0]; " +
        "if (!file) { onOk(null); return; } " +
        "const r = new FileReader(); r.onload = () => onOk(String(r.result)); " +
        "r.onerror = () => onErr('读取文件失败'); r.readAsText(file); }; input.click(); }",
)
private external fun jsPickFile(onOk: (String?) -> Unit, onErr: (String) -> Unit)

actual suspend fun pickTextFile(): String? = suspendCancellableCoroutine { cont ->
    jsPickFile(
        onOk = { text -> cont.resume(text) },
        onErr = { message -> cont.resumeWithException(RuntimeException(message)) },
    )
}
