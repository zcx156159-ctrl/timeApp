package com.example.timetable.data

import kotlin.test.Test
import kotlin.test.assertEquals

class CsvParserTest {

    @Test
    fun simpleRows() {
        val result = parseCsv("a,b\nc,d\n")
        assertEquals(2, result.rows.size)
        assertEquals(listOf("a", "b"), result.rows[0].fields)
    }

    @Test
    fun quotedCommaAndNewline() {
        val result = parseCsv("name,\"hi, there\"\n\"line1\nline2\",x")
        assertEquals(2, result.rows.size)
        assertEquals("hi, there", result.rows[0].fields[1])
        assertEquals("line1\nline2", result.rows[1].fields[0])
    }

    @Test
    fun blankLinesSkipped() {
        val result = parseCsv("a\n\nb\n")
        assertEquals(2, result.rows.size)
    }

    @Test
    fun escapedQuote() {
        val result = parseCsv("\"say \"\"hi\"\"\"")
        assertEquals("say \"hi\"", result.rows[0].fields[0])
    }

    @Test
    fun spaceDelimited() {
        val result = parseCsv("高等数学 王老师 A101 1 1 2\n大学英语 李老师 B203 1 3 4")
        assertEquals(2, result.rows.size)
        assertEquals(listOf("高等数学", "王老师", "A101", "1", "1", "2"), result.rows[0].fields)
    }

    @Test
    fun consecutiveSpacesAndTabs() {
        val result = parseCsv("a\tb   c")
        assertEquals(listOf("a", "b", "c"), result.rows[0].fields)
    }
}
