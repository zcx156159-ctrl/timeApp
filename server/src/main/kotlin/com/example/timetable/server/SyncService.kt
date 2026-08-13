package com.example.timetable.server

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

@Serializable
data class SyncRequest(
    val upserts: List<CourseIn> = emptyList(),
    val deletes: List<String> = emptyList(),
    val clientUpdatedAt: Long = 0,
)

@Serializable
data class SyncResponse(
    val changes: List<CourseIn>,
    val deletedIds: List<String>,
    val serverUpdatedAt: Long,
)

/**
 * 同步合并（保守策略）：
 * - 同一 sync_id 取 updatedAt 较大者（客户端较旧则服务端覆盖并回传）
 * - 删除以 tombstone（deleted=true）记录
 * - 只返回比 clientUpdatedAt 新的变更
 */
object SyncService {

    fun sync(userId: Long, timetableId: Long, req: SyncRequest): SyncResponse? = transaction {
        val row = Timetables.selectAll().where { Timetables.id eq timetableId }.firstOrNull()
            ?: return@transaction null
        if (row[Timetables.ownerId] != userId) return@transaction null

        val now = System.currentTimeMillis()

        fun find(syncId: String): ResultRow? =
            Courses.selectAll()
                .where { Courses.timetableId eq timetableId }
                .firstOrNull { it[Courses.syncId] == syncId }

        for (c in req.upserts) {
            val existing = find(c.id)
            when {
                existing == null -> insertCourse(timetableId, c, maxOf(c.updatedAt, now))
                existing[Courses.deleted] || existing[Courses.updatedAt] < c.updatedAt ->
                    updateCourse(existing[Courses.id], c, maxOf(c.updatedAt, now))
                else -> Unit // 服务端较新，保留并在 changes 中回传
            }
        }

        for (syncId in req.deletes) {
            val existing = find(syncId)
            if (existing != null) {
                Courses.update({ Courses.id eq existing[Courses.id] }) {
                    it[deleted] = true
                    it[updatedAt] = now
                }
            }
        }

        val newer = Courses.selectAll()
            .where { Courses.timetableId eq timetableId }
            .filter { it[Courses.updatedAt] > req.clientUpdatedAt }
        val changes = mutableListOf<CourseIn>()
        val deletedIds = mutableListOf<String>()
        newer.forEach { r ->
            if (r[Courses.deleted]) deletedIds.add(r[Courses.syncId])
            else changes.add(toCourseIn(r))
        }
        val serverUpdatedAt = maxOf(now, newer.maxOfOrNull { it[Courses.updatedAt] } ?: 0)
        SyncResponse(changes, deletedIds, serverUpdatedAt)
    }

    private fun insertCourse(ttId: Long, c: CourseIn, ts: Long) {
        Courses.insert {
            it[timetableId] = ttId
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
            it[updatedAt] = ts
            it[deleted] = false
        }
    }

    private fun updateCourse(dbId: Long, c: CourseIn, ts: Long) {
        Courses.update({ Courses.id eq dbId }) {
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
            it[updatedAt] = ts
            it[deleted] = false
        }
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
