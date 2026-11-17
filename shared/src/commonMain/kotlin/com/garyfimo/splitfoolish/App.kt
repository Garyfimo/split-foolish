package com.garyfimo.splitfoolish

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garyfimo.splitfoolish.core.nav.Screen
import com.garyfimo.splitfoolish.core.ui.SplitFoolishTheme
import com.garyfimo.splitfoolish.data.InviteAcceptResult
import com.garyfimo.splitfoolish.data.UserRegistry
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import com.garyfimo.splitfoolish.feature.auth.AuthState
import com.garyfimo.splitfoolish.feature.auth.AuthUser
import com.garyfimo.splitfoolish.feature.auth.LoginScreen
import com.garyfimo.splitfoolish.feature.friends.FriendsScreen
import com.garyfimo.splitfoolish.feature.groups.AcceptInviteScreen
import com.garyfimo.splitfoolish.feature.groups.AcceptOutcome
import com.garyfimo.splitfoolish.feature.groups.ExpenseEditorScreen
import com.garyfimo.splitfoolish.feature.groups.GroupDetailScreen
import com.garyfimo.splitfoolish.feature.groups.GroupsListScreen
import com.garyfimo.splitfoolish.feature.groups.InvitePreview
import com.garyfimo.splitfoolish.feature.groups.NewGroupScreen
import kotlinx.coroutines.launch

/**
 * Root composable. Branches on [AuthState]: null user → [LoginScreen], signed-in user → the
 * authenticated app tree.
 *
 * [appStateFactory] is a thunk the platform entry point supplies — it knows how to build an
 * [AppState] with per-uid repositories (SQLite for friends, Firestore for groups). Keeping it
 * as a parameter rather than wiring platform details into commonMain means neither Android's
 * Context dependency nor Firebase's platform SDKs leak into this file.
 */
/** Unix epoch seconds. Used for the client-side "has this invite expired?" preview check so the
 *  user sees "expired" without round-tripping the accept call. Firestore's rule enforces expiry
 *  server-side anyway; this is a UX nicety. */
@OptIn(ExperimentalTime::class)
private fun nowUnixSeconds(): Long = Clock.System.now().epochSeconds

@Composable
fun App(
    authState: AuthState,
    userRegistry: UserRegistry,
    deepLinkBridge: DeepLinkBridge,
    appStateFactory: (user: AuthUser, scope: kotlinx.coroutines.CoroutineScope) -> AppState,
) {
    SplitFoolishTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = androidx.compose.material3.MaterialTheme.colorScheme.background,
        ) {
            val user by authState.user.collectAsStateWithLifecycle()
            when (val signedIn = user) {
                null -> LoginScreen(authState = authState)
                else -> AuthenticatedApp(
                    user = signedIn,
                    authState = authState,
                    userRegistry = userRegistry,
                    deepLinkBridge = deepLinkBridge,
                    appStateFactory = appStateFactory,
                )
            }
        }
    }
}

@Composable
private fun AuthenticatedApp(
    user: AuthUser,
    authState: AuthState,
    userRegistry: UserRegistry,
    deepLinkBridge: DeepLinkBridge,
    appStateFactory: (user: AuthUser, scope: kotlinx.coroutines.CoroutineScope) -> AppState,
) {
    val scope = rememberCoroutineScope()
    val state = remember(user.uid) { appStateFactory(user, scope) }

    // Register this user in /users/{uid} + /users_by_email/{email} so other users can find them
    // by email in the Add Member dialog. Idempotent — safe to re-run on every sign-in.
    LaunchedEffect(user.uid) {
        userRegistry.ensureRegistered(
            uid = user.uid,
            email = user.email.orEmpty(),
            displayName = null,
        )
    }

    // Deep-link routing. When the platform entry point pushes an invite URL into the bridge,
    // navigate to the accept screen and mark it consumed so a subsequent back-nav doesn't
    // bounce us onto it again. We route here (not inside the bridge) because this is where
    // we have access to AppState.screen and the per-uid state the accept call needs.
    val pendingInvite by deepLinkBridge.pendingInviteToken.collectAsStateWithLifecycle()
    LaunchedEffect(pendingInvite) {
        val token = pendingInvite ?: return@LaunchedEffect
        state.screen = Screen.AcceptInvite(token)
        deepLinkBridge.clearPendingInvite()
    }

    when (val screen = state.screen) {
        Screen.GroupsList -> GroupsListScreen(
            groups = state.groups,
            friendCount = state.friends.size,
            currentUserUid = user.uid,
            onOpenFriends = { state.screen = Screen.Friends },
            onOpenGroup = { groupId -> state.screen = Screen.GroupDetail(groupId) },
            onDeleteGroup = { groupId -> state.deleteGroup(groupId) },
            onNewGroup = { state.screen = Screen.NewGroup },
            onOpenSettings = { state.screen = Screen.Settings },
        )

        Screen.Settings -> SettingsScreen(
            state = SettingsUiState(
                profile = state.profile,
                draftUsername = state.draftUsername,
                draftIconId = state.draftIconId,
                saving = state.settingsSaving,
                message = state.settingsMessage,
            ),
            onUsernameChange = { state.updateDraftUsername(it) },
            onPickIcon = { state.updateDraftIcon(it) },
            onSave = { state.saveSettings() },
            onClearMessage = { state.clearSettingsMessage() },
            onSignOut = { scope.launch { authState.signOut() } },
            onBack = { state.screen = Screen.GroupsList },
        )

        Screen.Friends -> FriendsScreen(
            friends = state.friends,
            onAddFriendByUsername = { username -> state.addFriendByUsername(username) },
            onRemoveFriend = { id -> state.removeFriend(id) },
            onBack = { state.screen = Screen.GroupsList },
        )

        Screen.NewGroup -> NewGroupScreen(
            onCreateGroup = { name ->
                val group = state.createEmptyGroup(name)
                state.screen = Screen.GroupDetail(group.id)
            },
            onBack = { state.screen = Screen.GroupsList },
        )

        is Screen.GroupDetail -> {
            val group = state.findGroup(screen.groupId)
            // LaunchedEffect-based navigation instead of writing state in the composable body
            // (the old `state.screen = ...` directly during composition is an anti-pattern that
            // fires on every recomposition). This path also handles the "viewer was removed from
            // the group" case: owner removes them → Firestore writes new memberUids → removed
            // user's array-contains query re-evaluates → observeSnapshot emits without this
            // group → applyRemoteSnapshot drops it → findGroup returns null → this effect fires
            // → they land on Screen.GroupsList. No "you were kicked" dialog needed; the nav back
            // to home is the signal.
            LaunchedEffect(group == null) {
                if (group == null) state.screen = Screen.GroupsList
            }
            if (group != null) {
                GroupDetailScreen(
                    group = group,
                    nextId = { state.nextId() },
                    currentUserUid = user.uid,
                    onAddMemberByEmail = { email -> state.addMemberByEmail(group, email) },
                    onCreateInviteLink = { state.createInviteLink(group) },
                    onDeleteExpense = { expense -> state.deleteExpense(group, expense) },
                    onBack = { state.screen = Screen.GroupsList },
                    onAddExpense = {
                        state.screen = Screen.ExpenseEditor(group.id, expenseId = null)
                    },
                    onEditExpense = { expenseId ->
                        state.screen = Screen.ExpenseEditor(group.id, expenseId = expenseId)
                    },
                )
            }
        }

        is Screen.AcceptInvite -> AcceptInviteScreen(
            token = screen.token,
            onPreview = { token ->
                // Map the data-layer Result<GroupInvite?> into the feature's InvitePreview
                // sum. :feature:groups intentionally doesn't depend on :data, so the shared
                // App owns this translation. The nowSeconds check keeps "expired" visible to
                // the user even if their client clock disagrees with the server by less than
                // the 7-day expiry window.
                val result = state.previewInvite(token)
                result.fold(
                    onSuccess = { invite ->
                        when {
                            invite == null -> InvitePreview.NotFound
                            invite.isExpired(nowUnixSeconds()) -> InvitePreview.Expired
                            else -> InvitePreview.Preview(groupName = invite.groupName)
                        }
                    },
                    onFailure = { t ->
                        InvitePreview.Error(t.message ?: t::class.simpleName ?: "network error")
                    },
                )
            },
            onAccept = { token ->
                // Same translation shape for the accept call. The data-layer differentiates
                // join-failure modes (NotFound/Expired) that should have already been caught
                // by the preview step; folding them into Error here is the fallback for the
                // rare case where the invite was revoked between preview and accept.
                when (val r = state.acceptInvite(token)) {
                    is InviteAcceptResult.Joined -> AcceptOutcome.Joined
                    is InviteAcceptResult.AlreadyMember -> AcceptOutcome.AlreadyMember
                    InviteAcceptResult.InviteNotFound ->
                        AcceptOutcome.Error("This invite isn't valid anymore.")
                    InviteAcceptResult.InviteExpired ->
                        AcceptOutcome.Error("This invite has expired.")
                    is InviteAcceptResult.Error -> AcceptOutcome.Error(r.message)
                }
            },
            onDone = { state.screen = Screen.GroupsList },
        )

        is Screen.ExpenseEditor -> {
            val group = state.findGroup(screen.groupId)
            // Same removed-mid-flow protection as GroupDetail above. If the viewer was editing
            // an expense in a group they just got kicked from, snap them home rather than keeping
            // them in an editor backed by a stale group reference.
            LaunchedEffect(group == null) {
                if (group == null) state.screen = Screen.GroupsList
            }
            if (group != null) {
                ExpenseEditorScreen(
                    group = group,
                    expenseId = screen.expenseId,
                    nextId = { state.nextId() },
                    currentUserUid = user.uid,
                    onDone = { state.screen = Screen.GroupDetail(group.id) },
                    onDeleteExpense = { expense -> state.deleteExpense(group, expense) },
                )
            }
        }
    }
}
