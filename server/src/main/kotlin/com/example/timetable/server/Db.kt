package com.example.timetable.server

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.transactions.transaction

object Users : Table("users") {
    val id = long("id").autoIncrement()
    val email = varchar("email", 255).nullable().uniqueIndex()
    val phone = varchar("phone", 20).nullable().uniqueIndex()
    val passwordHash = varchar("password_hash", 100)
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object RefreshTokens : Table("refresh_tokens") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").references(Users.id)
    val tokenHash = varchar("token_hash", 64).uniqueIndex()
    val expiresAt = long("expires_at")
    val revoked = bool("revoked").default(false)
    override val primaryKey = PrimaryKey(id)
}

object Timetables : Table("timetables") {
    val id = long("id").autoIncrement()
    val ownerId = long("owner_id").references(Users.id)
    val name = varchar("name", 100).default("我的课表")
    val totalWeeks = integer("total_weeks").default(20)
    val currentWeek = integer("current_week").default(1)
    val startDate = varchar("start_date", 10).nullable()
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object PeriodTimes : Table("period_times") {
    val timetableId = long("timetable_id").references(Timetables.id)
    val periodNo = integer("period_no")
    val startTime = varchar("start_time", 5)
    val endTime = varchar("end_time", 5)
    override val primaryKey = PrimaryKey(timetableId, periodNo)
}

object Courses : Table("courses") {
    val id = long("id").autoIncrement()
    val timetableId = long("timetable_id").references(Timetables.id)
    val syncId = varchar("sync_id", 36)
    val name = varchar("name", 100)
    val teacher = varchar("teacher", 50).default("")
    val location = varchar("location", 100).default("")
    val day = integer("day")
    val startPeriod = integer("start_period")
    val endPeriod = integer("end_period")
    val colorIndex = integer("color_index").default(0)
    val startWeek = integer("start_week").nullable()
    val endWeek = integer("end_week").nullable()
    val weekType = integer("week_type").default(0)
    val note = varchar("note", 255).default("")
    val updatedAt = long("updated_at")
    val deleted = bool("deleted").default(false)
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(timetableId, syncId)
        index(isUnique = false, timetableId)
    }
}

object ShareTokens : Table("share_tokens") {
    val token = varchar("token", 16)
    val timetableId = long("timetable_id").references(Timetables.id)
    val permission = varchar("permission", 2).default("RO")
    val createdBy = long("created_by").references(Users.id)
    val revoked = bool("revoked").default(false)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(token)
}

object Exams : Table("exams") {
    val id = long("id").autoIncrement()
    val timetableId = long("timetable_id").references(Timetables.id)
    val name = varchar("name", 100)
    val examDate = varchar("exam_date", 10)
    val startTime = varchar("start_time", 5).nullable()
    val location = varchar("location", 100).default("")
    val note = varchar("note", 255).default("")
    override val primaryKey = PrimaryKey(id)
}

/** 初始化数据库连接并建表。开发默认 SQLite；生产设 DB_URL=jdbc:mysql://... */
fun initDatabase() {
    val url = System.getenv("DB_URL")
        ?: System.getProperty("db.url")
        ?: "jdbc:sqlite:timetable-v2.db"
    val driver = if (url.startsWith("jdbc:mysql")) "com.mysql.cj.jdbc.Driver" else "org.sqlite.JDBC"
    Database.connect(
        url = url,
        driver = driver,
        user = envOrNull("DB_USER") ?: "",
        password = envOrNull("DB_PASSWORD") ?: "",
    )
    transaction {
        SchemaUtils.create(Users, RefreshTokens, Timetables, PeriodTimes, Courses, ShareTokens, Exams)
    }
}

/** 安全读取环境变量：未设置或平台空检查抛异常时返回 null。 */
private fun envOrNull(name: String): String? = try {
    System.getenv(name)
} catch (_: NullPointerException) {
    null
}
