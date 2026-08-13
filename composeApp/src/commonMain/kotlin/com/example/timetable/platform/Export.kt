package com.example.timetable.platform

/** 导出文本文件（各端用保存/分享/下载方式实现）；返回是否成功。 */
expect fun exportTextFile(fileName: String, content: String): Boolean
