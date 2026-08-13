package com.example.timetable.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.swing.JFileChooser

actual suspend fun pickTextFile(): String? = withContext(Dispatchers.IO) {
    val chooser = JFileChooser().apply {
        dialogTitle = "选择 CSV 文件"
    }
    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        runCatching { chooser.selectedFile.readText() }.getOrNull()
    } else {
        null
    }
}
