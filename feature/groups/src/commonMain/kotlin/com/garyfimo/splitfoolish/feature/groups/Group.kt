package com.garyfimo.splitfoolish.feature.groups

import com.garyfimo.splitfoolish.core.billsplit.SplitMode

data class GroupMember(
    val id: String,
    val name: String,
)

/**
 * A single purchase within a group. [payerId] paid [amountCents] on behalf of [participants],
 * split according to [splitMode]. Participants is a subset of the group's members — not everyone
 * necessarily shares every expense (e.g. one person ordered dessert).
 */
data class Expense(
    val id: String,
    val title: String,
    val amountCents: Long,
    val payerId: String,
    val splitMode: SplitMode,
    val participants: List<ExpenseParticipant>,
)

data class ExpenseParticipant(
    val memberId: String,
    val percentage: Double = 0.0,
)

data class Group(
    val id: String,
    val name: String,
    val members: List<GroupMember>,
    val expenses: List<Expense>,
)
