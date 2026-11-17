package com.garyfimo.splitfoolish

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.garyfimo.splitfoolish.data.FirestoreGroupRepository
import com.garyfimo.splitfoolish.data.FirestoreInviteRepository
import com.garyfimo.splitfoolish.data.SplitFoolishRepository
import com.garyfimo.splitfoolish.data.SyncedRepository
import com.garyfimo.splitfoolish.data.UserRegistry
import com.garyfimo.splitfoolish.data.createSplitFoolishDb
import com.garyfimo.splitfoolish.feature.auth.AuthState
import com.garyfimo.splitfoolish.feature.auth.FirebaseAuthRepository

class MainActivity : ComponentActivity() {

    /**
     * App-scoped deep-link bridge. Held as an Activity property (not a Compose remember) so
     * `onNewIntent` — which fires outside composition — has a stable reference to push into.
     * Config-change recomposition re-reads the same instance via setContent's closure, so
     * pending state survives rotations without a ViewModel.
     *
     * singleTask launch mode (set in the manifest) guarantees MainActivity is a singleton,
     * which is what makes "property on this class" a safe home for app-scoped state.
     */
    private val deepLinkBridge = DeepLinkBridge()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Cold-start deep link: the launching Intent's data URI is what arrived from Android's
        // link-dispatch. submitUrl silently drops non-invite URLs so it's safe to always call.
        intent?.dataString?.let(deepLinkBridge::submitUrl)

        setContent {
            // Firebase Android SDK auto-initialises from the merged manifest entries the
            // com.google.gms.google-services Gradle plugin injects at build time.
            val context = LocalContext.current
            val scope = rememberCoroutineScope()

            val authState = remember {
                AuthState(repository = FirebaseAuthRepository(), scope = scope)
            }
            val userRegistry = remember { UserRegistry() }
            // Invites are stateless from the client's point of view — a plain `remember` is
            // enough; no per-uid rebuild needed because the repository reads auth on each call.
            val inviteRepo = remember { FirestoreInviteRepository() }

            // Factory invoked once per signed-in uid (keyed inside App.kt's AuthenticatedApp).
            // Groups live in Firestore; friends stay in a per-uid SQLite file so a second
            // account on the same device doesn't see the first account's friends cache.
            val appStateFactory = remember {
                { user: com.garyfimo.splitfoolish.feature.auth.AuthUser,
                  appScope: kotlinx.coroutines.CoroutineScope ->
                    val db = createSplitFoolishDb(
                        context = context.applicationContext,
                        // v2: friends table added uid + icon_id columns in the profile-and-
                        // username PR. Bumped file name instead of a .sqm migration (see the
                        // iOS entry point for the same rationale).
                        name = "splitfoolish-v2-${user.uid}.db",
                    )
                    val friendsRepo = SplitFoolishRepository(db)
                    val groupsRepo = FirestoreGroupRepository()
                    val synced = SyncedRepository(friendsRepo, groupsRepo, user.uid)
                    AppState(
                        repo = synced,
                        userRegistry = userRegistry,
                        invites = inviteRepo,
                        currentUserUid = user.uid,
                        currentUserName = user.email ?: "You",
                        scope = appScope,
                    )
                }
            }

            App(
                authState = authState,
                userRegistry = userRegistry,
                deepLinkBridge = deepLinkBridge,
                appStateFactory = appStateFactory,
            )
        }
    }

    /**
     * Fires when a running MainActivity receives a new Intent — e.g. the user taps a
     * splitfoolish:// link while the app is already open. setIntent() keeps the Activity's
     * stored intent in sync (important if the OS asks for it later, and for getIntent() in
     * child code), and we push the URL into the bridge for App to pick up.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.dataString?.let(deepLinkBridge::submitUrl)
    }
}
