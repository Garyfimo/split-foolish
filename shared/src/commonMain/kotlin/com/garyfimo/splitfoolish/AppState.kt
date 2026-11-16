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
import com.garyfimo.splitfoolish.data.UserRegistry
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

    /** Latest snapshot this instance has either received from or sent to [repo]. Used to break
     *  the write-after-read loop: a captured local snapshot that equals this is a no-op write. */
    private var lastSyncedSnapshot: AppSnapshot? = null

    init {
        scope.launch {
            // Collect live Firestore updates. First emission hydrates the UI; subsequent
            // emissions are remote changes (another device added an expense, member joined, etc.).
            repo.observeSnapshot().collect { snapshot ->
                applyRemoteSnapshot(snapshot)
            }
        }
        scope.launch {
            // Write-through: any local Compose state change → push to repo after settling.
            // drop(1) skips the empty initial state before any remote emission has arrived.
            // distinctUntilChanged + lastSyncedSnapshot check together break the Firestore
            // read→apply→write→read loop.
            snapshotFlow { captureSnapshot() }
                .drop(1)
                .debounce(500)
                .distinctUntilChanged()
                .collect { local ->
                    if (local != lastSyncedSnapshot) {
                        repo.saveSnapshot(local)
                        lastSyncedSnapshot = local
                    }
                }
        }
    }

    private fun applyRemoteSnapshot(snapshot: AppSnapshot) {
        // Replace friends list (local source of truth is this device's SQLite — remote friends
        // should always equal local friends; this is just a defensive refresh).
        friends.clear()
        snapshot.friends.forEach { friends.add(EditableFriend(it.id, it.name)) }

        // Replace groups list wholesale. Any in-flight local edits to a currently-displayed
        // group will be lost on remote update — MVP cost; a per-field reconciliation layer
        // would preserve them but adds a lot of complexity for occasional conflicts.
        groups.clear()
        snapshot.groups.forEach { groups.add(it.toEditable()) }

        lastSyncedSnapshot = snapshot
    }

    fun addFriend(name: String): EditableFriend {
        val friend = EditableFriend(nextId(), name)
        friends.add(friend)
        return friend
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
     */
    suspend fun addMemberByEmail(group: EditableGroup, email: String): String? {
        val lookup: RegisteredUser = userRegistry.lookupByEmail(email)
            ?: return "No SplitFoolish account with that email."
        if (group.members.any { it.id == lookup.uid }) {
            return "They're already in this group."
        }
        val displayName = lookup.displayName?.takeIf { it.isNotBlank() } ?: lookup.email
        group.members.add(EditableMember(id = lookup.uid, name = displayName))
        return null
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
        friends = friends.map { PersistedFriend(it.id, it.name) },
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

