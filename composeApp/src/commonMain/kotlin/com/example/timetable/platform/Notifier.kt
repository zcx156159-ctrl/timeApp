package com.example.timetable.platform

/** 在 minutesFromNow 分钟后发本地通知。Web 端降级为无操作。 */
expect fun scheduleNotification(id: Int, title: String, body: String, minutesFromNow: Int)

expect fun cancelAllNotifications()
