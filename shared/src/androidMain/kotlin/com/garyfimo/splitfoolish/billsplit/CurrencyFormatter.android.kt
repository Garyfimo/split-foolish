package com.garyfimo.splitfoolish.billsplit

import java.text.NumberFormat

actual fun formatCents(cents: Long): String =
    NumberFormat.getCurrencyInstance().format(cents / 100.0)

actual fun currencySymbol(): String =
    NumberFormat.getCurrencyInstance().currency?.symbol ?: "$"
