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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carebeacon.app.R
import com.carebeacon.app.data.Reminder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardianScreen(
    viewModel: AppViewModel,
    demoMode: Boolean,
    onAdd: () -> Unit,
    onTest: (Reminder) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val reminders by viewModel.reminders.collectAsState()
    val pairCode by viewModel.pairCode.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("提醒管理") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Text(stringResource(R.string.back), fontSize = 14.sp)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = "新建提醒")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("你的邀请码", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = pairCode ?: "正在生成…",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (demoMode)
                            "演示模式已开启：两角色共享本机数据库。"
                        else
                            "生产模式下，被提醒人手机需输入此码完成配对。",
                        fontSize = 12.sp
                    )
                }
            }

            if (!demoMode) {
                Spacer(Modifier.height(8.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("生产模式", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "本机不会响铃。请在被提醒人手机上启动 CareBeacon 并输入上方邀请码。",
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            if (reminders.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无提醒，点击右下角加号新建", fontSize = 14.sp)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(reminders, key = { it.id }) { r ->
                        ReminderRow(
                            reminder = r,
                            demoMode = demoMode,
                            onDelete = { viewModel.deleteReminder(r) },
                            onTest = { onTest(r) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReminderRow(
    reminder: Reminder,
    demoMode: Boolean,
    onDelete: () -> Unit,
    onTest: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(reminder.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    String.format("%02d:%02d", reminder.hour, reminder.minute),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                if (reminder.note.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(reminder.note, fontSize = 12.sp)
                }
                val repeat = if (reminder.weekMask == 0) "仅一次" else "每周重复"
                Text(repeat, fontSize = 12.sp)
            }
            // Strict role rule: the test-fire button only exists in demo mode.
            // In production the Guardian never sees the alert.
            if (demoMode) {
                IconButton(onClick = onTest) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "演示：立即触发")
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除")
            }
        }
    }
}