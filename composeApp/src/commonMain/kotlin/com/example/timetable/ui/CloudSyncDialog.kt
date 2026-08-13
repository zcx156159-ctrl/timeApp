package com.example.timetable.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

@Composable
fun CloudSyncDialog(state: TimetableState, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var joinCode by remember { mutableStateOf("") }
    var accountText by remember { mutableStateOf("") }
    var passwordText by remember { mutableStateOf("") }
    var viewShareText by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(state.userEmail) {
        if (state.userEmail.isNotBlank()) {
            state.refreshShares()
        }
    }

    fun runBusy(block: suspend () -> Unit) {
        if (!busy) scope.launch {
            busy = true
            try {
                block()
            } finally {
                busy = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("云同步 / 共享课表") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.userEmail.isBlank()) {
                    Text("账号（云同步）", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = accountText,
                        onValueChange = { accountText = it },
                        label = { Text("邮箱/手机号") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = passwordText,
                        onValueChange = { passwordText = it },
                        label = { Text("密码（至少 6 位）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { runBusy { state.login(accountText.trim(), passwordText) } }) { Text("登录") }
                        TextButton(onClick = { runBusy { state.register(accountText.trim(), passwordText) } }) { Text("注册") }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("账号：${state.userEmail}", modifier = Modifier.weight(1f))
                        TextButton(onClick = { state.logout() }) { Text("退出") }
                        TextButton(onClick = { runBusy { state.syncWithServer() } }) { Text("立即同步") }
                    }
                    Text("共享管理", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { runBusy { state.createShareV2("RO") } }) { Text("生成只读码") }
                        TextButton(onClick = { runBusy { state.createShareV2("RW") } }) { Text("生成可编辑码") }
                        TextButton(onClick = { runBusy { state.refreshShares() } }) { Text("刷新") }
                    }
                    state.shares.take(5).forEach { s ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${s.token} ${if (s.permission == "RW") "可编辑" else "只读"}",
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { scope.launch { state.revokeShareV2(s.token) } }) { Text("撤销") }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = viewShareText,
                            onValueChange = { viewShareText = it },
                            label = { Text("输入共享码查看") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                if (viewShareText.isNotBlank()) runBusy { state.viewShared(viewShareText) }
                            },
                        ) {
                            Text("载入")
                        }
                    }
                }
                Text("共享课表（旧版）", style = MaterialTheme.typography.titleSmall)
                Text("共享码：${state.syncCode.ifBlank { "（无）" }}")
                Text(
                    "状态：${state.syncStatus}",
                    color = if (state.syncError == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                state.syncError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
                OutlinedTextField(
                    value = state.apiBase,
                    onValueChange = { state.updateApiBase(it) },
                    label = { Text("服务器地址") },
                    supportingText = { Text("留空=同源 /api，桌面/手机填 http://服务器:8080") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = joinCode,
                    onValueChange = { joinCode = it },
                    label = { Text("输入共享码加入") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { runBusy { state.createShare() } }) { Text("新建共享") }
                    TextButton(
                        onClick = {
                            if (joinCode.isNotBlank()) runBusy { state.joinShare(joinCode) }
                        },
                    ) {
                        Text("加入")
                    }
                    TextButton(onClick = { runBusy { state.pushNow() } }) { Text("上传") }
                    TextButton(onClick = { runBusy { state.refreshNow() } }) { Text("刷新") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { state.leaveSync() }) { Text("退出同步") }
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
            }
        },
    )
}
