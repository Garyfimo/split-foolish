package com.garyfimo.splitfoolish.groups

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewGroupScreen(state: GroupsAppState) {
    var name by remember { mutableStateOf("") }
    val selectedFriendIds: SnapshotStateList<Long> = remember { mutableStateListOf() }
    var showAddFriendDialog by remember { mutableStateOf(false) }

    val canCreate = name.isNotBlank() && selectedFriendIds.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New group") },
                navigationIcon = { BackIconButton(onClick = { state.screen = Screen.GroupsList }) },
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
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Group name (e.g. Beach trip)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            FriendPickerSection(
                friends = state.friends,
                selectedFriendIds = selectedFriendIds,
                onAddFriend = { showAddFriendDialog = true },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val group = state.createGroup(name.trim())
                        selectedFriendIds.forEach { friendId ->
                            val friend = state.friends.firstOrNull { it.id == friendId } ?: return@forEach
                            group.members.add(EditableMember(state.nextId(), friend.name.trim()))
                        }
                        state.screen = Screen.GroupDetail(group.id)
                    },
                    enabled = canCreate,
                    modifier = Modifier.weight(1f),
                ) { Text("Create") }
                OutlinedButton(
                    onClick = { state.screen = Screen.GroupsList },
                    modifier = Modifier.weight(1f),
                ) { Text("Cancel") }
            }

            if (!canCreate) {
                val hint = when {
                    name.isBlank() && selectedFriendIds.isEmpty() -> "Enter a name and pick at least one friend."
                    name.isBlank() -> "Enter a group name."
                    else -> "Pick at least one friend."
                }
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showAddFriendDialog) {
        AddFriendInlineDialog(
            onDismiss = { showAddFriendDialog = false },
            onConfirm = { friendName ->
                val friend = state.addFriend(friendName)
                selectedFriendIds.add(friend.id)
                showAddFriendDialog = false
            },
        )
    }
}

@Composable
private fun FriendPickerSection(
    friends: List<EditableFriend>,
    selectedFriendIds: SnapshotStateList<Long>,
    onAddFriend: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Members", style = MaterialTheme.typography.titleMedium)

        if (friends.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "No friends yet — add one to include in this group.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = onAddFriend, modifier = Modifier.fillMaxWidth()) {
                        Text("+ Add friend")
                    }
                }
            }
            return@Column
        }

        Text(
            "Pick at least one friend to add.",
            style = MaterialTheme.typography.bodySmall,
        )
        friends.forEach { friend ->
            val checked = friend.id in selectedFriendIds
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { shouldCheck ->
                        if (shouldCheck) selectedFriendIds.add(friend.id)
                        else selectedFriendIds.remove(friend.id)
                    },
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    friend.name.trim().ifEmpty { "(unnamed)" },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        OutlinedButton(onClick = onAddFriend, modifier = Modifier.fillMaxWidth()) {
            Text("+ Add another friend")
        }
    }
}

@Composable
private fun AddFriendInlineDialog(
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
