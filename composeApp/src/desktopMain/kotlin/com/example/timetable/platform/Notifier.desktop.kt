package com.example.timetable.platform

import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage

actual fun scheduleNotification(id: Int, title: String, body: String, minutesFromNow: Int) {
    Thread {
        try {
            Thread.sleep(minutesFromNow * 60_000L)
            if (SystemTray.isSupported()) {
                val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
                val trayIcon = TrayIcon(image, "课表提醒")
                SystemTray.getSystemTray().add(trayIcon)
                trayIcon.displayMessage(title, body, TrayIcon.MessageType.INFO)
                SystemTray.getSystemTray().remove(trayIcon)
            } else {
                println("提醒：$title - $body")
            }
        } catch (_: InterruptedException) {
        }
    }.apply {
        isDaemon = true
        start()
    }
}

actual fun cancelAllNotifications() {
    // 桌面端无法统一取消已排程的线程提醒，保留为空实现
}
