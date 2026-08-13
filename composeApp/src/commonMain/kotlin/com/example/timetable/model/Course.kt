package com.example.timetable.model

import kotlinx.serialization.Serializable

/**
 * 课程数据模型。
 *
 * @param day 星期几：1 = 周一 ... 7 = 周日
 * @param startPeriod 开始节次（1..12）
 * @param endPeriod 结束节次（1..12，>= startPeriod）
 */
@Serializable
data class Course(
    val id: String = "",
    val name: String,
    val teacher: String = "",
    val location: String = "",
    val day: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val colorIndex: Int = 0,
    // V1.1：周次体系（带默认值，兼容旧数据）
    val startWeek: Int? = null,
    val endWeek: Int? = null,
    val weekType: Int = 0,
    val note: String = "",
    /** 云端同步时间戳（毫秒）。 */
    val updatedAt: Long = 0,
)

val weekDayLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
val periodCount = 12

/** 课程周类型文案。 */
val weekTypeLabels = listOf("每周", "单周", "双周")
