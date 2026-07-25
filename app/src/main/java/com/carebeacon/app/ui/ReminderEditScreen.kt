package com.carebeacon.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carebeacon.app.data.Reminder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderEditScreen(
    initial: Reminder?,
    onSave: (String, Int, Int, Int, String) -> Unit,
    onCancel: () -> Unit
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var hour by remember { mutableStateOf(initial?.hour ?: 8) }
    var minute by remember { mutableStateOf(initial?.minute ?: 0) }
    var weekMask by remember { mutableStateOf(initial?.weekMask ?: 0) }

    val days = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    Scaffold(topBar = { TopAppBar(title = { Text(if (initial == null) "新建提醒" else "编辑提醒") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("提醒标题") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("备注（可选）") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Text("时间", fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                NumberPicker(
                    value = hour,
                    range = 0..23,
                    onChange = { hour = it },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.height(0.dp))
                Text("时", fontSize = 16.sp)
                NumberPicker(
                    value = minute,
                    range = 0..59,
                    onChange = { minute = it },
                    modifier = Modifier.weight(1f)
                )
                Text("分", fontSize = 16.sp)
            }
            Spacer(Modifier.height(16.dp))
            Text("按周重复（不勾选 = 仅一次）", fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Column {
                days.forEachIndexed { i, label ->
                    val bit = 1 shl i
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = (weekMask and bit) != 0,
                            onCheckedChange = {
                                weekMask = if (it) weekMask or bit else weekMask and bit.inv()
                            }
                        )
                        Text(label, fontSize = 14.sp)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(onClick = onCancel) { Text("取消") }
                Button(onClick = { onSave(title, hour, minute, weekMask, note) }) { Text("保存") }
            }
        }
    }
}

@Composable
private fun NumberPicker(
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(onClick = { onChange(if (value > range.first) value - 1 else range.last) }) { Text("-") }
        Text(String.format("%02d", value), fontSize = 18.sp)
        Button(onClick = { onChange(if (value < range.last) value + 1 else range.first) }) { Text("+") }
    }
}