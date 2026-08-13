package com.example.timetable.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WeekLogicTest {

    private val everyWeek = Course(name = "A", day = 1, startPeriod = 1, endPeriod = 1)
    private val odd = Course(name = "B", day = 1, startPeriod = 1, endPeriod = 1, startWeek = 1, endWeek = 10, weekType = 1)
    private val even = Course(name = "C", day = 1, startPeriod = 1, endPeriod = 1, startWeek = 1, endWeek = 10, weekType = 2)
    private val range = Course(name = "D", day = 1, startPeriod = 1, endPeriod = 1, startWeek = 3, endWeek = 6)

    @Test
    fun everyWeekAlwaysActive() {
        assertTrue(everyWeek.isActiveOn(1))
        assertTrue(everyWeek.isActiveOn(20))
    }

    @Test
    fun oddWeek() {
        assertTrue(odd.isActiveOn(1))
        assertTrue(odd.isActiveOn(9))
        assertFalse(odd.isActiveOn(2))
        assertFalse(odd.isActiveOn(10))
    }

    @Test
    fun evenWeek() {
        assertTrue(even.isActiveOn(2))
        assertTrue(even.isActiveOn(10))
        assertFalse(even.isActiveOn(1))
        assertFalse(even.isActiveOn(9))
    }

    @Test
    fun weekRange() {
        assertTrue(range.isActiveOn(3))
        assertTrue(range.isActiveOn(6))
        assertFalse(range.isActiveOn(2))
        assertFalse(range.isActiveOn(7))
    }

    @Test
    fun invalidWeek() {
        assertFalse(everyWeek.isActiveOn(0))
    }
}
