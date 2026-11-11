package com.garyfimo.splitfoolish.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.garyfimo.splitfoolish.billsplit.formatCents
import kotlin.math.abs

@Composable
fun GroupDetailScreen(
    state: GroupsAppState,
    group: EditableGroup,
) {
    var showAddMember by remember { mutableStateOf(false) }
    var memberPendingRemoval by remember { mutableStateOf<EditableMember?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeContentPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { state.screen = Screen.GroupsList }) { Text("← Back") }
        }

        OutlinedTextField(
            value = group.name,
            onValueChange = { group.name = it },
            label = { Text("Group name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        MembersSection(
            group = group,
            onAddMember = { showAddMember = true },
            onRemoveMember = { member ->
                val impact = group.previewMemberRemoval(member.id)
                if (impact.isDestructive) {
                    memberPendingRemoval = member
                } else {
                    group.removeMemberCascade(member.id)
                }
            },
        )

        ExpensesSection(
            group = group,
            onAdd = {
                if (group.members.isNotEmpty()) {
                    state.screen = Screen.ExpenseEditor(group.id, expenseId = null)
                }
            },
            onEdit = { expense ->
                state.screen = Screen.ExpenseEditor(group.id, expenseId = expense.id)
            },
            onDelete = { expense -> group.expenses.removeAll { it.id == expense.id } },
        )

        SettlementSection(group)
    }

    if (showAddMember) {
        AddMemberDialog(
            onDismiss = { showAddMember = false },
            onConfirm = { name ->
                group.members.add(EditableMember(state.nextId(), name))
                showAddMember = false
            },
        )
    }

    memberPendingRemoval?.let { member ->
        ConfirmRemoveMemberDialog(
            member = member,
            impact = group.previewMemberRemoval(member.id),
            onDismiss = { memberPendingRemoval = null },
            onConfirm = {
                group.removeMemberCascade(member.id)
                memberPendingRemoval = null
            },
        )
    }
}

@Composable
private fun MembersSection(
    group: EditableGroup,
    onAddMember: () -> Unit,
    onRemoveMember: (EditableMember) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Members", style = MaterialTheme.typography.titleMedium)
        if (group.members.isEmpty()) {
            Text(
                "Add at least one member to start recording expenses.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            group.members.forEach { member ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = member.name,
                        onValueChange = { member.name = it },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { onRemoveMember(member) }) {
                        Text("Remove")
                    }
                }
            }
        }
        FilledTonalButton(
            onClick = onAddMember,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("+ Add member") }
    }
}

@Composable
private fun ExpensesSection(
    group: EditableGroup,
    onAdd: () -> Unit,
    onEdit: (EditableExpense) -> Unit,
    onDelete: (EditableExpense) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Expenses", style = MaterialTheme.typography.titleMedium)
        if (group.expenses.isEmpty()) {
            Text(
                if (group.members.isEmpty()) "Add members first, then add an expense."
                else "No expenses yet.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            group.expenses.forEach { expense ->
                ExpenseRow(
                    expense = expense,
                    payerName = group.members.firstOrNull { it.id == expense.payerId }?.name ?: "Unknown",
                    onEdit = { onEdit(expense) },
                    onDelete = { onDelete(expense) },
                )
            }
        }
        Button(
            onClick = onAdd,
            enabled = group.members.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("+ Add expense") }
    }
}

@Composable
private fun ExpenseRow(
    expense: EditableExpense,
    payerName: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    expense.title.ifBlank { "(untitled)" },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "${expense.amountInput.ifBlank { "0" }} · paid by $payerName",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onEdit) { Text("Edit") }
            Spacer(Modifier.width(4.dp))
            OutlinedButton(onClick = onDelete) { Text("×") }
        }
    }
}

@Composable
private fun SettlementSection(group: EditableGroup) {
    // derivedStateOf makes Compose actually re-subscribe to every state read inside the lambda
    // (amountInput, payerId, splitMode, each participant's `included`, the expenses/members lists).
    // Without it, EditableGroup is an unstable class without @Stable, so callers of this composable
    // can skip recomposition even when its contents change — which showed up as the settlement
    // freezing on an older value after expenses were added or edited.
    val settlement by remember(group) {
        derivedStateOf { computeSettlement(group.toDomain()) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Settlement", style = MaterialTheme.typography.titleMedium)

        if (settlement.balances.isEmpty() || settlement.balances.all { it.netCents == 0L }) {
            Text(
                if (settlement.balances.isEmpty()) "Add members and expenses to see the split."
                else "Everyone's even — no debts.",
                style = MaterialTheme.typography.bodyMedium,
            )
            return@Column
        }

        Text("Balances", style = MaterialTheme.typography.labelLarge)
        settlement.balances.forEach { balance ->
            val sign = if (balance.netCents >= 0) "is owed" else "owes"
            val color = if (balance.netCents >= 0)
                MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            Text(
                "${balance.name} $sign ${formatCents(abs(balance.netCents))}",
                color = if (balance.netCents == 0L) MaterialTheme.colorScheme.onSurface else color,
            )
        }

        if (settlement.debts.isNotEmpty()) {
            Spacer(Modifier.width(1.dp))
            Text("Who pays whom", style = MaterialTheme.typography.labelLarge)
            settlement.debts.forEach { debt ->
                Text(
                    "${debt.fromName} → ${debt.toName}: ${formatCents(debt.cents)}",
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun ConfirmRemoveMemberDialog(
    member: EditableMember,
    impact: MemberRemovalImpact,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove ${member.name.ifBlank { "member" }}?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (impact.expensesToDelete > 0) {
                    val noun = if (impact.expensesToDelete == 1) "expense" else "expenses"
                    Text("• ${impact.expensesToDelete} $noun they paid for will be deleted.")
                }
                if (impact.expensesToUpdate > 0) {
                    val noun = if (impact.expensesToUpdate == 1) "other expense" else "other expenses"
                    Text("• They will be removed from ${impact.expensesToUpdate} $noun.")
                }
                Text("This can't be undone.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Remove") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AddMemberDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add member") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
