package com.garyfimo.splitfoolish.feature.groups

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test

/**
 * Tests that exercise the "Invite with link" entry point inside [GroupDetailScreen] + its
 * [InviteLinkDialog]. Owner-only gating lives alongside the Remove-member gate, so both are
 * worth asserting together. The dialog's suspend callback is handed a canned Result to drive
 * the success / error branches without a Firestore round-trip.
 */
@OptIn(ExperimentalTestApi::class)
class InviteLinkDialogTest {

    private val viewerUid = "u-viewer"
    private val ownerUid = "u-owner"
    private val otherUid = "u-other"

    private fun ownerGroup(): EditableGroup =
        EditableGroup(id = "g1", name = "Trip", createdByUid = viewerUid).apply {
            members.add(EditableMember(id = viewerUid, name = "Me"))
            members.add(EditableMember(id = otherUid, name = "Other"))
        }

    private fun memberGroup(): EditableGroup =
        EditableGroup(id = "g1", name = "Trip", createdByUid = ownerUid).apply {
            members.add(EditableMember(id = ownerUid, name = "Owner"))
            members.add(EditableMember(id = viewerUid, name = "Me"))
        }

    @Test
    fun owner_sees_invite_with_link_button() = runComposeUiTest {
        setContent {
            GroupDetailScreen(
                group = ownerGroup(),
                nextId = { "id-1" },
                currentUserUid = viewerUid,
                onAddMemberByEmail = { null },
                onCreateInviteLink = { Result.success("https://example.test/i/x") },
                onDeleteExpense = {},
                onBack = {},
                onAddExpense = {},
                onEditExpense = {},
            )
        }
        onNodeWithText("Invite with link").assertIsDisplayed()
    }

    @Test
    fun non_owner_does_not_see_invite_with_link_button() = runComposeUiTest {
        setContent {
            GroupDetailScreen(
                group = memberGroup(),
                nextId = { "id-1" },
                currentUserUid = viewerUid,
                onAddMemberByEmail = { null },
                onCreateInviteLink = { Result.success("https://example.test/i/x") },
                onDeleteExpense = {},
                onBack = {},
                onAddExpense = {},
                onEditExpense = {},
            )
        }
        onNodeWithText("Invite with link").assertDoesNotExist()
    }

    @Test
    fun invite_dialog_displays_url_on_success() = runComposeUiTest {
        val link = "https://splitfoolish-77870.web.app/i/abcd-1234"
        setContent {
            GroupDetailScreen(
                group = ownerGroup(),
                nextId = { "id-1" },
                currentUserUid = viewerUid,
                onAddMemberByEmail = { null },
                onCreateInviteLink = { Result.success(link) },
                onDeleteExpense = {},
                onBack = {},
                onAddExpense = {},
                onEditExpense = {},
            )
        }
        onNodeWithText("Invite with link").performClick()
        waitForIdle()
        onNodeWithText(link).assertIsDisplayed()
        onNodeWithText("Copy & close").assertIsDisplayed()
    }

    @Test
    fun invite_dialog_shows_error_message_on_failure() = runComposeUiTest {
        setContent {
            GroupDetailScreen(
                group = ownerGroup(),
                nextId = { "id-1" },
                currentUserUid = viewerUid,
                onAddMemberByEmail = { null },
                onCreateInviteLink = {
                    Result.failure(IllegalStateException("Only the group owner can create invite links."))
                },
                onDeleteExpense = {},
                onBack = {},
                onAddExpense = {},
                onEditExpense = {},
            )
        }
        onNodeWithText("Invite with link").performClick()
        waitForIdle()
        onNodeWithText("Only the group owner can create invite links.").assertIsDisplayed()
    }
}
