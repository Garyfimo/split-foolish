package com.garyfimo.splitfoolish.feature.groups

import com.garyfimo.splitfoolish.core.billsplit.SplitMode

/**
 * A group member is identified by their Firebase Auth uid. The display name is snapshot-copied
 * at the time they're added so renaming your account later doesn't cascade into every group's
 * history. Ghost members (name without an account) are not supported in the current design.
 */
data class GroupMember(
    val id: String, // The user's Firebase Auth uid; also used as memberId in ExpenseParticipant/payerId.
    val name: String,
)

/**
 * A single purchase within a group. [payerId] paid [amountCents] on behalf of [participants],
 * split according to [splitMode]. Participants is a subset of the group's members — not everyone
 * necessarily shares every expense (e.g. one person ordered dessert).
 *
 * [createdByUid] is the uid of the user who created this expense; only that user is allowed to
 * delete it (enforced in the UI by hiding the delete control, and in Firestore by a security rule).
 */
data class Expense(
    val id: String,
    val title: String,
    val amountCents: Long,
    val payerId: String,
    val splitMode: SplitMode,
    val participants: List<ExpenseParticipant>,
    val createdByUid: String,
)

data class ExpenseParticipant(
    val memberId: String,
    val percentage: Double = 0.0,
)

/**
 * A group with its creator, members, expenses, and the flat list of member uids. [memberUids]
 * duplicates `members.map { it.id }` so Firestore's array-contains queries can find all groups
 * for a given user efficiently — without that, finding "groups where current user is a member"
 * would need either a client-side scan or a composite index on nested member objects.
 */
data class Group(
    val id: String,
    val name: String,
    val createdByUid: String,
    val members: List<GroupMember>,
    val memberUids: List<String>,
    val expenses: List<Expense>,
)
