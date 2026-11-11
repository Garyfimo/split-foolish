package com.garyfimo.splitfoolish.billsplit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BillSplitterTest {

    private fun p(id: Long, name: String = "P$id", pct: Double = 0.0) = Participant(id, name, pct)

    @Test
    fun equalSplit_exact_divides_cleanly() {
        val result = splitBill(900, SplitMode.Equal, listOf(p(1), p(2), p(3)))
        val shares = assertIs<SplitResult.Success>(result).shares
        assertEquals(listOf(300L, 300L, 300L), shares.map { it.cents })
    }

    @Test
    fun equalSplit_remainder_goes_to_top_of_list() {
        val result = splitBill(1000, SplitMode.Equal, listOf(p(1), p(2), p(3)))
        val shares = assertIs<SplitResult.Success>(result).shares
        assertEquals(listOf(334L, 333L, 333L), shares.map { it.cents })
        assertEquals(1000L, shares.sumOf { it.cents })
    }

    @Test
    fun equalSplit_larger_remainder_spreads_from_top() {
        // 10 cents / 7 people = 1 base + 3 leftover cents to the top 3
        val result = splitBill(10, SplitMode.Equal, List(7) { p(it.toLong()) })
        val shares = assertIs<SplitResult.Success>(result).shares
        assertEquals(listOf(2L, 2L, 2L, 1L, 1L, 1L, 1L), shares.map { it.cents })
    }

    @Test
    fun equalSplit_single_participant_gets_entire_total() {
        val result = splitBill(4237, SplitMode.Equal, listOf(p(1)))
        val shares = assertIs<SplitResult.Success>(result).shares
        assertEquals(listOf(4237L), shares.map { it.cents })
    }

    @Test
    fun equalSplit_zero_total_is_allowed() {
        val result = splitBill(0, SplitMode.Equal, listOf(p(1), p(2)))
        val shares = assertIs<SplitResult.Success>(result).shares
        assertEquals(listOf(0L, 0L), shares.map { it.cents })
    }

    @Test
    fun percentageSplit_exact_percentages() {
        val result = splitBill(
            totalCents = 1000,
            mode = SplitMode.Percentage,
            participants = listOf(p(1, pct = 50.0), p(2, pct = 50.0)),
        )
        val shares = assertIs<SplitResult.Success>(result).shares
        assertEquals(listOf(500L, 500L), shares.map { it.cents })
    }

    @Test
    fun percentageSplit_distributes_rounding_from_top() {
        // 33.33 / 33.33 / 33.34 of $1.00 = 33¢ / 33¢ / 33¢ with 1¢ remainder to the top
        val result = splitBill(
            totalCents = 100,
            mode = SplitMode.Percentage,
            participants = listOf(p(1, pct = 33.33), p(2, pct = 33.33), p(3, pct = 33.34)),
        )
        val shares = assertIs<SplitResult.Success>(result).shares
        assertEquals(100L, shares.sumOf { it.cents })
        assertEquals(34L, shares[0].cents)
        assertEquals(33L, shares[1].cents)
        assertEquals(33L, shares[2].cents)
    }

    @Test
    fun percentageSplit_uneven_shares() {
        val result = splitBill(
            totalCents = 10000,
            mode = SplitMode.Percentage,
            participants = listOf(p(1, pct = 70.0), p(2, pct = 20.0), p(3, pct = 10.0)),
        )
        val shares = assertIs<SplitResult.Success>(result).shares
        assertEquals(listOf(7000L, 2000L, 1000L), shares.map { it.cents })
    }

    @Test
    fun percentageSplit_allows_tolerance_near_hundred() {
        // 99.99 falls inside the 0.01 tolerance — still a valid split.
        val result = splitBill(
            totalCents = 1000,
            mode = SplitMode.Percentage,
            participants = listOf(p(1, pct = 33.33), p(2, pct = 33.33), p(3, pct = 33.33)),
        )
        assertIs<SplitResult.Success>(result)
    }

    @Test
    fun percentageSplit_rejects_sum_far_from_hundred() {
        val result = splitBill(
            totalCents = 1000,
            mode = SplitMode.Percentage,
            participants = listOf(p(1, pct = 50.0), p(2, pct = 40.0)),
        )
        assertEquals(SplitResult.Failure(SplitError.PercentagesDoNotSumToHundred), result)
    }

    @Test
    fun percentageSplit_rejects_negative_percentage() {
        val result = splitBill(
            totalCents = 1000,
            mode = SplitMode.Percentage,
            participants = listOf(p(1, pct = 150.0), p(2, pct = -50.0)),
        )
        assertEquals(SplitResult.Failure(SplitError.NegativePercentage), result)
    }

    @Test
    fun rejects_empty_participants() {
        assertEquals(
            SplitResult.Failure(SplitError.NoParticipants),
            splitBill(1000, SplitMode.Equal, emptyList()),
        )
    }

    @Test
    fun rejects_negative_total() {
        assertEquals(
            SplitResult.Failure(SplitError.NegativeTotal),
            splitBill(-1, SplitMode.Equal, listOf(p(1))),
        )
    }

    @Test
    fun parseAmount_handles_common_inputs() {
        assertEquals(1234L, parseAmountToCents("12.34"))
        assertEquals(1200L, parseAmountToCents("12"))
        assertEquals(50L, parseAmountToCents(".5"))
        assertEquals(50L, parseAmountToCents("0.5"))
        assertEquals(1235L, parseAmountToCents("12.345")) // rounds half-up
    }

    @Test
    fun parseAmount_rejects_bad_inputs() {
        assertEquals(null, parseAmountToCents(""))
        assertEquals(null, parseAmountToCents("abc"))
        assertEquals(null, parseAmountToCents("1.2.3"))
        assertEquals(null, parseAmountToCents("-5"))
    }

    @Test
    fun totalsAlwaysSumToInput_fuzzy() {
        val totals = listOf(1L, 7L, 99L, 100L, 101L, 12345L, 99999L)
        val sizes = listOf(1, 2, 3, 5, 7, 11)
        for (total in totals) {
            for (size in sizes) {
                val participants = List(size) { p(it.toLong()) }
                val shares = assertIs<SplitResult.Success>(
                    splitBill(total, SplitMode.Equal, participants)
                ).shares
                assertTrue(
                    total == shares.sumOf { it.cents },
                    "equal split of $total across $size should sum to $total, got ${shares.sumOf { it.cents }}",
                )
            }
        }
    }
}
