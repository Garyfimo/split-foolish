package com.garyfimo.splitfoolish.core.billsplit

import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterCurrencyStyle

private fun currencyFormatter(): NSNumberFormatter = NSNumberFormatter().apply {
    numberStyle = NSNumberFormatterCurrencyStyle
}

actual fun formatCents(cents: Long): String =
    currencyFormatter().stringFromNumber(NSNumber(double = cents / 100.0)) ?: "$cents"

actual fun currencySymbol(): String =
    currencyFormatter().currencySymbol
