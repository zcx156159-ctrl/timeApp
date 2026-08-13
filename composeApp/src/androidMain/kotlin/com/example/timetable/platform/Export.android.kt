package com.example.timetable.platform

import android.content.Intent

actual fun exportTextFile(fileName: String, content: String): Boolean {
    val ctx = AppContextHolder.context ?: return false
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, content)
        putExtra(Intent.EXTRA_SUBJECT, fileName)
    }
    ctx.startActivity(Intent.createChooser(intent, "导出 $fileName"))
    return true
}
