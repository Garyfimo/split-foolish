package com.garyfimo.splitfoolish.billsplit

import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterCurrencyStyle

actual fun formatCents(cents: Long): String {
    val formatter = NSNumberFormatter().apply {
        numberStyle = NSNumberFormatterCurrencyStyle
    }
    return formatter.stringFromNumber(NSNumber(double = cents / 100.0)) ?: "$cents"
}
