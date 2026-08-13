package com.example.timetable.model

import kotlinx.serialization.Serializable

/** 上课提醒规则。 */
@Serializable
data class NotificationRule(
    val enabled: Boolean = true,
    val minutesBefore: Int = 10,
)
