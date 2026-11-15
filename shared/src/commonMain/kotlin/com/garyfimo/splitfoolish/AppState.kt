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
import com.garyfimo.splitfoolish.data.SplitFoolishRepository
import com.garyfimo.splitfoolish.feature.friends.EditableFriend
import com.garyfimo.splitfoolish.feature.groups.EditableExpense
import com.garyfimo.splitfoolish.feature.groups.EditableExpenseParticipant
import com.garyfimo.splitfoolish.feature.groups.EditableGroup
import com.garyfimo.splitfoolish.feature.groups.EditableMember
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Root coordinator for the whole app. Owns the nav state plus the two cross-feature collections
 * (friends and groups). Lives in :shared because this is the only module that depends on both
 * features and can see all of their types at once; the features themselves don't know about each
 * other (groups depends on friends only for the EditableFriend type).
 *
 * Persistence model: [repo] is the authoritative store. On construction we kick off a hydrate
 * pass that fills the editable collections from disk, then install a [snapshotFlow] watcher that
 * serialises the entire tree and writes it back on every Compose state change. That "save the
 * whole world on every edit" approach is wasteful on paper but at this app's scale (dozens of
 * groups max) it's imperceptibly fast and sidesteps every partial-write bug. When Firebase sync
 * lands we'll switch to granular writes; the UUID-string ids are already in the right shape.
 *
 * All ids are UUID strings generated client-side at creation time so an offline-created record
 * already owns its final, stable id — nothing to translate when cloud sync shows up.
 */
@OptIn(ExperimentalUuidApi::class)
class AppState(
    private val repo: SplitFoolishRepository,
    private val scope: CoroutineScope,
) {
    fun nextId(): String = Uuid.random().toString()

    var screen: Screen by mutableStateOf(Screen.GroupsList)
    val groups: SnapshotStateList<EditableGroup> = mutableStateListOf()
    val friends: SnapshotStateList<EditableFriend> = mutableStateListOf()

    init {
        scope.launch {
            // Hydrate must complete before the snapshotFlow watcher subscribes; otherwise the
            // mid-hydration state changes would trigger a cascade of save-snapshot calls each
            // writing a half-loaded world back to disk.
            hydrate(repo.loadSnapshot())

            // drop(1) discards the initial emission, which by definition equals what we just
            // loaded from disk. Every subsequent emission is a real user edit worth persisting.
            snapshotFlow { captureSnapshot() }
                .drop(1)
                .collect { repo.saveSnapshot(it) }
        }
    }

    fun addFriend(name: String): EditableFriend {
        val friend = EditableFriend(nextId(), name)
        friends.add(friend)
        return friend
    }

    fun removeFriend(id: String) {
        friends.removeAll { it.id == id }
    }

    fun deleteGroup(id: String) {
        groups.removeAll { it.id == id }
    }

    fun findGroup(id: String): EditableGroup? = groups.firstOrNull { it.id == id }

    /**
     * Create a new group with the given name, pre-populated with members copied from the given
     * friend ids. Member names are snapshot-copied — no reference link between members and
     * friends — see docs on [EditableFriend] for why.
     */
    fun createGroupWithFriends(name: String, friendIds: List<String>): EditableGroup {
        val group = EditableGroup(nextId(), name)
        friendIds.forEach { friendId ->
            val friend = friends.firstOrNull { it.id == friendId } ?: return@forEach
            group.members.add(EditableMember(nextId(), friend.name.trim()))
        }
        groups.add(group)
        return group
    }

    private fun hydrate(snapshot: AppSnapshot) {
        friends.clear()
        snapshot.friends.forEach { friends.add(EditableFriend(it.id, it.name)) }
        groups.clear()
        snapshot.groups.forEach { groups.add(it.toEditable()) }
    }

    private fun PersistedGroup.toEditable(): EditableGroup {
        val group = EditableGroup(id = id, name = name)
        members.forEach { group.members.add(EditableMember(it.id, it.name)) }
        expenses.forEach { expense ->
            // Defensive: if the stored string doesn't match an enum constant (e.g. a future
            // rename or a hand-edited db), fall back to Equal so one bad row doesn't crash load.
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
            )
        },
    )

}
