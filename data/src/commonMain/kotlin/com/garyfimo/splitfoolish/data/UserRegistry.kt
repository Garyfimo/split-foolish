package com.garyfimo.splitfoolish.data

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

/**
 * Lookup result for a user-directory resolution (email → user or username → user). Null return
 * from a lookup means no SplitFoolish account matches that handle.
 */
data class RegisteredUser(
    val uid: String,
    val email: String,
    val displayName: String?,
    val username: String? = null,
    val iconId: String? = null,
)

/**
 * The signed-in user's own profile, surfaced to the Settings screen as a live Flow so remote
 * changes (another device's edit, admin tooling) show up immediately.
 */
data class UserProfile(
    val uid: String,
    val email: String,
    val displayName: String?,
    val username: String?,
    val iconId: String?,
)

/**
 * Result of a username claim attempt. [claimUsername] always succeeds in terms of not throwing;
 * the enum tells the UI which inline message to render.
 */
enum class UsernameClaimResult {
    /** Written successfully — also updated [UserProfile.username] on the user doc. */
    Ok,

    /** Same username as the user already has. No-op. */
    Unchanged,

    /** Someone else owns the pointer. */
    Taken,

    /** Didn't match the client-side format rules. Caller shouldn't reach this if it validates
     *  first, but defence-in-depth is cheap. */
    Invalid,

    /** Firestore call failed (network, permissions, …). Caller should surface the exception
     *  message; this enum value exists so the result shape stays uniform. */
    NetworkError,
}

/**
 * Keeps the user-directory collections in sync:
 * - `/users/{uid}` — canonical user record keyed by uid. Carries email, optional displayName,
 *   optional username (the user-chosen handle) and optional iconId (profile avatar selection).
 * - `/users_by_email/{lowercased_email}` — email → uid reverse index. Written at sign-up and
 *   on every sign-in (idempotent). Powers "Add member by email".
 * - `/usernames/{lowercased_username}` — username → uid reverse index. Written via a Firestore
 *   transaction so simultaneous claims of the same handle serialise correctly (the second
 *   transaction observes the first's commit and aborts). Powers "Add friend by username".
 *
 * All reverse-index writes are keyed on a lowercased, trimmed string so lookups are case-insensitive.
 */
class UserRegistry {

    private val firestore get() = Firebase.firestore

    /**
     * Call on every successful sign-in / sign-up. Writes the user doc with merge semantics so
     * existing username / iconId / migrated flags aren't clobbered by a sign-in that doesn't
     * know about them. The email reverse index uses regular set() — it has only one field and
     * overwriting it is the correct behaviour (email might have changed).
     */
    suspend fun ensureRegistered(uid: String, email: String, displayName: String? = null) {
        val normalisedEmail = email.trim().lowercase()
        // merge = true so we don't zero out username / iconId / migrated on the way in. SignupUserDoc
        // only has the three fields we actually mean to write at sign-in; the others remain untouched.
        firestore.collection(USERS).document(uid).set(
            SignupUserDoc(uid = uid, email = normalisedEmail, displayName = displayName),
            merge = true,
        )
        firestore.collection(USERS_BY_EMAIL).document(normalisedEmail).set(
            UsersByEmailDoc(uid = uid)
        )
    }

    /** Live view of the signed-in user's own profile. Used by the Settings screen so a save on
     *  one device surfaces on the other within the same session. */
    fun observeProfile(uid: String): Flow<UserProfile?> =
        firestore.collection(USERS).document(uid).snapshots.map { snap ->
            if (!snap.exists) return@map null
            val doc = runCatching { snap.data<UserDoc>() }.getOrNull() ?: return@map null
            UserProfile(
                uid = doc.uid,
                email = doc.email,
                displayName = doc.displayName,
                username = doc.username,
                iconId = doc.iconId,
            )
        }

    /**
     * Claim or rename a username. Runs as a Firestore transaction so two clients can't both win
     * the same name — whichever transaction commits second observes the first's write on its
     * retry read and we return [UsernameClaimResult.Taken].
     *
     * The transaction does four things atomically:
     *   1. Read the new pointer. If it exists and belongs to someone else → Taken.
     *   2. Read the user's current /users/{uid} doc to find any existing username pointer that
     *      needs releasing.
     *   3. Write the new /usernames/{normalised} pointer → { uid }.
     *   4. Delete the old /usernames/{old} pointer (if any).
     *   5. Merge-write username = normalised onto /users/{uid}.
     *
     * The "valid" check (format) runs client-side before the transaction — the server rule allows
     * any string Firestore accepts as a doc id, and we don't want to codify format details into the
     * rules file where changing them is a security-review concern.
     */
    suspend fun claimUsername(uid: String, desired: String): UsernameClaimResult {
        val normalised = desired.trim().lowercase()
        if (!isValidUsername(normalised)) return UsernameClaimResult.Invalid

        val newPointerRef = firestore.collection(USERNAMES).document(normalised)
        val userRef = firestore.collection(USERS).document(uid)

        return runCatching {
            firestore.runTransaction {
                val newPointer = get(newPointerRef)
                if (newPointer.exists) {
                    val existingUid = runCatching { newPointer.data<UsernameDoc>().uid }.getOrNull()
                    if (existingUid == uid) return@runTransaction UsernameClaimResult.Unchanged
                    return@runTransaction UsernameClaimResult.Taken
                }

                // Read current user doc so we know which old username pointer (if any) to release.
                val userSnap = get(userRef)
                val oldUsername = if (userSnap.exists) {
                    runCatching { userSnap.data<UserDoc>().username }.getOrNull()
                } else null

                set(newPointerRef, UsernameDoc(uid = uid))
                if (!oldUsername.isNullOrBlank() && oldUsername != normalised) {
                    delete(firestore.collection(USERNAMES).document(oldUsername))
                }
                set(userRef, UsernameFieldDoc(username = normalised), merge = true)

                UsernameClaimResult.Ok
            }
        }.getOrElse { UsernameClaimResult.NetworkError }
    }

    /**
     * Set or change the profile icon. Simple merge-write — icon changes aren't uniqueness-
     * constrained so no transaction needed.
     */
    suspend fun setIcon(uid: String, iconId: String) {
        firestore.collection(USERS).document(uid)
            .set(IconFieldDoc(iconId = iconId), merge = true)
    }

    /**
     * Resolve an email to a registered SplitFoolish user. Returns null if no account has that
     * email.
     */
    suspend fun lookupByEmail(email: String): RegisteredUser? {
        val normalisedEmail = email.trim().lowercase()
        if (normalisedEmail.isBlank()) return null

        val pointer = firestore.collection(USERS_BY_EMAIL).document(normalisedEmail).get()
        if (!pointer.exists) return null
        val uid = runCatching { pointer.data<UsersByEmailDoc>() }.getOrNull()?.uid ?: return null

        return fetchUser(uid)
    }

    /**
     * Resolve a username to a registered SplitFoolish user. Same shape as [lookupByEmail] but
     * reads from the /usernames index. Powers "Add friend by username".
     */
    suspend fun lookupByUsername(username: String): RegisteredUser? {
        val normalised = username.trim().lowercase()
        if (normalised.isBlank()) return null

        val pointer = firestore.collection(USERNAMES).document(normalised).get()
        if (!pointer.exists) return null
        val uid = runCatching { pointer.data<UsernameDoc>() }.getOrNull()?.uid ?: return null

        return fetchUser(uid)
    }

    private suspend fun fetchUser(uid: String): RegisteredUser? {
        val userDoc = firestore.collection(USERS).document(uid).get()
        if (!userDoc.exists) return null
        val user = runCatching { userDoc.data<UserDoc>() }.getOrNull() ?: return null
        return RegisteredUser(
            uid = user.uid,
            email = user.email,
            displayName = user.displayName,
            username = user.username,
            iconId = user.iconId,
        )
    }

    /**
     * Flip the per-user "I have pushed my pre-Firestore local groups to the cloud" flag. Kept
     * in place from PR #10 even though the migration itself was deferred.
     */
    suspend fun markMigrated(uid: String) {
        firestore.collection(USERS).document(uid)
            .set(MigrationFlagDoc(migrated = true), merge = true)
    }

    suspend fun isMigrated(uid: String): Boolean {
        val doc = firestore.collection(USERS).document(uid).get()
        if (!doc.exists) return false
        return runCatching { doc.data<UserDoc>() }.getOrNull()?.migrated == true
    }

    companion object {
        const val MIN_USERNAME_LEN = 3
        const val MAX_USERNAME_LEN = 20

        private val USERNAME_RE = Regex("^[a-z][a-z0-9_]{${MIN_USERNAME_LEN - 1},${MAX_USERNAME_LEN - 1}}$")

        /**
         * True if [candidate] (already lowercased + trimmed) passes the username format rules.
         * 3-20 chars, first char is a letter, subsequent chars are letters / digits / underscore.
         * Exposed so the Settings screen can mirror the check for inline validation.
         */
        fun isValidUsername(candidate: String): Boolean = USERNAME_RE.matches(candidate)

        private const val USERS = "users"
        private const val USERS_BY_EMAIL = "users_by_email"
        private const val USERNAMES = "usernames"
    }
}

/**
 * Full read-model DTO for `/users/{uid}`. Carries every field that may be present on the doc.
 * We never pass this to a .set() call directly (it would clobber unknown fields on merge-false
 * paths, and include unwanted nulls on merge-true paths) — writes use the narrower field DTOs
 * below so each call only touches exactly the fields it means to.
 */
@Serializable
internal data class UserDoc(
    val uid: String,
    val email: String,
    val displayName: String? = null,
    val username: String? = null,
    val iconId: String? = null,
    val migrated: Boolean = false,
)

/** Written by ensureRegistered — doesn't mention username/iconId so merge-writes don't clobber them. */
@Serializable
internal data class SignupUserDoc(
    val uid: String,
    val email: String,
    val displayName: String? = null,
)

@Serializable
internal data class UsersByEmailDoc(
    val uid: String,
)

/** `/usernames/{username}` pointer doc. */
@Serializable
internal data class UsernameDoc(
    val uid: String,
)

/** Merge-write DTO for setting just the username field on /users/{uid}. */
@Serializable
internal data class UsernameFieldDoc(
    val username: String,
)

/** Merge-write DTO for setting just the iconId field on /users/{uid}. */
@Serializable
internal data class IconFieldDoc(
    val iconId: String,
)

/** Tiny DTO used only for the merge-write that flips the migration flag. */
@Serializable
internal data class MigrationFlagDoc(
    val migrated: Boolean,
)
