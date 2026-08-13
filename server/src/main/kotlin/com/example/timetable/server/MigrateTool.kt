package com.example.timetable.server

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.nio.file.Path

/**
 * 迁移工具：把旧版 timetables.json 导入数据库。
 * 用法: MigrateToolKt <timetables.json> <ownerEmail> [password]
 */
fun main(args: Array<String>) {
    val dataFile = args.getOrNull(0) ?: error("用法: MigrateToolKt <timetables.json> <ownerEmail> [password]")
    val ownerEmail = args.getOrNull(1) ?: error("缺少 ownerEmail")
    val password = args.getOrNull(2) ?: "migrate-pass-123"

    initDatabase()

    val ownerId = runCatching { AuthService.register(ownerEmail, null, password).userId }.getOrElse {
        transaction {
            Users.selectAll().where { Users.email eq ownerEmail }.firstOrNull()?.get(Users.id)
        } ?: throw IllegalStateException("用户创建/查找失败")
    }

    val json = Json { ignoreUnknownKeys = true }
    val data = json.decodeFromString<Map<String, StoredTimetable>>(Files.readString(Path.of(dataFile)))
    var count = 0
    data.forEach { (code, stored) ->
        val now = System.currentTimeMillis()
        val ttId = transaction {
            Timetables.insert {
                it[Timetables.ownerId] = ownerId
                it[Timetables.name] = "迁移课表 $code"
                it[Timetables.createdAt] = now
                it[Timetables.updatedAt] = now
            } get Timetables.id
        }
        transaction {
            stored.courses.forEach { c ->
                Courses.insert {
                    it[Courses.timetableId] = ttId
                    it[Courses.syncId] = c.id.ifBlank { code + "-" + kotlin.random.Random.nextLong().toString(16) }
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
                    it[Courses.updatedAt] = stored.updatedAt
                    it[Courses.deleted] = false
                }
            }
        }
        count++
    }
    println("迁移完成：$count 份课表，owner=$ownerEmail")
}
