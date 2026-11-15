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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
    onOpenFriends: () -> Unit,
    onOpenGroup: (groupId: Long) -> Unit,
    onDeleteGroup: (groupId: Long) -> Unit,
    onNewGroup: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Groups") },
                actions = {
                    TextButton(onClick = onOpenFriends) {
                        Text(if (friendCount == 0) "Friends" else "Friends ($friendCount)")
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
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onDelete) { Text("Delete") }
        }
    }
}
