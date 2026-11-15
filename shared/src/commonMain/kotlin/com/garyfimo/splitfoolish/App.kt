package com.garyfimo.splitfoolish

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garyfimo.splitfoolish.core.nav.Screen
import com.garyfimo.splitfoolish.core.ui.SplitFoolishTheme
import com.garyfimo.splitfoolish.data.SplitFoolishRepository
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
 * [repositoryForUid] is a factory the platform entry point supplies — it knows how to build a
 * per-uid [SplitFoolishRepository] (on Android that needs a Context, on iOS it doesn't). Lifting
 * it to a parameter keeps commonMain free of platform specifics.
 */
@Composable
fun App(
    authState: AuthState,
    repositoryForUid: (uid: String) -> SplitFoolishRepository,
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
                    repositoryForUid = repositoryForUid,
                )
            }
        }
    }
}

/**
 * The pre-auth composable tree. `remember(user.uid)` throws away the old [AppState] (and its
 * SQLDelight connection) the moment the signed-in uid changes — switching accounts can't leak
 * data across sessions because each uid has its own database file under the hood.
 */
@Composable
private fun AuthenticatedApp(
    user: AuthUser,
    authState: AuthState,
    repositoryForUid: (uid: String) -> SplitFoolishRepository,
) {
    val scope = rememberCoroutineScope()
    val state = remember(user.uid) {
        AppState(repo = repositoryForUid(user.uid), scope = scope)
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
            friends = state.friends,
            onAddFriend = { name -> state.addFriend(name) },
            onCreateGroup = { name, friendIds ->
                val group = state.createGroupWithFriends(name, friendIds)
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
                    friends = state.friends,
                    nextId = { state.nextId() },
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
                    onDone = { state.screen = Screen.GroupDetail(group.id) },
                )
            }
        }
    }
}
