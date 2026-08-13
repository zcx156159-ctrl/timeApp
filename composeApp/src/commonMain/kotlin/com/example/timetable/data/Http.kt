package com.example.timetable.data

/**
 * 跨平台简单 HTTP 请求（expect/actual，无第三方依赖）：
 * 返回响应体文本；非 2xx 抛异常。
 */
expect suspend fun httpJsonRequest(
    method: String,
    url: String,
    body: String?,
    bearerToken: String? = null,
): String
