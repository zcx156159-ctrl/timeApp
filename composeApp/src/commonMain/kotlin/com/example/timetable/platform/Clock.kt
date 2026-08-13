package com.example.timetable.platform

/** 今天星期几：1 = 周一 ... 7 = 周日。 */
expect fun currentDayIndex(): Int

/** 当前时刻的分钟数（0..1439）。 */
expect fun currentMinutes(): Int

/** 当前毫秒时间戳（同步用）。 */
expect fun currentTimeMillis(): Long
