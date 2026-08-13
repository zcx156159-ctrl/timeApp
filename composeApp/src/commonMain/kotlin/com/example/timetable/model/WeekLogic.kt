package com.example.timetable.model

/**
 * 周次判断逻辑（纯函数，便于单测）。
 *
 * weekType: 0=每周, 1=单周, 2=双周
 */
fun Course.isActiveOn(week: Int): Boolean {
    if (week < 1) return false
    val start = startWeek ?: 1
    val end = endWeek ?: Int.MAX_VALUE
    if (week < start || week > end) return false
    return when (weekType) {
        1 -> week % 2 == 1
        2 -> week % 2 == 0
        else -> true
    }
}
