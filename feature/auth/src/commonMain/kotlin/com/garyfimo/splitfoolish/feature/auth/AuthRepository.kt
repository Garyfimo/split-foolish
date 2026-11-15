package com.garyfimo.splitfoolish.feature.auth

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseAuthException
import dev.gitlive.firebase.auth.FirebaseAuthInvalidCredentialsException
import dev.gitlive.firebase.auth.FirebaseAuthInvalidUserException
import dev.gitlive.firebase.auth.FirebaseAuthUserCollisionException
import dev.gitlive.firebase.auth.FirebaseAuthWeakPasswordException
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Outcome of a sign-in or sign-up attempt. [AuthResult.Failure] carries a human-readable [reason]
 * that the UI shows directly — we translate Firebase's platform-specific exception types to a
 * small, stable set of messages here so the UI doesn't have to pattern-match on vendor exceptions.
 */
sealed class AuthResult {
    data class Success(val user: AuthUser) : AuthResult()
    data class Failure(val reason: String) : AuthResult()
}

/**
 * The slice of Firebase Auth the rest of the app actually uses. Keeping this as an interface
 * lets tests and previews substitute an in-memory implementation without pulling in Firebase.
 */
interface AuthRepository {
    /** Emits the current [AuthUser] (or null when signed out) and every subsequent change. */
    val currentUser: Flow<AuthUser?>

    /** Who's signed in right now, synchronously. Null when signed out. */
    fun snapshotUser(): AuthUser?

    suspend fun signIn(email: String, password: String): AuthResult
    suspend fun signUp(email: String, password: String): AuthResult
    suspend fun signOut()
}

/**
 * Default production implementation backed by `dev.gitlive:firebase-auth`. Delegates to Firebase
 * Auth under the hood — on Android that's `com.google.firebase:firebase-auth`, on iOS that's
 * `FirebaseAuth` from the Firebase iOS SDK (added to the Xcode project via SPM).
 *
 * Platform SDKs must be initialised BEFORE this class is constructed, because the gitlive facade
 * resolves `Firebase.auth` lazily against the default FirebaseApp:
 * - Android: automatic, courtesy of the `com.google.gms.google-services` Gradle plugin.
 * - iOS: `FirebaseApp.configure()` called from `iOSApp.swift` at app launch.
 */
class FirebaseAuthRepository : AuthRepository {
    private val firebaseAuth get() = Firebase.auth

    override val currentUser: Flow<AuthUser?> =
        firebaseAuth.authStateChanged.map { it?.toAuthUser() }

    override fun snapshotUser(): AuthUser? = firebaseAuth.currentUser?.toAuthUser()

    override suspend fun signIn(email: String, password: String): AuthResult = runAuth {
        firebaseAuth.signInWithEmailAndPassword(email.trim(), password).user
    }

    override suspend fun signUp(email: String, password: String): AuthResult = runAuth {
        firebaseAuth.createUserWithEmailAndPassword(email.trim(), password).user
    }

    override suspend fun signOut() {
        firebaseAuth.signOut()
    }

    private inline fun runAuth(block: () -> dev.gitlive.firebase.auth.FirebaseUser?): AuthResult =
        try {
            val user = block()?.toAuthUser()
            if (user != null) AuthResult.Success(user)
            else AuthResult.Failure("Firebase returned no user — try again.")
        } catch (e: FirebaseAuthInvalidUserException) {
            // Account doesn't exist, disabled, or has been deleted.
            AuthResult.Failure("No account with that email. Try signing up instead.")
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            // Wrong password, or malformed email string.
            AuthResult.Failure("Wrong email or password.")
        } catch (e: FirebaseAuthUserCollisionException) {
            // Sign-up with an email that already has an account.
            AuthResult.Failure("An account with that email already exists. Try signing in.")
        } catch (e: FirebaseAuthWeakPasswordException) {
            AuthResult.Failure("Password is too weak — use at least 6 characters.")
        } catch (e: FirebaseAuthException) {
            // Catch-all for the vendor-specific errors we haven't singled out (quota, network, etc.).
            AuthResult.Failure(e.message ?: "Authentication failed. Please try again.")
        }

    private fun dev.gitlive.firebase.auth.FirebaseUser.toAuthUser(): AuthUser =
        AuthUser(uid = uid, email = email)
}
