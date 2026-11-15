package com.garyfimo.splitfoolish.feature.groups

import com.garyfimo.splitfoolish.core.billsplit.SplitMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettlementTest {

    private val alice = GroupMember(1, "Alice")
    private val bob = GroupMember(2, "Bob")
    private val charlie = GroupMember(3, "Charlie")

    private fun group(
        members: List<GroupMember> = listOf(alice, bob, charlie),
        vararg expenses: Expense,
    ) = Group(id = 1, name = "Trip", members = members, expenses = expenses.toList())

    private fun equalExpense(
        id: Long,
        payer: GroupMember,
        amount: Long,
        among: List<GroupMember>,
    ) = Expense(
        id = id,
        title = "e$id",
        amountCents = amount,
        payerId = payer.id,
        splitMode = SplitMode.Equal,
        participants = among.map { ExpenseParticipant(it.id) },
    )

    @Test
    fun empty_group_has_zero_balances_and_no_debts() {
        val settlement = computeSettlement(group())
        assertEquals(listOf(0L, 0L, 0L), settlement.balances.map { it.netCents })
        assertEquals(emptyList(), settlement.debts)
    }

    @Test
    fun one_person_pays_split_equally_produces_single_debt_per_other_person() {
        // Alice pays $9 for A+B+C → each owes $3. Alice net +$6, B −$3, C −$3.
        val settlement = computeSettlement(
            group(expenses = arrayOf(equalExpense(1, alice, 900, listOf(alice, bob, charlie))))
        )
        val byName = settlement.balances.associate { it.name to it.netCents }
        assertEquals(600L, byName["Alice"])
        assertEquals(-300L, byName["Bob"])
        assertEquals(-300L, byName["Charlie"])

        assertEquals(2, settlement.debts.size)
        val debtors = settlement.debts.map { it.fromName }.toSet()
        assertEquals(setOf("Bob", "Charlie"), debtors)
        assertTrue(settlement.debts.all { it.toName == "Alice" && it.cents == 300L })
    }

    @Test
    fun everyone_pays_equal_shares_settles_to_zero() {
        // Each of 3 people pays $10 and all split equally → net zero for everyone.
        val settlement = computeSettlement(
            group(
                expenses = arrayOf(
                    equalExpense(1, alice, 1000, listOf(alice, bob, charlie)),
                    equalExpense(2, bob, 1000, listOf(alice, bob, charlie)),
                    equalExpense(3, charlie, 1000, listOf(alice, bob, charlie)),
                )
            )
        )
        assertTrue(
            settlement.balances.all { it.netCents == 0L },
            "expected all balances to settle to zero, got ${settlement.balances}",
        )
        assertEquals(emptyList(), settlement.debts)
    }

    @Test
    fun penny_remainders_balance_out() {
        // $10 equal split across 3 people → shares 334, 333, 333. Alice (payer, idx 0 in split order)
        // got +334 back on her share, so her net = 1000 − 334 = 666. Others each −333.
        val settlement = computeSettlement(
            group(expenses = arrayOf(equalExpense(1, alice, 1000, listOf(alice, bob, charlie))))
        )
        val total = settlement.balances.sumOf { it.netCents }
        assertEquals(0L, total, "balances must sum to zero regardless of rounding")
    }

    @Test
    fun two_payers_produces_chained_debts() {
        // Alice pays $9 for all three → A +6, B/C −3 each.
        // Bob pays $6 for all three → A/C −2, B +4.
        // Combined: A +4, B +1, C −5.
        // Greedy: biggest debtor Charlie(5) → biggest creditor Alice(4) for $4.
        //         Charlie still owes $1 → Bob for $1.
        val settlement = computeSettlement(
            group(
                expenses = arrayOf(
                    equalExpense(1, alice, 900, listOf(alice, bob, charlie)),
                    equalExpense(2, bob, 600, listOf(alice, bob, charlie)),
                )
            )
        )
        val byName = settlement.balances.associate { it.name to it.netCents }
        assertEquals(400L, byName["Alice"])
        assertEquals(100L, byName["Bob"])
        assertEquals(-500L, byName["Charlie"])

        assertEquals(2, settlement.debts.size)
        assertTrue(settlement.debts.all { it.fromName == "Charlie" })
        val toAlice = settlement.debts.first { it.toName == "Alice" }
        val toBob = settlement.debts.first { it.toName == "Bob" }
        assertEquals(400L, toAlice.cents)
        assertEquals(100L, toBob.cents)
    }

    @Test
    fun expense_with_subset_of_members_ignores_the_rest() {
        // Only Alice and Bob share dessert; Charlie isn't a participant.
        val settlement = computeSettlement(
            group(expenses = arrayOf(equalExpense(1, alice, 600, listOf(alice, bob))))
        )
        val byName = settlement.balances.associate { it.name to it.netCents }
        assertEquals(300L, byName["Alice"])
        assertEquals(-300L, byName["Bob"])
        assertEquals(0L, byName["Charlie"])
        assertEquals(1, settlement.debts.size)
        assertEquals("Bob", settlement.debts[0].fromName)
        assertEquals("Alice", settlement.debts[0].toName)
    }

    @Test
    fun percentage_split_with_different_payer_tracks_who_paid() {
        // Alice pays $100, split 50/30/20 among A/B/C.
        // Shares: A 50, B 30, C 20. Alice net: +100 − 50 = +50. Bob −30, Charlie −20.
        val expense = Expense(
            id = 1,
            title = "trip",
            amountCents = 10000,
            payerId = alice.id,
            splitMode = SplitMode.Percentage,
            participants = listOf(
                ExpenseParticipant(alice.id, 50.0),
                ExpenseParticipant(bob.id, 30.0),
                ExpenseParticipant(charlie.id, 20.0),
            ),
        )
        val settlement = computeSettlement(group(expenses = arrayOf(expense)))
        val byName = settlement.balances.associate { it.name to it.netCents }
        assertEquals(5000L, byName["Alice"])
        assertEquals(-3000L, byName["Bob"])
        assertEquals(-2000L, byName["Charlie"])
        assertEquals(2, settlement.debts.size)
    }

    @Test
    fun debt_count_never_exceeds_members_minus_one() {
        // Greedy simplification guarantees ≤ n−1 debts even in messy scenarios.
        val diners = (1L..5L).map { GroupMember(it, "P$it") }
        val expenses = diners.mapIndexed { idx, payer ->
            equalExpense(
                id = idx.toLong() + 10,
                payer = payer,
                amount = (idx + 1) * 1234L,
                among = diners,
            )
        }
        val settlement = computeSettlement(
            Group(id = 99, name = "Dinner", members = diners, expenses = expenses)
        )
        assertTrue(
            settlement.debts.size <= diners.size - 1,
            "greedy settlement should produce at most n−1 debts, got ${settlement.debts.size}",
        )
        val totalNet = settlement.balances.sumOf { it.netCents }
        assertEquals(0L, totalNet)
    }

    @Test
    fun expense_with_invalid_payer_is_skipped() {
        val ghostExpense = equalExpense(1, alice, 1000, listOf(alice, bob))
            .copy(payerId = 999)
        val settlement = computeSettlement(group(expenses = arrayOf(ghostExpense)))
        assertTrue(settlement.balances.all { it.netCents == 0L })
        assertEquals(emptyList(), settlement.debts)
    }
}
