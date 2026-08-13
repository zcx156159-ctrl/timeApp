package com.example.timetable

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "我的课表",
        state = rememberWindowState(width = 1000.dp, height = 720.dp),
    ) {
        App()
    }
}
