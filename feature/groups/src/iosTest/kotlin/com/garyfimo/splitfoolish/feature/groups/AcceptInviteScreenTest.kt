package com.garyfimo.splitfoolish.feature.groups

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Screen-level tests for [AcceptInviteScreen]. We hand the composable canned [InvitePreview]
 * / [AcceptOutcome] values (the real resolver lives in AppState — pure callbacks keep the
 * screen test free of Firestore). Each test verifies one branch of the preview/outcome state
 * machine renders the right copy and wires buttons back to the right callbacks.
 */
@OptIn(ExperimentalTestApi::class)
class AcceptInviteScreenTest {

    @Test
    fun preview_renders_group_name_and_join_button() = runComposeUiTest {
        setContent {
            AcceptInviteScreen(
                token = "t-1",
                onPreview = { InvitePreview.Preview(groupName = "Weekend Trip") },
                onAccept = { AcceptOutcome.Joined },
                onDone = {},
            )
        }
        waitForIdle()
        onNodeWithText("Weekend Trip").assertIsDisplayed()
        onNodeWithText("Join").assertIsDisplayed()
        onNodeWithText("Not now").assertIsDisplayed()
    }

    @Test
    fun not_found_renders_error_copy() = runComposeUiTest {
        setContent {
            AcceptInviteScreen(
                token = "t-2",
                onPreview = { InvitePreview.NotFound },
                onAccept = { AcceptOutcome.Joined },
                onDone = {},
            )
        }
        waitForIdle()
        onNodeWithText("Invite not found").assertIsDisplayed()
    }

    @Test
    fun expired_renders_expired_copy() = runComposeUiTest {
        setContent {
            AcceptInviteScreen(
                token = "t-3",
                onPreview = { InvitePreview.Expired },
                onAccept = { AcceptOutcome.Joined },
                onDone = {},
            )
        }
        waitForIdle()
        onNodeWithText("Invite expired").assertIsDisplayed()
    }

    @Test
    fun preview_error_surfaces_message() = runComposeUiTest {
        setContent {
            AcceptInviteScreen(
                token = "t-4",
                onPreview = { InvitePreview.Error("Network down") },
                onAccept = { AcceptOutcome.Joined },
                onDone = {},
            )
        }
        waitForIdle()
        onNodeWithText("Couldn't open invite").assertIsDisplayed()
        onNodeWithText("Network down").assertIsDisplayed()
    }

    @Test
    fun join_button_triggers_accept_and_shows_joined_outcome() = runComposeUiTest {
        var acceptedToken: String? = null
        setContent {
            AcceptInviteScreen(
                token = "t-5",
                onPreview = { InvitePreview.Preview(groupName = "Roadtrip") },
                onAccept = { token ->
                    acceptedToken = token
                    AcceptOutcome.Joined
                },
                onDone = {},
            )
        }
        waitForIdle()
        onNodeWithText("Join").performClick()
        waitForIdle()
        assertEquals("t-5", acceptedToken)
        onNodeWithText("You've joined").assertIsDisplayed()
    }

    @Test
    fun already_member_outcome_renders_already_message() = runComposeUiTest {
        setContent {
            AcceptInviteScreen(
                token = "t-6",
                onPreview = { InvitePreview.Preview(groupName = "Lunch") },
                onAccept = { AcceptOutcome.AlreadyMember },
                onDone = {},
            )
        }
        waitForIdle()
        onNodeWithText("Join").performClick()
        waitForIdle()
        onNodeWithText("You're already in this group").assertIsDisplayed()
    }

    @Test
    fun accept_error_surfaces_message() = runComposeUiTest {
        setContent {
            AcceptInviteScreen(
                token = "t-7",
                onPreview = { InvitePreview.Preview(groupName = "Drinks") },
                onAccept = { AcceptOutcome.Error("Some backend problem") },
                onDone = {},
            )
        }
        waitForIdle()
        onNodeWithText("Join").performClick()
        waitForIdle()
        onNodeWithText("Couldn't join").assertIsDisplayed()
        onNodeWithText("Some backend problem").assertIsDisplayed()
    }

    @Test
    fun not_now_calls_onDone() = runComposeUiTest {
        var doneCalled = false
        setContent {
            AcceptInviteScreen(
                token = "t-8",
                onPreview = { InvitePreview.Preview(groupName = "Dinner") },
                onAccept = { AcceptOutcome.Joined },
                onDone = { doneCalled = true },
            )
        }
        waitForIdle()
        onNodeWithText("Not now").performClick()
        assertTrue(doneCalled)
    }

    @Test
    fun outcome_done_button_calls_onDone() = runComposeUiTest {
        var doneCalled = false
        setContent {
            AcceptInviteScreen(
                token = "t-9",
                onPreview = { InvitePreview.Preview(groupName = "Dinner") },
                onAccept = { AcceptOutcome.Joined },
                onDone = { doneCalled = true },
            )
        }
        waitForIdle()
        onNodeWithText("Join").performClick()
        waitForIdle()
        onNodeWithText("Done").performClick()
        assertTrue(doneCalled)
    }
}
