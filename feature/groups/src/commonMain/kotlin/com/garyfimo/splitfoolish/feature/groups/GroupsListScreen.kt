package com.garyfimo.splitfoolish.feature.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsListScreen(
    groups: List<EditableGroup>,
    friendCount: Int,
    // Firebase auth uid of the signed-in user. Used to gate the per-row Delete button to
    // groups the viewer actually created — Firestore's delete rule rejects anyone else, so
    // showing the button to non-creators would produce a silent-failure tap.
    currentUserUid: String,
    onOpenFriends: () -> Unit,
    onOpenGroup: (groupId: String) -> Unit,
    onDeleteGroup: (groupId: String) -> Unit,
    onNewGroup: () -> Unit,
    // Optional so the screen stays reusable in contexts without auth (tests, previews). When
    // present, a gear-icon action opens the Settings screen — which is where profile editing and
    // sign-out live (previously a top-bar "Sign out" lived here directly).
    onOpenSettings: (() -> Unit)? = null,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Groups") },
                actions = {
                    TextButton(onClick = onOpenFriends) {
                        Text(if (friendCount == 0) "Friends" else "Friends ($friendCount)")
                    }
                    if (onOpenSettings != null) {
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Settings",
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (groups.isEmpty()) {
                Text(
                    "No groups yet. Create one to start tracking shared expenses.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                groups.forEach { group ->
                    GroupRow(
                        group = group,
                        canDelete = group.createdByUid == currentUserUid,
                        onOpen = { onOpenGroup(group.id) },
                        onDelete = { onDeleteGroup(group.id) },
                    )
                }
            }

            Button(
                onClick = onNewGroup,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("+ New group") }
        }
    }
}

@Composable
private fun GroupRow(
    group: EditableGroup,
    canDelete: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(group.name, style = MaterialTheme.typography.titleMedium)
                val memberSummary = when (group.members.size) {
                    0 -> "No members yet"
                    1 -> "1 member"
                    else -> "${group.members.size} members"
                }
                val expenseSummary = when (group.expenses.size) {
                    0 -> "no expenses"
                    1 -> "1 expense"
                    else -> "${group.expenses.size} expenses"
                }
                Text(
                    "$memberSummary · $expenseSummary",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onOpen) { Text("Open") }
            // Delete button only renders when the viewer created the group. Matches the
            // Firestore rule (`request.auth.uid == resource.data.createdByUid`) and the
            // AppState.deleteGroup guard — showing the button to non-creators would produce
            // a silent-failure tap (local removal + Firestore PERMISSION_DENIED + flicker-
            // back on the next observeSnapshot emission).
            if (canDelete) {
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}
