package com.garyfimo.splitfoolish.billsplit

import kotlin.math.abs
import kotlin.math.roundToLong

enum class SplitMode { Equal, Percentage }

data class Participant(
    val id: Long,
    val name: String,
    val percentage: Double = 0.0,
)

data class Share(
    val participant: Participant,
    val cents: Long,
)

sealed class SplitResult {
    data class Success(val shares: List<Share>) : SplitResult()
    data class Failure(val error: SplitError) : SplitResult()
}

enum class SplitError {
    NoParticipants,
    NegativeTotal,
    PercentagesDoNotSumToHundred,
    NegativePercentage,
}

// Users enter percentages with two decimals; three 33.33s sum to 99.9899999… in double precision,
// so anything tighter than ~0.1 would reject sums people intuitively consider correct.
private const val PERCENT_TOLERANCE = 0.1

fun splitBill(totalCents: Long, mode: SplitMode, participants: List<Participant>): SplitResult {
    if (participants.isEmpty()) return SplitResult.Failure(SplitError.NoParticipants)
    if (totalCents < 0) return SplitResult.Failure(SplitError.NegativeTotal)

    return when (mode) {
        SplitMode.Equal -> splitEqually(totalCents, participants)
        SplitMode.Percentage -> splitByPercentage(totalCents, participants)
    }
}

private fun splitEqually(totalCents: Long, participants: List<Participant>): SplitResult {
    val n = participants.size
    val base = totalCents / n
    val remainder = (totalCents - base * n).toInt()
    val shares = participants.mapIndexed { index, participant ->
        val extra = if (index < remainder) 1L else 0L
        Share(participant, base + extra)
    }
    return SplitResult.Success(shares)
}

private fun splitByPercentage(totalCents: Long, participants: List<Participant>): SplitResult {
    if (participants.any { it.percentage < 0 }) {
        return SplitResult.Failure(SplitError.NegativePercentage)
    }
    val sum = participants.sumOf { it.percentage }
    if (abs(sum - 100.0) > PERCENT_TOLERANCE) {
        return SplitResult.Failure(SplitError.PercentagesDoNotSumToHundred)
    }

    val cents = participants
        .map { (totalCents * it.percentage / 100.0).roundToLong() }
        .toMutableList()
    var remainder = totalCents - cents.sum()

    // Positive remainder: hand 1-cent extras from the top of the list.
    // Negative remainder (rare — only when the tolerant sum is slightly over 100): take 1-cent back,
    // skipping shares that are already zero so we never produce a negative share.
    var i = 0
    while (remainder > 0) {
        cents[i] = cents[i] + 1
        remainder--
        i = (i + 1) % cents.size
    }
    if (remainder < 0) {
        var guard = 0
        i = 0
        while (remainder < 0 && guard < cents.size * 2) {
            if (cents[i] > 0) {
                cents[i] = cents[i] - 1
                remainder++
                guard = 0
            } else {
                guard++
            }
            i = (i + 1) % cents.size
        }
    }

    val shares = participants.mapIndexed { index, participant -> Share(participant, cents[index]) }
    return SplitResult.Success(shares)
}
