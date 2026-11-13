package com.garyfimo.splitfoolish.groups

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.garyfimo.splitfoolish.billsplit.SplitMode
import com.garyfimo.splitfoolish.billsplit.parseAmountToCents

/** Routes the single Compose tree between the five screens without pulling in a nav library. */
sealed class Screen {
    data object GroupsList : Screen()
    data object Friends : Screen()
    data object NewGroup : Screen()
    data class GroupDetail(val groupId: Long) : Screen()
    data class ExpenseEditor(val groupId: Long, val expenseId: Long?) : Screen()
}

class EditableMember(
    val id: Long,
    name: String,
) {
    var name: String by mutableStateOf(name)
}

/**
 * A friend lives in a global list, independent of any group. Picking a friend when adding a group
 * member copies the current name into a new [EditableMember]; later edits on either side don't
 * propagate. This keeps removal simple — no cascade — at the cost of not being able to ask "which
 * groups is Alice in?" without a lookup pass. Fine for V1.
 */
class EditableFriend(
    val id: Long,
    name: String,
) {
    var name: String by mutableStateOf(name)
}

class EditableExpenseParticipant(
    val memberId: Long,
    included: Boolean,
    percentInput: String,
) {
    var included: Boolean by mutableStateOf(included)
    var percentInput: String by mutableStateOf(percentInput)
}

class EditableExpense(
    val id: Long,
    title: String,
    amountInput: String,
    payerId: Long,
    splitMode: SplitMode,
    participants: List<EditableExpenseParticipant>,
) {
    var title: String by mutableStateOf(title)
    var amountInput: String by mutableStateOf(amountInput)
    var payerId: Long by mutableStateOf(payerId)
    var splitMode: SplitMode by mutableStateOf(splitMode)
    val participants: SnapshotStateList<EditableExpenseParticipant> =
        mutableStateListOf<EditableExpenseParticipant>().apply { addAll(participants) }
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

class EditableGroup(
    val id: Long,
    name: String,
) {
    var name: String by mutableStateOf(name)
    val members: SnapshotStateList<EditableMember> = mutableStateListOf()
    val expenses: SnapshotStateList<EditableExpense> = mutableStateListOf()

    fun previewMemberRemoval(memberId: Long): MemberRemovalImpact {
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
    fun removeMemberCascade(memberId: Long) {
        expenses.removeAll { it.payerId == memberId }
        expenses.forEach { it.participants.removeAll { ep -> ep.memberId == memberId } }
        members.removeAll { it.id == memberId }
    }

    /** Snapshot current editor state as an immutable [Group] so [computeSettlement] can run on it. */
    fun toDomain(): Group = Group(
        id = id,
        name = name,
        members = members.map { GroupMember(it.id, it.name) },
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
        )
    }
}

class GroupsAppState {
    private var idCounter: Long = 0
    fun nextId(): Long = ++idCounter

    var screen: Screen by mutableStateOf(Screen.GroupsList)
    val groups: SnapshotStateList<EditableGroup> = mutableStateListOf()
    val friends: SnapshotStateList<EditableFriend> = mutableStateListOf()

    fun createGroup(name: String): EditableGroup {
        val group = EditableGroup(nextId(), name)
        groups.add(group)
        return group
    }

    fun deleteGroup(id: Long) {
        groups.removeAll { it.id == id }
    }

    fun findGroup(id: Long): EditableGroup? = groups.firstOrNull { it.id == id }

    fun findExpense(groupId: Long, expenseId: Long): EditableExpense? =
        findGroup(groupId)?.expenses?.firstOrNull { it.id == expenseId }

    fun addFriend(name: String): EditableFriend {
        val friend = EditableFriend(nextId(), name)
        friends.add(friend)
        return friend
    }

    fun removeFriend(id: Long) {
        friends.removeAll { it.id == id }
    }
}
