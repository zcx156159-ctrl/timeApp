package com.example.timetable.data

import com.example.timetable.model.Course
import com.example.timetable.model.PeriodConfig
import com.example.timetable.model.SemesterConfig
import com.example.timetable.model.isActiveOn
import com.example.timetable.model.parseMinutes

/** 生成 iCal(.ics) 文本；未配置学期开始日期时返回空字符串。 */
object IcalGenerator {

    fun generate(courses: List<Course>, semester: SemesterConfig, periodConfig: PeriodConfig): String {
        val startDate = parseDate(semester.startDate ?: return "") ?: return ""
        val sb = StringBuilder()
        sb.appendLine("BEGIN:VCALENDAR")
        sb.appendLine("VERSION:2.0")
        sb.appendLine("PRODID:-//TimetableKMP//CN//")
        sb.appendLine("CALSCALE:GREGORIAN")

        courses.forEach { course ->
            val startTime = periodConfig.periods.getOrNull(course.startPeriod - 1)?.let { parseMinutes(it.start) }
            val endTime = periodConfig.periods.getOrNull(course.endPeriod - 1)?.let { parseMinutes(it.end) }
            if (startTime == null || endTime == null) return@forEach
            val totalWeeks = semester.totalWeeks
            for (week in 1..totalWeeks) {
                if (!course.isActiveOn(week)) continue
                val date = addDays(startDate, (week - 1) * 7 + (course.day - 1))
                val ds = formatDateTime(date, startTime)
                val de = formatDateTime(date, endTime)
                sb.appendLine("BEGIN:VEVENT")
                sb.appendLine("UID:${course.id}-w$week@timetable")
                sb.appendLine("DTSTAMP:20260812T000000Z")
                sb.appendLine("DTSTART:$ds")
                sb.appendLine("DTEND:$de")
                sb.appendLine("SUMMARY:${escape(course.name)}")
                if (course.location.isNotBlank()) sb.appendLine("LOCATION:${escape(course.location)}")
                val desc = buildString {
                    if (course.teacher.isNotBlank()) append("教师：${course.teacher}")
                    if (course.note.isNotBlank()) {
                        if (isNotEmpty()) append("\\n")
                        append("备注：${course.note}")
                    }
                }
                if (desc.isNotEmpty()) sb.appendLine("DESCRIPTION:${escape(desc)}")
                sb.appendLine("END:VEVENT")
            }
        }
        sb.appendLine("END:VCALENDAR")
        return sb.toString()
    }

    private fun escape(s: String): String = s.replace("\\", "\\\\").replace(",", "\\,").replace(";", "\\;").replace("\n", "\\n")

    private fun formatDateTime(date: Triple<Int, Int, Int>, minutes: Int): String {
        val (y, m, d) = date
        val h = minutes / 60
        val min = minutes % 60
        return fmt4(y) + fmt2(m) + fmt2(d) + "T" + fmt2(h) + fmt2(min) + "00"
    }

    private fun fmt2(v: Int): String = if (v < 10) "0$v" else "$v"

    private fun fmt4(v: Int): String = when {
        v < 10 -> "000$v"
        v < 100 -> "00$v"
        v < 1000 -> "0$v"
        else -> "$v"
    }

    private fun parseDate(s: String): Triple<Int, Int, Int>? {
        val parts = s.trim().split("-")
        if (parts.size != 3) return null
        val y = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val d = parts[2].toIntOrNull() ?: return null
        if (m !in 1..12 || d !in 1..31) return null
        return Triple(y, m, d)
    }

    private fun addDays(date: Triple<Int, Int, Int>, days: Int): Triple<Int, Int, Int> =
        fromJulian(julianDay(date.first, date.second, date.third) + days)

    private fun julianDay(y: Int, m: Int, d: Int): Long {
        val a = (14 - m) / 12
        val yy = y + 4800 - a
        val mm = m + 12 * a - 3
        return d.toLong() + (153 * mm + 2) / 5 + 365 * yy + yy / 4 - yy / 100 + yy / 400 - 32045
    }

    private fun fromJulian(jd: Long): Triple<Int, Int, Int> {
        val a = jd + 32044
        val b = (4 * a + 3) / 146097
        val c = a - 146097 * b / 4
        val d = (4 * c + 3) / 1461
        val e = c - 1461 * d / 4
        val mm = (5 * e + 2) / 153
        val day = (e - (153 * mm + 2) / 5 + 1).toInt()
        val month = (mm + 3 - 12 * (mm / 10)).toInt()
        val year = (100 * b + d - 4800 + mm / 10).toInt()
        return Triple(year, month, day)
    }
}
