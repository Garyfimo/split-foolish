package com.garyfimo.splitfoolish.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Single-screen auth entry point. A ToggleButton pair flips between **Sign in** (existing account)
 * and **Sign up** (create account). Both share one email + password form so the user doesn't have
 * to retype their credentials when they realise they picked the wrong mode.
 *
 * All sign-in / sign-up calls happen on the composition's scope — the screen stays usable
 * (cancel button, retry) if a network call takes a while. Errors returned by [AuthState] surface
 * inline under the form rather than as snackbars so they stay visible while the user retypes.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoginScreen(
    authState: AuthState,
) {
    var mode by remember { mutableStateOf(AuthMode.SignIn) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var inFlight by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val submitLabel = if (mode == AuthMode.SignIn) "Sign in" else "Create account"
    val canSubmit = !inFlight && email.isNotBlank() && password.length >= 6

    fun submit() {
        if (!canSubmit) return
        inFlight = true
        errorMessage = null
        scope.launch {
            val result = when (mode) {
                AuthMode.SignIn -> authState.signIn(email, password)
                AuthMode.SignUp -> authState.signUp(email, password)
            }
            // On success, nothing to do here — AuthState's StateFlow emits the new user and the
            // parent composable swaps this screen out for the main app. We just drop in-flight state.
            if (result is AuthResult.Failure) {
                errorMessage = result.reason
            }
            inFlight = false
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("SplitFoolish") }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                if (mode == AuthMode.SignIn)
                    "Welcome back. Sign in to sync your groups across devices."
                else
                    "Create an account to sync your groups across devices.",
                style = MaterialTheme.typography.bodyMedium,
            )

            // ToggleButton pair mirrors the Split-mode selector in ExpenseEditorScreen for
            // visual consistency with the rest of the Expressive-themed UI.
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ToggleButton(
                    checked = mode == AuthMode.SignIn,
                    onCheckedChange = { if (it) { mode = AuthMode.SignIn; errorMessage = null } },
                    modifier = Modifier.weight(1f),
                ) { Text("Sign in") }
                ToggleButton(
                    checked = mode == AuthMode.SignUp,
                    onCheckedChange = { if (it) { mode = AuthMode.SignUp; errorMessage = null } },
                    modifier = Modifier.weight(1f),
                ) { Text("Sign up") }
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it; errorMessage = null },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
                enabled = !inFlight,
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it; errorMessage = null },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                supportingText = {
                    if (mode == AuthMode.SignUp) Text("At least 6 characters.")
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !inFlight,
            )

            errorMessage?.let { msg ->
                Text(
                    msg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Button(
                onClick = { submit() },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (inFlight) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(submitLabel)
                }
            }

            // Quick toggle at the bottom too — common UX pattern (both the tabs at top and the
            // inline swap link) to catch people who miss the top toggle.
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (mode == AuthMode.SignIn) "Don't have an account?" else "Already have one?",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(
                    onClick = {
                        mode = if (mode == AuthMode.SignIn) AuthMode.SignUp else AuthMode.SignIn
                        errorMessage = null
                    },
                    enabled = !inFlight,
                ) {
                    Text(if (mode == AuthMode.SignIn) "Sign up" else "Sign in")
                }
            }
        }
    }
}

private enum class AuthMode { SignIn, SignUp }
