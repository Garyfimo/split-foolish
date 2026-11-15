package com.garyfimo.splitfoolish

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.ComposeUIViewController
import com.garyfimo.splitfoolish.data.SplitFoolishRepository
import com.garyfimo.splitfoolish.data.createSplitFoolishDb
import com.garyfimo.splitfoolish.feature.auth.AuthState
import com.garyfimo.splitfoolish.feature.auth.FirebaseAuthRepository

/**
 * iOS entry point. Assumes `FirebaseApp.configure()` has already been invoked in the Swift
 * side of the app launch sequence (see `iOSApp.swift`) — the gitlive facade's first read of
 * `Firebase.auth` crashes if the default FirebaseApp isn't initialised yet.
 */
fun MainViewController() = ComposeUIViewController {
    val scope = rememberCoroutineScope()

    val authState = remember {
        AuthState(repository = FirebaseAuthRepository(), scope = scope)
    }

    // Capturing nothing from Context (iOS doesn't need it) — the per-uid SQLite file lives in
    // the app's documents directory under the name we supply.
    val repositoryForUid: (String) -> SplitFoolishRepository = remember {
        { uid ->
            SplitFoolishRepository(createSplitFoolishDb(name = "splitfoolish-$uid.db"))
        }
    }

    App(authState = authState, repositoryForUid = repositoryForUid)
}
