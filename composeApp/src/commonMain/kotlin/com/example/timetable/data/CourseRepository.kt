package com.example.timetable.data

import com.example.timetable.model.Course
import com.example.timetable.model.NotificationRule
import com.example.timetable.model.PeriodConfig
import com.example.timetable.model.SemesterConfig
import com.example.timetable.model.TimetableMeta
import com.russhwolf.settings.Settings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 课程本地持久化：Android 用 SharedPreferences，iOS 用 NSUserDefaults，
 * Desktop 用 Java Preferences，全部通过 multiplatform-settings 统一接口。
 */
class CourseRepository(private val settings: Settings?) {

    private val key = "timetable.courses"
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): List<Course> {
        val raw = settings?.getStringOrNull(key) ?: return emptyList()
        return try {
            json.decodeFromString<List<Course>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(courses: List<Course>) {
        settings?.putString(key, json.encodeToString(courses))
    }

    fun loadSyncCode(): String = settings?.getString("timetable.syncCode", "") ?: ""

    fun saveSyncCode(code: String) {
        settings?.putString("timetable.syncCode", code)
    }

    fun loadApiBase(): String = settings?.getString("timetable.apiBase", "") ?: ""

    fun saveApiBase(base: String) {
        settings?.putString("timetable.apiBase", base)
    }

    fun loadSemester(): SemesterConfig {
        val raw = settings?.getString("timetable.semester", "") ?: return SemesterConfig()
        return try {
            json.decodeFromString<SemesterConfig>(raw)
        } catch (_: Exception) {
            SemesterConfig()
        }
    }

    fun saveSemester(config: SemesterConfig) {
        settings?.putString("timetable.semester", json.encodeToString(config))
    }

    fun loadPeriodConfig(): PeriodConfig {
        val raw = settings?.getString("timetable.periods", "") ?: return PeriodConfig()
        return try {
            json.decodeFromString<PeriodConfig>(raw)
        } catch (_: Exception) {
            PeriodConfig()
        }
    }

    fun savePeriodConfig(config: PeriodConfig) {
        settings?.putString("timetable.periods", json.encodeToString(config))
    }

    fun loadNotificationRule(): NotificationRule {
        val raw = settings?.getString("timetable.notification", "") ?: return NotificationRule()
        return try {
            json.decodeFromString<NotificationRule>(raw)
        } catch (_: Exception) {
            NotificationRule()
        }
    }

    fun saveNotificationRule(rule: NotificationRule) {
        settings?.putString("timetable.notification", json.encodeToString(rule))
    }

    fun loadDarkTheme(): Boolean = settings?.getBoolean("timetable.darkTheme", false) ?: false

    fun saveDarkTheme(dark: Boolean) {
        settings?.putBoolean("timetable.darkTheme", dark)
    }

    fun loadAccessToken(): String = settings?.getString("v2.accessToken", "") ?: ""
    fun saveAccessToken(token: String) = settings?.putString("v2.accessToken", token)

    fun loadRefreshToken(): String = settings?.getString("v2.refreshToken", "") ?: ""
    fun saveRefreshToken(token: String) = settings?.putString("v2.refreshToken", token)

    fun loadUserEmail(): String = settings?.getString("v2.userEmail", "") ?: ""
    fun saveUserEmail(email: String) = settings?.putString("v2.userEmail", email)

    fun loadTimetableId(): Long = settings?.getLong("v2.timetableId", 0L) ?: 0L
    fun saveTimetableId(id: Long) = settings?.putLong("v2.timetableId", id)

    fun loadLastSyncedAt(): Long = settings?.getLong("v2.lastSyncedAt", 0L) ?: 0L
    fun saveLastSyncedAt(ts: Long) = settings?.putLong("v2.lastSyncedAt", ts)

    fun loadTimetables(): List<TimetableMeta> {
        val raw = settings?.getString("v2.timetables", "") ?: return emptyList()
        return try {
            json.decodeFromString<List<TimetableMeta>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveTimetables(list: List<TimetableMeta>) {
        settings?.putString("v2.timetables", json.encodeToString(list))
    }

    fun loadCurrentTimetableId(): Long = settings?.getLong("v2.currentTimetableId", 0L) ?: 0L

    fun saveCurrentTimetableId(id: Long) {
        settings?.putLong("v2.currentTimetableId", id)
    }
}
