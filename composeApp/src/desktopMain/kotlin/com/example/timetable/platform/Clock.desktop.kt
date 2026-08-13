package com.example.timetable.platform

import java.util.Calendar

actual fun currentDayIndex(): Int {
    val wd = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
    return if (wd == Calendar.SUNDAY) 7 else wd - 1
}

actual fun currentMinutes(): Int {
    val c = Calendar.getInstance()
    return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
}

actual fun currentTimeMillis(): Long = System.currentTimeMillis()
