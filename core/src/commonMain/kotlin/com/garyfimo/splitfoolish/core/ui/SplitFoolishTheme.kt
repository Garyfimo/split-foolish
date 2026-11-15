package com.garyfimo.splitfoolish.core.ui

import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.runtime.Composable

/**
 * App-wide theme wrapper. Uses Material 3 Expressive — `MaterialExpressiveTheme` substitutes
 * expressive defaults for color scheme (`expressiveLightColorScheme`) and motion
 * (`MotionScheme.expressive()`), and we pass [SplitFoolishShapes] to bump the corner radii past
 * the classic scale. Typography stays at Compose's `Typography()` default for now — upstream
 * still has a TODO for expressive typography.
 */
@Composable
fun SplitFoolishTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        shapes = SplitFoolishShapes,
        content = content,
    )
}
