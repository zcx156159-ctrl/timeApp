package com.example.timetable.platform

actual fun scheduleNotification(id: Int, title: String, body: String, minutesFromNow: Int) {
    // Web 端无本地通知，V1.1 降级为空实现
}

actual fun cancelAllNotifications() {
}
