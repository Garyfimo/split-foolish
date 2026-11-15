package com.garyfimo.splitfoolish.feature.auth

/**
 * Minimal signed-in user representation that the app actually needs. We deliberately don't expose
 * the full gitlive [dev.gitlive.firebase.auth.FirebaseUser] outside this module so callers aren't
 * coupled to Firebase's type graph — future swaps (another IdP, a mock for tests) only need to
 * produce one of these.
 *
 * [uid] is the Firebase auth uid: opaque, stable, used as the SQLite file-name suffix so each
 * signed-in account keeps its own local database on this device.
 */
data class AuthUser(
    val uid: String,
    val email: String?,
)
