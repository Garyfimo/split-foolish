package com.garyfimo.splitfoolish

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.garyfimo.splitfoolish.core.nav.Screen
import com.garyfimo.splitfoolish.core.ui.SplitFoolishTheme
import com.garyfimo.splitfoolish.feature.friends.FriendsScreen
import com.garyfimo.splitfoolish.feature.groups.ExpenseEditorScreen
import com.garyfimo.splitfoolish.feature.groups.GroupDetailScreen
import com.garyfimo.splitfoolish.feature.groups.GroupsListScreen
import com.garyfimo.splitfoolish.feature.groups.NewGroupScreen

/**
 * Root composable. Holds the [AppState] coordinator and dispatches the current [Screen] to the
 * right feature screen with the specific state/callbacks that screen needs. Feature screens are
 * "dumb" — they accept data + callbacks and never reach into [AppState] directly, which is what
 * lets each feature live in its own Gradle module without depending on this one.
 */
@Composable
@Preview
fun App(state: AppState = remember { AppState() }) {
    SplitFoolishTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = androidx.compose.material3.MaterialTheme.colorScheme.background,
        ) {
            when (val screen = state.screen) {
                Screen.GroupsList -> GroupsListScreen(
                    groups = state.groups,
                    friendCount = state.friends.size,
                    onOpenFriends = { state.screen = Screen.Friends },
                    onOpenGroup = { groupId -> state.screen = Screen.GroupDetail(groupId) },
                    onDeleteGroup = { groupId -> state.deleteGroup(groupId) },
                    onNewGroup = { state.screen = Screen.NewGroup },
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
    }
}
