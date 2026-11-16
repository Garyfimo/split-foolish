package com.garyfimo.splitfoolish

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.ComposeUIViewController
import com.garyfimo.splitfoolish.data.FirestoreGroupRepository
import com.garyfimo.splitfoolish.data.SplitFoolishRepository
import com.garyfimo.splitfoolish.data.SyncedRepository
import com.garyfimo.splitfoolish.data.UserRegistry
import com.garyfimo.splitfoolish.data.createSplitFoolishDb
import com.garyfimo.splitfoolish.feature.auth.AuthState
import com.garyfimo.splitfoolish.feature.auth.AuthUser
import com.garyfimo.splitfoolish.feature.auth.FirebaseAuthRepository
import kotlinx.coroutines.CoroutineScope

/**
 * iOS entry point. Assumes FirebaseApp.configure() was called from iOSApp.swift's AppDelegate.
 */
fun MainViewController() = ComposeUIViewController {
    val scope = rememberCoroutineScope()

    val authState = remember { AuthState(repository = FirebaseAuthRepository(), scope = scope) }
    val userRegistry = remember { UserRegistry() }

    val appStateFactory = remember {
        { user: AuthUser, appScope: CoroutineScope ->
            val db = createSplitFoolishDb(name = "splitfoolish-${user.uid}.db")
            val friendsRepo = SplitFoolishRepository(db)
            val groupsRepo = FirestoreGroupRepository()
            val synced = SyncedRepository(friendsRepo, groupsRepo, user.uid)
            AppState(
                repo = synced,
                userRegistry = userRegistry,
                currentUserUid = user.uid,
                currentUserName = user.email ?: "You",
                scope = appScope,
            )
        }
    }

    App(
        authState = authState,
        userRegistry = userRegistry,
        appStateFactory = appStateFactory,
    )
}
