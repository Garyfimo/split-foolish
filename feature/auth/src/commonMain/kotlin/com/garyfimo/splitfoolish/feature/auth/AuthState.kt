package com.garyfimo.splitfoolish.feature.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Exposes the currently signed-in user to the UI as a [StateFlow] that Compose can collect with
 * `collectAsState()`. Wraps a [repository] and relays its [AuthRepository.currentUser] flow into
 * an always-readable StateFlow seeded with the synchronous snapshot — so the first frame doesn't
 * have to wait a tick before knowing whether we're signed in.
 */
class AuthState(
    private val repository: AuthRepository,
    scope: CoroutineScope,
) {
    private val _user = MutableStateFlow<AuthUser?>(repository.snapshotUser())
    val user: StateFlow<AuthUser?> = _user.asStateFlow()

    init {
        // Keep the StateFlow in lock-step with Firebase's authStateChanged — covers sign-ins,
        // sign-outs, token refreshes and session expirations initiated anywhere else (deep links,
        // background refresh, etc.) without the UI having to know about them.
        scope.launch {
            repository.currentUser.collect { _user.value = it }
        }
    }

    suspend fun signIn(email: String, password: String): AuthResult =
        repository.signIn(email, password)

    suspend fun signUp(email: String, password: String): AuthResult =
        repository.signUp(email, password)

    suspend fun signOut() {
        repository.signOut()
    }
}
