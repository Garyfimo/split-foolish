package com.garyfimo.splitfoolish

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.garyfimo.splitfoolish.data.FirestoreGroupRepository
import com.garyfimo.splitfoolish.data.SplitFoolishRepository
import com.garyfimo.splitfoolish.data.SyncedRepository
import com.garyfimo.splitfoolish.data.UserRegistry
import com.garyfimo.splitfoolish.data.createSplitFoolishDb
import com.garyfimo.splitfoolish.feature.auth.AuthState
import com.garyfimo.splitfoolish.feature.auth.FirebaseAuthRepository

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            // Firebase Android SDK auto-initialises from the merged manifest entries the
            // com.google.gms.google-services Gradle plugin injects at build time.
            val context = LocalContext.current
            val scope = rememberCoroutineScope()

            val authState = remember {
                AuthState(repository = FirebaseAuthRepository(), scope = scope)
            }
            val userRegistry = remember { UserRegistry() }

            // Factory invoked once per signed-in uid (keyed inside App.kt's AuthenticatedApp).
            // Groups live in Firestore; friends stay in a per-uid SQLite file so a second
            // account on the same device doesn't see the first account's friends cache.
            val appStateFactory = remember {
                { user: com.garyfimo.splitfoolish.feature.auth.AuthUser,
                  appScope: kotlinx.coroutines.CoroutineScope ->
                    val db = createSplitFoolishDb(
                        context = context.applicationContext,
                        name = "splitfoolish-${user.uid}.db",
                    )
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
    }
}
