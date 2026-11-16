package com.garyfimo.splitfoolish

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.garyfimo.splitfoolish.core.ui.BackIconButton
import com.garyfimo.splitfoolish.core.ui.ProfileAvatar
import com.garyfimo.splitfoolish.core.ui.profileIconCatalog
import com.garyfimo.splitfoolish.data.UserProfile
import com.garyfimo.splitfoolish.data.UserRegistry
import com.garyfimo.splitfoolish.data.UsernameClaimResult
import kotlinx.coroutines.launch

/**
 * Snapshot of what the Settings screen knows + edits. [profile] is the live Firestore state;
 * [draftUsername] and [draftIconId] are what the user is editing right now (not yet saved).
 */
data class SettingsUiState(
    val profile: UserProfile?,
    val draftUsername: String,
    val draftIconId: String?,
    val saving: Boolean,
    val message: String?,
)

/**
 * Settings screen: edit your username + profile icon, plus sign-out. Username is unique across
 * the whole app and powers "Add friend by username". Icon choice is a visual-identity picker from
 * the shared catalog in :core:ui.
 *
 * The screen is stateless — it takes the current [state] and emits intent callbacks. The actual
 * state machine lives in AppState so the Firestore wiring can be tested independently of the UI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onUsernameChange: (String) -> Unit,
    onPickIcon: (String) -> Unit,
    onSave: suspend () -> Unit,
    onClearMessage: () -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Live preview — the catalog icon on its tinted background, sized up to feel like a
            // real avatar. Updates as the user taps icons in the grid so they see the change
            // before saving. Falls back to initials for an unset icon.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentSize(Alignment.Center),
            ) {
                ProfileAvatar(
                    iconId = state.draftIconId,
                    fallbackLabel = state.profile?.displayName
                        ?: state.profile?.email
                        ?: "?",
                    size = 96.dp,
                )
            }

            Text(
                "Signed in as ${state.profile?.email ?: "…"}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().wrapContentSize(Alignment.Center),
            )

            UsernameSection(
                draft = state.draftUsername,
                currentUsername = state.profile?.username,
                onChange = { onUsernameChange(it); onClearMessage() },
            )

            IconPickerSection(
                selectedId = state.draftIconId,
                onPick = { onPickIcon(it); onClearMessage() },
            )

            state.message?.let { msg ->
                Text(
                    msg,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Button(
                onClick = { scope.launch { onSave() } },
                enabled = !state.saving && hasChanges(state),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.saving) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                } else {
                    Text("Save")
                }
            }

            OutlinedButton(
                onClick = onSignOut,
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Sign out") }
        }
    }
}

/** True iff the draft has diverged from the saved profile; used to disable Save when there's
 *  nothing to persist. */
private fun hasChanges(state: SettingsUiState): Boolean {
    val saved = state.profile ?: return state.draftUsername.isNotBlank() || state.draftIconId != null
    return state.draftUsername.lowercase() != (saved.username ?: "") ||
        state.draftIconId != saved.iconId
}

@Composable
private fun UsernameSection(
    draft: String,
    currentUsername: String?,
    onChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Username", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = draft,
            onValueChange = onChange,
            label = { Text("Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        val normalised = draft.trim().lowercase()
        val helperText = when {
            normalised.isEmpty() -> "3–20 characters. Lowercase letters, digits, underscore. " +
                "Must start with a letter."
            !UserRegistry.isValidUsername(normalised) ->
                "Invalid format — 3–20 chars, start with a letter, then letters/digits/underscore."
            currentUsername == null -> "Available to claim."
            currentUsername == normalised -> "This is your current username."
            else -> "Changing from “$currentUsername” to “$normalised”."
        }
        Text(helperText, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun IconPickerSection(
    selectedId: String?,
    onPick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Profile icon", style = MaterialTheme.typography.titleSmall)
        // LazyVerticalGrid gives us a fixed-width 5-column layout that wraps as the catalog grows
        // and keeps each tappable tile a consistent size. Height is capped via heightIn so the
        // grid doesn't try to be infinitely tall inside the parent verticalScroll.
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            userScrollEnabled = false,
            modifier = Modifier
                .fillMaxWidth()
                // Catalog is 22 icons laid out 5 per row → 5 rows × 56dp tile + 4 × 8dp gaps = 312dp.
                // heightIn caps the grid inside the parent verticalScroll so it doesn't try to
                // be infinitely tall; userScrollEnabled = false above prevents nested scroll.
                .heightIn(max = 360.dp),
        ) {
            items(profileIconCatalog, key = { it.id }) { icon ->
                val isSelected = icon.id == selectedId
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clickable { onPick(icon.id) }
                        .border(
                            width = if (isSelected) 3.dp else 0.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    ProfileAvatar(
                        iconId = icon.id,
                        fallbackLabel = "",
                        size = 48.dp,
                    )
                }
            }
        }
    }
}

