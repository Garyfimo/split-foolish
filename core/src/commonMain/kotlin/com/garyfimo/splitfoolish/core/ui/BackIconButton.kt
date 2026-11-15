package com.garyfimo.splitfoolish.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable

/**
 * Standard back arrow for TopAppBar navigationIcon slots across the app.
 * `AutoMirrored.Filled.ArrowBack` flips for RTL locales (so Arabic/Hebrew read the arrow correctly
 * as "back" instead of "forward"), and `contentDescription` wins the TalkBack/VoiceOver
 * announcement — a plain "←" glyph in Text would be read as "leftwards arrow" and leak the wrong
 * semantics.
 */
@Composable
fun BackIconButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
        )
    }
}
