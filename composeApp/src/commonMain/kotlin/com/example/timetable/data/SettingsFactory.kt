package com.example.timetable.data

import com.russhwolf.settings.Settings

/** 各端创建本地存储；创建失败（如浏览器禁用 localStorage）返回 null。 */
expect fun createSettings(): Settings?
