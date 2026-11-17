package com.garyfimo.splitfoolish.feature.groups

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.garyfimo.splitfoolish.core.billsplit.SplitMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class GroupDetailScreenTest {

    private val viewerUid = "u-viewer"
    private val ownerUid = "u-owner"
    private val otherUid = "u-other"

    /** Group where the signed-in viewer is also the creator (sees owner-only controls). */
    private fun ownerGroup(): EditableGroup =
        EditableGroup(id = "g1", name = "Trip", createdByUid = viewerUid).apply {
            members.add(EditableMember(id = viewerUid, name = "Me"))
            members.add(EditableMember(id = otherUid, name = "Other"))
        }

    /** Group where the viewer is a member but NOT the creator. */
    private fun memberGroup(): EditableGroup =
        EditableGroup(id = "g1", name = "Trip", createdByUid = ownerUid).apply {
            members.add(EditableMember(id = ownerUid, name = "Owner"))
            members.add(EditableMember(id = viewerUid, name = "Me"))
            members.add(EditableMember(id = otherUid, name = "Other"))
        }

    private fun expense(
        id: String,
        createdBy: String,
        payer: String,
        title: String = "Dinner",
        amount: String = "30",
        participants: List<String> = listOf(payer),
    ): EditableExpense = EditableExpense(
        id = id,
        title = title,
        amountInput = amount,
        payerId = payer,
        splitMode = SplitMode.Equal,
        participants = participants.map {
            EditableExpenseParticipant(it, included = true, percentInput = "")
        },
        createdByUid = createdBy,
    )

    @Test
    fun owner_sees_remove_buttons_for_other_members() = runComposeUiTest {
        val group = ownerGroup()
        setContent {
            GroupDetailScreen(
                group = group,
                nextId = { "id-1" },
                currentUserUid = viewerUid,
                onAddMemberByEmail = { null },
                onDeleteExpense = {},
                onBack = {},
                onAddExpense = {},
                onEditExpense = {},
            )
        }
        // Exactly one Remove button — on "Other"'s row. Self-row never shows one.
        onNodeWithText("Remove").assertIsDisplayed()
    }

    @Test
    fun non_owner_sees_no_remove_buttons() = runComposeUiTest {
        val group = memberGroup()
        setContent {
            GroupDetailScreen(
                group = group,
                nextId = { "id-1" },
                currentUserUid = viewerUid,
                onAddMemberByEmail = { null },
                onDeleteExpense = {},
                onBack = {},
                onAddExpense = {},
                onEditExpense = {},
            )
        }
        onNodeWithText("Remove").assertDoesNotExist()
    }

    @Test
    fun removing_uninvolved_member_shrinks_member_list_without_dialog() = runComposeUiTest {
        val group = ownerGroup()
        setContent {
            GroupDetailScreen(
                group = group,
                nextId = { "id-1" },
                currentUserUid = viewerUid,
                onAddMemberByEmail = { null },
                onDeleteExpense = {},
                onBack = {},
                onAddExpense = {},
                onEditExpense = {},
            )
        }
        onNodeWithText("Remove").performClick()
        assertEquals(1, group.members.size)
        assertNull(group.members.firstOrNull { it.id == otherUid })
    }

    @Test
    fun removing_payer_opens_confirmation_dialog() = runComposeUiTest {
        val group = ownerGroup()
        group.expenses.add(
            expense(
                id = "e1", createdBy = viewerUid, payer = otherUid, amount = "60",
                participants = group.members.map { it.id },
            ),
        )
        setContent {
            GroupDetailScreen(
                group = group,
                nextId = { "id-1" },
                currentUserUid = viewerUid,
                onAddMemberByEmail = { null },
                onDeleteExpense = {},
                onBack = {},
                onAddExpense = {},
                onEditExpense = {},
            )
        }
        onNodeWithText("Remove").performClick()
        // Dialog renders with the preview; state is NOT yet modified.
        onNodeWithText("Remove Other?").assertIsDisplayed()
        assertEquals(2, group.members.size)
        assertEquals(1, group.expenses.size)
    }

    @Test
    fun confirming_destructive_removal_cascades() = runComposeUiTest {
        val group = ownerGroup()
        group.expenses.add(
            expense(
                id = "e1", createdBy = viewerUid, payer = otherUid, amount = "60",
                participants = group.members.map { it.id },
            ),
        )
        setContent {
            GroupDetailScreen(
                group = group,
                nextId = { "id-1" },
                currentUserUid = viewerUid,
                onAddMemberByEmail = { null },
                onDeleteExpense = {},
                onBack = {},
                onAddExpense = {},
                onEditExpense = {},
            )
        }
        onNodeWithText("Remove").performClick() // row button → opens dialog
        // After the dialog opens, a second "Remove" node exists (its confirm button). Both the
        // row button and the dialog button render the same text, so disambiguate by tree order:
        // the dialog's confirm button is composed after the row button.
        val allRemove = onAllNodesWithText("Remove")
        allRemove[allRemove.fetchSemanticsNodes().size - 1].performClick()
        assertEquals(1, group.members.size)
        assertEquals(0, group.expenses.size)
    }

    @Test
    fun cancel_in_confirmation_leaves_state_intact() = runComposeUiTest {
        val group = ownerGroup()
        group.expenses.add(
            expense(
                id = "e1", createdBy = viewerUid, payer = otherUid, amount = "60",
                participants = group.members.map { it.id },
            ),
        )
        setContent {
            GroupDetailScreen(
                group = group,
                nextId = { "id-1" },
                currentUserUid = viewerUid,
                onAddMemberByEmail = { null },
                onDeleteExpense = {},
                onBack = {},
                onAddExpense = {},
                onEditExpense = {},
            )
        }
        onNodeWithText("Remove").performClick()
        onNodeWithText("Cancel").performClick()
        assertEquals(2, group.members.size)
        assertEquals(1, group.expenses.size)
    }

    @Test
    fun add_expense_disabled_when_group_has_no_members() = runComposeUiTest {
        val group = EditableGroup(id = "g1", name = "Empty", createdByUid = viewerUid)
        setContent {
            GroupDetailScreen(
                group = group,
                nextId = { "id-1" },
                currentUserUid = viewerUid,
                onAddMemberByEmail = { null },
                onDeleteExpense = {},
                onBack = {},
                onAddExpense = {},
                onEditExpense = {},
            )
        }
        onNodeWithText("+ Add expense").assertIsNotEnabled()
    }

    @Test
    fun expense_delete_control_only_shown_to_creator() = runComposeUiTest {
        val group = ownerGroup()
        // Expense created by "Other" — viewer can Edit but not Delete.
        group.expenses.add(expense(id = "e1", createdBy = otherUid, payer = otherUid))
        setContent {
            GroupDetailScreen(
                group = group,
                nextId = { "id-1" },
                currentUserUid = viewerUid,
                onAddMemberByEmail = { null },
                onDeleteExpense = {},
                onBack = {},
                onAddExpense = {},
                onEditExpense = {},
            )
        }
        onNodeWithText("Edit").assertIsDisplayed()
        onNodeWithText("×").assertDoesNotExist()
    }

    @Test
    fun tapping_expense_delete_fires_callback() = runComposeUiTest {
        val group = ownerGroup()
        val mine = expense(id = "em", createdBy = viewerUid, payer = viewerUid, title = "Taxi")
        group.expenses.add(mine)
        var deleted: EditableExpense? = null
        setContent {
            GroupDetailScreen(
                group = group,
                nextId = { "id-1" },
                currentUserUid = viewerUid,
                onAddMemberByEmail = { null },
                onDeleteExpense = { deleted = it },
                onBack = {},
                onAddExpense = {},
                onEditExpense = {},
            )
        }
        onNodeWithText("×").performClick()
        assertEquals("em", deleted?.id)
    }

    @Test
    fun add_member_dialog_submits_entered_email() = runComposeUiTest {
        val group = ownerGroup()
        var lastEmail: String? = null
        setContent {
            GroupDetailScreen(
                group = group,
                nextId = { "id-1" },
                currentUserUid = viewerUid,
                onAddMemberByEmail = { email ->
                    lastEmail = email
                    null
                },
                onDeleteExpense = {},
                onBack = {},
                onAddExpense = {},
                onEditExpense = {},
            )
        }
        onNodeWithText("+ Add member by email").performClick()
        onNodeWithText("Add member by email").assertIsDisplayed()
        onNodeWithText("Email").performTextInput("new@friend.example")
        onNodeWithText("Add").assertIsEnabled().performClick()
        waitForIdle()
        assertEquals("new@friend.example", lastEmail)
    }

    @Test
    fun add_member_dialog_surfaces_inline_error() = runComposeUiTest {
        val group = ownerGroup()
        setContent {
            GroupDetailScreen(
                group = group,
                nextId = { "id-1" },
                currentUserUid = viewerUid,
                onAddMemberByEmail = { "No SplitFoolish account with that email." },
                onDeleteExpense = {},
                onBack = {},
                onAddExpense = {},
                onEditExpense = {},
            )
        }
        onNodeWithText("+ Add member by email").performClick()
        onNodeWithText("Email").performTextInput("ghost@x.example")
        onNodeWithText("Add").performClick()
        waitForIdle()
        onNodeWithText("No SplitFoolish account with that email.").assertIsDisplayed()
        // Dialog title still visible → dialog stayed open for retry.
        onNodeWithText("Add member by email").assertIsDisplayed()
    }

    @Test
    fun settlement_section_renders_balances_and_debts() = runComposeUiTest {
        val group = ownerGroup()
        // Viewer pays $10 split between viewer + other → other owes viewer.
        group.expenses.add(
            expense(
                id = "e1", createdBy = viewerUid, payer = viewerUid, amount = "10",
                participants = listOf(viewerUid, otherUid),
            ),
        )
        setContent {
            GroupDetailScreen(
                group = group,
                nextId = { "id-1" },
                currentUserUid = viewerUid,
                onAddMemberByEmail = { null },
                onDeleteExpense = {},
                onBack = {},
                onAddExpense = {},
                onEditExpense = {},
            )
        }
        onNodeWithText("Balances").assertIsDisplayed()
        onNodeWithText("Who pays whom").assertIsDisplayed()
        // Locale-independent: settlement sentence structure.
        onNodeWithText("Me is owed", substring = true).assertIsDisplayed()
        onNodeWithText("Other owes", substring = true).assertIsDisplayed()
    }

    @Test
    fun back_button_fires_callback() = runComposeUiTest {
        var back = false
        setContent {
            GroupDetailScreen(
                group = ownerGroup(),
                nextId = { "id-1" },
                currentUserUid = viewerUid,
                onAddMemberByEmail = { null },
                onDeleteExpense = {},
                onBack = { back = true },
                onAddExpense = {},
                onEditExpense = {},
            )
        }
        onNodeWithContentDescription("Back").performClick()
        assertTrue(back)
    }
}
