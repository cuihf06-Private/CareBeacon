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
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.carebeacon.app.data.Account
import com.carebeacon.app.data.Relationship

/**
 * Logged-in landing screen. Shows the current account plus the two
 * relationship lists. The "进入监护/被提醒模式（临时）" buttons exist because
 * the legacy Guardian/Ward screens still filter by device role; PR4 retires
 * them and replaces with proper account-aware workspaces.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: AppViewModel,
    onEnterGuardianMode: () -> Unit,
    onEnterWardMode: () -> Unit,
    onOpenInvite: () -> Unit,
) {
    val account by viewModel.currentAccount.collectAsState()
    val myWards by viewModel.myWards.collectAsState()
    val myGuardians by viewModel.myGuardians.collectAsState()
    val accountsById by viewModel.accountsById.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    IconButton(onClick = { viewModel.logout() }) {
                        Text(stringResource(R.string.home_logout), fontSize = 14.sp)
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
            AccountCard(account)
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onEnterGuardianMode,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.home_enter_guardian), fontSize = 13.sp) }
                Button(
                    onClick = onEnterWardMode,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.home_enter_ward), fontSize = 13.sp) }
            }

            Spacer(Modifier.height(16.dp))

            RelationshipsCard(
                title = "${stringResource(R.string.home_my_wards)} (${myWards.size})",
                rows = myWards,
                accountsById = accountsById,
                actionLabel = stringResource(R.string.home_invite),
                onAction = onOpenInvite,
                emptyText = stringResource(R.string.home_empty_wards),
                onRevoke = { viewModel.revokeRelationship(it.id) },
            )

            Spacer(Modifier.height(12.dp))

            RelationshipsCard(
                title = "${stringResource(R.string.home_my_guardians)} (${myGuardians.size})",
                rows = myGuardians,
                accountsById = accountsById,
                emptyText = stringResource(R.string.home_empty_guardians),
                onRevoke = { viewModel.revokeRelationship(it.id) },
            )
        }
    }
}

@Composable
private fun AccountCard(account: Account?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.home_title), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                text = account?.displayName ?: stringResource(R.string.empty),
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
            )
            if (account != null) {
                Text(text = "@${account.username}", fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun RelationshipsCard(
    title: String,
    rows: List<Relationship>,
    accountsById: Map<String, Account>,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    emptyText: String,
    onRevoke: (Relationship) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (actionLabel != null && onAction != null) {
                    IconButton(onClick = onAction) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PersonAdd, contentDescription = actionLabel)
                            Spacer(Modifier.height(0.dp))
                            Text(actionLabel, fontSize = 13.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (rows.isEmpty()) {
                Text(emptyText, fontSize = 13.sp)
            } else {
                LazyColumn {
                    items(rows, key = { it.id }) { rel ->
                        RelationshipRow(rel, accountsById, onRevoke)
                    }
                }
            }
        }
    }
}

@Composable
private fun RelationshipRow(
    rel: Relationship,
    accountsById: Map<String, Account>,
    onRevoke: (Relationship) -> Unit,
) {
    // Self-invite gets a chip label; everything else shows the peer's display
    // name when we can resolve it, otherwise the id prefix.
    val isSelf = rel.wardId == rel.guardianId
    val peerAccount = accountsById[rel.wardId] ?: accountsById[rel.guardianId]
    val peerLabel = peerAccount?.let { "${it.displayName} (@${it.username})" }
        ?: "${rel.wardId.take(8)}…"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = if (isSelf) "自己 (self-invite)" else peerLabel,
                fontSize = 14.sp,
            )
            if (peerAccount == null) {
                Text(
                    text = "id: ${rel.wardId.take(8)}…",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
        IconButton(onClick = { onRevoke(rel) }) {
            Text("撤销", fontSize = 12.sp)
        }
    }
}