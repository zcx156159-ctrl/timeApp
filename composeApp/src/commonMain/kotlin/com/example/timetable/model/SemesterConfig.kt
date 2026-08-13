package com.example.timetable.model

import kotlinx.serialization.Serializable

/** 学期配置：总周数与当前周。 */
@Serializable
data class SemesterConfig(
    val totalWeeks: Int = 20,
    val currentWeek: Int = 1,
    /** 学期第一周周一日期，格式 YYYY-MM-DD（iCal 导出需要）。 */
    val startDate: String? = null,
)
