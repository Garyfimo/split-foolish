package com.garyfimo.splitfoolish.feature.groups

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import com.garyfimo.splitfoolish.core.billsplit.SplitMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ExpenseEditorScreenTest {

    private val viewerUid = "u-viewer"
    private val otherUid = "u-other"

    private fun twoMemberGroup(): EditableGroup =
        EditableGroup(id = "g1", name = "Trip", createdByUid = viewerUid).apply {
            members.add(EditableMember(id = viewerUid, name = "Me"))
            members.add(EditableMember(id = otherUid, name = "Other"))
        }

    private fun existingExpense(
        id: String = "e1",
        createdBy: String = viewerUid,
        title: String = "Taxi",
        amount: String = "12",
        payer: String = viewerUid,
        mode: SplitMode = SplitMode.Equal,
    ): EditableExpense = EditableExpense(
        id = id,
        title = title,
        amountInput = amount,
        payerId = payer,
        splitMode = mode,
        participants = listOf(
            EditableExpenseParticipant(viewerUid, included = true, percentInput = ""),
            EditableExpenseParticipant(otherUid, included = true, percentInput = ""),
        ),
        createdByUid = createdBy,
    )

    @Test
    fun new_expense_shows_new_expense_title_and_discard_button() = runComposeUiTest {
        setContent {
            ExpenseEditorScreen(
                group = twoMemberGroup(),
                expenseId = null,
                nextId = { "new-id" },
                currentUserUid = viewerUid,
                onDone = {},
                onDeleteExpense = {},
            )
        }
        onNodeWithText("New expense").assertIsDisplayed()
        // For a new expense the secondary button is always labelled Discard.
        onNodeWithText("Discard").assertIsDisplayed()
    }

    @Test
    fun edit_mode_as_creator_shows_delete_button() = runComposeUiTest {
        val group = twoMemberGroup().apply { expenses.add(existingExpense()) }
        setContent {
            ExpenseEditorScreen(
                group = group,
                expenseId = "e1",
                nextId = { "new-id" },
                currentUserUid = viewerUid,
                onDone = {},
                onDeleteExpense = {},
            )
        }
        onNodeWithText("Edit expense").assertIsDisplayed()
        onNodeWithText("Delete").assertIsDisplayed()
    }

    @Test
    fun edit_mode_as_non_creator_shows_discard_instead_of_delete() = runComposeUiTest {
        val group = twoMemberGroup().apply {
            expenses.add(existingExpense(createdBy = otherUid))
        }
        setContent {
            ExpenseEditorScreen(
                group = group,
                expenseId = "e1",
                nextId = { "new-id" },
                currentUserUid = viewerUid,
                onDone = {},
                onDeleteExpense = {},
            )
        }
        onNodeWithText("Discard").assertIsDisplayed()
        onNodeWithText("Delete").assertDoesNotExist()
    }

    @Test
    fun done_on_new_expense_appends_to_group_and_notifies() = runComposeUiTest {
        val group = twoMemberGroup()
        var done = false
        setContent {
            ExpenseEditorScreen(
                group = group,
                expenseId = null,
                nextId = { "new-id" },
                currentUserUid = viewerUid,
                onDone = { done = true },
                onDeleteExpense = {},
            )
        }
        onNodeWithText("Title (e.g. Dinner, Gas)").performTextInput("Pizza")
        onNodeWithText("Amount").performTextInput("24")
        onNodeWithText("Done").performClick()
        assertEquals(1, group.expenses.size)
        assertEquals("Pizza", group.expenses[0].title)
        assertEquals("24", group.expenses[0].amountInput)
        assertEquals("new-id", group.expenses[0].id)
        assertTrue(done)
    }

    @Test
    fun discard_on_new_expense_does_not_add_to_group() = runComposeUiTest {
        val group = twoMemberGroup()
        var done = false
        setContent {
            ExpenseEditorScreen(
                group = group,
                expenseId = null,
                nextId = { "new-id" },
                currentUserUid = viewerUid,
                onDone = { done = true },
                onDeleteExpense = {},
            )
        }
        onNodeWithText("Title (e.g. Dinner, Gas)").performTextInput("Draft")
        onNodeWithText("Discard").performClick()
        assertEquals(0, group.expenses.size, "Draft never committed")
        assertTrue(done)
    }

    @Test
    fun done_on_edit_merges_draft_into_real_expense() = runComposeUiTest {
        val group = twoMemberGroup().apply { expenses.add(existingExpense(title = "Old")) }
        setContent {
            ExpenseEditorScreen(
                group = group,
                expenseId = "e1",
                nextId = { "new-id" },
                currentUserUid = viewerUid,
                onDone = {},
                onDeleteExpense = {},
            )
        }
        // performTextReplacement sets the field's value outright; performTextInput would prepend
        // because the test input injector leaves the cursor at position 0.
        onNodeWithText("Old").performTextReplacement("Old renamed")
        onNodeWithText("Done").performClick()
        assertEquals(1, group.expenses.size)
        assertEquals("Old renamed", group.expenses[0].title)
        assertEquals("e1", group.expenses[0].id, "Id preserved across edit")
    }

    @Test
    fun delete_on_edit_fires_callback_with_real_expense() = runComposeUiTest {
        val group = twoMemberGroup().apply { expenses.add(existingExpense()) }
        var deleted: EditableExpense? = null
        var done = false
        setContent {
            ExpenseEditorScreen(
                group = group,
                expenseId = "e1",
                nextId = { "new-id" },
                currentUserUid = viewerUid,
                onDone = { done = true },
                onDeleteExpense = { deleted = it },
            )
        }
        onNodeWithText("Delete").performClick()
        assertEquals("e1", deleted?.id)
        assertTrue(done, "onDone fires after delete so caller navigates back")
    }

    @Test
    fun toggling_percentage_reveals_percent_inputs_for_included_participants() = runComposeUiTest {
        val group = twoMemberGroup()
        setContent {
            ExpenseEditorScreen(
                group = group,
                expenseId = null,
                nextId = { "new-id" },
                currentUserUid = viewerUid,
                onDone = {},
                onDeleteExpense = {},
            )
        }
        // Percentage toggle flips splitMode and reveals a % suffix on participant rows.
        onNodeWithText("Percentage").performClick()
        // Two participants are included by default, so two "%" suffixes render.
        val percentNodes = onAllNodesWithText("%")
        assertEquals(2, percentNodes.fetchSemanticsNodes().size)
    }

    @Test
    fun toggling_to_percentage_persists_split_mode_on_done() = runComposeUiTest {
        val group = twoMemberGroup()
        setContent {
            ExpenseEditorScreen(
                group = group,
                expenseId = null,
                nextId = { "new-id" },
                currentUserUid = viewerUid,
                onDone = {},
                onDeleteExpense = {},
            )
        }
        onNodeWithText("Percentage").performClick()
        onNodeWithText("Amount").performTextInput("10")
        onNodeWithText("Done").performClick()
        assertEquals(SplitMode.Percentage, group.expenses[0].splitMode)
    }

    @Test
    fun selecting_a_different_payer_updates_the_draft() = runComposeUiTest {
        val group = twoMemberGroup()
        var done = false
        setContent {
            ExpenseEditorScreen(
                group = group,
                expenseId = null,
                nextId = { "new-id" },
                currentUserUid = viewerUid,
                onDone = { done = true },
                onDeleteExpense = {},
            )
        }
        // "Other" appears twice — once as a payer button (PayerSelector), once as a Text label in
        // the Participants row. The PayerSelector button is composed first, so index 0 is the one
        // we want to tap.
        onAllNodesWithText("Other")[0].performClick()
        onNodeWithText("Amount").performTextInput("10")
        onNodeWithText("Done").performClick()
        assertEquals(otherUid, group.expenses[0].payerId)
        assertTrue(done)
    }

    @Test
    fun empty_group_short_circuits_to_done() = runComposeUiTest {
        val emptyGroup = EditableGroup(id = "g1", name = "Empty", createdByUid = viewerUid)
        var done = false
        setContent {
            ExpenseEditorScreen(
                group = emptyGroup,
                expenseId = null,
                nextId = { "new-id" },
                currentUserUid = viewerUid,
                onDone = { done = true },
                onDeleteExpense = {},
            )
        }
        assertTrue(done, "Editor opened on a memberless group returns immediately")
    }

    @Test
    fun editing_a_missing_expense_id_short_circuits_to_done() = runComposeUiTest {
        val group = twoMemberGroup()
        var done = false
        setContent {
            ExpenseEditorScreen(
                group = group,
                expenseId = "does-not-exist",
                nextId = { "new-id" },
                currentUserUid = viewerUid,
                onDone = { done = true },
                onDeleteExpense = {},
            )
        }
        assertTrue(done)
    }
}
