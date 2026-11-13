package com.garyfimo.splitfoolish.feature.friends

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garyfimo.splitfoolish.core.ui.BackIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    friends: List<EditableFriend>,
    onAddFriend: (name: String) -> Unit,
    onRemoveFriend: (id: Long) -> Unit,
    onBack: () -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Friends") },
                navigationIcon = { BackIconButton(onClick = onBack) },
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
            Text(
                "People you can quickly pick when adding members to a group. " +
                    "Removing a friend here doesn't change any existing group.",
                style = MaterialTheme.typography.bodySmall,
            )

            if (friends.isEmpty()) {
                Text("No friends yet.", style = MaterialTheme.typography.bodyMedium)
            } else {
                friends.forEach { friend ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = friend.name,
                            onValueChange = { friend.name = it },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { onRemoveFriend(friend.id) }) { Text("Remove") }
                    }
                }
            }

            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("+ Add friend") }
        }
    }

    if (showAddDialog) {
        AddFriendDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name ->
                onAddFriend(name)
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun AddFriendDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add friend") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
