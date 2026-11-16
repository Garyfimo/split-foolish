package com.garyfimo.splitfoolish.feature.groups

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.garyfimo.splitfoolish.core.billsplit.SplitMode
import com.garyfimo.splitfoolish.core.billsplit.parseAmountToCents

/** A group member's `id` is their Firebase Auth uid. See [GroupMember] docs. */
class EditableMember(
    val id: String,
    name: String,
) {
    var name: String by mutableStateOf(name)
}

class EditableExpenseParticipant(
    val memberId: String,
    included: Boolean,
    percentInput: String,
) {
    var included: Boolean by mutableStateOf(included)
    var percentInput: String by mutableStateOf(percentInput)
}

/** [createdByUid] identifies the user who may delete this expense — see [Expense] docs. */
class EditableExpense(
    val id: String,
    title: String,
    amountInput: String,
    payerId: String,
    splitMode: SplitMode,
    participants: List<EditableExpenseParticipant>,
    val createdByUid: String,
) {
    var title: String by mutableStateOf(title)
    var amountInput: String by mutableStateOf(amountInput)
    var payerId: String by mutableStateOf(payerId)
    var splitMode: SplitMode by mutableStateOf(splitMode)
    val participants: SnapshotStateList<EditableExpenseParticipant> =
        mutableStateListOf<EditableExpenseParticipant>().apply { addAll(participants) }

    /**
     * Deep copy for the editor's draft-copy pattern. The ExpenseEditor works on a cloned expense
     * so keystrokes don't propagate into [EditableGroup.expenses] (which would trigger the Firestore
     * write-through); on Done the draft is merged into the real expense via [applyFrom], and on
     * back/discard it's simply dropped. Each participant is deep-copied too so toggling inclusion
     * on the draft doesn't mutate the original.
     */
    fun cloneDraft(): EditableExpense = EditableExpense(
        id = id,
        title = title,
        amountInput = amountInput,
        payerId = payerId,
        splitMode = splitMode,
        participants = participants.map {
            EditableExpenseParticipant(
                memberId = it.memberId,
                included = it.included,
                percentInput = it.percentInput,
            )
        },
        createdByUid = createdByUid,
    )

    /**
     * Merge [other]'s edited fields into this expense in place. Used by the editor on Done when
     * editing an existing expense — mutates the already-attached instance so the write-through
     * captures the final state. Participants are replaced wholesale; snapshotFlow's debounce
     * collapses the intermediate states into one save.
     */
    fun applyFrom(other: EditableExpense) {
        title = other.title
        amountInput = other.amountInput
        payerId = other.payerId
        splitMode = other.splitMode
        participants.clear()
        other.participants.forEach {
            participants.add(
                EditableExpenseParticipant(
                    memberId = it.memberId,
                    included = it.included,
                    percentInput = it.percentInput,
                ),
            )
        }
    }
}

/**
 * What will happen if a given member is removed. [expensesToDelete] counts expenses they paid for
 * (no way to auto-pick a replacement payer, so those have to go); [expensesToUpdate] counts the
 * other expenses where they're only a participant (we strip them from the participant list).
 */
data class MemberRemovalImpact(
    val expensesToDelete: Int,
    val expensesToUpdate: Int,
) {
    val isDestructive: Boolean get() = expensesToDelete > 0 || expensesToUpdate > 0
}

/**
 * [createdByUid] is the uid of the user who originally created the group. Preserved but doesn't
 * grant any current authorisation beyond showing "created by you" affordances (the only real
 * per-user permission in the app right now is per-expense delete; see [Expense]).
 */
class EditableGroup(
    val id: String,
    name: String,
    val createdByUid: String,
) {
    var name: String by mutableStateOf(name)
    val members: SnapshotStateList<EditableMember> = mutableStateListOf()
    val expenses: SnapshotStateList<EditableExpense> = mutableStateListOf()

    fun previewMemberRemoval(memberId: String): MemberRemovalImpact {
        var deletes = 0
        var updates = 0
        for (expense in expenses) {
            if (expense.payerId == memberId) {
                deletes++
            } else if (expense.participants.any { it.memberId == memberId }) {
                updates++
            }
        }
        return MemberRemovalImpact(deletes, updates)
    }

    /**
     * Remove a member and cascade-clean every reference to them so the settlement can't silently
     * drop money: expenses they paid for are deleted (we can't guess a replacement payer), and they
     * are stripped from any other expense's participant list.
     */
    fun removeMemberCascade(memberId: String) {
        expenses.removeAll { it.payerId == memberId }
        expenses.forEach { it.participants.removeAll { ep -> ep.memberId == memberId } }
        members.removeAll { it.id == memberId }
    }

    /** Snapshot current editor state as an immutable [Group] so [computeSettlement] can run on it. */
    fun toDomain(): Group = Group(
        id = id,
        name = name,
        createdByUid = createdByUid,
        members = members.map { GroupMember(it.id, it.name) },
        memberUids = members.map { it.id },
        expenses = expenses.mapNotNull { it.toDomainOrNull() },
    )

    private fun EditableExpense.toDomainOrNull(): Expense? {
        val amount = parseAmountToCents(amountInput) ?: return null
        val included = participants.filter { it.included }
        if (included.isEmpty()) return null
        return Expense(
            id = id,
            title = title,
            amountCents = amount,
            payerId = payerId,
            splitMode = splitMode,
            participants = included.map {
                ExpenseParticipant(
                    memberId = it.memberId,
                    percentage = it.percentInput.toDoubleOrNull() ?: 0.0,
                )
            },
            createdByUid = createdByUid,
        )
    }
}
