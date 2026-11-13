package com.garyfimo.splitfoolish.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun GroupsListScreen(state: GroupsAppState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeContentPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Groups",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = { state.screen = Screen.Friends }) {
                val count = state.friends.size
                Text(if (count == 0) "Friends" else "Friends ($count)")
            }
        }

        if (state.groups.isEmpty()) {
            Text(
                "No groups yet. Create one to start tracking shared expenses.",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            state.groups.forEach { group ->
                GroupRow(
                    group = group,
                    onOpen = { state.screen = Screen.GroupDetail(group.id) },
                    onDelete = { state.deleteGroup(group.id) },
                )
            }
        }

        Button(
            onClick = { state.screen = Screen.NewGroup },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("+ New group") }
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
