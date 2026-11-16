package com.garyfimo.splitfoolish.feature.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.garyfimo.splitfoolish.core.billsplit.SplitMode
import com.garyfimo.splitfoolish.core.billsplit.currencySymbol
import com.garyfimo.splitfoolish.core.ui.BackIconButton

/**
 * Expense editor. Works on a **draft copy** of the expense that isn't attached to [group.expenses]
 * until the user hits Done — that's what keeps a discarded new expense out of Firestore. Entering
 * with a null id starts a fresh draft; entering with an id clones the existing expense so the
 * original stays unchanged until Done confirms the edit.
 *
 * The write-through in [AppState] observes [group.expenses] via snapshotFlow; since the draft
 * lives outside that list during editing, keystrokes never trigger a Firestore save. On Done, the
 * draft is either appended (new) or merge-written into the real expense in place (edit). On
 * Discard / back, the draft is simply dropped and nothing goes to Firestore. The Delete button
 * (edit mode only) routes through [onDeleteExpense], which fires the explicit Firestore delete —
 * SyncedRepository.saveSnapshot is upsert-only and would otherwise orphan the deleted doc.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseEditorScreen(
    group: EditableGroup,
    expenseId: String?,
    nextId: () -> String,
    currentUserUid: String,
    onDone: () -> Unit,
    onDeleteExpense: (EditableExpense) -> Unit,
) {
    if (group.members.isEmpty()) {
        onDone()
        return
    }

    // Draft state. For a new expense it's a blank EditableExpense never attached to the group
    // until Done. For an edit it's a clone of the real expense; the real one stays untouched
    // until Done merges the draft into it via applyFrom.
    val draft = remember(expenseId) {
        if (expenseId == null) {
            EditableExpense(
                id = nextId(),
                title = "",
                amountInput = "",
                payerId = group.members.first().id,
                splitMode = SplitMode.Equal,
                participants = group.members.map {
                    EditableExpenseParticipant(it.id, included = true, percentInput = "")
                },
                // Creator is whoever opens the editor for a new expense — only they will be able
                // to delete it later (see Expense.createdByUid + the firestore.rules file).
                createdByUid = currentUserUid,
            )
        } else {
            group.expenses.firstOrNull { it.id == expenseId }?.cloneDraft()
        }
    }

    if (draft == null) {
        onDone()
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (expenseId == null) "New expense" else "Edit expense") },
                // Back = discard. For a new expense nothing was ever attached, so there's nothing
                // to clean up. For an edit, the original expense was never mutated, so there's
                // nothing to revert. Either way: drop the draft and return.
                navigationIcon = { BackIconButton(onClick = onDone) },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = draft.title,
                onValueChange = { draft.title = it },
                label = { Text("Title (e.g. Dinner, Gas)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = draft.amountInput,
                onValueChange = { draft.amountInput = it },
                label = { Text("Amount") },
                prefix = { Text(currencySymbol()) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            PayerSelector(group, draft)
            SplitModeToggle(draft)
            ParticipantsEditor(group, draft)

            // Delete is only offered to the expense's creator — Firestore's rule rejects anyone
            // else's delete, and AppState.deleteExpense enforces the same check client-side with
            // a silent return. Without this UI gate, a non-creator editing someone else's expense
            // would see a "Delete" button, tap it, and get zero feedback (nav back + expense
            // still there). Mirrors GroupDetailScreen's row-delete gate (canDelete).
            val canDelete = expenseId != null && draft.createdByUid == currentUserUid
            val secondaryLabel = when {
                expenseId == null -> "Discard"
                canDelete -> "Delete"
                // Non-creator editing someone else's expense: back/this button both drop the
                // draft without touching the original. "Discard" is the honest label.
                else -> "Discard"
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        // Commit the draft to group.expenses. For a new expense: append. For an
                        // edit: find the real expense by id and merge the draft's fields in place.
                        // Either path mutates the SnapshotStateList the write-through observes, so
                        // the next debounced save pushes exactly the final committed state to
                        // Firestore — no intermediate keystroke emissions, no orphan documents.
                        if (expenseId == null) {
                            group.expenses.add(draft)
                        } else {
                            group.expenses.firstOrNull { it.id == expenseId }?.applyFrom(draft)
                        }
                        onDone()
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Done") }
                OutlinedButton(
                    onClick = {
                        if (canDelete) {
                            // Creator editing their own expense: real delete. Routes through
                            // AppState which fires the explicit Firestore delete (saveSnapshot
                            // is upsert-only so a Compose-only removal would orphan the cloud doc).
                            val real = group.expenses.firstOrNull { it.id == expenseId }
                            if (real != null) onDeleteExpense(real)
                        }
                        // Discard paths (new expense + non-creator edit): nothing to clean up —
                        // the draft was never attached to group.expenses and the real expense
                        // (if any) was never mutated. Just navigate back.
                        onDone()
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(secondaryLabel) }
            }
        }
    }
}

@Composable
private fun PayerSelector(group: EditableGroup, expense: EditableExpense) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Paid by", style = MaterialTheme.typography.labelLarge)
        group.members.forEach { member ->
            val selected = expense.payerId == member.id
            if (selected) {
                Button(
                    onClick = { expense.payerId = member.id },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(member.name.ifBlank { "(unnamed)" }) }
            } else {
                OutlinedButton(
                    onClick = { expense.payerId = member.id },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(member.name.ifBlank { "(unnamed)" }) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SplitModeToggle(expense: EditableExpense) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Split", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // ToggleButton morphs its corner shape while pressed/checked — the signature
            // Expressive animation. See MaterialTheme.motionScheme / shapeByInteraction.
            ToggleButton(
                checked = expense.splitMode == SplitMode.Equal,
                onCheckedChange = { if (it) expense.splitMode = SplitMode.Equal },
                modifier = Modifier.weight(1f),
            ) { Text("Equal") }
            ToggleButton(
                checked = expense.splitMode == SplitMode.Percentage,
                onCheckedChange = { if (it) expense.splitMode = SplitMode.Percentage },
                modifier = Modifier.weight(1f),
            ) { Text("Percentage") }
        }
    }
}

@Composable
private fun ParticipantsEditor(group: EditableGroup, expense: EditableExpense) {
    // Members added to the group after this expense was created won't retroactively appear here —
    // keep expenses stable once recorded. Users who want them in can delete + re-add the expense.
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Participants", style = MaterialTheme.typography.labelLarge)
        expense.participants.forEach { ep ->
            val member = group.members.firstOrNull { it.id == ep.memberId } ?: return@forEach
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Checkbox(
                    checked = ep.included,
                    onCheckedChange = { ep.included = it },
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    member.name.ifBlank { "(unnamed)" },
                    modifier = Modifier.weight(1f),
                )
                if (expense.splitMode == SplitMode.Percentage && ep.included) {
                    OutlinedTextField(
                        value = ep.percentInput,
                        onValueChange = { ep.percentInput = it },
                        suffix = { Text("%") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.width(120.dp),
                    )
                }
            }
        }
    }
}
