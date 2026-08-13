package com.example.timetable.data

import com.example.timetable.model.Course
import com.example.timetable.model.PeriodTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class TimetablePayload(val courses: List<Course>)

@Serializable
private data class CreateResponse(val code: String = "")

@Serializable
private data class TimetableResponse(
    val code: String = "",
    val courses: List<Course> = emptyList(),
    val updatedAt: Long = 0,
)

@Serializable
private data class AuthResponseV2(
    val userId: Long = 0,
    val accessToken: String = "",
    val refreshToken: String = "",
)

@Serializable
internal data class TimetableSummaryV2(
    val id: Long = 0,
    val name: String = "",
    val totalWeeks: Int = 20,
    val currentWeek: Int = 1,
    val startDate: String? = null,
    val updatedAt: Long = 0,
    val courseCount: Long = 0,
)

@Serializable
private data class SyncRequestV2(
    val upserts: List<Course>,
    val deletes: List<String>,
    val clientUpdatedAt: Long,
)

@Serializable
internal data class SyncResponseV2(
    val changes: List<Course> = emptyList(),
    val deletedIds: List<String> = emptyList(),
    val serverUpdatedAt: Long = 0,
)

data class AuthInfo(
    val accessToken: String,
    val refreshToken: String,
    val userId: Long,
)

@Serializable
data class ShareInfoV2(
    val token: String = "",
    val timetableId: Long = 0,
    val permission: String = "RO",
)

@Serializable
data class PublicTimetableV2(
    val id: Long = 0,
    val name: String = "",
    val totalWeeks: Int = 20,
    val currentWeek: Int = 1,
    val startDate: String? = null,
    val periods: List<PeriodTime> = emptyList(),
    val courses: List<Course> = emptyList(),
)

/** 云同步 API 客户端：新建共享、拉取、覆盖保存。 */
class CloudRepository {
    private val json = Json { ignoreUnknownKeys = true }

    /** 新建共享课表，返回共享码。 */
    suspend fun create(base: String, courses: List<Course>): String {
        val body = json.encodeToString(TimetablePayload(courses))
        val resp = httpJsonRequest("POST", apiUrl(base, "timetables"), body)
        return json.decodeFromString<CreateResponse>(resp).code
    }

    /** 拉取共享课表。 */
    suspend fun get(base: String, code: String): List<Course> {
        val resp = httpJsonRequest("GET", apiUrl(base, "timetables/$code"), null)
        return json.decodeFromString<TimetableResponse>(resp).courses
    }

    /** 覆盖保存共享课表。 */
    suspend fun put(base: String, code: String, courses: List<Course>) {
        val body = json.encodeToString(TimetablePayload(courses))
        httpJsonRequest("PUT", apiUrl(base, "timetables/$code"), body)
    }

    // ---- V2 账号与同步 ----

    suspend fun login(base: String, account: String, password: String): AuthInfo =
        authCall(base, "auth/login", """{"account":"${escapeJson(account)}","password":"${escapeJson(password)}"}""")

    suspend fun register(base: String, account: String, password: String): AuthInfo {
        val isPhone = account.all { it.isDigit() } && account.length in 6..20
        val field = if (isPhone) "phone" else "email"
        return authCall(base, "auth/register", """{"$field":"${escapeJson(account)}","password":"${escapeJson(password)}"}""")
    }

    internal suspend fun listTimetables(base: String, token: String): List<TimetableSummaryV2> {
        val resp = httpJsonRequest("GET", apiUrlV1(base, "timetables"), null, token)
        return json.decodeFromString<List<TimetableSummaryV2>>(resp)
    }

    internal suspend fun createTimetable(base: String, token: String, name: String): TimetableSummaryV2 {
        val body = json.encodeToString(CreateTimetableRequestV2(name))
        val resp = httpJsonRequest("POST", apiUrlV1(base, "timetables"), body, token)
        return json.decodeFromString<TimetableSummaryV2>(resp)
    }

    internal suspend fun renameTimetable(base: String, token: String, id: Long, name: String) {
        httpJsonRequest("PUT", apiUrlV1(base, "timetables/$id"), """{"name":"${escapeJson(name)}"}""", token)
    }

    internal suspend fun deleteTimetable(base: String, token: String, id: Long) {
        httpJsonRequest("DELETE", apiUrlV1(base, "timetables/$id"), null, token)
    }

    internal suspend fun syncCourses(
        base: String,
        token: String,
        timetableId: Long,
        upserts: List<Course>,
        deletes: List<String>,
        clientUpdatedAt: Long,
    ): SyncResponseV2 {
        val body = json.encodeToString(SyncRequestV2(upserts, deletes, clientUpdatedAt))
        val resp = httpJsonRequest("POST", apiUrlV1(base, "timetables/$timetableId/sync"), body, token)
        return json.decodeFromString<SyncResponseV2>(resp)
    }

    internal suspend fun createShare(base: String, token: String, timetableId: Long, permission: String): ShareInfoV2 {
        val body = json.encodeToString(ShareCreateRequestV2(timetableId, permission))
        val resp = httpJsonRequest("POST", apiUrlV1(base, "shares"), body, token)
        return json.decodeFromString<ShareInfoV2>(resp)
    }

    internal suspend fun listShares(base: String, token: String): List<ShareInfoV2> {
        val resp = httpJsonRequest("GET", apiUrlV1(base, "shares"), null, token)
        return json.decodeFromString<List<ShareInfoV2>>(resp)
    }

    internal suspend fun revokeShare(base: String, token: String, shareToken: String) {
        httpJsonRequest("DELETE", apiUrlV1(base, "shares/$shareToken"), null, token)
    }

    internal suspend fun fetchPublic(base: String, shareToken: String): PublicTimetableV2 {
        val resp = httpJsonRequest("GET", apiUrlV1(base, "public/timetables/$shareToken"), null)
        return json.decodeFromString<PublicTimetableV2>(resp)
    }

    private fun apiUrl(base: String, path: String): String {
        val b = base.trim().trimEnd('/')
        return b + "/api/" + path
    }

    private fun apiUrlV1(base: String, path: String): String {
        val b = base.trim().trimEnd('/')
        return b + "/api/v1/" + path
    }

    private suspend fun authCall(base: String, path: String, body: String): AuthInfo {
        val resp = httpJsonRequest("POST", apiUrlV1(base, path), body)
        val data = json.decodeFromString<AuthResponseV2>(resp)
        return AuthInfo(data.accessToken, data.refreshToken, data.userId)
    }

    private fun escapeJson(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")
}

@Serializable
private data class CreateTimetableRequestV2(val name: String? = null)

@Serializable
private data class ShareCreateRequestV2(val timetableId: Long, val permission: String = "RO")
