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
            // v2 file name: the friends table gained uid + icon_id columns in the profile-and-
            // username PR. Rather than wire a .sqm migration for a user-count of ~2 testers, we
            // bump the file name — any legacy v1 file on disk is orphaned, and new installs/
            // signed-in uids get a fresh schema.
            val db = createSplitFoolishDb(name = "splitfoolish-v2-${user.uid}.db")
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
