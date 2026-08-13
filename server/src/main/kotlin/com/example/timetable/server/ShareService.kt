package com.example.timetable.server

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

@Serializable
data class ShareCreateRequest(val timetableId: Long, val permission: String = "RO")

@Serializable
data class ShareCreateResponse(val token: String, val permission: String)

@Serializable
data class ShareInfo(val token: String, val timetableId: Long, val permission: String)

@Serializable
data class PublicTimetable(
    val id: Long,
    val name: String,
    val totalWeeks: Int,
    val currentWeek: Int,
    val startDate: String?,
    val periods: List<PeriodTimeIn>,
    val courses: List<CourseIn>,
)

object ShareService {
    private const val CHARS = "23456789abcdefghjkmnpqrstuvwxyz"

    fun create(userId: Long, req: ShareCreateRequest): ShareCreateResponse? = transaction {
        val tt = Timetables.selectAll().where { Timetables.id eq req.timetableId }.firstOrNull()
            ?: return@transaction null
        if (tt[Timetables.ownerId] != userId) return@transaction null
        val permission = if (req.permission == "RW") "RW" else "RO"
        val token = generateToken()
        ShareTokens.insert {
            it[ShareTokens.token] = token
            it[ShareTokens.timetableId] = req.timetableId
            it[ShareTokens.permission] = permission
            it[ShareTokens.createdBy] = userId
            it[ShareTokens.revoked] = false
            it[ShareTokens.createdAt] = System.currentTimeMillis()
        }
        ShareCreateResponse(token, permission)
    }

    fun listMine(userId: Long): List<ShareInfo> = transaction {
        ShareTokens.selectAll()
            .where { ShareTokens.createdBy eq userId }
            .filter { !it[ShareTokens.revoked] }
            .map {
                ShareInfo(it[ShareTokens.token], it[ShareTokens.timetableId], it[ShareTokens.permission])
            }
    }

    fun revoke(userId: Long, token: String): Boolean = transaction {
        val share = ShareTokens.selectAll().where { ShareTokens.token eq token }.firstOrNull()
            ?: return@transaction false
        if (share[ShareTokens.createdBy] != userId) return@transaction false
        ShareTokens.update({ ShareTokens.token eq token }) { it[revoked] = true }
        true
    }

    fun publicGet(token: String): PublicTimetable? = transaction {
        val share = ShareTokens.selectAll().where { ShareTokens.token eq token }.firstOrNull()
            ?: return@transaction null
        if (share[ShareTokens.revoked]) return@transaction null
        val tt = Timetables.selectAll()
            .where { Timetables.id eq share[ShareTokens.timetableId] }
            .firstOrNull() ?: return@transaction null
        val periods = PeriodTimes.selectAll()
            .where { PeriodTimes.timetableId eq share[ShareTokens.timetableId] }
            .orderBy(PeriodTimes.periodNo)
            .map { PeriodTimeIn(it[PeriodTimes.startTime], it[PeriodTimes.endTime]) }
        val courses = Courses.selectAll()
            .where { Courses.timetableId eq share[ShareTokens.timetableId] }
            .filter { !it[Courses.deleted] }
            .map { toCourseIn(it) }
        PublicTimetable(
            id = tt[Timetables.id],
            name = tt[Timetables.name],
            totalWeeks = tt[Timetables.totalWeeks],
            currentWeek = tt[Timetables.currentWeek],
            startDate = tt[Timetables.startDate],
            periods = periods,
            courses = courses,
        )
    }

    /** 免登录写入：仅 RW 且未撤销的共享码可用。 */
    fun publicPutCourses(token: String, courses: List<CourseIn>): Boolean = transaction {
        val share = ShareTokens.selectAll().where { ShareTokens.token eq token }.firstOrNull()
            ?: return@transaction false
        if (share[ShareTokens.revoked] || share[ShareTokens.permission] != "RW") return@transaction false
        val timetableId = share[ShareTokens.timetableId]
        Courses.deleteWhere { Courses.timetableId eq timetableId }
        val now = System.currentTimeMillis()
        courses.forEach { c ->
            Courses.insert {
                it[Courses.timetableId] = timetableId
                it[Courses.syncId] = c.id.ifBlank { "c${kotlin.random.Random.nextLong().toString(16)}" }
                it[Courses.name] = c.name
                it[Courses.teacher] = c.teacher
                it[Courses.location] = c.location
                it[Courses.day] = c.day
                it[Courses.startPeriod] = c.startPeriod
                it[Courses.endPeriod] = c.endPeriod
                it[Courses.colorIndex] = c.colorIndex
                it[Courses.startWeek] = c.startWeek
                it[Courses.endWeek] = c.endWeek
                it[Courses.weekType] = c.weekType
                it[Courses.note] = c.note
                it[Courses.updatedAt] = if (c.updatedAt > 0) c.updatedAt else now
                it[Courses.deleted] = false
            }
        }
        true
    }

    private fun generateToken(): String {
        while (true) {
            val token = (1..6).map { CHARS.random() }.joinToString("")
            val exists = ShareTokens.selectAll().where { ShareTokens.token eq token }.empty()
            if (exists) return token
        }
    }

    private fun toCourseIn(row: org.jetbrains.exposed.sql.ResultRow): CourseIn = CourseIn(
        id = row[Courses.syncId],
        name = row[Courses.name],
        teacher = row[Courses.teacher],
        location = row[Courses.location],
        day = row[Courses.day],
        startPeriod = row[Courses.startPeriod],
        endPeriod = row[Courses.endPeriod],
        colorIndex = row[Courses.colorIndex],
        startWeek = row[Courses.startWeek],
        endWeek = row[Courses.endWeek],
        weekType = row[Courses.weekType],
        note = row[Courses.note],
        updatedAt = row[Courses.updatedAt],
    )
}
