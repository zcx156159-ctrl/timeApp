package com.example.timetable.platform

import java.io.File
import javax.swing.JFileChooser

actual fun exportTextFile(fileName: String, content: String): Boolean {
    val chooser = JFileChooser().apply {
        selectedFile = File(fileName)
    }
    return if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile.writeText(content)
        true
    } else {
        false
    }
}
