package com.example.timetable.platform

import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter

actual fun scheduleNotification(id: Int, title: String, body: String, minutesFromNow: Int) {
    val center = UNUserNotificationCenter.currentNotificationCenter()
    center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound) { granted, _ ->
        if (granted) {
            val content = UNMutableNotificationContent().apply {
                setTitle(title)
                setBody(body)
            }
            val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(
                minutesFromNow.toDouble() * 60.0,
                repeats = false,
            )
            val request = UNNotificationRequest.requestWithIdentifier(
                "timetable-$id",
                content = content,
                trigger = trigger,
            )
            center.addNotificationRequest(request, null)
        }
    }
}

actual fun cancelAllNotifications() {
    val center = UNUserNotificationCenter.currentNotificationCenter()
    center.removeAllDeliveredNotifications()
    center.removeAllPendingNotificationRequests()
}
