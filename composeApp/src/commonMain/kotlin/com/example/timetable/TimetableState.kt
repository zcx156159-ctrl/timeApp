package com.example.timetable

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.timetable.data.CloudRepository
import com.example.timetable.data.ShareInfoV2
import com.example.timetable.data.CourseRepository
import com.example.timetable.model.Course
import com.example.timetable.model.NotificationRule
import com.example.timetable.model.PeriodConfig
import com.example.timetable.model.SemesterConfig
import com.example.timetable.model.TimetableMeta
import com.example.timetable.platform.currentTimeMillis

/**
 * 课表状态：持有课程列表并在每次变更后持久化；支持云端共享/多端同步。
 * 纯 Compose 状态，不依赖平台 API。
 */
class TimetableState(
    private val repository: CourseRepository,
    private val cloud: CloudRepository,
) {

    var courses by mutableStateOf(safeLoad())
        private set

    var syncCode by mutableStateOf(safeSyncCode())
        private set

    var apiBase by mutableStateOf(safeApiBase())
        private set

    var syncStatus by mutableStateOf(if (safeSyncCode().isBlank()) "未开启" else "未同步")
        private set

    var syncError by mutableStateOf<String?>(null)
        private set

    var dirtyCount by mutableStateOf(0)
        private set

    var semester by mutableStateOf(repository.loadSemester())
        private set

    var periodConfig by mutableStateOf(repository.loadPeriodConfig())
        private set

    var notificationRule by mutableStateOf(repository.loadNotificationRule())
        private set

    var darkTheme by mutableStateOf(repository.loadDarkTheme())
        private set

    var accessToken by mutableStateOf(repository.loadAccessToken())
        private set

    var userEmail by mutableStateOf(repository.loadUserEmail())
        private set

    var lastSyncedAt by mutableStateOf(repository.loadLastSyncedAt())
        private set

    var pendingDeletes by mutableStateOf<List<String>>(emptyList())
        private set

    var timetables by mutableStateOf(repository.loadTimetables())
        private set

    var currentTimetableId by mutableStateOf(repository.loadCurrentTimetableId())
        private set

    var shares by mutableStateOf<List<ShareInfoV2>>(emptyList())
        private set

    /** 新增（id 为空）或更新（id 已存在）课程。 */
    fun save(course: Course) {
        val newId = if (course.id.isBlank()) newId() else course.id
        val weeks = normalizeWeeks(course.startWeek, course.endWeek)
        val normalized = course.copy(
            id = newId,
            endPeriod = maxOf(course.endPeriod, course.startPeriod),
            startWeek = weeks.first,
            endWeek = weeks.second,
            updatedAt = currentTimeMillis(),
        )
        courses = if (courses.any { it.id == newId }) {
            courses.map { if (it.id == newId) normalized else it }
        } else {
            courses + normalized
        }
        persist()
        dirtyCount++
    }

    fun remove(id: String) {
        courses = courses.filterNot { it.id == id }
        pendingDeletes = pendingDeletes + id
        persist()
        dirtyCount++
    }

    fun clear() {
        pendingDeletes = pendingDeletes + courses.map { it.id }
        courses = emptyList()
        persist()
        dirtyCount++
    }

    fun loadSample() {
        courses = sampleCourses()
        persist()
    }

    fun updateSemester(config: SemesterConfig) {
        semester = config
        repository.saveSemester(config)
    }

    fun updatePeriodConfig(config: PeriodConfig) {
        periodConfig = config
        repository.savePeriodConfig(config)
    }

    fun updateNotificationRule(rule: NotificationRule) {
        notificationRule = rule
        repository.saveNotificationRule(rule)
    }

    fun toggleTheme() {
        darkTheme = !darkTheme
        repository.saveDarkTheme(darkTheme)
    }

    // ---- V2 账号与云端同步 ----

    suspend fun login(account: String, password: String) {
        syncStatus = "登录中…"
        syncError = null
        try {
            val info = cloud.login(apiBase, account, password)
            applyAuth(info.accessToken, info.refreshToken, account)
            refreshTimetables()
            syncStatus = "已登录"
        } catch (e: Exception) {
            syncStatus = "登录失败"
            syncError = e.message
        }
    }

    suspend fun register(email: String, password: String) {
        syncStatus = "注册中…"
        syncError = null
        try {
            val info = cloud.register(apiBase, email, password)
            applyAuth(info.accessToken, info.refreshToken, email)
            refreshTimetables()
            syncStatus = "已注册并登录"
        } catch (e: Exception) {
            syncStatus = "注册失败"
            syncError = e.message
        }
    }

    fun logout() {
        accessToken = ""
        userEmail = ""
        lastSyncedAt = 0
        pendingDeletes = emptyList()
        repository.saveAccessToken("")
        repository.saveRefreshToken("")
        repository.saveUserEmail("")
        repository.saveTimetableId(0)
        repository.saveLastSyncedAt(0)
        timetables = emptyList()
        repository.saveTimetables(emptyList())
        currentTimetableId = 0
        repository.saveCurrentTimetableId(0)
        syncStatus = "未登录"
        syncError = null
    }

    /**
     * 与云端同步。
     * @param fetchOnly true 时只拉取目标课表（切换/新建用），不推送本地变更。
     */
    suspend fun syncWithServer(fetchOnly: Boolean = false) {
        if (accessToken.isBlank()) return
        syncStatus = "云端同步中…"
        syncError = null
        try {
            val tid = ensureTimetableId()
            val now = currentTimeMillis()
            val upserts = if (fetchOnly) {
                emptyList()
            } else {
                courses.map { c -> if (c.updatedAt > 0) c else c.copy(updatedAt = now) }
            }
            val resp = cloud.syncCourses(
                apiBase,
                accessToken,
                tid,
                upserts,
                if (fetchOnly) emptyList() else pendingDeletes,
                lastSyncedAt,
            )
            var merged = if (fetchOnly) mutableListOf<Course>() else courses.toMutableList()
            resp.changes.forEach { sc ->
                val idx = merged.indexOfFirst { it.id == sc.id }
                if (idx >= 0) {
                    if (sc.updatedAt >= merged[idx].updatedAt) merged[idx] = sc
                } else {
                    merged.add(sc)
                }
            }
            resp.deletedIds.forEach { did -> merged.removeAll { it.id == did } }
            courses = merged
            pendingDeletes = if (fetchOnly) emptyList() else pendingDeletes.filterNot { it in resp.deletedIds }
            runCatching { repository.save(courses) }
            lastSyncedAt = resp.serverUpdatedAt
            repository.saveLastSyncedAt(resp.serverUpdatedAt)
            dirtyCount = 0
            syncStatus = if (fetchOnly) "课表已加载" else "已同步"
        } catch (e: Exception) {
            syncStatus = if (fetchOnly) "课表加载失败" else "同步失败"
            syncError = e.message
        }
    }

    private suspend fun ensureTimetableId(): Long {
        if (currentTimetableId > 0) return currentTimetableId
        refreshTimetables()
        if (currentTimetableId > 0) return currentTimetableId
        val created = cloud.createTimetable(apiBase, accessToken, "我的课表").id
        currentTimetableId = created
        repository.saveCurrentTimetableId(created)
        refreshTimetables()
        return created
    }

    // ---- 多课表管理 ----

    suspend fun refreshTimetables() {
        if (accessToken.isBlank()) return
        try {
            val list = cloud.listTimetables(apiBase, accessToken)
            timetables = list.map { TimetableMeta(it.id, it.name) }
            repository.saveTimetables(timetables)
            if (currentTimetableId == 0L || timetables.none { it.id == currentTimetableId }) {
                val first = timetables.firstOrNull()
                currentTimetableId = first?.id ?: 0L
                repository.saveCurrentTimetableId(currentTimetableId)
            }
        } catch (e: Exception) {
            syncError = e.message
        }
    }

    suspend fun createTimetable(name: String) {
        try {
            val t = cloud.createTimetable(apiBase, accessToken, name)
            timetables = timetables + TimetableMeta(t.id, t.name)
            repository.saveTimetables(timetables)
            switchTimetable(t.id)
        } catch (e: Exception) {
            syncError = e.message
        }
    }

    suspend fun renameTimetable(id: Long, name: String) {
        try {
            cloud.renameTimetable(apiBase, accessToken, id, name)
            timetables = timetables.map { if (it.id == id) it.copy(name = name) else it }
            repository.saveTimetables(timetables)
        } catch (e: Exception) {
            syncError = e.message
        }
    }

    suspend fun deleteTimetable(id: Long) {
        try {
            cloud.deleteTimetable(apiBase, accessToken, id)
            timetables = timetables.filterNot { it.id == id }
            repository.saveTimetables(timetables)
            if (currentTimetableId == id) {
                val first = timetables.firstOrNull()
                currentTimetableId = first?.id ?: 0L
                repository.saveCurrentTimetableId(currentTimetableId)
                courses = emptyList()
                pendingDeletes = emptyList()
                runCatching { repository.save(courses) }
            }
        } catch (e: Exception) {
            syncError = e.message
        }
    }

    suspend fun switchTimetable(id: Long) {
        if (id == currentTimetableId) return
        currentTimetableId = id
        repository.saveCurrentTimetableId(id)
        lastSyncedAt = 0
        repository.saveLastSyncedAt(0)
        syncStatus = "切换课表，拉取中…"
        syncWithServer(fetchOnly = true)
    }

    // ---- V2 共享管理 ----

    suspend fun refreshShares() {
        if (accessToken.isBlank()) return
        try {
            shares = cloud.listShares(apiBase, accessToken)
        } catch (e: Exception) {
            syncError = e.message
        }
    }

    suspend fun createShareV2(permission: String): String? {
        if (accessToken.isBlank()) {
            syncError = "请先登录"
            return null
        }
        return try {
            val tid = ensureTimetableId()
            val info = cloud.createShare(apiBase, accessToken, tid, permission)
            shares = shares + ShareInfoV2(info.token, info.timetableId, info.permission)
            syncStatus = "共享码：${info.token}（${if (info.permission == "RW") "可编辑" else "只读"}）"
            info.token
        } catch (e: Exception) {
            syncStatus = "生成失败"
            syncError = e.message
            null
        }
    }

    suspend fun revokeShareV2(shareToken: String) {
        try {
            cloud.revokeShare(apiBase, accessToken, shareToken)
            shares = shares.filterNot { it.token == shareToken }
            syncStatus = "已撤销：$shareToken"
        } catch (e: Exception) {
            syncError = e.message
        }
    }

    /** 用共享码载入他人课表（免登录只读拉取，替换当前视图）。 */
    suspend fun viewShared(shareToken: String) {
        syncStatus = "载入共享课表…"
        syncError = null
        try {
            val data = cloud.fetchPublic(apiBase, shareToken.trim())
            courses = data.courses
            runCatching { repository.save(courses) }
            updateSemester(
                semester.copy(
                    totalWeeks = data.totalWeeks,
                    currentWeek = data.currentWeek,
                    startDate = data.startDate,
                )
            )
            if (data.periods.isNotEmpty()) {
                updatePeriodConfig(PeriodConfig(data.periods))
            }
            dirtyCount = 0
            syncStatus = "已载入：${data.name}"
        } catch (e: Exception) {
            syncStatus = "载入失败"
            syncError = e.message
        }
    }

    private fun applyAuth(token: String, refresh: String, email: String) {
        accessToken = token
        userEmail = email
        repository.saveAccessToken(token)
        repository.saveRefreshToken(refresh)
        repository.saveUserEmail(email)
    }

    /** 向前/后切换当前周（限制在 1..总周数）。 */
    fun goToWeek(delta: Int) {
        val w = (semester.currentWeek + delta).coerceIn(1, semester.totalWeeks)
        updateSemester(semester.copy(currentWeek = w))
    }

    fun resetToFirstWeek() {
        updateSemester(semester.copy(currentWeek = 1))
    }

    /** 批量导入课程；返回 (新增数, 跳过数)。同名+同天+同开始节视为重复，默认跳过。 */
    fun importCourses(newCourses: List<Course>): Pair<Int, Int> {
        var added = 0
        var skipped = 0
        val merged = courses.toMutableList()
        for (c in newCourses) {
            if (merged.any { it.name == c.name && it.day == c.day && it.startPeriod == c.startPeriod }) {
                skipped++
            } else {
                merged.add(c)
                added++
            }
        }
        courses = merged
        persist()
        dirtyCount++
        return added to skipped
    }

    private fun normalizeWeeks(start: Int?, end: Int?): Pair<Int?, Int?> {
        if (start == null && end == null) return null to null
        val s = start ?: end
        val e = end ?: start
        return if (s != null && e != null && s > e) e to s else s to e
    }

    private fun persist() {
        runCatching { repository.save(courses) }
    }

    private fun newId(): String = "c" + kotlin.random.Random.nextLong().toString(16)

    private fun safeLoad(): List<Course> = runCatching { repository.load() }.getOrDefault(emptyList())

    private fun safeSyncCode(): String = runCatching { repository.loadSyncCode() }.getOrDefault("")

    private fun safeApiBase(): String = runCatching { repository.loadApiBase() }.getOrDefault("")

    fun updateApiBase(value: String) {
        apiBase = value
        runCatching { repository.saveApiBase(value) }
    }

    /** 启动时如果有共享码，自动从云端拉取（云端优先）。 */
    suspend fun tryAutoSync() {
        if (accessToken.isNotBlank()) {
            syncWithServer()
            return
        }
        if (syncCode.isBlank()) return
        syncStatus = "自动同步中…"
        try {
            courses = cloud.get(apiBase, syncCode)
            runCatching { repository.save(courses) }
            syncStatus = "已同步"
            syncError = null
            dirtyCount = 0
        } catch (e: Exception) {
            syncStatus = "离线"
            syncError = e.message
        }
    }

    /** 新建共享课表，返回共享码。 */
    suspend fun createShare(): String {
        syncStatus = "正在创建…"
        syncError = null
        return try {
            val code = cloud.create(apiBase, courses)
            syncCode = code
            runCatching { repository.saveSyncCode(code) }
            dirtyCount = 0
            syncStatus = "已创建共享码：$code"
            code
        } catch (e: Exception) {
            syncStatus = "创建失败"
            syncError = e.message
            throw e
        }
    }

    /** 输入共享码加入（用云端数据覆盖本地）。 */
    suspend fun joinShare(code: String) {
        val c = code.trim()
        if (c.length < 4) {
            syncError = "共享码太短"
            return
        }
        syncStatus = "正在下载…"
        syncError = null
        try {
            courses = cloud.get(apiBase, c)
            runCatching { repository.save(courses) }
            syncCode = c
            runCatching { repository.saveSyncCode(c) }
            dirtyCount = 0
            syncStatus = "已加入：$c"
        } catch (e: Exception) {
            syncStatus = "加入失败"
            syncError = e.message
        }
    }

    /** 把本地课表上传覆盖到云端。 */
    suspend fun pushNow() {
        if (syncCode.isBlank()) {
            syncError = "还没有共享码，先新建或加入"
            return
        }
        syncStatus = "正在上传…"
        syncError = null
        try {
            cloud.put(apiBase, syncCode, courses)
            dirtyCount = 0
            syncStatus = "已上传"
        } catch (e: Exception) {
            syncStatus = "上传失败"
            syncError = e.message
        }
    }

    /** 从云端拉取最新课表。 */
    suspend fun refreshNow() {
        if (syncCode.isBlank()) {
            syncError = "还没有共享码，先新建或加入"
            return
        }
        syncStatus = "正在刷新…"
        syncError = null
        try {
            courses = cloud.get(apiBase, syncCode)
            runCatching { repository.save(courses) }
            dirtyCount = 0
            syncStatus = "已刷新"
        } catch (e: Exception) {
            syncStatus = "刷新失败"
            syncError = e.message
        }
    }

    /** 退出同步（本地数据保留，只清掉共享码）。 */
    fun leaveSync() {
        syncCode = ""
        runCatching { repository.saveSyncCode("") }
        dirtyCount = 0
        syncStatus = "未开启"
        syncError = null
    }

    companion object {
        fun sampleCourses(): List<Course> = listOf(
            Course(name = "高等数学", teacher = "王老师", location = "A101", day = 1, startPeriod = 1, endPeriod = 2, colorIndex = 0),
            Course(name = "大学英语", teacher = "李老师", location = "B203", day = 1, startPeriod = 3, endPeriod = 4, colorIndex = 1),
            Course(name = "数据结构", teacher = "张老师", location = "C305", day = 2, startPeriod = 1, endPeriod = 2, colorIndex = 2, startWeek = 1, endWeek = 16, weekType = 1),
            Course(name = "操作系统", teacher = "赵老师", location = "D402", day = 3, startPeriod = 5, endPeriod = 6, colorIndex = 3, startWeek = 2, endWeek = 16, weekType = 2),
            Course(name = "体育", teacher = "刘老师", location = "操场", day = 4, startPeriod = 7, endPeriod = 8, colorIndex = 4, startWeek = 1, endWeek = 18),
            Course(name = "思政课", teacher = "陈老师", location = "A201", day = 5, startPeriod = 1, endPeriod = 2, colorIndex = 5),
        )
    }
}
