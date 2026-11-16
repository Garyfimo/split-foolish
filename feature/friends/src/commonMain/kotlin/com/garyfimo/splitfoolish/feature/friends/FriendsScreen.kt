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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.garyfimo.splitfoolish.core.ui.BackIconButton
import com.garyfimo.splitfoolish.core.ui.ProfileAvatar
import kotlinx.coroutines.launch

/**
 * Friends tab. Post profile-and-username: friends are *linked* references to real SplitFoolish
 * accounts — the add flow takes a username, resolves it via Firestore's /usernames index and
 * stores the uid + cached displayName + iconId. Removing a friend only breaks the local link;
 * the underlying user account is untouched.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    friends: List<EditableFriend>,
    onAddFriendByUsername: suspend (username: String) -> String?,
    onRemoveFriend: (id: String) -> Unit,
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
                "People you can quickly pick when adding members to a group. Find friends by " +
                    "the unique username they set in their Settings screen.",
                style = MaterialTheme.typography.bodySmall,
            )

            if (friends.isEmpty()) {
                Text("No friends yet.", style = MaterialTheme.typography.bodyMedium)
            } else {
                friends.forEach { friend ->
                    FriendRow(
                        friend = friend,
                        onRemove = { onRemoveFriend(friend.id) },
                    )
                }
            }

            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("+ Add friend by username") }
        }
    }

    if (showAddDialog) {
        AddFriendByUsernameDialog(
            onDismiss = { showAddDialog = false },
            onLookup = onAddFriendByUsername,
            onSuccess = { showAddDialog = false },
        )
    }
}

@Composable
private fun FriendRow(
    friend: EditableFriend,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfileAvatar(
            iconId = friend.iconId,
            fallbackLabel = friend.name,
            size = 40.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(friend.name, style = MaterialTheme.typography.bodyLarge)
        }
        OutlinedButton(onClick = onRemove) { Text("Remove") }
    }
}

/**
 * Dialog that takes a username, resolves it to a linked friend via [onLookup]. Mirrors
 * AddMemberByEmailDialog in :feature:groups — same in-flight/inline-error pattern so the user
 * can re-type after a "no such user" without the dialog disappearing. The try/catch around the
 * suspend call matters on iOS: rememberCoroutineScope installs a non-supervisor Job and an
 * uncaught throw reaches propagateExceptionFinalResort (see PR #10 post-review fix for the
 * same shape in the group's add-member dialog).
 */
@Composable
private fun AddFriendByUsernameDialog(
    onDismiss: () -> Unit,
    onLookup: suspend (username: String) -> String?,
    onSuccess: () -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var inFlight by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val canSubmit = !inFlight && username.trim().isNotBlank()

    AlertDialog(
        onDismissRequest = { if (!inFlight) onDismiss() },
        title = { Text("Add friend by username") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "The person needs to have set a username in their own Settings screen first.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it; errorMessage = null },
                    label = { Text("Username") },
                    singleLine = true,
                    enabled = !inFlight,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth(),
                )
                errorMessage?.let { msg ->
                    Text(
                        msg,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSubmit,
                onClick = {
                    inFlight = true
                    errorMessage = null
                    scope.launch {
                        try {
                            val err = onLookup(username)
                            if (err == null) onSuccess() else errorMessage = err
                        } catch (t: Throwable) {
                            errorMessage = "Something went wrong: ${t.message ?: t::class.simpleName}"
                        } finally {
                            inFlight = false
                        }
                    }
                },
            ) {
                if (inFlight) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.width(16.dp))
                } else {
                    Text("Add")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !inFlight) { Text("Cancel") }
        },
    )
}
