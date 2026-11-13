package com.garyfimo.splitfoolish.ui

import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.runtime.Composable

/**
 * App-wide theme wrapper. Uses Material 3 Expressive — `MaterialExpressiveTheme` substitutes
 * expressive defaults for color scheme (`expressiveLightColorScheme`) and motion
 * (`MotionScheme.expressive()`) and keeps shape/typography defaults for now (upstream still
 * has a TODO for expressive typography). All existing `MaterialTheme.colorScheme` reads keep
 * working because the Expressive theme is itself a MaterialTheme under the hood.
 */
@Composable
fun SplitFoolishTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(content = content)
}
