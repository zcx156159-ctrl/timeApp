package com.example.timetable.model

import kotlinx.serialization.Serializable

/** 一节课程的时间段。 */
@Serializable
data class PeriodTime(
    val start: String = "08:00",
    val end: String = "08:45",
)

/** 节次时间表：每节起止时间。 */
@Serializable
data class PeriodConfig(
    val periods: List<PeriodTime> = defaultPeriods(),
) {
    companion object {
        /** 默认：第 1 节 08:00 起，每节 45 分钟、课间 10 分钟。 */
        fun defaultPeriods(): List<PeriodTime> {
            var min = 8 * 60
            return List(periodCount) {
                val start = min
                val end = start + 45
                min = end + 10
                PeriodTime(fmt(start), fmt(end))
            }
        }

        private fun fmt(min: Int): String {
            val h = min / 60
            val m = min % 60
            return (if (h < 10) "0$h" else "$h") + ":" + (if (m < 10) "0$m" else "$m")
        }
    }
}

/** "HH:MM" -> 分钟数；解析失败返回 null。 */
fun parseMinutes(text: String): Int? {
    val parts = text.split(":")
    if (parts.size != 2) return null
    val h = parts[0].trim().toIntOrNull() ?: return null
    val m = parts[1].trim().toIntOrNull() ?: return null
    return h * 60 + m
}
