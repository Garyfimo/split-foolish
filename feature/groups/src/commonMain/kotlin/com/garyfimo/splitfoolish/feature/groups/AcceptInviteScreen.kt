package com.garyfimo.splitfoolish.feature.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Screen-local invite-preview state. Defined here (rather than reusing the :data layer's
 * GroupInvite / InviteAcceptResult) so :feature:groups stays free of a dependency on :data —
 * the shared App composable does the translation from the data-layer types to these.
 */
sealed interface InvitePreview {
    data class Preview(val groupName: String) : InvitePreview
    data object NotFound : InvitePreview
    data object Expired : InvitePreview
    data class Error(val message: String) : InvitePreview
}

sealed interface AcceptOutcome {
    data object Joined : AcceptOutcome
    data object AlreadyMember : AcceptOutcome
    data class Error(val message: String) : AcceptOutcome
}

/**
 * Deep-link landing for shareable invite URLs. The flow is:
 *
 * 1. On entry, call [onPreview] to resolve the token to a group name — a Firestore read of
 *    `/invites/{token}`. The rule allows any signed-in user to get an invite doc by its id.
 * 2. Render the preview with an Accept button (or an error state if the invite is gone /
 *    expired).
 * 3. On Accept, call [onAccept] which performs the two-step join (see FirestoreInviteRepository
 *    docs). Render the outcome: Joined, AlreadyMember, or an error.
 * 4. Done pops back to the groups list — the newly-joined group will appear once the Firestore
 *    listener picks it up.
 *
 * [onPreview] / [onAccept] are hoisted callbacks because the screen doesn't talk to Firebase
 * directly — the shared App composable wires them to [AppState.previewInvite] /
 * [AppState.acceptInvite].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcceptInviteScreen(
    token: String,
    onPreview: suspend (String) -> InvitePreview,
    onAccept: suspend (String) -> AcceptOutcome,
    onDone: () -> Unit,
) {
    var preview by remember(token) { mutableStateOf<InvitePreview?>(null) }
    var outcome by remember(token) { mutableStateOf<AcceptOutcome?>(null) }
    var accepting by remember(token) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // keyed on token so re-entering with a different invite re-runs the lookup rather than
    // showing the stale preview.
    LaunchedEffect(token) {
        preview = onPreview(token)
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Join group") })
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                outcome != null -> OutcomePanel(
                    outcome = outcome!!,
                    onDone = onDone,
                )

                preview == null -> LoadingPanel()

                else -> PreviewPanel(
                    preview = preview!!,
                    accepting = accepting,
                    onAccept = {
                        accepting = true
                        scope.launch {
                            // try/finally so a thrown exception from the suspend call can't leave
                            // the spinner permanently on.
                            try {
                                outcome = onAccept(token)
                            } catch (t: Throwable) {
                                outcome = AcceptOutcome.Error(
                                    t.message ?: t::class.simpleName ?: "unknown error",
                                )
                            } finally {
                                accepting = false
                            }
                        }
                    },
                    onCancel = onDone,
                )
            }
        }
    }
}

@Composable
private fun LoadingPanel() {
    CircularProgressIndicator()
    Text("Looking up invite…", style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun PreviewPanel(
    preview: InvitePreview,
    accepting: Boolean,
    onAccept: () -> Unit,
    onCancel: () -> Unit,
) {
    when (preview) {
        is InvitePreview.Preview -> {
            Text(
                "Join group",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                preview.groupName.ifBlank { "(unnamed group)" },
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                "By joining, you'll see this group's members and expenses, and everyone in the " +
                    "group will see your profile.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onAccept,
                enabled = !accepting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (accepting) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(16.dp))
                } else {
                    Text("Join")
                }
            }
            TextButton(onClick = onCancel, enabled = !accepting) {
                Text("Not now")
            }
        }

        InvitePreview.NotFound -> ErrorPanel(
            title = "Invite not found",
            body = "This invite link isn't valid anymore. Ask the group owner for a new one.",
            onDone = onCancel,
        )

        InvitePreview.Expired -> ErrorPanel(
            title = "Invite expired",
            body = "This invite link has expired. Ask the group owner for a fresh one.",
            onDone = onCancel,
        )

        is InvitePreview.Error -> ErrorPanel(
            title = "Couldn't open invite",
            body = preview.message,
            onDone = onCancel,
        )
    }
}

@Composable
private fun OutcomePanel(
    outcome: AcceptOutcome,
    onDone: () -> Unit,
) {
    when (outcome) {
        AcceptOutcome.Joined -> ErrorPanel(
            title = "You've joined",
            body = "The group will appear in your list in a moment.",
            onDone = onDone,
        )

        AcceptOutcome.AlreadyMember -> ErrorPanel(
            title = "You're already in this group",
            body = "You're already a member — it's in your groups list.",
            onDone = onDone,
        )

        is AcceptOutcome.Error -> ErrorPanel(
            title = "Couldn't join",
            body = outcome.message,
            onDone = onDone,
        )
    }
}

/** Shared layout for a title-plus-body message with a single Done button. */
@Composable
private fun ErrorPanel(
    title: String,
    body: String,
    onDone: () -> Unit,
) {
    Text(title, style = MaterialTheme.typography.titleLarge)
    Text(body, style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(8.dp))
    Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
        Text("Done")
    }
}
