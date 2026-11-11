package com.garyfimo.splitfoolish.billsplit

expect fun formatCents(cents: Long): String

/** Locale currency symbol pulled from the same source as [formatCents], so the input prefix matches the formatted shares. */
expect fun currencySymbol(): String
