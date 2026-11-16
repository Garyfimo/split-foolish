package com.garyfimo.splitfoolish.data

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import kotlinx.serialization.Serializable

/**
 * Lookup result for an email → user resolution. Null uid means no SplitFoolish account exists
 * with that email.
 */
data class RegisteredUser(
    val uid: String,
    val email: String,
    val displayName: String?,
)

/**
 * Keeps the two user-directory collections in sync:
 * - `/users/{uid}` — canonical user record keyed by uid. Carries email + optional display name.
 * - `/users_by_email/{lowercased_email}` — reverse lookup so we can resolve an email to a uid
 *   without scanning the users collection. Firestore can't efficiently query users by email
 *   field without a separate index; maintaining this pointer document costs one extra write
 *   per sign-up and turns every "add member by email" into an O(1) document read.
 *
 * All writes are keyed on a lowercased, trimmed email so lookups are case-insensitive.
 */
class UserRegistry {

    private val firestore get() = Firebase.firestore

    /**
     * Call on every successful sign-in / sign-up. Writes both documents (idempotent for sign-in,
     * necessary for sign-up). Also safe to call repeatedly — Firestore's set() is upsert.
     */
    suspend fun ensureRegistered(uid: String, email: String, displayName: String? = null) {
        val normalisedEmail = email.trim().lowercase()
        firestore.collection(USERS).document(uid).set(
            UserDoc(uid = uid, email = normalisedEmail, displayName = displayName)
        )
        firestore.collection(USERS_BY_EMAIL).document(normalisedEmail).set(
            UsersByEmailDoc(uid = uid)
        )
    }

    /**
     * Resolve an email to a registered SplitFoolish user. Returns null if no account has that
     * email — the UI shows "no SplitFoolish account with that email" so the inviter can nudge
     * them to sign up out of band.
     */
    suspend fun lookupByEmail(email: String): RegisteredUser? {
        val normalisedEmail = email.trim().lowercase()
        if (normalisedEmail.isBlank()) return null

        val pointer = firestore.collection(USERS_BY_EMAIL).document(normalisedEmail).get()
        if (!pointer.exists) return null
        val uid = runCatching { pointer.data<UsersByEmailDoc>() }.getOrNull()?.uid ?: return null

        val userDoc = firestore.collection(USERS).document(uid).get()
        if (!userDoc.exists) return null
        val user = runCatching { userDoc.data<UserDoc>() }.getOrNull() ?: return null

        return RegisteredUser(uid = user.uid, email = user.email, displayName = user.displayName)
    }

    /**
     * Flip the per-user "I have pushed my pre-Firestore local groups to the cloud" flag. We use
     * a boolean on the user doc rather than per-device storage so re-installing the app doesn't
     * cause a duplicate migration attempt.
     */
    suspend fun markMigrated(uid: String) {
        // set(merge=true) performs a field-level merge: writes only the fields present in the DTO,
        // leaves existing fields untouched. Avoids having to re-read the doc just to flip one bool.
        firestore.collection(USERS).document(uid)
            .set(MigrationFlagDoc(migrated = true), merge = true)
    }

    suspend fun isMigrated(uid: String): Boolean {
        val doc = firestore.collection(USERS).document(uid).get()
        if (!doc.exists) return false
        return runCatching { doc.data<UserDoc>() }.getOrNull()?.migrated == true
    }

    private companion object {
        const val USERS = "users"
        const val USERS_BY_EMAIL = "users_by_email"
    }
}

@Serializable
internal data class UserDoc(
    val uid: String,
    val email: String,
    val displayName: String? = null,
    val migrated: Boolean = false,
)

@Serializable
internal data class UsersByEmailDoc(
    val uid: String,
)

/** Tiny DTO used only for the merge-write that flips the migration flag. */
@Serializable
internal data class MigrationFlagDoc(
    val migrated: Boolean,
)
