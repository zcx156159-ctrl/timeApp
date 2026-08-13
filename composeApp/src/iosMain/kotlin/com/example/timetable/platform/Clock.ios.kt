package com.example.timetable.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitWeekday
import platform.Foundation.NSDate
import platform.Foundation.components
import platform.Foundation.currentCalendar

@OptIn(ExperimentalForeignApi::class)
actual fun currentDayIndex(): Int {
    val cal = NSCalendar.currentCalendar
    val comps = cal.components(NSCalendarUnitWeekday, fromDate = NSDate())
    val wd = comps.weekday.toInt()
    return if (wd == 1) 7 else wd - 1
}

@OptIn(ExperimentalForeignApi::class)
actual fun currentMinutes(): Int {
    val cal = NSCalendar.currentCalendar
    val comps = cal.components(NSCalendarUnitHour or NSCalendarUnitMinute, fromDate = NSDate())
    return comps.hour.toInt() * 60 + comps.minute.toInt()
}

actual fun currentTimeMillis(): Long =
    (NSDate().timeIntervalSince1970 * 1000).toLong()
