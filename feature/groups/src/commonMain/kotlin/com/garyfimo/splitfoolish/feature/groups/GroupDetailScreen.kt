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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.garyfimo.splitfoolish.core.billsplit.formatCents
import com.garyfimo.splitfoolish.core.ui.BackIconButton
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * [onAddMemberByEmail] runs the Firestore users-by-email lookup and attaches the resolved user
 * to the group. Returns null on success, or a short error message to render inline on failure
 * ("no such account", "already a member"). The String return keeps :feature:groups free of a
 * dependency on :shared's result types.
 *
 * [currentUserUid] is used to decide which expenses show a delete control — only the user who
 * created an expense can delete it. See [EditableExpense.createdByUid] for the field, and
 * firestore.rules at the repo root for the server-side enforcement.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    group: EditableGroup,
    nextId: () -> String,
    currentUserUid: String,
    onAddMemberByEmail: suspend (email: String) -> String?,
    onCreateInviteLink: suspend () -> Result<String>,
    onDeleteExpense: (EditableExpense) -> Unit,
    onBack: () -> Unit,
    onAddExpense: () -> Unit,
    onEditExpense: (expenseId: String) -> Unit,
) {
    var showAddMember by remember { mutableStateOf(false) }
    var showInviteLink by remember { mutableStateOf(false) }
    var memberPendingRemoval by remember { mutableStateOf<EditableMember?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(group.name.ifBlank { "Group" }) },
                navigationIcon = { BackIconButton(onClick = onBack) },
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
                value = group.name,
                onValueChange = { group.name = it },
                label = { Text("Group name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Removing members is a creator-only action. Mirrors the Firestore rule which rejects
            // any non-creator write that shrinks memberUids — without this UI gate a non-owner
            // would see a Remove button that silently fails on save. Add is still open to any
            // member so the "collaborate to build the group" flow keeps working.
            val isOwner = group.createdByUid == currentUserUid
            MembersSection(
                group = group,
                currentUserUid = currentUserUid,
                canRemove = isOwner,
                canInviteByLink = isOwner,
                onAddMember = { showAddMember = true },
                onInviteByLink = { showInviteLink = true },
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
                currentUserUid = currentUserUid,
                onAdd = {
                    if (group.members.isNotEmpty()) onAddExpense()
                },
                onEdit = { expense -> onEditExpense(expense.id) },
                onDelete = onDeleteExpense,
            )

            SettlementSection(group)
        }
    }

    if (showAddMember) {
        AddMemberByEmailDialog(
            onDismiss = { showAddMember = false },
            onLookup = onAddMemberByEmail,
            onSuccess = { showAddMember = false },
        )
    }

    if (showInviteLink) {
        InviteLinkDialog(
            onDismiss = { showInviteLink = false },
            onCreateLink = onCreateInviteLink,
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
    currentUserUid: String,
    canRemove: Boolean,
    canInviteByLink: Boolean,
    onAddMember: () -> Unit,
    onInviteByLink: () -> Unit,
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
                val isSelf = member.id == currentUserUid
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = member.name + if (isSelf) " (you)" else "",
                        onValueChange = { /* names are from the Firebase Auth record, not editable here */ },
                        singleLine = true,
                        enabled = false,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    // Remove button only renders when the viewer is the group owner AND the row
                    // is for someone else. Non-owners never see it; owners don't see it on their
                    // own row (owner can't leave their own group — Firestore rule also enforces).
                    if (canRemove && !isSelf) {
                        OutlinedButton(onClick = { onRemoveMember(member) }) {
                            Text("Remove")
                        }
                    }
                }
            }
        }
        FilledTonalButton(
            onClick = onAddMember,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("+ Add member by email") }
        // Owner-only "Invite with link" entry point. Hidden for non-owners because the
        // Firestore rule on /invites/{token} rejects creates from anyone who isn't the group's
        // createdByUid — showing the button would mean showing a Button that silently fails.
        if (canInviteByLink) {
            FilledTonalButton(
                onClick = onInviteByLink,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Invite with link") }
        }
    }
}

@Composable
private fun ExpensesSection(
    group: EditableGroup,
    currentUserUid: String,
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
                    canDelete = expense.createdByUid == currentUserUid,
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
    canDelete: Boolean,
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
            if (canDelete) {
                Spacer(Modifier.width(4.dp))
                OutlinedButton(onClick = onDelete) { Text("×") }
            }
        }
    }
}

@Composable
private fun SettlementSection(group: EditableGroup) {
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

/**
 * Dialog that creates a 7-day shareable invite link and shows the resulting URL with a Copy
 * button. Issues the Firestore create once on first composition (not on every recomposition —
 * LaunchedEffect with Unit key). Error path renders inline so the user can retry.
 *
 * The link is not revealed before the create succeeds, which also means no Copy button renders
 * for the empty state — users can't share a URL that doesn't exist server-side.
 */
@Composable
private fun InviteLinkDialog(
    onDismiss: () -> Unit,
    onCreateLink: suspend () -> Result<String>,
) {
    var link by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var inFlight by remember { mutableStateOf(true) }
    val clipboard = LocalClipboardManager.current

    androidx.compose.runtime.LaunchedEffect(Unit) {
        // Same scope-vs-crash reasoning as AddMemberByEmailDialog: the suspend call can throw
        // a Firestore exception, and on K/N an uncaught throw tears down the composition. The
        // try/catch here plus the Result wrapper give us a clean error state to render.
        try {
            onCreateLink().fold(
                onSuccess = { link = it },
                onFailure = { t ->
                    error = t.message ?: t::class.simpleName ?: "Couldn't create invite link."
                },
            )
        } catch (t: Throwable) {
            error = t.message ?: t::class.simpleName ?: "Couldn't create invite link."
        } finally {
            inFlight = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invite with link") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    inFlight -> {
                        Text(
                            "Creating a link that expires in 7 days…",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.width(20.dp),
                        )
                    }

                    error != null -> Text(
                        error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )

                    link != null -> {
                        Text(
                            "Anyone with this link can join the group for 7 days. Share it " +
                                "with people you trust.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        // Read-only field so the user can see AND select the URL manually on
                        // platforms where the OS clipboard integration might not fire — the
                        // Copy button is the happy path.
                        OutlinedTextField(
                            value = link!!,
                            onValueChange = { /* read-only */ },
                            singleLine = true,
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (link != null) {
                TextButton(onClick = {
                    // AnnotatedString is Compose MP's cross-platform clipboard payload. iOS maps
                    // to UIPasteboard.general, Android to ClipboardManager — the same call works
                    // on both without the shared code touching a platform SDK.
                    clipboard.setText(AnnotatedString(link!!))
                    onDismiss()
                }) { Text("Copy & close") }
            } else {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = if (link != null) {
            { TextButton(onClick = onDismiss) { Text("Close") } }
        } else null,
    )
}

/**
 * Dialog that resolves an email to a Firebase Auth uid via [onLookup] and adds that user to
 * the current group. All feedback stays inline inside the dialog (no snackbar) so the user can
 * retype after a "no such user" without the dialog disappearing on them.
 */
@Composable
private fun AddMemberByEmailDialog(
    onDismiss: () -> Unit,
    onLookup: suspend (email: String) -> String?,
    onSuccess: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var inFlight by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val canSubmit = !inFlight && email.trim().isNotBlank()

    AlertDialog(
        onDismissRequest = { if (!inFlight) onDismiss() },
        title = { Text("Add member by email") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "The person must already have a SplitFoolish account for their email to resolve.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; errorMessage = null },
                    label = { Text("Email") },
                    singleLine = true,
                    enabled = !inFlight,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                errorMessage?.let { msg ->
                    Text(
                        msg,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSubmit,
                onClick = {
                    inFlight = true
                    errorMessage = null
                    scope.launch {
                        // try/finally guarantees the spinner stops even if onLookup throws an
                        // unexpected exception the suspend contract was supposed to have caught.
                        // Without this the dialog would stay spinning forever on an escaped throw.
                        try {
                            val err = onLookup(email)
                            if (err == null) onSuccess() else errorMessage = err
                        } catch (t: Throwable) {
                            errorMessage = "Something went wrong: ${t.message ?: t::class.simpleName}"
                        } finally {
                            inFlight = false
                        }
                    }
                },
            ) {
                if (inFlight) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.width(16.dp))
                } else {
                    Text("Add")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !inFlight) { Text("Cancel") }
        },
    )
}
