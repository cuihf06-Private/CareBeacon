package com.carebeacon.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carebeacon.app.R
import com.carebeacon.app.data.DuplicateInvite
import com.carebeacon.app.data.GuardianNotFound

/**
 * Modal dialog for inviting an account to act as a guardian. The caller
 * supplies the [currentAccountId] (always the ward in v1) and an
 * [onSubmit] callback that fires `inviteGuardian(currentAccountId, username)`.
 *
 * Errors from the repository are caught here so the sheet can display them
 * inline without the caller needing to plumb an additional error channel.
 */
@Composable
fun InviteSheet(
    onDismiss: () -> Unit,
    onSubmit: (guardianUsername: String, onResult: (Result<Unit>) -> Unit) -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var inFlight by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.invite_title)) },
        text = {
            Column {
                Text(stringResource(R.string.invite_subtitle), fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.invite_self_hint), fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it.trim().take(32); error = null },
                    label = { Text(stringResource(R.string.auth_username_label)) },
                    singleLine = true,
                    enabled = !inFlight,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    error = null
                    inFlight = true
                    onSubmit(username) { result ->
                        inFlight = false
                        val err = result.exceptionOrNull()
                        if (err == null) {
                            onDismiss()
                        } else {
                            error = when (err) {
                                is GuardianNotFound -> "找不到用户 $username"
                                is DuplicateInvite -> "已经是监护人了"
                                else -> err.message ?: "邀请失败"
                            }
                        }
                    }
                },
                enabled = username.isNotBlank() && !inFlight,
            ) { Text(stringResource(R.string.invite_action)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}