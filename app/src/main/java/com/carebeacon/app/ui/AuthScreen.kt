package com.carebeacon.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

/**
 * First-run / logged-out screen. Password-less for now (per design §10.1):
 * login resolves by username alone. Registration also asks for a display
 * name; that is what other accounts see on invitations.
 */
@Composable
fun AuthScreen(
    showLegacyHint: Boolean,
    onLogin: (username: String, onResult: (Result<Unit>) -> Unit) -> Unit,
    onRegister: (username: String, displayName: String, onResult: (Result<Unit>) -> Unit) -> Unit,
) {
    var tab by remember { mutableStateOf(0) } // 0 = login, 1 = register
    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var inFlight by remember { mutableStateOf(false) }

    // Reset transient state whenever the user switches tabs.
    LaunchedEffect(tab) {
        error = null
        inFlight = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.auth_title),
            fontSize = 36.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(text = stringResource(R.string.auth_subtitle), fontSize = 14.sp)

        Spacer(Modifier.height(24.dp))

        TabRow(selectedTabIndex = tab, modifier = Modifier.fillMaxWidth()) {
            Tab(
                selected = tab == 0,
                onClick = { tab = 0 },
                text = { Text(stringResource(R.string.auth_tab_login)) }
            )
            Tab(
                selected = tab == 1,
                onClick = { tab = 1 },
                text = { Text(stringResource(R.string.auth_tab_register)) }
            )
        }

        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it.trim().take(32); error = null },
                    label = { Text(stringResource(R.string.auth_username_label)) },
                    singleLine = true,
                    enabled = !inFlight,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (tab == 1) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it.take(32); error = null },
                        label = { Text(stringResource(R.string.auth_displayname_label)) },
                        singleLine = true,
                        enabled = !inFlight,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                    )
                }

                Spacer(Modifier.height(16.dp))

                val ready = username.isNotBlank() && (tab == 0 || displayName.isNotBlank())
                Button(
                    onClick = {
                        error = null
                        inFlight = true
                        if (tab == 0) {
                            onLogin(username) { result ->
                                inFlight = false
                                result.exceptionOrNull()?.let { error = it.message ?: "登录失败" }
                            }
                        } else {
                            onRegister(username, displayName) { result ->
                                inFlight = false
                                result.exceptionOrNull()?.let { error = it.message ?: "注册失败" }
                            }
                        }
                    },
                    enabled = ready && !inFlight,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (tab == 0) stringResource(R.string.auth_login_action)
                        else stringResource(R.string.auth_register_action)
                    )
                }
            }
        }

        if (showLegacyHint) {
            Spacer(Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.auth_legacy_hint),
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}