package com.garyfimo.splitfoolish

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.garyfimo.splitfoolish.data.SplitFoolishRepository
import com.garyfimo.splitfoolish.data.createSplitFoolishDb
import com.garyfimo.splitfoolish.feature.auth.AuthState
import com.garyfimo.splitfoolish.feature.auth.FirebaseAuthRepository

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            // Firebase Android SDK auto-initialises from the merged manifest entries the
            // `com.google.gms.google-services` Gradle plugin injects at build time — no explicit
            // FirebaseApp.initializeApp(context) call needed here.
            val context = LocalContext.current
            val scope = rememberCoroutineScope()

            // Auth state is constructed once per Activity. Its internal StateFlow stays live
            // across sign-in/out cycles — we only swap the per-uid repository below.
            val authState = remember {
                AuthState(repository = FirebaseAuthRepository(), scope = scope)
            }

            // Factory that the shared layer calls when it knows which uid to scope data to.
            // Capturing `context` here keeps Android's Context requirement out of commonMain.
            val repositoryForUid: (String) -> SplitFoolishRepository = remember {
                { uid ->
                    val db = createSplitFoolishDb(
                        context = context.applicationContext,
                        name = "splitfoolish-$uid.db",
                    )
                    SplitFoolishRepository(db)
                }
            }

            App(authState = authState, repositoryForUid = repositoryForUid)
        }
    }
}
