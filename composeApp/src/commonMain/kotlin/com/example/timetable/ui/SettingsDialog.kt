package com.example.timetable.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timetable.TimetableState
import com.example.timetable.data.IcalGenerator
import com.example.timetable.model.NotificationRule
import com.example.timetable.model.PeriodConfig
import com.example.timetable.model.SemesterConfig
import com.example.timetable.platform.exportTextFile

/** 设置：学期（总周数/当前周）+ 节次时间表。 */
@Composable
fun SettingsDialog(state: TimetableState, onDismiss: () -> Unit) {
    var totalWeeksText by remember { mutableStateOf(state.semester.totalWeeks.toString()) }
    var currentWeekText by remember { mutableStateOf(state.semester.currentWeek.toString()) }
    var startDateText by remember { mutableStateOf(state.semester.startDate ?: "") }
    var periods by remember { mutableStateOf(state.periodConfig.periods) }
    var remindEnabled by remember { mutableStateOf(state.notificationRule.enabled) }
    var remindMinutesText by remember { mutableStateOf(state.notificationRule.minutesBefore.toString()) }
    var exportMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("学期", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = totalWeeksText,
                        onValueChange = { totalWeeksText = it },
                        label = { Text("总周数") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = currentWeekText,
                        onValueChange = { currentWeekText = it },
                        label = { Text("当前周") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = startDateText,
                    onValueChange = { startDateText = it },
                    label = { Text("学期开始日期（YYYY-MM-DD，iCal 用）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("提醒", style = MaterialTheme.typography.titleSmall)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Checkbox(checked = remindEnabled, onCheckedChange = { remindEnabled = it })
                    Text("上课前提醒")
                    OutlinedTextField(
                        value = remindMinutesText,
                        onValueChange = { remindMinutesText = it },
                        label = { Text("提前分钟数") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text("节次时间", style = MaterialTheme.typography.titleSmall)
                periods.forEachIndexed { index, period ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("第 ${index + 1} 节", modifier = Modifier.width(52.dp), fontSize = MaterialTheme.typography.bodySmall.fontSize)
                        OutlinedTextField(
                            value = period.start,
                            onValueChange = { value ->
                                periods = periods.mapIndexed { i, p -> if (i == index) p.copy(start = value) else p }
                            },
                            label = { Text("开始") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = period.end,
                            onValueChange = { value ->
                                periods = periods.mapIndexed { i, p -> if (i == index) p.copy(end = value) else p }
                            },
                            label = { Text("结束") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                exportMsg?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { periods = PeriodConfig.defaultPeriods() }) { Text("恢复默认") }
                TextButton(
                    onClick = {
                        val ics = IcalGenerator.generate(state.courses, state.semester, state.periodConfig)
                        exportMsg = if (ics.isBlank()) {
                            "请先填写学期开始日期（YYYY-MM-DD）"
                        } else if (exportTextFile("timetable.ics", ics)) {
                            "已导出 timetable.ics"
                        } else {
                            "已取消导出"
                        }
                    },
                ) {
                    Text("导出 iCal")
                }
                TextButton(onClick = onDismiss) { Text("关闭") }
                Button(
                    onClick = {
                        val total = totalWeeksText.toIntOrNull()?.coerceAtLeast(1) ?: state.semester.totalWeeks
                        val current = currentWeekText.toIntOrNull()?.coerceIn(1, total) ?: 1
                        state.updateSemester(
                            SemesterConfig(
                                totalWeeks = total,
                                currentWeek = current,
                                startDate = startDateText.trim().ifBlank { null },
                            )
                        )
                        state.updatePeriodConfig(PeriodConfig(periods))
                        state.updateNotificationRule(
                            NotificationRule(
                                enabled = remindEnabled,
                                minutesBefore = remindMinutesText.toIntOrNull()?.coerceIn(1, 120) ?: 10,
                            )
                        )
                        onDismiss()
                    },
                ) {
                    Text("保存")
                }
            }
        },
    )
}
