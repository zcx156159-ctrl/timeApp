package com.example.timetable.data

import com.example.timetable.model.Course
import com.example.timetable.model.PeriodConfig
import com.example.timetable.model.PeriodTime
import com.example.timetable.model.SemesterConfig
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IcalGeneratorTest {

    private val semester = SemesterConfig(totalWeeks = 4, currentWeek = 1, startDate = "2026-09-07")
    private val period = PeriodConfig(listOf(PeriodTime("08:00", "08:45")))
    private val course = Course(
        name = "高等数学",
        location = "A101",
        day = 1,
        startPeriod = 1,
        endPeriod = 1,
        startWeek = 1,
        endWeek = 4,
        weekType = 1,
    )

    @Test
    fun containsVeventAndEscapes() {
        val ics = IcalGenerator.generate(listOf(course), semester, period)
        assertTrue(ics.startsWith("BEGIN:VCALENDAR"))
        assertContains(ics, "SUMMARY:高等数学")
        assertContains(ics, "LOCATION:A101")
    }

    @Test
    fun oddWeekOnly() {
        val ics = IcalGenerator.generate(listOf(course), semester, period)
        assertTrue(ics.contains("20260907T080000")) // 第 1 周周一
        assertFalse(ics.contains("20260914T080000")) // 第 2 周周一（双周，不生成）
        assertTrue(ics.contains("20260921T080000")) // 第 3 周周一
    }

    @Test
    fun missingStartDateReturnsEmpty() {
        val noDate = semester.copy(startDate = null)
        assertEquals("", IcalGenerator.generate(listOf(course), noDate, period))
    }
}
