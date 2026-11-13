package com.garyfimo.splitfoolish.groups

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.garyfimo.splitfoolish.ui.SplitFoolishTheme

@Composable
fun GroupsApp(
    state: GroupsAppState = remember { GroupsAppState() },
) {
    SplitFoolishTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            when (val screen = state.screen) {
                Screen.GroupsList -> GroupsListScreen(state)
                Screen.Friends -> FriendsScreen(state)
                Screen.NewGroup -> NewGroupScreen(state)
                is Screen.GroupDetail -> {
                    val group = state.findGroup(screen.groupId)
                    if (group == null) {
                        state.screen = Screen.GroupsList
                        Text("Group not found")
                    } else {
                        GroupDetailScreen(state, group)
                    }
                }
                is Screen.ExpenseEditor -> {
                    val group = state.findGroup(screen.groupId)
                    if (group == null) {
                        state.screen = Screen.GroupsList
                        Text("Group not found")
                    } else {
                        ExpenseEditorScreen(state, group, screen.expenseId)
                    }
                }
            }
        }
    }
}
