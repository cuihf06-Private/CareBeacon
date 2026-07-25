package com.carebeacon.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RoleSelectScreen(
    demoMode: Boolean,
    onSetDemoMode: (Boolean) -> Unit,
    onGuardian: () -> Unit,
    onWard: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "CareBeacon",
            fontSize = 36.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(text = "请选择身份", fontSize = 18.sp)
        Spacer(Modifier.height(24.dp))
        Text(
            text = "本机只能选一个身份。生产部署中监护人端和被提醒人端分别在两台手机上，通过服务器同步。",
            fontSize = 12.sp
        )
        Spacer(Modifier.height(24.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("我是监护人", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("为长辈或孩子配置不可忽略的提醒", fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text("本机不会被弹窗提醒", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onGuardian, modifier = Modifier.fillMaxWidth()) {
                    Text("以监护人身份进入")
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("我是被提醒人", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("我需要被提醒按时完成任务", fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text("本机只会接收，不会看到配置入口", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onWard, modifier = Modifier.fillMaxWidth()) {
                    Text("以被提醒人身份进入")
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("演示模式", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "同机扮演两个角色，便于无后端时端到端测试。生产环境请关闭。",
                            fontSize = 12.sp
                        )
                    }
                    Switch(checked = demoMode, onCheckedChange = onSetDemoMode)
                }
            }
        }
    }
}