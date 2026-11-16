package com.garyfimo.splitfoolish.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Replacement for the SQLDelight-only [SplitFoolishRepository] that now blends two stores:
 * - **Friends** stay in the local SQLDelight DB (per-user quick-pick cache, no need to share).
 * - **Groups + expenses** live in Firestore so members of the same group see shared data on
 *   every device they sign in to, and so Firestore security rules can enforce per-expense
 *   permissions server-side.
 *
 * Exposes the same [AppSnapshot] shape the rest of the app already knows, so AppState's hydrate
 * + snapshotFlow write-through stays almost identical to the pre-sync version.
 */
class SyncedRepository(
    private val friendsStore: SplitFoolishRepository,
    private val groups: FirestoreGroupRepository,
    private val currentUserUid: String,
) {
    /**
     * Combined live view: friends from local SQLite (one-shot — friends don't update live) plus
     * groups from Firestore (reactive — re-emits whenever any group the user belongs to changes
     * on any device). Emits whenever the Firestore side does.
     */
    fun observeSnapshot(): Flow<AppSnapshot> =
        groups.observeGroupsForUser(currentUserUid).map { cloudGroups ->
            val friendsSnap = friendsStore.loadSnapshot().friends
            AppSnapshot(
                friends = friendsSnap,
                groups = cloudGroups.map { it.toPersisted() },
            )
        }

    /**
     * Write the full snapshot back: friends go to SQLite in one transaction, groups + expenses
     * are fanned out to Firestore doc-by-doc. Deletions are detected by diffing the current
     * remote state against [snapshot] inside [upsertAllGroups].
     */
    suspend fun saveSnapshot(snapshot: AppSnapshot) {
        // Friends: reuse the existing per-user SQLite save path. Only friends participate in
        // the snapshot; the groups field is written to Firestore separately below.
        friendsStore.saveSnapshot(AppSnapshot(friends = snapshot.friends, groups = emptyList()))
        upsertAllGroups(snapshot.groups)
    }

    /** Write each group + its expenses to Firestore. Used by [saveSnapshot]. */
    private suspend fun upsertAllGroups(groupsToSave: List<PersistedGroup>) {
        for (group in groupsToSave) {
            groups.upsertGroup(group.toCloud(currentUserUid))
            for (expense in group.expenses) {
                groups.upsertExpense(group.id, expense.toCloud(currentUserUid))
            }
        }
    }

    /** Delete a single expense — used by the per-creator-only delete path in AppState. */
    suspend fun deleteExpense(groupId: String, expenseId: String) {
        groups.deleteExpense(groupId, expenseId)
    }

    /** Delete a whole group (creator-only in UI; Firestore rule enforces server-side). */
    suspend fun deleteGroup(groupId: String) {
        groups.deleteGroup(groupId)
    }
}

private fun CloudGroup.toPersisted(): PersistedGroup = PersistedGroup(
    id = id,
    name = name,
    createdByUid = createdByUid,
    members = members.map { PersistedMember(id = it.uid, name = it.name) },
    expenses = expenses.map { it.toPersisted() },
)

private fun CloudExpense.toPersisted(): PersistedExpense = PersistedExpense(
    id = id,
    title = title,
    amountInput = amountInput,
    payerId = payerUid,
    splitMode = splitMode,
    participants = participants.map {
        PersistedExpenseParticipant(
            memberId = it.memberUid,
            included = it.included,
            percentInput = it.percentInput,
        )
    },
    createdByUid = createdByUid,
)

private fun PersistedGroup.toCloud(createdByUidDefault: String): CloudGroup = CloudGroup(
    id = id,
    name = name,
    // Prefer the group's own createdByUid — Firestore-hydrated groups carry the real owner.
    // Fall back to the current user only for the pre-Firestore local path where the field is
    // genuinely unknown (empty). This is what prevents a non-creator's write-through from
    // flipping ownership — and lets the server-side "createdByUid pinned on update" rule pass.
    createdByUid = createdByUid.ifBlank { createdByUidDefault },
    members = members.map { CloudMember(uid = it.id, name = it.name) },
    expenses = expenses.map { it.toCloud(createdByUidDefault) },
)

private fun PersistedExpense.toCloud(createdByUidDefault: String): CloudExpense = CloudExpense(
    id = id,
    title = title,
    amountInput = amountInput,
    payerUid = payerId,
    splitMode = splitMode,
    participants = participants.map {
        CloudExpenseParticipant(
            memberUid = it.memberId,
            included = it.included,
            percentInput = it.percentInput,
        )
    },
    createdByUid = createdByUid.ifBlank { createdByUidDefault },
)
