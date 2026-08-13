package com.example.timetable.platform

@JsFun(
    "(fileName, content) => { const blob = new Blob([content], {type: 'text/calendar;charset=utf-8'}); " +
        "const a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = fileName; " +
        "document.body.appendChild(a); a.click(); document.body.removeChild(a); URL.revokeObjectURL(a.href); return true; }",
)
private external fun jsExportText(fileName: String, content: String): Boolean

actual fun exportTextFile(fileName: String, content: String): Boolean = jsExportText(fileName, content)
