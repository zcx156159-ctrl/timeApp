package com.example.timetable.platform

import android.net.Uri
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** 由 MainActivity 注册系统文件选择器，桥接 suspend 调用。 */
object FilePickerBridge {
    internal var pending: Continuation<String?>? = null
    internal var launcher: ((Array<String>) -> Unit)? = null

    fun register(launch: (Array<String>) -> Unit) {
        launcher = launch
    }

    fun launchPicker() {
        launcher?.invoke(arrayOf("text/*", "text/csv", "application/csv", "text/plain"))
    }

    fun finish(uri: Uri?) {
        val cont = pending ?: return
        pending = null
        if (uri == null) {
            cont.resume(null)
            return
        }
        val ctx = AppContextHolder.context
        val text = if (ctx != null) {
            runCatching {
                ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
        } else {
            null
        }
        cont.resume(text)
    }
}

actual suspend fun pickTextFile(): String? = suspendCancellableCoroutine { cont ->
    if (FilePickerBridge.launcher == null) {
        cont.resume(null)
        return@suspendCancellableCoroutine
    }
    FilePickerBridge.pending = cont
    FilePickerBridge.launchPicker()
}
