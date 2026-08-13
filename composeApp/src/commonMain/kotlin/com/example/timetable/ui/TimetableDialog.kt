package com.example.timetable.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timetable.TimetableState
import kotlinx.coroutines.launch

/** 多课表管理：列表 / 切换 / 新建 / 重命名 / 删除。 */
@Composable
fun TimetableDialog(state: TimetableState, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var newName by remember { mutableStateOf("") }
    var renameId by remember { mutableStateOf<Long?>(null) }
    var renameText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("我的课表") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.timetables.isEmpty()) {
                    Text("还没有课表，登录账号后新建。", style = MaterialTheme.typography.bodySmall)
                }
                state.timetables.forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (t.id == state.currentTimetableId) "● ${t.name}" else t.name,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { scope.launch { state.switchTimetable(t.id) } }) { Text("切换") }
                        TextButton(onClick = { renameId = t.id; renameText = t.name }) { Text("改名") }
                        TextButton(onClick = { scope.launch { state.deleteTimetable(t.id) } }) { Text("删除") }
                    }
                }
                if (renameId != null) {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        label = { Text("课表名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            if (renameText.isNotBlank()) {
                                val id = renameId
                                scope.launch {
                                    state.renameTimetable(id!!, renameText.trim())
                                    renameId = null
                                }
                            }
                        },
                    ) {
                        Text("保存名称")
                    }
                }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("新课表名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            val name = newName.trim()
                            scope.launch {
                                state.createTimetable(name)
                                newName = ""
                            }
                        }
                    },
                ) {
                    Text("新建课表")
                }
                state.syncError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}
