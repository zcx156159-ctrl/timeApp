package com.example.timetable.data

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// 在 JS 侧构造 fetch 请求（避免 wasm 侧动态对象互操问题）
@JsFun(
    "(url, method, body, bearer, onOk, onErr) => {" +
        "const opts = { method: method, headers: {'Content-Type': 'application/json'} };" +
        "if (bearer) opts.headers['Authorization'] = 'Bearer ' + bearer;" +
        "if (body !== null && body !== undefined) opts.body = body;" +
        "fetch(url, opts).then(r => r.text().then(t => onOk(r.status, t))).catch(e => onErr(e && e.message ? e.message : 'fetch failed'));" +
        "}"
)
private external fun jsFetch(
    url: String,
    method: String,
    body: String?,
    bearer: String?,
    onOk: (status: Int, text: String) -> Unit,
    onErr: (message: String) -> Unit,
)

actual suspend fun httpJsonRequest(
    method: String,
    url: String,
    body: String?,
    bearerToken: String?,
): String =
    suspendCancellableCoroutine { cont ->
        jsFetch(
            url,
            method,
            body,
            bearerToken,
            onOk = { status, text ->
                if (status in 200..299) {
                    cont.resume(text)
                } else {
                    cont.resumeWithException(RuntimeException("HTTP $status: $text"))
                }
            },
            onErr = { message -> cont.resumeWithException(RuntimeException(message)) },
        )
    }
