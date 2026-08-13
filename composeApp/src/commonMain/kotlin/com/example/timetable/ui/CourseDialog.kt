@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.timetable.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.timetable.model.Course
import com.example.timetable.model.periodCount
import com.example.timetable.model.weekDayLabels
import com.example.timetable.model.weekTypeLabels

@Composable
fun CourseDialog(
    editing: Course?,
    defaultDay: Int,
    defaultPeriod: Int,
    onDismiss: () -> Unit,
    onSave: (Course) -> Unit,
    onDelete: (String) -> Unit,
) {
    var name by remember { mutableStateOf(editing?.name ?: "") }
    var teacher by remember { mutableStateOf(editing?.teacher ?: "") }
    var location by remember { mutableStateOf(editing?.location ?: "") }
    var day by remember { mutableIntStateOf(editing?.day ?: defaultDay) }
    var start by remember { mutableIntStateOf(editing?.startPeriod ?: defaultPeriod) }
    var end by remember {
        mutableIntStateOf(
            maxOf(editing?.endPeriod ?: defaultPeriod, editing?.startPeriod ?: defaultPeriod)
        )
    }
    var colorIndex by remember { mutableIntStateOf(editing?.colorIndex ?: 0) }
    var weekType by remember { mutableIntStateOf(editing?.weekType ?: 0) }
    var startWeekText by remember { mutableStateOf(editing?.startWeek?.toString() ?: "") }
    var endWeekText by remember { mutableStateOf(editing?.endWeek?.toString() ?: "") }
    var note by remember { mutableStateOf(editing?.note ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing == null) "新增课程" else "编辑课程") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("课程名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = teacher,
                    onValueChange = { teacher = it },
                    label = { Text("教师（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("教室（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LabeledDropdown(
                    label = "星期",
                    options = weekDayLabels,
                    selectedIndex = day - 1,
                    onSelect = { day = it + 1 },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabeledDropdown(
                        label = "开始节",
                        options = (1..periodCount).map { "第 ${it} 节" },
                        selectedIndex = start - 1,
                        onSelect = {
                            start = it + 1
                            if (end < start) end = start
                        },
                        modifier = Modifier.weight(1f),
                    )
                    LabeledDropdown(
                        label = "结束节",
                        options = (1..periodCount).map { "第 ${it} 节" },
                        selectedIndex = end - 1,
                        onSelect = { end = it + 1 },
                        modifier = Modifier.weight(1f),
                    )
                }
                LabeledDropdown(
                    label = "单双周",
                    options = weekTypeLabels,
                    selectedIndex = weekType,
                    onSelect = { weekType = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startWeekText,
                        onValueChange = { startWeekText = it },
                        label = { Text("起始周（空=全学期）") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = endWeekText,
                        onValueChange = { endWeekText = it },
                        label = { Text("结束周（空=全学期）") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（可选）") },
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("课程颜色", style = MaterialTheme.typography.labelMedium)
                ColorRow(selected = colorIndex, onSelect = { colorIndex = it })
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (editing != null) {
                    TextButton(
                        onClick = { onDelete(editing!!.id) },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text("删除")
                    }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            onSave(
                                Course(
                                    id = editing?.id ?: "",
                                    name = name.trim(),
                                    teacher = teacher.trim(),
                                    location = location.trim(),
                                    day = day,
                                    startPeriod = start,
                                    endPeriod = end,
                                    colorIndex = colorIndex,
                                    startWeek = startWeekText.trim().toIntOrNull(),
                                    endWeek = endWeekText.trim().toIntOrNull(),
                                    weekType = weekType,
                                    note = note.trim(),
                                )
                            )
                        }
                    },
                    enabled = name.isNotBlank(),
                ) {
                    Text("保存")
                }
            }
        },
    )
}

@Composable
private fun LabeledDropdown(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = options.getOrElse(selectedIndex) { "" },
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(index)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ColorRow(selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        courseColors.forEachIndexed { index, color ->
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (index == selected) 3.dp else 1.dp,
                        color = if (index == selected) Color.DarkGray else Color.Gray,
                        shape = CircleShape,
                    )
                    .clickable { onSelect(index) },
            )
        }
    }
}
