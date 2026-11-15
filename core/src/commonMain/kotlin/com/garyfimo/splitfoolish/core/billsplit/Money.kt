package com.garyfimo.splitfoolish.core.billsplit

/**
 * Parses a user-typed amount like "12.34", "12", or ".5" into whole cents.
 * Uses integer arithmetic throughout to avoid IEEE754 rounding surprises
 * ("12.345" stores as 12.3449999… in a double, so FP-based parsing mis-rounds).
 * Anything past two decimal places is rounded half-up.
 */
fun parseAmountToCents(input: String): Long? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    if (trimmed.any { it != '.' && !it.isDigit() }) return null

    val dotIdx = trimmed.indexOf('.')
    if (dotIdx != trimmed.lastIndexOf('.')) return null

    val wholeStr: String
    val fracStr: String
    if (dotIdx < 0) {
        wholeStr = trimmed
        fracStr = ""
    } else {
        wholeStr = trimmed.substring(0, dotIdx)
        fracStr = trimmed.substring(dotIdx + 1)
    }
    if (wholeStr.isEmpty() && fracStr.isEmpty()) return null

    val whole = if (wholeStr.isEmpty()) 0L else wholeStr.toLongOrNull() ?: return null

    val fracCents: Long = when {
        fracStr.isEmpty() -> 0L
        fracStr.length == 1 -> fracStr.toLong() * 10
        fracStr.length == 2 -> fracStr.toLong()
        else -> {
            val truncated = fracStr.substring(0, 2).toLong()
            val roundingDigit = fracStr[2].digitToInt()
            if (roundingDigit >= 5) truncated + 1 else truncated
        }
    }

    return whole * 100 + fracCents
}
