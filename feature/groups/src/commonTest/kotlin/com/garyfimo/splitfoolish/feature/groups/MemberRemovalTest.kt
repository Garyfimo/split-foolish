package com.garyfimo.splitfoolish.feature.groups

import com.garyfimo.splitfoolish.core.billsplit.SplitMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MemberRemovalTest {

    private fun buildGroup(): EditableGroup {
        val group = EditableGroup(id = 1, name = "Trip")
        group.members.add(EditableMember(id = 10, name = "Alice"))
        group.members.add(EditableMember(id = 11, name = "Bob"))
        group.members.add(EditableMember(id = 12, name = "Charlie"))
        return group
    }

    private fun addEqualExpense(
        group: EditableGroup,
        id: Long,
        payerId: Long,
        amountInput: String,
        amongIds: List<Long>,
    ) {
        group.expenses.add(
            EditableExpense(
                id = id,
                title = "e$id",
                amountInput = amountInput,
                payerId = payerId,
                splitMode = SplitMode.Equal,
                participants = amongIds.map {
                    EditableExpenseParticipant(it, included = true, percentInput = "")
                },
            )
        )
    }

    @Test
    fun preview_is_non_destructive_when_member_has_no_expenses() {
        val group = buildGroup()
        addEqualExpense(group, id = 100, payerId = 10, amountInput = "30", amongIds = listOf(10, 11))
        val impact = group.previewMemberRemoval(memberId = 12)
        assertEquals(0, impact.expensesToDelete)
        assertEquals(0, impact.expensesToUpdate)
        assertTrue(!impact.isDestructive)
    }

    @Test
    fun preview_counts_payer_and_participant_separately() {
        val group = buildGroup()
        // Alice paid for expense 100.
        addEqualExpense(group, id = 100, payerId = 10, amountInput = "30", amongIds = listOf(10, 11, 12))
        // Bob paid for expense 200, Alice is a participant.
        addEqualExpense(group, id = 200, payerId = 11, amountInput = "15", amongIds = listOf(10, 11))
        // Charlie paid for 300, Alice NOT a participant.
        addEqualExpense(group, id = 300, payerId = 12, amountInput = "9", amongIds = listOf(11, 12))

        val impact = group.previewMemberRemoval(memberId = 10)
        assertEquals(1, impact.expensesToDelete, "Alice paid for one expense")
        assertEquals(1, impact.expensesToUpdate, "Alice participates in one other expense")
    }

    @Test
    fun cascade_removes_payer_expenses_and_cleans_participant_lists() {
        val group = buildGroup()
        addEqualExpense(group, id = 100, payerId = 10, amountInput = "30", amongIds = listOf(10, 11, 12))
        addEqualExpense(group, id = 200, payerId = 11, amountInput = "15", amongIds = listOf(10, 11))
        addEqualExpense(group, id = 300, payerId = 12, amountInput = "9", amongIds = listOf(11, 12))

        group.removeMemberCascade(memberId = 10)

        // Expense 100 (paid by Alice) is gone.
        assertNull(group.expenses.firstOrNull { it.id == 100L })
        // Expense 200 (paid by Bob) stays but Alice is removed from participants.
        val e200 = group.expenses.first { it.id == 200L }
        assertEquals(listOf(11L), e200.participants.map { it.memberId })
        // Expense 300 (Alice not involved) is untouched.
        val e300 = group.expenses.first { it.id == 300L }
        assertEquals(listOf(11L, 12L), e300.participants.map { it.memberId })
        // Member list has been shrunk.
        assertEquals(listOf(11L, 12L), group.members.map { it.id })
    }

    @Test
    fun settlement_stays_coherent_after_cascade() {
        val group = buildGroup()
        // Bob pays $6 for Alice + Bob + Charlie → each owes $2. Net: Alice −2, Bob +4, Charlie −2.
        addEqualExpense(group, id = 100, payerId = 11, amountInput = "6", amongIds = listOf(10, 11, 12))
        // Alice pays $9 for Alice + Bob + Charlie — this gets wiped when Alice is removed.
        addEqualExpense(group, id = 200, payerId = 10, amountInput = "9", amongIds = listOf(10, 11, 12))

        group.removeMemberCascade(memberId = 10)

        val settlement = computeSettlement(group.toDomain())
        val byName = settlement.balances.associate { it.name to it.netCents }
        // After cascade only expense 100 remains, with participants Bob and Charlie.
        // Bob paid $6 for Bob+Charlie, each owes $3. Bob net +3, Charlie net −3.
        assertEquals(300L, byName["Bob"])
        assertEquals(-300L, byName["Charlie"])
        assertEquals(1, settlement.debts.size)
        assertEquals("Charlie", settlement.debts[0].fromName)
        assertEquals("Bob", settlement.debts[0].toName)
        assertEquals(300L, settlement.debts[0].cents)
    }

    @Test
    fun cascade_on_uninvolved_member_is_a_plain_remove() {
        val group = buildGroup()
        addEqualExpense(group, id = 100, payerId = 10, amountInput = "6", amongIds = listOf(10, 11))

        group.removeMemberCascade(memberId = 12)

        assertEquals(1, group.expenses.size)
        assertEquals(listOf(10L, 11L), group.members.map { it.id })
    }
}
