package com.example.timetable.platform

/** 选择并读取文本文件（CSV）；用户取消或当前端不支持时返回 null。 */
expect suspend fun pickTextFile(): String?
