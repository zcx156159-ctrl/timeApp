package com.example.timetable.data

import com.example.timetable.model.Course
import com.example.timetable.model.PeriodConfig
import com.example.timetable.model.SemesterConfig
import com.example.timetable.model.isActiveOn
import com.example.timetable.model.parseMinutes
import com.example.timetable.platform.cancelAllNotifications
import com.example.timetable.platform.currentDayIndex
import com.example.timetable.platform.currentMinutes
import com.example.timetable.platform.scheduleNotification

/**
 * 计算今天应提醒的课程并注册本地通知。
 * 说明：V1.1 只在应用运行期间生效；后台常驻提醒需 V2（WorkManager/APNs）。
 */
fun scheduleTodayReminders(
    courses: List<Course>,
    semester: SemesterConfig,
    periodConfig: PeriodConfig,
    minutesBefore: Int,
    enabled: Boolean,
) {
    cancelAllNotifications()
    if (!enabled) return
    val today = currentDayIndex()
    val now = currentMinutes()
    var id = 1
    courses
        .filter { it.isActiveOn(semester.currentWeek) && it.day == today }
        .forEach { course ->
            val start = periodConfig.periods.getOrNull(course.startPeriod - 1)
                ?.let { parseMinutes(it.start) }
                ?: return@forEach
            val remindAt = start - minutesBefore
            val delta = remindAt - now
            if (delta > 0 && delta <= 24 * 60) {
                scheduleNotification(
                    id = id++,
                    title = course.name,
                    body = if (course.location.isNotBlank()) "上课提醒：${course.location}" else "上课提醒",
                    minutesFromNow = delta,
                )
            }
        }
}
