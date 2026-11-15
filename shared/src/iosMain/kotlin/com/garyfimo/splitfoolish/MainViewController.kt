package com.garyfimo.splitfoolish

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.ComposeUIViewController
import com.garyfimo.splitfoolish.data.SplitFoolishRepository
import com.garyfimo.splitfoolish.data.createSplitFoolishDb

fun MainViewController() = ComposeUIViewController {
    val scope = rememberCoroutineScope()
    val state = remember {
        val db = createSplitFoolishDb()
        AppState(SplitFoolishRepository(db), scope)
    }
    App(state)
}
