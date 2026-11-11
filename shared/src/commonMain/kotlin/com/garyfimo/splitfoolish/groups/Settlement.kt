package com.garyfimo.splitfoolish.groups

import com.garyfimo.splitfoolish.billsplit.Participant
import com.garyfimo.splitfoolish.billsplit.SplitResult
import com.garyfimo.splitfoolish.billsplit.splitBill

/** Net position of one member: positive cents = the group owes them, negative = they owe the group. */
data class MemberBalance(
    val memberId: Long,
    val name: String,
    val netCents: Long,
)

data class Debt(
    val fromMemberId: Long,
    val fromName: String,
    val toMemberId: Long,
    val toName: String,
    val cents: Long,
)

data class Settlement(
    val balances: List<MemberBalance>,
    val debts: List<Debt>,
)

/**
 * Compute each member's net balance across every expense in the group, then simplify the debt graph
 * into O(n) payments using a greedy largest-creditor ↔ largest-debtor match. Expenses whose split
 * inputs are invalid (missing payer, bad percentages) are ignored so one broken row can't break the
 * whole summary.
 */
fun computeSettlement(group: Group): Settlement {
    val balances = LinkedHashMap<Long, Long>()
    for (member in group.members) balances[member.id] = 0L

    for (expense in group.expenses) {
        if (expense.payerId !in balances) continue

        val raw = expense.participants.mapNotNull { ep ->
            group.members.firstOrNull { it.id == ep.memberId }
                ?.let { member -> Participant(member.id, member.name, ep.percentage) }
        }
        if (raw.isEmpty()) continue

        // BillSplitter gives the Equal-split penny remainder to the first participant in the list.
        // Putting the payer first means they absorb their own expense's stray cent, so when members
        // take turns paying evenly the remainders rotate with the payer and all balances settle
        // to zero instead of permanently drifting onto whoever happened to be member index 0.
        val participants = raw.sortedByDescending { it.id == expense.payerId }

        val splitResult = splitBill(expense.amountCents, expense.splitMode, participants)
        if (splitResult !is SplitResult.Success) continue

        balances[expense.payerId] = (balances[expense.payerId] ?: 0L) + expense.amountCents
        for (share in splitResult.shares) {
            balances[share.participant.id] = (balances[share.participant.id] ?: 0L) - share.cents
        }
    }

    val memberBalances = group.members.map { member ->
        MemberBalance(member.id, member.name, balances[member.id] ?: 0L)
    }
    return Settlement(memberBalances, simplifyDebts(memberBalances))
}

/**
 * Greedy minimum-transaction settlement: repeatedly pair the biggest creditor with the biggest
 * debtor and transfer the smaller of the two amounts. Produces at most n − 1 debts for n members.
 */
private fun simplifyDebts(balances: List<MemberBalance>): List<Debt> {
    data class Mutable(val memberId: Long, val name: String, var cents: Long)

    val debtors = balances
        .filter { it.netCents < 0 }
        .map { Mutable(it.memberId, it.name, -it.netCents) }
        .sortedByDescending { it.cents }
        .toMutableList()
    val creditors = balances
        .filter { it.netCents > 0 }
        .map { Mutable(it.memberId, it.name, it.netCents) }
        .sortedByDescending { it.cents }
        .toMutableList()

    val debts = mutableListOf<Debt>()
    var di = 0
    var ci = 0
    while (di < debtors.size && ci < creditors.size) {
        val debtor = debtors[di]
        val creditor = creditors[ci]
        val transfer = minOf(debtor.cents, creditor.cents)
        if (transfer > 0) {
            debts.add(
                Debt(
                    fromMemberId = debtor.memberId,
                    fromName = debtor.name,
                    toMemberId = creditor.memberId,
                    toName = creditor.name,
                    cents = transfer,
                )
            )
            debtor.cents -= transfer
            creditor.cents -= transfer
        }
        if (debtor.cents == 0L) di++
        if (creditor.cents == 0L) ci++
    }
    return debts
}
