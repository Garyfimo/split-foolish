package com.garyfimo.splitfoolish

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garyfimo.splitfoolish.core.nav.Screen
import com.garyfimo.splitfoolish.core.ui.SplitFoolishTheme
import com.garyfimo.splitfoolish.data.UserRegistry
import com.garyfimo.splitfoolish.feature.auth.AuthState
import com.garyfimo.splitfoolish.feature.auth.AuthUser
import com.garyfimo.splitfoolish.feature.auth.LoginScreen
import com.garyfimo.splitfoolish.feature.friends.FriendsScreen
import com.garyfimo.splitfoolish.feature.groups.ExpenseEditorScreen
import com.garyfimo.splitfoolish.feature.groups.GroupDetailScreen
import com.garyfimo.splitfoolish.feature.groups.GroupsListScreen
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
@Composable
fun App(
    authState: AuthState,
    userRegistry: UserRegistry,
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

    when (val screen = state.screen) {
        Screen.GroupsList -> GroupsListScreen(
            groups = state.groups,
            friendCount = state.friends.size,
            onOpenFriends = { state.screen = Screen.Friends },
            onOpenGroup = { groupId -> state.screen = Screen.GroupDetail(groupId) },
            onDeleteGroup = { groupId -> state.deleteGroup(groupId) },
            onNewGroup = { state.screen = Screen.NewGroup },
            onSignOut = { scope.launch { authState.signOut() } },
        )

        Screen.Friends -> FriendsScreen(
            friends = state.friends,
            onAddFriend = { name -> state.addFriend(name) },
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
            if (group == null) {
                state.screen = Screen.GroupsList
                Text("Group not found")
            } else {
                GroupDetailScreen(
                    group = group,
                    nextId = { state.nextId() },
                    currentUserUid = user.uid,
                    onAddMemberByEmail = { email -> state.addMemberByEmail(group, email) },
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

        is Screen.ExpenseEditor -> {
            val group = state.findGroup(screen.groupId)
            if (group == null) {
                state.screen = Screen.GroupsList
                Text("Group not found")
            } else {
                ExpenseEditorScreen(
                    group = group,
                    expenseId = screen.expenseId,
                    nextId = { state.nextId() },
                    currentUserUid = user.uid,
                    onDone = { state.screen = Screen.GroupDetail(group.id) },
                )
            }
        }
    }
}
