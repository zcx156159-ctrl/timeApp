package com.example.timetable.data

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@OptIn(ExperimentalForeignApi::class)
actual suspend fun httpJsonRequest(
    method: String,
    url: String,
    body: String?,
    bearerToken: String?,
): String =
    suspendCancellableCoroutine { cont ->
        val nsUrl = NSURL.URLWithString(url)
        if (nsUrl == null) {
            cont.resumeWithException(RuntimeException("invalid url: $url"))
            return@suspendCancellableCoroutine
        }
        val request = NSMutableURLRequest.requestWithURL(nsUrl).apply {
            setHTTPMethod(method)
            setValue("application/json", forHTTPHeaderField = "Content-Type")
            if (bearerToken != null) {
                setValue("Bearer $bearerToken", forHTTPHeaderField = "Authorization")
            }
            if (body != null) {
                val bytes = body.encodeToByteArray()
                val data = bytes.usePinned { pinned ->
                    NSData.dataWithBytes(pinned.addressOf(0), bytes.size.toULong())
                }
                setHTTPBody(data)
            }
        }
        val task = NSURLSession.sharedSession.dataTaskWithRequest(request) { data, response, error ->
            if (error != null) {
                cont.resumeWithException(RuntimeException(error.localizedDescription ?: "network error"))
                return@dataTaskWithRequest
            }
            val http = response as? NSHTTPURLResponse
            val text = if (data != null) {
                NSString.create(data = data, encoding = NSUTF8StringEncoding).toString()
            } else {
                ""
            }
            val code = http?.statusCode?.toInt() ?: 200
            if (code !in 200..299) {
                cont.resumeWithException(RuntimeException("HTTP $code: $text"))
            } else {
                cont.resume(text)
            }
        }
        task.resume()
        cont.invokeOnCancellation { task.cancel() }
    }
