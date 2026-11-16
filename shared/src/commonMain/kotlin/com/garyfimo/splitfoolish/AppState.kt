package com.garyfimo.splitfoolish

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.garyfimo.splitfoolish.core.billsplit.SplitMode
import com.garyfimo.splitfoolish.core.nav.Screen
import com.garyfimo.splitfoolish.data.AppSnapshot
import com.garyfimo.splitfoolish.data.PersistedExpense
import com.garyfimo.splitfoolish.data.PersistedExpenseParticipant
import com.garyfimo.splitfoolish.data.PersistedFriend
import com.garyfimo.splitfoolish.data.PersistedGroup
import com.garyfimo.splitfoolish.data.PersistedMember
import com.garyfimo.splitfoolish.data.RegisteredUser
import com.garyfimo.splitfoolish.data.SyncedRepository
import com.garyfimo.splitfoolish.data.UserProfile
import com.garyfimo.splitfoolish.data.UserRegistry
import com.garyfimo.splitfoolish.data.UsernameClaimResult
import com.garyfimo.splitfoolish.feature.friends.EditableFriend
import com.garyfimo.splitfoolish.feature.groups.EditableExpense
import com.garyfimo.splitfoolish.feature.groups.EditableExpenseParticipant
import com.garyfimo.splitfoolish.feature.groups.EditableGroup
import com.garyfimo.splitfoolish.feature.groups.EditableMember
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Root coordinator for the whole app. Owns the nav state plus the two cross-feature collections
 * (friends and groups).
 *
 * Persistence model: [repo] blends local SQLite (friends) with Firestore (groups + expenses).
 * [observeSnapshot] emits whenever Firestore pushes an update — the init block populates the
 * editable collections on every emission, so remote changes from another device propagate here.
 * A second coroutine watches local Compose state changes via [snapshotFlow] and writes them
 * back; it debounces to avoid writing on every keystroke, and skips writes when the serialised
 * state exactly equals what we last received or sent (break the sync loop).
 */
@OptIn(ExperimentalUuidApi::class)
class AppState(
    private val repo: SyncedRepository,
    private val userRegistry: UserRegistry,
    private val currentUserUid: String,
    private val currentUserName: String,
    private val scope: CoroutineScope,
) {
    fun nextId(): String = Uuid.random().toString()

    var screen: Screen by mutableStateOf(Screen.GroupsList)
    val groups: SnapshotStateList<EditableGroup> = mutableStateListOf()
    val friends: SnapshotStateList<EditableFriend> = mutableStateListOf()

    /** Live profile for the signed-in user, hydrated from `UserRegistry.observeProfile`. Null
     *  until the first Firestore emission arrives. Settings screen reads this to pre-fill fields. */
    var profile: UserProfile? by mutableStateOf(null)
        private set

    /** Draft fields the Settings screen is editing but hasn't yet saved. Separate from [profile]
     *  so a remote update doesn't clobber an in-progress edit, and so the Save button has a clear
     *  "has changes" signal. */
    var draftUsername: String by mutableStateOf("")
    var draftIconId: String? by mutableStateOf(null)
        private set

    /** Transient flags for the Settings screen's spinner + inline message. */
    var settingsSaving: Boolean by mutableStateOf(false)
        private set
    var settingsMessage: String? by mutableStateOf(null)
        private set

    /** Latest snapshot this instance has either received from or sent to [repo]. Used to break
     *  the write-after-read loop: a captured local snapshot that equals this is a no-op write. */
    private var lastSyncedSnapshot: AppSnapshot? = null

    init {
        scope.launch {
            // Mirror the live /users/{uid} document into [profile] so Settings is always showing
            // what's actually in Firestore. Same runCatching rationale as the two collectors
            // below — a permissions/network error must not reach propagateExceptionFinalResort.
            runCatching {
                userRegistry.observeProfile(currentUserUid).collect { fresh ->
                    profile = fresh
                    // Seed the drafts the first time the profile arrives (and when nothing has
                    // been typed yet). Don't clobber in-progress edits on subsequent emissions —
                    // the user is mid-type and the screen's Save button is what reconciles.
                    if (fresh != null && draftUsername.isBlank() && draftIconId == null) {
                        draftUsername = fresh.username.orEmpty()
                        draftIconId = fresh.iconId
                    }
                }
            }
        }
        scope.launch {
            // Collect live Firestore updates. First emission hydrates the UI; subsequent
            // emissions are remote changes (another device added an expense, member joined, etc.).
            // Wrapped in runCatching because Firestore emits a terminal error into the flow if
            // permissions are rejected or the network stays down — on K/N that otherwise reaches
            // propagateExceptionFinalResort and tears down the composition. Swallowing here keeps
            // the app alive on cached data until the next successful connection.
            runCatching {
                repo.observeSnapshot().collect { snapshot ->
                    applyRemoteSnapshot(snapshot)
                }
            }
        }
        scope.launch {
            // Write-through: any local Compose state change → push to repo after settling.
            // drop(1) skips the empty initial state before any remote emission has arrived.
            // distinctUntilChanged + lastSyncedSnapshot check together break the Firestore
            // read→apply→write→read loop. Per-emission runCatching so a single failed write
            // doesn't terminate the collector — the next local edit will retry on its own.
            snapshotFlow { captureSnapshot() }
                .drop(1)
                .debounce(500)
                .distinctUntilChanged()
                .collect { local ->
                    if (local != lastSyncedSnapshot) {
                        val saved = runCatching { repo.saveSnapshot(local) }
                        if (saved.isSuccess) lastSyncedSnapshot = local
                    }
                }
        }
    }

    private fun applyRemoteSnapshot(snapshot: AppSnapshot) {
        // Replace friends list (local source of truth is this device's SQLite — remote friends
        // should always equal local friends; this is just a defensive refresh).
        friends.clear()
        snapshot.friends.forEach {
            friends.add(
                EditableFriend(id = it.id, uid = it.uid, name = it.name, iconId = it.iconId),
            )
        }

        // Replace groups list wholesale. Any in-flight local edits to a currently-displayed
        // group will be lost on remote update — MVP cost; a per-field reconciliation layer
        // would preserve them but adds a lot of complexity for occasional conflicts.
        groups.clear()
        snapshot.groups.forEach { groups.add(it.toEditable()) }

        lastSyncedSnapshot = snapshot
    }

    /**
     * Resolve a username to a SplitFoolish user and add them to the local friends list. Returns
     * null on success, a user-visible error string otherwise. Same return-shape as
     * [addMemberByEmail] — keeps the dialogs symmetric and avoids leaking a sealed Result type
     * into :feature:friends. Firestore exceptions are caught here so the UI's scope.launch stays
     * safe on K/N (see PR #10 post-review notes).
     */
    suspend fun addFriendByUsername(username: String): String? {
        val lookup: RegisteredUser = try {
            userRegistry.lookupByUsername(username)
        } catch (t: Throwable) {
            return "Couldn't reach the user directory: ${t.message ?: t::class.simpleName ?: "unknown error"}"
        } ?: return "No SplitFoolish account with that username."

        if (lookup.uid == currentUserUid) return "That's you."
        if (friends.any { it.uid == lookup.uid }) return "Already in your friends."

        val displayName = lookup.displayName?.takeIf { it.isNotBlank() }
            ?: lookup.username
            ?: lookup.email
        friends.add(
            EditableFriend(
                id = nextId(),
                uid = lookup.uid,
                name = displayName,
                iconId = lookup.iconId,
            ),
        )
        return null
    }

    fun removeFriend(id: String) {
        friends.removeAll { it.id == id }
    }

    /**
     * Create an empty group with the current user as the sole real-user member. The friend
     * picker is no longer part of group creation — invite other members from the group detail
     * screen by email once the group exists.
     */
    fun createEmptyGroup(name: String): EditableGroup {
        val group = EditableGroup(nextId(), name, createdByUid = currentUserUid)
        group.members.add(EditableMember(id = currentUserUid, name = currentUserName))
        groups.add(group)
        return group
    }

    fun deleteGroup(id: String) {
        groups.removeAll { it.id == id }
        // Also issue an explicit Firestore delete — write-through above only writes upserts,
        // never figures out what was removed. Fire-and-forget; the local removal above makes
        // the UI feel instant. The try/catch matters because rememberCoroutineScope() installs
        // a non-supervisor Job: an uncaught FirebaseFirestoreException (rule rejection, network
        // drop) would otherwise propagate up and tear down the composition.
        scope.launch {
            runCatching { repo.deleteGroup(id) }
        }
    }

    fun findGroup(id: String): EditableGroup? = groups.firstOrNull { it.id == id }

    /**
     * Delete an expense. UI should already have hidden the delete control for non-creators;
     * this is a defence-in-depth check so a mis-wired call site can't bypass it. Firestore
     * security rules enforce the same constraint server-side so even a compromised client
     * can't delete someone else's expense.
     */
    fun deleteExpense(group: EditableGroup, expense: EditableExpense) {
        if (expense.createdByUid != currentUserUid) return
        group.expenses.removeAll { it.id == expense.id }
        // Same reasoning as deleteGroup — swallow Firestore errors rather than crashing the UI.
        // The local removal above already applied; a rule rejection here just means the server
        // state and local state will diverge until the next observeSnapshot emission reconciles.
        scope.launch {
            runCatching { repo.deleteExpense(group.id, expense.id) }
        }
    }

    /**
     * Look up a user by email in the Firestore users_by_email registry and add them to the
     * given group. Returns null on success; otherwise a short error message the UI renders
     * inline. Keeping this as a String return avoids leaking a result type into :feature:groups,
     * which doesn't depend on :shared or :data.
     *
     * The Firestore lookup can throw (PERMISSION_DENIED if rules haven't been deployed, the
     * token.email guard rejects us, Firestore isn't enabled on the project, or the network is
     * unreachable). We turn those into a user-visible string rather than letting them propagate
     * — the caller invokes this inside a plain scope.launch on iOS, where an uncaught throw
     * reaches K/N's propagateExceptionFinalResort and crashes the composition.
     */
    suspend fun addMemberByEmail(group: EditableGroup, email: String): String? {
        val lookup: RegisteredUser = try {
            userRegistry.lookupByEmail(email)
        } catch (t: Throwable) {
            return "Couldn't reach the user directory: ${t.message ?: t::class.simpleName ?: "unknown error"}"
        } ?: return "No SplitFoolish account with that email."

        if (group.members.any { it.id == lookup.uid }) {
            return "They're already in this group."
        }
        val displayName = lookup.displayName?.takeIf { it.isNotBlank() } ?: lookup.email
        group.members.add(EditableMember(id = lookup.uid, name = displayName))
        return null
    }

    // ----- Settings screen ------------------------------------------------------------------

    fun updateDraftUsername(value: String) {
        draftUsername = value
    }

    fun updateDraftIcon(iconId: String) {
        draftIconId = iconId
    }

    fun clearSettingsMessage() {
        settingsMessage = null
    }

    /**
     * Persist Settings-screen edits. Two possibly-independent writes:
     *   1. If the username draft differs from what's saved, run the transactional claim. The
     *      result enum tells us which inline message to show.
     *   2. If the icon draft differs, merge-write the iconId.
     *
     * Both operations are wrapped in try/catch — a failure in one shouldn't leave the UI
     * believing the other succeeded, so each sets its own piece of [settingsMessage].
     */
    suspend fun saveSettings() {
        val snapshot = profile
        settingsSaving = true
        settingsMessage = null
        try {
            val normalisedUsername = draftUsername.trim().lowercase()
            val currentUsername = snapshot?.username.orEmpty()
            val usernameChanged = normalisedUsername.isNotBlank() &&
                normalisedUsername != currentUsername

            val usernameResult: UsernameClaimResult? = if (usernameChanged) {
                try {
                    userRegistry.claimUsername(currentUserUid, normalisedUsername)
                } catch (t: Throwable) {
                    settingsMessage = "Couldn't claim username: ${t.message ?: t::class.simpleName}"
                    return
                }
            } else null

            // If the username claim failed, don't go on to write the icon — the user needs to
            // fix the username first, and a partial save would be confusing.
            if (usernameResult != null && usernameResult != UsernameClaimResult.Ok &&
                usernameResult != UsernameClaimResult.Unchanged) {
                // The guard above means only Taken / Invalid / NetworkError get here; the when
                // matches all three with no else branch, so the compiler also catches any future
                // enum value we might add without a corresponding message.
                settingsMessage = when (usernameResult) {
                    UsernameClaimResult.Taken -> "That username is already taken."
                    UsernameClaimResult.Invalid ->
                        "Username must be 3–20 chars, start with a letter, " +
                            "then letters/digits/underscore."
                    UsernameClaimResult.NetworkError ->
                        "Couldn't reach the server. Check your connection and try again."
                    UsernameClaimResult.Ok, UsernameClaimResult.Unchanged -> null
                }
                return
            }

            val iconChanged = draftIconId != null && draftIconId != snapshot?.iconId
            if (iconChanged) {
                try {
                    userRegistry.setIcon(currentUserUid, draftIconId!!)
                } catch (t: Throwable) {
                    settingsMessage = "Couldn't save icon: ${t.message ?: t::class.simpleName}"
                    return
                }
            }

            settingsMessage = when {
                usernameChanged && iconChanged -> "Profile saved."
                usernameChanged -> "Username saved."
                iconChanged -> "Icon saved."
                else -> "Nothing to save."
            }
        } finally {
            settingsSaving = false
        }
    }

    private fun PersistedGroup.toEditable(): EditableGroup {
        // createdByUid now flows end-to-end from Firestore → CloudGroup → PersistedGroup, so we
        // use the real creator uid here. Fall back to the current user only for the legacy
        // local-only path where the Persisted DTO was written with an empty creator — that's
        // the pre-Firestore case and the fallback matches what we push to the cloud below.
        val group = EditableGroup(
            id = id,
            name = name,
            createdByUid = createdByUid.ifBlank { currentUserUid },
        )
        members.forEach { group.members.add(EditableMember(it.id, it.name)) }
        expenses.forEach { expense ->
            val splitMode = runCatching { SplitMode.valueOf(expense.splitMode) }
                .getOrDefault(SplitMode.Equal)
            group.expenses.add(
                EditableExpense(
                    id = expense.id,
                    title = expense.title,
                    amountInput = expense.amountInput,
                    payerId = expense.payerId,
                    splitMode = splitMode,
                    participants = expense.participants.map {
                        EditableExpenseParticipant(
                            memberId = it.memberId,
                            included = it.included,
                            percentInput = it.percentInput,
                        )
                    },
                    // Preserve the real creator — this is the field the delete-permission UI
                    // check reads, so getting it wrong here is what the review flagged.
                    createdByUid = expense.createdByUid.ifBlank { currentUserUid },
                )
            )
        }
        return group
    }

    private fun captureSnapshot(): AppSnapshot = AppSnapshot(
        friends = friends.map {
            PersistedFriend(id = it.id, name = it.name, uid = it.uid, iconId = it.iconId)
        },
        groups = groups.map { it.toPersisted() },
    )

    private fun EditableGroup.toPersisted(): PersistedGroup = PersistedGroup(
        id = id,
        name = name,
        createdByUid = createdByUid,
        members = members.map { PersistedMember(it.id, it.name) },
        expenses = expenses.map { exp ->
            PersistedExpense(
                id = exp.id,
                title = exp.title,
                amountInput = exp.amountInput,
                payerId = exp.payerId,
                splitMode = exp.splitMode.name,
                participants = exp.participants.map {
                    PersistedExpenseParticipant(
                        memberId = it.memberId,
                        included = it.included,
                        percentInput = it.percentInput,
                    )
                },
                createdByUid = exp.createdByUid,
            )
        },
    )
}

