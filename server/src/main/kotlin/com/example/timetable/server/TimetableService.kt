package com.example.timetable.server

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

@Serializable
data class PeriodTimeIn(val start: String = "08:00", val end: String = "08:45")

@Serializable
data class CourseIn(
    val id: String = "",
    val name: String = "",
    val teacher: String = "",
    val location: String = "",
    val day: Int = 1,
    val startPeriod: Int = 1,
    val endPeriod: Int = 1,
    val colorIndex: Int = 0,
    val startWeek: Int? = null,
    val endWeek: Int? = null,
    val weekType: Int = 0,
    val note: String = "",
    val updatedAt: Long = 0,
)

@Serializable
data class TimetableSummary(
    val id: Long,
    val name: String,
    val totalWeeks: Int,
    val currentWeek: Int,
    val startDate: String?,
    val updatedAt: Long,
    val courseCount: Long,
)

@Serializable
data class TimetableDetail(
    val id: Long,
    val name: String,
    val totalWeeks: Int,
    val currentWeek: Int,
    val startDate: String?,
    val periods: List<PeriodTimeIn>,
    val courses: List<CourseIn>,
)

@Serializable
data class CreateTimetableRequest(
    val name: String? = null,
    val totalWeeks: Int? = null,
    val currentWeek: Int? = null,
    val startDate: String? = null,
)

@Serializable
data class UpdateTimetableRequest(
    val name: String? = null,
    val totalWeeks: Int? = null,
    val currentWeek: Int? = null,
    val startDate: String? = null,
)

object TimetableService {

    fun list(userId: Long): List<TimetableSummary> = transaction {
        Timetables.selectAll()
            .where { Timetables.ownerId eq userId }
            .orderBy(Timetables.updatedAt, SortOrder.DESC)
            .map { row ->
                val count = Courses.selectAll()
                    .where { Courses.timetableId eq row[Timetables.id] }
                    .count()
                TimetableSummary(
                    id = row[Timetables.id],
                    name = row[Timetables.name],
                    totalWeeks = row[Timetables.totalWeeks],
                    currentWeek = row[Timetables.currentWeek],
                    startDate = row[Timetables.startDate],
                    updatedAt = row[Timetables.updatedAt],
                    courseCount = count,
                )
            }
    }

    fun create(userId: Long, req: CreateTimetableRequest): TimetableSummary = transaction {
        val now = System.currentTimeMillis()
        val resolvedName = req.name?.takeIf { s -> s.isNotBlank() } ?: "我的课表"
        val id = Timetables.insert {
            it[ownerId] = userId
            it[name] = resolvedName
            it[totalWeeks] = req.totalWeeks ?: 20
            it[currentWeek] = req.currentWeek ?: 1
            it[startDate] = req.startDate
            it[createdAt] = now
            it[updatedAt] = now
        } get Timetables.id
        TimetableSummary(id, resolvedName, req.totalWeeks ?: 20, req.currentWeek ?: 1, req.startDate, now, 0)
    }

    /** 返回 null 表示不存在或非本人。 */
    fun detail(userId: Long, id: Long): TimetableDetail? = transaction {
        val row = Timetables.selectAll().where { Timetables.id eq id }.firstOrNull() ?: return@transaction null
        if (row[Timetables.ownerId] != userId) return@transaction null
        val periods = PeriodTimes.selectAll()
            .where { PeriodTimes.timetableId eq id }
            .orderBy(PeriodTimes.periodNo)
            .map { PeriodTimeIn(it[PeriodTimes.startTime], it[PeriodTimes.endTime]) }
        val courses = Courses.selectAll()
            .where { Courses.timetableId eq id }
            .filter { !it[Courses.deleted] }
            .map { toCourseIn(it) }
        TimetableDetail(
            id = row[Timetables.id],
            name = row[Timetables.name],
            totalWeeks = row[Timetables.totalWeeks],
            currentWeek = row[Timetables.currentWeek],
            startDate = row[Timetables.startDate],
            periods = periods,
            courses = courses,
        )
    }

    fun update(userId: Long, id: Long, req: UpdateTimetableRequest): Boolean = transaction {
        val row = Timetables.selectAll().where { Timetables.id eq id }.firstOrNull() ?: return@transaction false
        if (row[Timetables.ownerId] != userId) return@transaction false
        Timetables.update({ Timetables.id eq id }) {
            req.name?.takeIf { s -> s.isNotBlank() }?.let { v -> it[name] = v }
            req.totalWeeks?.let { v -> it[totalWeeks] = v }
            req.currentWeek?.let { v -> it[currentWeek] = v }
            it[startDate] = req.startDate
            it[updatedAt] = System.currentTimeMillis()
        }
        true
    }

    fun delete(userId: Long, id: Long): Boolean = transaction {
        val row = Timetables.selectAll().where { Timetables.id eq id }.firstOrNull() ?: return@transaction false
        if (row[Timetables.ownerId] != userId) return@transaction false
        Courses.deleteWhere { Courses.timetableId eq id }
        PeriodTimes.deleteWhere { PeriodTimes.timetableId eq id }
        ShareTokens.deleteWhere { ShareTokens.timetableId eq id }
        Timetables.deleteWhere { Timetables.id eq id }
        true
    }

    fun savePeriods(userId: Long, id: Long, periods: List<PeriodTimeIn>): Boolean = transaction {
        val row = Timetables.selectAll().where { Timetables.id eq id }.firstOrNull() ?: return@transaction false
        if (row[Timetables.ownerId] != userId) return@transaction false
        PeriodTimes.deleteWhere { PeriodTimes.timetableId eq id }
        periods.forEachIndexed { index, p ->
            PeriodTimes.insert {
                it[timetableId] = id
                it[periodNo] = index + 1
                it[startTime] = p.start
                it[endTime] = p.end
            }
        }
        true
    }

    fun replaceCourses(userId: Long, id: Long, courses: List<CourseIn>): Boolean = transaction {
        val row = Timetables.selectAll().where { Timetables.id eq id }.firstOrNull() ?: return@transaction false
        if (row[Timetables.ownerId] != userId) return@transaction false
        Courses.deleteWhere { Courses.timetableId eq id }
        val now = System.currentTimeMillis()
        courses.forEach { c ->
            Courses.insert {
                it[timetableId] = id
                it[syncId] = c.id.ifBlank { "c${kotlin.random.Random.nextLong().toString(16)}" }
                it[name] = c.name
                it[teacher] = c.teacher
                it[location] = c.location
                it[day] = c.day
                it[startPeriod] = c.startPeriod
                it[endPeriod] = c.endPeriod
                it[colorIndex] = c.colorIndex
                it[startWeek] = c.startWeek
                it[endWeek] = c.endWeek
                it[weekType] = c.weekType
                it[note] = c.note
                it[updatedAt] = if (c.updatedAt > 0) c.updatedAt else now
                it[deleted] = false
            }
        }
        Timetables.update({ Timetables.id eq id }) { it[updatedAt] = System.currentTimeMillis() }
        true
    }

    private fun toCourseIn(row: ResultRow): CourseIn = CourseIn(
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
