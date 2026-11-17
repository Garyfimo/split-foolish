package com.garyfimo.splitfoolish.feature.friends

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class FriendsScreenTest {

    private fun friend(id: String, name: String) = EditableFriend(
        id = id, uid = "uid-$id", name = name, iconId = null,
    )

    @Test
    fun empty_state_shown_when_no_friends() = runComposeUiTest {
        setContent {
            FriendsScreen(
                friends = emptyList(),
                onAddFriendByUsername = { null },
                onRemoveFriend = {},
                onBack = {},
            )
        }
        onNodeWithText("No friends yet.").assertIsDisplayed()
    }

    @Test
    fun friends_are_listed_by_name() = runComposeUiTest {
        setContent {
            FriendsScreen(
                friends = listOf(friend("a", "Alice"), friend("b", "Bob")),
                onAddFriendByUsername = { null },
                onRemoveFriend = {},
                onBack = {},
            )
        }
        onNodeWithText("Alice").assertIsDisplayed()
        onNodeWithText("Bob").assertIsDisplayed()
    }

    @Test
    fun remove_button_fires_callback_with_friend_id() = runComposeUiTest {
        var removed: String? = null
        setContent {
            FriendsScreen(
                friends = listOf(friend("a", "Alice")),
                onAddFriendByUsername = { null },
                onRemoveFriend = { removed = it },
                onBack = {},
            )
        }
        onNodeWithText("Remove").performClick()
        assertEquals("a", removed)
    }

    @Test
    fun add_friend_dialog_submits_entered_username() = runComposeUiTest {
        var lookedUp: String? = null
        setContent {
            FriendsScreen(
                friends = emptyList(),
                onAddFriendByUsername = { lookedUp = it; null },
                onRemoveFriend = {},
                onBack = {},
            )
        }
        onNodeWithText("+ Add friend by username").performClick()
        onNodeWithText("Add friend by username").assertIsDisplayed()
        onNodeWithText("Username").performTextInput("alice")
        onNodeWithText("Add").assertIsEnabled().performClick()
        waitForIdle()
        assertEquals("alice", lookedUp)
    }

    @Test
    fun add_friend_dialog_shows_inline_error_from_lookup() = runComposeUiTest {
        setContent {
            FriendsScreen(
                friends = emptyList(),
                onAddFriendByUsername = { "No SplitFoolish account with that username." },
                onRemoveFriend = {},
                onBack = {},
            )
        }
        onNodeWithText("+ Add friend by username").performClick()
        onNodeWithText("Username").performTextInput("ghost")
        onNodeWithText("Add").performClick()
        waitForIdle()
        onNodeWithText("No SplitFoolish account with that username.").assertIsDisplayed()
        // Dialog stayed open for retry.
        onNodeWithText("Add friend by username").assertIsDisplayed()
    }

    @Test
    fun back_button_fires_callback() = runComposeUiTest {
        var back = false
        setContent {
            FriendsScreen(
                friends = emptyList(),
                onAddFriendByUsername = { null },
                onRemoveFriend = {},
                onBack = { back = true },
            )
        }
        onNodeWithContentDescription("Back").performClick()
        assertTrue(back)
    }
}
