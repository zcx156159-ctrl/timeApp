package com.example.timetable.model

import kotlinx.serialization.Serializable

/** 客户端课表元数据（列表/切换用）。 */
@Serializable
data class TimetableMeta(
    val id: Long,
    val name: String,
)
