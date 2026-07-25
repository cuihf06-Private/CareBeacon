package com.carebeacon.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carebeacon.app.R
import com.carebeacon.app.data.AckLog
import com.carebeacon.app.data.Reminder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WardScreen(
    viewModel: AppViewModel,
    onRequestPermissions: () -> Unit,
    onArmReminders: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val reminders by viewModel.reminders.collectAsState()
    val acks by viewModel.acks.collectAsState()
    val demo by viewModel.demoMode.collectAsState()
    var input by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("今日提醒") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Text(stringResource(R.string.back), fontSize = 14.sp)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("守护本机", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (demo)
                            "演示模式：本机同时作为监护人和被提醒人。"
                        else
                            "本机只会接收弹窗，不会看到配置入口。",
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onRequestPermissions,
                            modifier = Modifier.weight(1f)
                        ) { Text("开启保活权限") }
                        Button(
                            onClick = onArmReminders,
                            modifier = Modifier.weight(1f)
                        ) { Text("启动守护") }
                    }
                }
            }

            if (!demo) {
                Spacer(Modifier.height(8.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("输入监护人邀请码", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it.filter { c -> c.isDigit() }.take(6) },
                            label = { Text("6 位数字码") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.pairWithGuardian(input) },
                            enabled = input.length == 6,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("配对")
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text("已配置的提醒", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (reminders.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无提醒，请让监护人在其手机上配置", fontSize = 14.sp)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(reminders, key = { it.id }) { r ->
                        ReminderRow(r, acks.firstOrNull { it.reminderId == r.id })
                    }
                }
            }
        }
    }
}

@Composable
private fun ReminderRow(r: Reminder, lastAck: AckLog?) {
    val now = System.currentTimeMillis()
    val todayCutoff = now - 24L * 3600L * 1000L
    val status = when {
        lastAck != null && lastAck.acknowledgedAt >= todayCutoff -> "已确认"
        r.nextTriggerAt < now - 30 * 60_000L -> "已超时"
        else -> "待提醒"
    }
    val color = when (status) {
        "已确认" -> MaterialTheme.colorScheme.secondary
        "已超时" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(r.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(String.format("%02d:%02d", r.hour, r.minute), fontSize = 14.sp)
                if (lastAck != null) {
                    val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                    Text(
                        "上次确认：${fmt.format(Date(lastAck.acknowledgedAt))}",
                        fontSize = 12.sp
                    )
                }
            }
            Text(status, color = color, fontWeight = FontWeight.Bold)
        }
    }
}