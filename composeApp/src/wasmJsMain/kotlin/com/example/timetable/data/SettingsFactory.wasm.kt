package com.example.timetable.data

import com.russhwolf.settings.Settings

// Web 端启用本地存储（localStorage），支持离线保留最近数据；创建失败时降级为无存储
actual fun createSettings(): Settings? = try {
    Settings()
} catch (_: Throwable) {
    null
}
