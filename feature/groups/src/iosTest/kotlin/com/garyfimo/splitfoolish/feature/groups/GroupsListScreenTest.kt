package com.garyfimo.splitfoolish.feature.groups

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GroupsListScreenTest {

    private val owner = "u-owner"
    private val viewer = "u-viewer"

    private fun groupOwnedBy(uid: String, name: String = "Trip", id: String = "g1"): EditableGroup =
        EditableGroup(id = id, name = name, createdByUid = uid).apply {
            members.add(EditableMember(id = uid, name = "Owner"))
        }

    @Test
    fun empty_state_shown_when_no_groups() = runComposeUiTest {
        setContent {
            GroupsListScreen(
                groups = emptyList(),
                friendCount = 0,
                currentUserUid = viewer,
                onOpenFriends = {},
                onOpenGroup = {},
                onDeleteGroup = {},
                onNewGroup = {},
            )
        }
        onNodeWithText(
            "No groups yet. Create one to start tracking shared expenses.",
        ).assertIsDisplayed()
    }

    @Test
    fun delete_button_only_shown_on_groups_the_viewer_created() = runComposeUiTest {
        val mine = groupOwnedBy(viewer, name = "Mine", id = "gm")
        val theirs = groupOwnedBy(owner, name = "Theirs", id = "gt")
        setContent {
            GroupsListScreen(
                groups = listOf(mine, theirs),
                friendCount = 0,
                currentUserUid = viewer,
                onOpenFriends = {},
                onOpenGroup = {},
                onDeleteGroup = {},
                onNewGroup = {},
            )
        }
        // Exactly one Delete button renders — the one on the viewer's own group.
        onNodeWithText("Delete").assertIsDisplayed()
    }

    @Test
    fun tapping_open_invokes_callback_with_group_id() = runComposeUiTest {
        var opened: String? = null
        setContent {
            GroupsListScreen(
                groups = listOf(groupOwnedBy(viewer, id = "gX")),
                friendCount = 0,
                currentUserUid = viewer,
                onOpenFriends = {},
                onOpenGroup = { opened = it },
                onDeleteGroup = {},
                onNewGroup = {},
            )
        }
        onNodeWithText("Open").performClick()
        assertEquals("gX", opened)
    }

    @Test
    fun tapping_delete_invokes_callback_with_group_id() = runComposeUiTest {
        var deleted: String? = null
        setContent {
            GroupsListScreen(
                groups = listOf(groupOwnedBy(viewer, id = "gD")),
                friendCount = 0,
                currentUserUid = viewer,
                onOpenFriends = {},
                onOpenGroup = {},
                onDeleteGroup = { deleted = it },
                onNewGroup = {},
            )
        }
        onNodeWithText("Delete").performClick()
        assertEquals("gD", deleted)
    }

    @Test
    fun friend_count_label_pluralises() = runComposeUiTest {
        setContent {
            GroupsListScreen(
                groups = emptyList(),
                friendCount = 3,
                currentUserUid = viewer,
                onOpenFriends = {},
                onOpenGroup = {},
                onDeleteGroup = {},
                onNewGroup = {},
            )
        }
        onNodeWithText("Friends (3)").assertIsDisplayed()
    }

    @Test
    fun new_group_button_fires_callback() = runComposeUiTest {
        var tapped = false
        setContent {
            GroupsListScreen(
                groups = emptyList(),
                friendCount = 0,
                currentUserUid = viewer,
                onOpenFriends = {},
                onOpenGroup = {},
                onDeleteGroup = {},
                onNewGroup = { tapped = true },
            )
        }
        onNodeWithText("+ New group").assertIsEnabled().performClick()
        assertEquals(true, tapped)
    }

    @Test
    fun settings_icon_hidden_when_callback_absent() = runComposeUiTest {
        setContent {
            GroupsListScreen(
                groups = emptyList(),
                friendCount = 0,
                currentUserUid = viewer,
                onOpenFriends = {},
                onOpenGroup = {},
                onDeleteGroup = {},
                onNewGroup = {},
                onOpenSettings = null,
            )
        }
        onNodeWithContentDescription("Settings").assertDoesNotExist()
    }

    @Test
    fun settings_icon_fires_when_callback_present() = runComposeUiTest {
        var tapped = false
        setContent {
            GroupsListScreen(
                groups = emptyList(),
                friendCount = 0,
                currentUserUid = viewer,
                onOpenFriends = {},
                onOpenGroup = {},
                onDeleteGroup = {},
                onNewGroup = {},
                onOpenSettings = { tapped = true },
            )
        }
        onNodeWithContentDescription("Settings").performClick()
        assertEquals(true, tapped)
    }
}
