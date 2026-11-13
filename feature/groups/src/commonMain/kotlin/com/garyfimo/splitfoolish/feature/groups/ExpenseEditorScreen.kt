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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseEditorScreen(
    group: EditableGroup,
    expenseId: Long?,
    nextId: () -> Long,
    onDone: () -> Unit,
) {
    if (group.members.isEmpty()) {
        onDone()
        return
    }

    // Editor works directly on the live EditableExpense in the group. Entering with a null id
    // creates a draft immediately; "Discard" removes it on the way out so cancellation is clean.
    val expense = remember(expenseId) {
        if (expenseId != null) {
            group.expenses.firstOrNull { it.id == expenseId }
        } else {
            val draft = EditableExpense(
                id = nextId(),
                title = "",
                amountInput = "",
                payerId = group.members.first().id,
                splitMode = SplitMode.Equal,
                participants = group.members.map {
                    EditableExpenseParticipant(it.id, included = true, percentInput = "")
                },
            )
            group.expenses.add(draft)
            draft
        }
    }

    if (expense == null) {
        onDone()
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (expenseId == null) "New expense" else "Edit expense") },
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
                value = expense.title,
                onValueChange = { expense.title = it },
                label = { Text("Title (e.g. Dinner, Gas)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = expense.amountInput,
                onValueChange = { expense.amountInput = it },
                label = { Text("Amount") },
                prefix = { Text(currencySymbol()) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            PayerSelector(group, expense)
            SplitModeToggle(expense)
            ParticipantsEditor(group, expense)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onDone,
                    modifier = Modifier.weight(1f),
                ) { Text("Done") }
                OutlinedButton(
                    onClick = {
                        group.expenses.removeAll { it.id == expense.id }
                        onDone()
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(if (expenseId == null) "Discard" else "Delete") }
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
