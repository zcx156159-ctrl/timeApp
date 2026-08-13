package com.example.timetable.server

import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files

/** 进程内冒烟测试：起服务 -> 建共享 -> 存课程 -> 拉取校验。 */
fun main() {
    val dir = Files.createTempDirectory("timetable-test")
    System.setProperty("db.url", "jdbc:sqlite:" + dir.resolve("v2.db"))
    println("SMOKE: starting server...")
    val server = startServer(18081, dir.resolve("data.json"), null)
    server.start()
    println("SMOKE: server started")
    try {
        val health = request("GET", "http://127.0.0.1:18081/api/health", null)
        println("SMOKE: health -> ${health.first}")
        check(health.first == 200)
        val create = request("POST", "http://127.0.0.1:18081/api/timetables", """{"courses":[]}""")
        println("SMOKE: create -> ${create.first} ${create.second}")
        check(create.first == 200) { "create failed: ${create.second}" }
        val code = Regex("\"code\"\\s*:\\s*\"([a-z0-9]+)\"").find(create.second)!!.groupValues[1]
        val put = request(
            "PUT",
            "http://127.0.0.1:18081/api/timetables/$code",
            """{"courses":[{"id":"c1","name":"高等数学","teacher":"王老师","location":"A101","day":1,"startPeriod":1,"endPeriod":2,"colorIndex":0}]}""",
        )
        println("SMOKE: put -> ${put.first}")
        check(put.first == 200) { "put failed: ${put.second}" }
        val get = request("GET", "http://127.0.0.1:18081/api/timetables/$code", null)
        println("SMOKE: get -> ${get.first} ${get.second.take(120)}")
        check(get.first == 200 && get.second.contains("高等数学")) { "get failed: ${get.second}" }
        println("SMOKE TEST PASSED: code=$code, course round-trip ok")

        // V2 账号流程
        val email = "t${System.currentTimeMillis()}@test.com"
        val reg = request(
            "POST",
            "http://127.0.0.1:18081/api/v1/auth/register",
            """{"email":"$email","password":"123456"}""",
        )
        check(reg.first == 200) { "register failed: ${reg.second}" }
        val access = Regex("\"accessToken\"\\s*:\\s*\"([^\"]+)\"").find(reg.second)!!.groupValues[1]
        val me = request(
            "GET",
            "http://127.0.0.1:18081/api/v1/me",
            null,
            mapOf("Authorization" to "Bearer $access"),
        )
        check(me.first == 200 && me.second.contains(email)) { "me failed: ${me.second}" }
        val bad = request(
            "GET",
            "http://127.0.0.1:18081/api/v1/me",
            null,
            mapOf("Authorization" to "Bearer bad"),
        )
        check(bad.first == 401) { "bad token should be 401, got ${bad.first}" }
        println("SMOKE V2 AUTH PASSED")

        // S2 多课表 + 改密 + 注销
        val headers = mapOf("Authorization" to "Bearer $access")
        val baseUrl = "http://127.0.0.1:18081"
        val createTt = request(
            "POST",
            "$baseUrl/api/v1/timetables",
            """{"name":"本学期","totalWeeks":18,"currentWeek":3,"startDate":"2026-09-07"}""",
            headers,
        )
        check(createTt.first == 200) { "create timetable failed: ${createTt.second}" }
        val tid = Regex("\"id\"\\s*:\\s*(\\d+)").find(createTt.second)!!.groupValues[1].toLong()
        val listTt = request("GET", "$baseUrl/api/v1/timetables", null, headers)
        check(listTt.first == 200 && listTt.second.contains(tid.toString())) { "list failed: ${listTt.second}" }
        val putPeriods = request(
            "PUT",
            "$baseUrl/api/v1/timetables/$tid/periods",
            """[{"start":"08:00","end":"08:45"},{"start":"08:55","end":"09:40"}]""",
            headers,
        )
        check(putPeriods.first == 200) { "put periods failed: ${putPeriods.second}" }
        val putCourses = request(
            "PUT",
            "$baseUrl/api/v1/timetables/$tid/courses",
            """[{"id":"c1","name":"高等数学","teacher":"王老师","location":"A101","day":1,"startPeriod":1,"endPeriod":2,"colorIndex":0,"weekType":1}]""",
            headers,
        )
        check(putCourses.first == 200) { "put courses failed: ${putCourses.second}" }
        val getTt = request("GET", "$baseUrl/api/v1/timetables/$tid", null, headers)
        check(getTt.first == 200 && getTt.second.contains("高等数学")) { "get failed: ${getTt.second}" }
        val changePw = request(
            "PUT",
            "$baseUrl/api/v1/me/password",
            """{"oldPassword":"123456","newPassword":"abcdef"}""",
            headers,
        )
        check(changePw.first == 200) { "change password failed: ${changePw.second}" }
        val login2 = request(
            "POST",
            "$baseUrl/api/v1/auth/login",
            """{"account":"$email","password":"abcdef"}""",
        )
        check(login2.first == 200) { "login with new password failed: ${login2.second}" }

        // S3 同步合并：新增课程 + 删除已有课程（tombstone）
        val sync1 = request(
            "POST",
            "$baseUrl/api/v1/timetables/$tid/sync",
            """{"upserts":[{"id":"c2","name":"大学英语","day":2,"startPeriod":3,"endPeriod":4,"updatedAt":2000}],"deletes":["c1"],"clientUpdatedAt":0}""",
            headers,
        )
        check(sync1.first == 200) { "sync failed: ${sync1.second}" }
        check(sync1.second.contains("大学英语")) { "sync upsert missing: ${sync1.second}" }
        check(sync1.second.contains("c1")) { "sync tombstone missing: ${sync1.second}" }
        val sync2 = request(
            "POST",
            "$baseUrl/api/v1/timetables/$tid/sync",
            """{"upserts":[],"deletes":[],"clientUpdatedAt":${System.currentTimeMillis()}}""",
            headers,
        )
        check(sync2.first == 200 && sync2.second.contains("\"changes\": []")) {
            "incremental sync should be empty: ${sync2.second}"
        }
        println("SMOKE S3 SYNC PASSED")

        // S4 共享：只读/可编辑 + 免登录订阅
        val shareRo = request(
            "POST",
            "$baseUrl/api/v1/shares",
            """{"timetableId":$tid,"permission":"RO"}""",
            headers,
        )
        check(shareRo.first == 200) { "create RO share failed: ${shareRo.second}" }
        val roToken = Regex("\"token\"\\s*:\\s*\"([^\"]+)\"").find(shareRo.second)!!.groupValues[1]
        val pubGet = request("GET", "$baseUrl/api/v1/public/timetables/$roToken", null)
        check(pubGet.first == 200 && pubGet.second.contains("大学英语")) {
            "public get failed: ${pubGet.second}"
        }
        val pubPutRo = request("PUT", "$baseUrl/api/v1/public/timetables/$roToken/courses", "[]")
        check(pubPutRo.first == 403) { "RO should forbid write, got ${pubPutRo.first}" }
        val shareRw = request(
            "POST",
            "$baseUrl/api/v1/shares",
            """{"timetableId":$tid,"permission":"RW"}""",
            headers,
        )
        check(shareRw.first == 200) { "create RW share failed: ${shareRw.second}" }
        val rwToken = Regex("\"token\"\\s*:\\s*\"([^\"]+)\"").find(shareRw.second)!!.groupValues[1]
        val pubPutRw = request(
            "PUT",
            "$baseUrl/api/v1/public/timetables/$rwToken/courses",
            """[{"id":"c3","name":"物理","day":1,"startPeriod":1,"endPeriod":1}]""",
        )
        check(pubPutRw.first == 200) { "RW write failed: ${pubPutRw.second}" }
        println("SMOKE S4 SHARE PASSED")

        val delTt = request("DELETE", "$baseUrl/api/v1/timetables/$tid", null, headers)
        check(delTt.first == 200) { "delete timetable failed: ${delTt.second}" }
        val delUser = request("DELETE", "$baseUrl/api/v1/me", null, headers)
        check(delUser.first == 200) { "delete user failed: ${delUser.second}" }
        println("SMOKE S2 PASSED")
        System.exit(0)
    } catch (e: Throwable) {
        println("SMOKE: FAILED: ${e::class.simpleName}: ${e.message}")
        e.printStackTrace()
        System.exit(1)
    } finally {
        runCatching { server.stop(0) }
    }
}

private fun request(
    method: String,
    url: String,
    body: String?,
    headers: Map<String, String> = emptyMap(),
): Pair<Int, String> {
    val conn = URL(url).openConnection() as HttpURLConnection
    try {
        conn.requestMethod = method
        conn.connectTimeout = 3000
        conn.readTimeout = 5000
        conn.setRequestProperty("Content-Type", "application/json")
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        if (body != null) {
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        return code to text
    } finally {
        conn.disconnect()
    }
}
