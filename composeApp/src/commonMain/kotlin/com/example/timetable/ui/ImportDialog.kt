package com.example.timetable.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timetable.TimetableState
import com.example.timetable.data.CsvRow
import com.example.timetable.data.parseCsv
import com.example.timetable.model.Course
import com.example.timetable.model.periodCount
import com.example.timetable.platform.pickTextFile
import kotlinx.coroutines.launch

private data class CsvImportResult(
    val valid: List<Course>,
    val errors: List<String>,
)

/** 批量导入：粘贴 CSV 文本，预览后导入。 */
@Composable
fun ImportDialog(state: TimetableState, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var csvText by remember { mutableStateOf("") }
    var imported by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val parsed = remember(csvText) { mapCsv(csvText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("批量导入课程") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "粘贴课表文本（支持逗号或空格分隔；含空格的字段用双引号包裹；模板见 docs/import-template.csv）：",
                    style = MaterialTheme.typography.labelMedium,
                )
                OutlinedTextField(
                    value = csvText,
                    onValueChange = { csvText = it },
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    placeholder = {
                        Text("课程名,教师,教室,星期,开始节,结束节,起始周,结束周,单双周,备注\n高等数学,王老师,A101,1,1,2,1,16,每周,")
                    },
                )
                if (csvText.isNotBlank()) {
                    Text(
                        "识别 ${parsed.valid.size} 条，错误 ${parsed.errors.size} 条",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    parsed.errors.take(8).forEach {
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
                imported?.let {
                    Text("已导入 ${it.first} 条，跳过 ${it.second} 条（同名同位置）")
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = {
                        scope.launch {
                            val text = pickTextFile()
                            if (text != null) {
                                csvText = text
                                imported = null
                            }
                        }
                    },
                ) {
                    Text("选择文件")
                }
                TextButton(onClick = { csvText = sampleCsv() }) { Text("填入示例") }
                TextButton(onClick = onDismiss) { Text("关闭") }
                Button(
                    onClick = {
                        if (parsed.valid.isNotEmpty()) {
                            imported = state.importCourses(parsed.valid)
                            csvText = ""
                        }
                    },
                    enabled = parsed.valid.isNotEmpty(),
                ) {
                    Text("导入")
                }
            }
        },
    )
}

private fun mapCsv(text: String): CsvImportResult {
    if (text.isBlank()) return CsvImportResult(emptyList(), emptyList())
    val parsed = parseCsv(text)
    val valid = mutableListOf<Course>()
    val errors = mutableListOf<String>()

    parsed.rows.forEach { row ->
        mapRow(row)?.let { result ->
            if (result.first != null) valid.add(result.first!!) else errors.add(result.second)
        }
    }
    return CsvImportResult(valid, errors)
}

private fun mapRow(row: CsvRow): Pair<Course?, String>? {
    if (row.fields.isEmpty()) return null
    val f = row.fields.map { it.trim() }
    val name = f.getOrNull(0).orEmpty()
    if (name.isEmpty()) return null to "第 ${row.line} 行：缺少课程名"
    val day = parseDay(f.getOrNull(3).orEmpty())
    if (day == null) return null to "第 ${row.line} 行：星期无效（1-7 或 周一~周日）"
    val start = f.getOrNull(4).orEmpty().toIntOrNull()
    val end = f.getOrNull(5).orEmpty().toIntOrNull()
    if (start == null || end == null || start !in 1..periodCount || end !in 1..periodCount || end < start) {
        return null to "第 ${row.line} 行：节次无效（开始/结束节需在 1..$periodCount 且结束 >= 开始）"
    }
    val startWeek = f.getOrNull(6).orEmpty().toIntOrNull()
    val endWeek = f.getOrNull(7).orEmpty().toIntOrNull()
    if ((startWeek == null) != (endWeek == null)) {
        return null to "第 ${row.line} 行：起始周与结束周需同时填写"
    }
    if (startWeek != null && endWeek != null && startWeek > endWeek) {
        return null to "第 ${row.line} 行：起始周不能大于结束周"
    }
    val weekType = parseWeekType(f.getOrNull(8).orEmpty())
    if (weekType == null) return null to "第 ${row.line} 行：单双周无效（每周/单周/双周）"
    return Course(
        name = name,
        teacher = f.getOrNull(1).orEmpty(),
        location = f.getOrNull(2).orEmpty(),
        day = day,
        startPeriod = start,
        endPeriod = end,
        startWeek = startWeek,
        endWeek = endWeek,
        weekType = weekType,
        note = f.getOrNull(9).orEmpty(),
    ) to ""
}

private fun parseDay(s: String): Int? = when (s) {
    "1", "周一" -> 1
    "2", "周二" -> 2
    "3", "周三" -> 3
    "4", "周四" -> 4
    "5", "周五" -> 5
    "6", "周六" -> 6
    "7", "周日" -> 7
    else -> null
}

private fun parseWeekType(s: String): Int? = when (s) {
    "", "0", "每周" -> 0
    "1", "单周" -> 1
    "2", "双周" -> 2
    else -> null
}

private fun sampleCsv(): String = """
    课程名,教师,教室,星期,开始节,结束节,起始周,结束周,单双周,备注
    高等数学,王老师,A101,1,1,2,1,16,每周,例题多
    大学英语,李老师,B203,1,3,4,,,,
    数据结构,张老师,C305,2,1,2,1,16,单周,
    操作系统,赵老师,D402,3,5,6,2,16,双周,
    体育,刘老师,操场,4,7,8,1,18,每周,
""".trimIndent()
