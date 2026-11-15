package com.garyfimo.splitfoolish

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.garyfimo.splitfoolish.data.SplitFoolishRepository
import com.garyfimo.splitfoolish.data.createSplitFoolishDb

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            // Context is pulled from LocalContext (resolves to this Activity) and passed through
            // to AndroidSqliteDriver via createSplitFoolishDb. remember keeps the repo + state
            // across recompositions; rememberCoroutineScope ties the AppState's hydrate + save-
            // through work to this composition's lifecycle (torn down with the Activity).
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val state = remember {
                val db = createSplitFoolishDb(context.applicationContext)
                AppState(SplitFoolishRepository(db), scope)
            }
            App(state)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App(remember { AppState.forPreview() })
}
