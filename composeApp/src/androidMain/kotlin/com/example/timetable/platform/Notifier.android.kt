package com.example.timetable.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Handler
import android.os.Build
import android.os.Looper

/** 由 MainActivity 初始化。 */
object AppContextHolder {
    var context: Context? = null
}

private const val CHANNEL_ID = "timetable_reminders"

actual fun scheduleNotification(id: Int, title: String, body: String, minutesFromNow: Int) {
    val ctx = AppContextHolder.context ?: return
    val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= 26) {
        val channel = NotificationChannel(CHANNEL_ID, "上课提醒", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "上课前提醒"
        }
        manager.createNotificationChannel(channel)
    }
    Handler(Looper.getMainLooper()).postDelayed({
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(ctx, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(ctx)
        }
        val notification = builder
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()
        manager.notify(id, notification)
    }, minutesFromNow * 60_000L)
}

actual fun cancelAllNotifications() {
    val ctx = AppContextHolder.context ?: return
    val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.cancelAll()
}
