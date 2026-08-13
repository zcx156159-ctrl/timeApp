package com.example.timetable.platform

@JsFun("() => { const d = new Date(); const wd = d.getDay(); return wd === 0 ? 7 : wd; }")
private external fun jsDayIndex(): Int

@JsFun("() => { const d = new Date(); return d.getHours() * 60 + d.getMinutes(); }")
private external fun jsMinutes(): Int

@JsFun("() => Date.now()")
private external fun jsNow(): Double

actual fun currentDayIndex(): Int = jsDayIndex()

actual fun currentMinutes(): Int = jsMinutes()

actual fun currentTimeMillis(): Long = jsNow().toLong()
