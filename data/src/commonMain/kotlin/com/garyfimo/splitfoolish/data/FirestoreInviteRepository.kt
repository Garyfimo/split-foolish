package com.garyfimo.splitfoolish.data

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.firestore
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable

/**
 * Platform-neutral view of an invite doc, surfaced to the UI and shared-code callers so
 * :feature:* modules don't need to pull in dev.gitlive.firebase just to render invite state.
 *
 * [expiresAtSeconds] is seconds since Unix epoch — a plain Long instead of a Firestore Timestamp
 * so callers outside :data don't have to transitively link the firestore artifact's Timestamp.
 */
data class GroupInvite(
    val token: String,
    val groupId: String,
    val groupName: String,
    val createdByUid: String,
    val expiresAtSeconds: Long,
) {
    fun isExpired(nowSeconds: Long): Boolean = nowSeconds >= expiresAtSeconds
}

/**
 * Outcome of an accept attempt. Sealed so UI can render each state inline with no error-string
 * matching. See [FirestoreInviteRepository.acceptInvite] for how each one is reached.
 */
sealed interface InviteAcceptResult {
    data class Joined(val groupId: String) : InviteAcceptResult
    data class AlreadyMember(val groupId: String) : InviteAcceptResult
    data object InviteNotFound : InviteAcceptResult
    data object InviteExpired : InviteAcceptResult
    data class Error(val message: String) : InviteAcceptResult
}

/**
 * Base URL for the Firebase Hosting redirect page. The user shares
 * `inviteLinkFor(token)` — tapping that URL in Messages / Mail / Slack opens the browser at
 * the hosted page, whose JS attempts `splitfoolish://invite/<token>` (opening the installed
 * app) and otherwise shows store-link fallbacks. See firebase.json's hosting section and the
 * accompanying public/i/[token].html for the hosted side.
 *
 * Hardcoded to the project's default Firebase Hosting site; a prod rollout on a custom domain
 * only has to change this constant plus the Android intent-filter host + redirect page copy.
 */
private const val INVITE_LINK_BASE = "https://splitfoolish-77870.web.app/i/"

/** Build the shareable https URL for a given invite token. */
fun inviteLinkFor(token: String): String = INVITE_LINK_BASE + token

/** Custom URL scheme the hosted redirect page jumps to. Must match Android's intent-filter
 *  host/scheme and iOS Info.plist CFBundleURLSchemes. */
const val INVITE_DEEP_LINK_SCHEME: String = "splitfoolish"
const val INVITE_DEEP_LINK_HOST: String = "invite"

/**
 * Extract the invite token from a deep-link URL. Accepts both the custom-scheme form
 * (`splitfoolish://invite/<token>`) and the hosted https form (`.../i/<token>` — in case
 * future Android App Links / iOS Universal Links verification routes the https URL into the
 * app directly, skipping the redirect page). Returns null for unrelated URLs.
 */
fun parseInviteToken(url: String): String? {
    val stripped = url.trim()
    if (stripped.isEmpty()) return null

    val customPrefix = "$INVITE_DEEP_LINK_SCHEME://$INVITE_DEEP_LINK_HOST/"
    if (stripped.startsWith(customPrefix)) {
        val token = stripped.removePrefix(customPrefix).substringBefore('?').substringBefore('#')
        return token.takeIf { it.isNotBlank() }
    }
    // Hosted form: anything that contains "/i/<token>" and isn't followed by more path segments.
    // Exposed in parseInviteToken so a deep link verified through App Links can also land here
    // without extra platform plumbing.
    val hostedMarker = "/i/"
    val idx = stripped.indexOf(hostedMarker)
    if (idx >= 0) {
        val token = stripped.substring(idx + hostedMarker.length)
            .substringBefore('?').substringBefore('#').substringBefore('/')
        return token.takeIf { it.isNotBlank() }
    }
    return null
}

/**
 * Firestore access for shareable group-invite links.
 *
 * Schema:
 * - `/invites/{token}` — one doc per invite. Token is an unguessable Uuid.random.
 * - `/groups/{groupId}/joinRedemptions/{uid}` — written by the accepter as the server-side
 *   proof that a valid invite was presented; see firestore.rules for the full security
 *   argument. The invite token never lands on the parent group doc.
 *
 * The accept flow performs TWO writes (redemption, then group-member merge) because rules can
 * only check pre-existing state — the redemption doc has to exist before the group update
 * rule can confirm it. See `acceptInvite` for the details.
 */
class FirestoreInviteRepository {
    private val firestore get() = Firebase.firestore

    /**
     * Create an invite for [groupId] valid for 7 days. Caller must be the group's creator;
     * the Firestore rule rejects the create otherwise. Returns the new invite so the caller
     * can render the URL immediately without a round-trip.
     *
     * Token generation uses the same [Uuid.random] source [AppState.nextId] uses — 122 random
     * bits, formatted as a UUID. Guessing one from scratch requires brute-forcing 2^122
     * identifiers.
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun createInvite(
        groupId: String,
        groupName: String,
        ownerUid: String,
    ): GroupInvite {
        val token = Uuid.random().toString()
        val now = Timestamp.now()
        val expiresAt = Timestamp(now.seconds + DEFAULT_EXPIRY_SECONDS, now.nanoseconds)
        val doc = InviteDoc(
            token = token,
            groupId = groupId,
            groupName = groupName,
            createdByUid = ownerUid,
            createdAt = now,
            expiresAt = expiresAt,
        )
        firestore.collection(INVITES).document(token).set(doc)
        return GroupInvite(
            token = token,
            groupId = groupId,
            groupName = groupName,
            createdByUid = ownerUid,
            expiresAtSeconds = expiresAt.seconds,
        )
    }

    /** Resolve a token to an invite. Returns null if no such doc exists or the body can't decode. */
    suspend fun lookupInvite(token: String): GroupInvite? {
        if (token.isBlank()) return null
        val snap = firestore.collection(INVITES).document(token).get()
        if (!snap.exists) return null
        val doc = runCatching { snap.data<InviteDoc>() }.getOrNull() ?: return null
        return GroupInvite(
            token = doc.token,
            groupId = doc.groupId,
            groupName = doc.groupName,
            createdByUid = doc.createdByUid,
            expiresAtSeconds = doc.expiresAt.seconds,
        )
    }

    /**
     * Redeem an invite and add the caller to the group.
     *
     * Two writes in sequence:
     *   1. `/groups/{gid}/joinRedemptions/{self.uid}` with { token, uid } — the create rule
     *      verifies the invite exists, points to this group, and is unexpired. If any of those
     *      fail, PERMISSION_DENIED surfaces here and we return InviteExpired / Error.
     *   2. `/groups/{gid}` update with arrayUnion of the caller onto memberUids + members.
     *      The rule's self-join branch requires joinRedemptions/{self.uid} to exist (step 1),
     *      so this only succeeds for legitimate accepts. arrayUnion is used because non-members
     *      can't read the group doc beforehand (read rule gates on memberUids) — so the client
     *      doesn't know the current array value. arrayUnion resolves server-side without
     *      needing the pre-read, and still produces a request.resource.data.memberUids the
     *      rule can length-check.
     *
     * An already-member retry: step 1 can succeed (nothing in that rule gates on membership),
     * but step 2's self-join branch requires `!(uid in resource.data.memberUids)` and the
     * member-authored branch requires `uid in resource.data.memberUids` AND preservation of
     * the entire pre-image — our arrayUnion write doesn't preserve name/createdByUid in the
     * request.resource.data way the rule expects when we only pass memberUids+members. Net
     * result is PERMISSION_DENIED; we interpret that as AlreadyMember.
     */
    suspend fun acceptInvite(
        token: String,
        acceptingUid: String,
        acceptingUserDisplayName: String,
    ): InviteAcceptResult {
        val invite = lookupInvite(token) ?: return InviteAcceptResult.InviteNotFound
        if (invite.isExpired(Timestamp.now().seconds)) return InviteAcceptResult.InviteExpired

        return try {
            firestore.collection(GROUPS).document(invite.groupId)
                .collection(JOIN_REDEMPTIONS).document(acceptingUid)
                .set(JoinRedemptionDoc(token = token, uid = acceptingUid))

            firestore.collection(GROUPS).document(invite.groupId).update(
                "memberUids" to FieldValue.arrayUnion(acceptingUid),
                "members" to FieldValue.arrayUnion(
                    CloudMemberDoc(uid = acceptingUid, name = acceptingUserDisplayName),
                ),
            )
            InviteAcceptResult.Joined(invite.groupId)
        } catch (t: Throwable) {
            // Permission denied on step 2 overwhelmingly means we're already a member — see the
            // docstring above for why. Report it as such so the UI can give a useful message
            // rather than a scary PERMISSION_DENIED surface. Everything else surfaces as Error.
            if (looksLikePermissionDenied(t)) {
                InviteAcceptResult.AlreadyMember(invite.groupId)
            } else {
                InviteAcceptResult.Error(t.message ?: t::class.simpleName ?: "unknown error")
            }
        }
    }

    /** Revoke an invite (owner only). Rules enforce the owner check server-side. */
    suspend fun revokeInvite(token: String) {
        firestore.collection(INVITES).document(token).delete()
    }

    private companion object {
        const val DEFAULT_EXPIRY_SECONDS: Long = 7L * 24L * 3600L
        const val INVITES = "invites"
        const val GROUPS = "groups"
        const val JOIN_REDEMPTIONS = "joinRedemptions"
    }
}

// Firebase doesn't expose a cross-platform exception type for PERMISSION_DENIED (the Android
// FirebaseFirestoreException and the iOS NSError are platform-specific), so we match on the
// error message. Both platforms surface strings containing "permission" / "denied" / code 7 for
// this condition.
private fun looksLikePermissionDenied(t: Throwable): Boolean {
    val msg = t.message?.lowercase() ?: return false
    return "permission" in msg || "denied" in msg || "permission_denied" in msg
}

@Serializable
internal data class InviteDoc(
    val token: String = "",
    val groupId: String = "",
    val groupName: String = "",
    val createdByUid: String = "",
    val createdAt: Timestamp = Timestamp(0, 0),
    val expiresAt: Timestamp = Timestamp(0, 0),
)

@Serializable
internal data class JoinRedemptionDoc(
    val token: String = "",
    val uid: String = "",
)
