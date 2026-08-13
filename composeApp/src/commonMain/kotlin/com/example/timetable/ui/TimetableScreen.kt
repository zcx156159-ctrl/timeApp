package com.example.timetable.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timetable.TimetableState
import com.example.timetable.data.scheduleTodayReminders
import com.example.timetable.model.Course
import com.example.timetable.model.PeriodConfig
import com.example.timetable.model.isActiveOn
import com.example.timetable.model.parseMinutes
import com.example.timetable.model.periodCount
import com.example.timetable.model.weekDayLabels
import com.example.timetable.platform.currentDayIndex
import com.example.timetable.platform.currentMinutes

val courseColors = listOf(
    Color(0xFF4CAF50),
    Color(0xFF2196F3),
    Color(0xFFFF9800),
    Color(0xFF9C27B0),
    Color(0xFFF44336),
    Color(0xFF009688),
    Color(0xFF3F51B5),
    Color(0xFF795548),
)

private val periodColWidth = 72.dp
private val cellWidth = 100.dp
private val cellHeight = 48.dp
private val headerHeight = 40.dp
private val borderColor = Color(0xFFE0E0E0)
private val todayHighlight = Color(0x1A3F51B5)
private val todayHighlightLight = Color(0x0D3F51B5)

@Composable
fun TimetableScreen(state: TimetableState) {
    var editing by remember { mutableStateOf<Course?>(null) }
    var defaults by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var showSync by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var showTimetables by remember { mutableStateOf(false) }
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()
    val todayIndex = remember { currentDayIndex() }
    val density = LocalDensity.current
    val visibleCourses = remember(state.courses, state.semester.currentWeek) {
        state.courses.filter { it.isActiveOn(state.semester.currentWeek) }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        TopBar(
            onAdd = {
                editing = null
                defaults = null
                showDialog = true
            },
            onSample = { state.loadSample() },
            onClear = { state.clear() },
            onSync = { showSync = true },
            onSettings = { showSettings = true },
            onImport = { showImport = true },
            onTimetables = { showTimetables = true },
            darkTheme = state.darkTheme,
            onToggleTheme = { state.toggleTheme() },
        )

        WeekBar(
            currentWeek = state.semester.currentWeek,
            totalWeeks = state.semester.totalWeeks,
            onPrev = { state.goToWeek(-1) },
            onNext = { state.goToWeek(1) },
            onReset = { state.resetToFirstWeek() },
        )

        TimetableGrid(
            courses = visibleCourses,
            periodConfig = state.periodConfig,
            todayIndex = todayIndex,
            hScroll = hScroll,
            vScroll = vScroll,
            onCourseClick = { course ->
                editing = course
                defaults = null
                showDialog = true
            },
            onEmptyClick = { day, period ->
                editing = null
                defaults = day to period
                showDialog = true
            },
        )
    }

    LaunchedEffect(state.dirtyCount) {
        if (state.dirtyCount > 0) {
            if (state.accessToken.isNotBlank()) {
                state.syncWithServer()
            } else if (state.syncCode.isNotBlank()) {
                state.pushNow()
            }
        }
    }

    LaunchedEffect(Unit) {
        state.tryAutoSync()
    }

    LaunchedEffect(Unit) {
        val now = currentMinutes()
        val idx = state.periodConfig.periods.indexOfFirst { p ->
            val s = parseMinutes(p.start)
            val e = parseMinutes(p.end)
            s != null && e != null && now in s..e
        }
        if (idx >= 0) {
            val px = with(density) { (cellHeight * idx).roundToPx() }
            vScroll.scrollTo(px)
        }
    }

    LaunchedEffect(state.courses, state.semester, state.periodConfig, state.notificationRule) {
        scheduleTodayReminders(
            courses = state.courses,
            semester = state.semester,
            periodConfig = state.periodConfig,
            minutesBefore = state.notificationRule.minutesBefore,
            enabled = state.notificationRule.enabled,
        )
    }

    if (showDialog) {
        CourseDialog(
            editing = editing,
            defaultDay = defaults?.first ?: 1,
            defaultPeriod = defaults?.second ?: 1,
            onDismiss = { showDialog = false },
            onSave = { course ->
                state.save(course)
                showDialog = false
            },
            onDelete = { id ->
                state.remove(id)
                showDialog = false
            },
        )
    }

    if (showSync) {
        CloudSyncDialog(state = state, onDismiss = { showSync = false })
    }

    if (showSettings) {
        SettingsDialog(state = state, onDismiss = { showSettings = false })
    }

    if (showImport) {
        ImportDialog(state = state, onDismiss = { showImport = false })
    }

    if (showTimetables) {
        TimetableDialog(state = state, onDismiss = { showTimetables = false })
    }
}

@Composable
private fun TopBar(
    onAdd: () -> Unit,
    onSample: () -> Unit,
    onClear: () -> Unit,
    onSync: () -> Unit,
    onSettings: () -> Unit,
    onImport: () -> Unit,
    onTimetables: () -> Unit,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF3F51B5)).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "我的课表",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onTimetables) { Text("课表", color = Color.White) }
        TextButton(onClick = onSample) { Text("示例", color = Color.White) }
        TextButton(onClick = onSettings) { Text("设置", color = Color.White) }
        TextButton(onClick = onImport) { Text("导入", color = Color.White) }
        TextButton(onClick = onClear) { Text("清空", color = Color.White) }
        TextButton(onClick = onSync) { Text("同步", color = Color.White) }
        Button(
            onClick = onAdd,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color(0xFF3F51B5),
            ),
        ) {
            Text("新增课程")
        }
        TextButton(onClick = onToggleTheme) {
            Text(if (darkTheme) "浅色" else "深色", color = Color.White)
        }
    }
}

@Composable
private fun WeekBar(
    currentWeek: Int,
    totalWeeks: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onReset: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TextButton(onClick = onPrev) { Text("◀") }
        Text(
            "第 $currentWeek 周 / 共 $totalWeeks 周",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onNext) { Text("▶") }
        TextButton(onClick = onReset) { Text("首周") }
    }
}

@Composable
private fun TimetableGrid(
    courses: List<Course>,
    periodConfig: PeriodConfig,
    todayIndex: Int,
    hScroll: ScrollState,
    vScroll: ScrollState,
    onCourseClick: (Course) -> Unit,
    onEmptyClick: (day: Int, period: Int) -> Unit,
) {
    Box(
        Modifier.fillMaxSize().horizontalScroll(hScroll).verticalScroll(vScroll),
    ) {
        Column {
            Row {
                GridCell(Modifier.width(periodColWidth).height(headerHeight)) {
                    Text("节", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                for (day in 1..7) {
                    GridCell(
                        Modifier.width(cellWidth).height(headerHeight)
                            .background(if (day == todayIndex) todayHighlight else Color.Transparent),
                    ) {
                        Text(weekDayLabels[day - 1], fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }

            for (period in 1..periodCount) {
                Row {
                    GridCell(Modifier.width(periodColWidth).height(cellHeight)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$period", fontSize = 12.sp, color = Color.Gray)
                            val time = periodConfig.periods.getOrNull(period - 1)
                            if (time != null) {
                                Text(
                                    "${time.start}-${time.end}",
                                    fontSize = 8.sp,
                                    color = Color.Gray,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                    for (day in 1..7) {
                        val startsHere = courses.any { it.day == day && it.startPeriod == period }
                        val covered = courses.any { it.day == day && period in it.startPeriod..it.endPeriod }
                        Box(
                            Modifier.width(cellWidth).height(cellHeight)
                                .background(if (day == todayIndex) todayHighlightLight else Color.Transparent)
                                .border(1.dp, borderColor)
                                .clickable(enabled = !startsHere && !covered) { onEmptyClick(day, period) },
                        )
                    }
                }
            }
        }

        // 课程卡片按绝对坐标叠加在网格上方，支持跨节次
        courses.forEach { course ->
            val span = (course.endPeriod - course.startPeriod + 1).coerceAtLeast(1)
            val x = periodColWidth + cellWidth * (course.day - 1)
            val y = headerHeight + cellHeight * (course.startPeriod - 1)
            CourseCard(
                modifier = Modifier
                    .offset(x = x + 2.dp, y = y + 2.dp)
                    .width(cellWidth - 4.dp)
                    .height(cellHeight * span - 4.dp),
                course = course,
                onClick = { onCourseClick(course) },
            )
        }
    }
}

@Composable
private fun GridCell(modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier.border(1.dp, borderColor), contentAlignment = Alignment.Center) {
        content()
    }
}

@Composable
private fun CourseCard(
    modifier: Modifier,
    course: Course,
    onClick: () -> Unit,
) {
    val color = courseColors[course.colorIndex % courseColors.size]
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .clickable(onClick = onClick)
            .padding(6.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column {
            Text(
                course.name,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (course.location.isNotBlank()) {
                Text(
                    course.location,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
