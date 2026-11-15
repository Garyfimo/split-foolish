package com.garyfimo.splitfoolish.core.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Expressive shape scale — noticeably rounder than Material 3 defaults so cards, buttons, and
 * text fields read as plush and curvy rather than rectangular. Default scale for reference
 * (extraSmall 4, small 8, medium 12, large 16, extraLarge 28). Bumping all of them meaningfully
 * is what gives the app the "expressive" silhouette without having to change a single screen.
 */
val SplitFoolishShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(40.dp),
)
