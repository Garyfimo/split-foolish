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

    override suspend fun signIn(email: String, password: String): AuthResult =
        runAuth(isSignUp = false) {
            firebaseAuth.signInWithEmailAndPassword(email.trim(), password).user
        }

    override suspend fun signUp(email: String, password: String): AuthResult =
        runAuth(isSignUp = true) {
            firebaseAuth.createUserWithEmailAndPassword(email.trim(), password).user
        }

    override suspend fun signOut() {
        firebaseAuth.signOut()
    }

    /**
     * Shared exception-to-message translation for both sign-in and sign-up. [isSignUp] is required
     * because several Firebase exception types mean very different things depending on the call
     * site — most notably [FirebaseAuthInvalidCredentialsException] which fires for both
     * "wrong password" on sign-in AND "malformed email" on sign-up.
     */
    private inline fun runAuth(
        isSignUp: Boolean,
        block: () -> dev.gitlive.firebase.auth.FirebaseUser?,
    ): AuthResult = try {
        val user = block()?.toAuthUser()
        if (user != null) AuthResult.Success(user)
        else AuthResult.Failure("Firebase returned no user — try again.")
    } catch (e: FirebaseAuthInvalidUserException) {
        // Firebase's email-enumeration protection (default on for projects created after Sept
        // 2023) folds the "no such user" case into FirebaseAuthInvalidCredentialsException below,
        // so on that path this branch is only reached for admin-disabled accounts and invalid
        // user tokens. We return the same generic sign-in message as the credentials branch so
        // account state (exists? disabled?) is never leaked through the error text.
        AuthResult.Failure(
            if (isSignUp) "Sign-up failed. Please try again."
            else "Wrong email or password."
        )
    } catch (e: FirebaseAuthInvalidCredentialsException) {
        // Same Firebase exception type, two very different user-facing causes depending on
        // which flow called us. Picking the wrong message here was the bug flagged in PR #9 review.
        AuthResult.Failure(
            if (isSignUp) "That email address looks malformed — please check it."
            else "Wrong email or password."
        )
    } catch (e: FirebaseAuthUserCollisionException) {
        // Deliberately generic — saying "an account with this email already exists" would let
        // a scripted sign-up attempt confirm which emails in a breach list are registered on
        // SplitFoolish (email-enumeration attack). The sign-in prompt below is still enough
        // guidance for a legitimate user who forgot they'd already signed up. If the explicit
        // message is ever needed back for UX, mitigate by enabling Firebase App Check first so
        // only requests from the real app bundle can hit the signup endpoint at scale.
        AuthResult.Failure("Sign-up failed. If you already have an account, try signing in instead.")
    } catch (e: FirebaseAuthWeakPasswordException) {
        AuthResult.Failure("Password is too weak — use at least 6 characters.")
    } catch (e: FirebaseAuthException) {
        // Catch-all for the vendor-specific errors we haven't singled out (quota, network, etc.).
        AuthResult.Failure(e.message ?: "Authentication failed. Please try again.")
    }

    private fun dev.gitlive.firebase.auth.FirebaseUser.toAuthUser(): AuthUser =
        AuthUser(uid = uid, email = email)
}
