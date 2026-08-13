package com.example.timetable.platform

import platform.UIKit.UIPasteboard

actual fun exportTextFile(fileName: String, content: String): Boolean {
    // iOS 简单方案：复制到剪贴板，由用户粘贴保存
    UIPasteboard.generalPasteboard.string = content
    return true
}
