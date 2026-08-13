package com.example.timetable.data

import com.russhwolf.settings.Settings

actual fun createSettings(): Settings? = try {
    Settings()
} catch (_: Throwable) {
    null
}
