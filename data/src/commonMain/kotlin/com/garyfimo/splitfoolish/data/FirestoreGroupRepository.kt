package com.garyfimo.splitfoolish.data

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

/**
 * Firestore-backed store for groups + their nested expenses.
 *
 * Schema:
 * - `/groups/{groupId}` — one document per group with name, createdByUid, memberUids (flat
 *   string array for array-contains queries), and members (richer per-user objects).
 * - `/groups/{groupId}/expenses/{expenseId}` — a subcollection so Firestore security rules can
 *   target individual expenses (per-expense delete permission wouldn't be expressible on an
 *   embedded array).
 *
 * Reads are real-time Flows backed by Firestore's offline cache: on first subscription Firestore
 * emits the cached snapshot immediately and then pushes server updates as they arrive. Writes
 * are suspend functions that return once Firestore has written to the local cache — the actual
 * server round-trip happens asynchronously behind that, and queues when offline.
 */
class FirestoreGroupRepository {

    private val firestore get() = Firebase.firestore

    /**
     * Stream of groups the signed-in user is a member of, each already enriched with its live
     * expense subcollection. Combines the group-doc flow with a per-group expenses flow — when
     * the user is added to or removed from a group, the outer flow re-emits with the new set,
     * and expenses for each group flow independently.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeGroupsForUser(uid: String): Flow<List<CloudGroup>> =
        firestore.collection(GROUPS)
            // Kotlin DSL: `contains` is an extension on String (field name), not a method on
            // FilterBuilder. Reads as "memberUids array-contains uid" in Firestore SQL terms.
            .where { "memberUids".contains(uid) }
            .snapshots
            .flatMapLatest { querySnapshot ->
                val groupDocs: List<GroupDoc> = querySnapshot.documents.mapNotNull { snap ->
                    runCatching { snap.data<GroupDoc>().copy(id = snap.id) }.getOrNull()
                }
                if (groupDocs.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    // One expenses flow per group, combined into one list-of-CloudGroup flow.
                    // combine() re-emits whenever any child flow emits — so an expense added to
                    // group A doesn't force group B's state to refresh.
                    val expensesFlows: List<Flow<Pair<GroupDoc, List<CloudExpense>>>> =
                        groupDocs.map { group ->
                            observeExpenses(group.id).map { exps -> group to exps }
                        }
                    combine(expensesFlows) { pairs ->
                        pairs.map { (g, expenses) -> g.toCloudGroup(expenses) }
                    }
                }
            }

    private fun observeExpenses(groupId: String): Flow<List<CloudExpense>> =
        firestore.collection(GROUPS).document(groupId).collection(EXPENSES).snapshots
            .map { querySnapshot ->
                querySnapshot.documents.mapNotNull { snap ->
                    runCatching {
                        snap.data<ExpenseDoc>().copy(id = snap.id).toCloudExpense()
                    }.getOrNull()
                }
            }

    /** Upsert a whole group doc (name, members[], memberUids[]). Doesn't touch the expense sub. */
    suspend fun upsertGroup(group: CloudGroup) {
        val doc = GroupDoc(
            id = group.id,
            name = group.name,
            createdByUid = group.createdByUid,
            memberUids = group.members.map { it.uid },
            members = group.members.map { CloudMemberDoc(uid = it.uid, name = it.name) },
        )
        firestore.collection(GROUPS).document(group.id).set(doc)
    }

    suspend fun deleteGroup(groupId: String) {
        // We intentionally don't recursively delete the expenses subcollection here — Firestore
        // doesn't support that server-side anyway, and in practice a group delete happens from
        // a device that owns the group so the subcollection docs follow in user intent, not
        // in client code. The Firestore security rules reject reads/writes for non-members
        // so orphaned expenses are inaccessible until the group is reconstituted.
        firestore.collection(GROUPS).document(groupId).delete()
    }

    suspend fun upsertExpense(groupId: String, expense: CloudExpense) {
        val doc = ExpenseDoc(
            id = expense.id,
            title = expense.title,
            amountInput = expense.amountInput,
            payerUid = expense.payerUid,
            splitMode = expense.splitMode,
            participants = expense.participants.toDocs(),
            createdByUid = expense.createdByUid,
        )
        firestore.collection(GROUPS).document(groupId)
            .collection(EXPENSES).document(expense.id).set(doc)
    }

    suspend fun deleteExpense(groupId: String, expenseId: String) {
        firestore.collection(GROUPS).document(groupId)
            .collection(EXPENSES).document(expenseId).delete()
    }

    private companion object {
        const val GROUPS = "groups"
        const val EXPENSES = "expenses"
    }
}

/**
 * Platform-neutral view of a group assembled from the Firestore doc + its expense subcollection.
 * Exposed back to callers (features, shared) so they don't take a dependency on
 * [dev.gitlive.firebase.firestore].
 */
data class CloudGroup(
    val id: String,
    val name: String,
    val createdByUid: String,
    val members: List<CloudMember>,
    val expenses: List<CloudExpense>,
)

data class CloudMember(
    val uid: String,
    val name: String,
)

data class CloudExpense(
    val id: String,
    val title: String,
    val amountInput: String,
    val payerUid: String,
    val splitMode: String,
    val participants: List<CloudExpenseParticipant>,
    val createdByUid: String,
)

data class CloudExpenseParticipant(
    val memberUid: String,
    val included: Boolean,
    val percentInput: String,
)

// --- @Serializable DTOs for Firestore encode/decode. Separate from the plain Cloud* types above
//     so Firestore-only concerns (default values, serial names) don't leak outward. ---

@Serializable
internal data class GroupDoc(
    val id: String = "",
    val name: String = "",
    val createdByUid: String = "",
    val memberUids: List<String> = emptyList(),
    val members: List<CloudMemberDoc> = emptyList(),
) {
    fun toCloudGroup(expenses: List<CloudExpense>): CloudGroup = CloudGroup(
        id = id,
        name = name,
        createdByUid = createdByUid,
        members = members.map { CloudMember(uid = it.uid, name = it.name) },
        expenses = expenses,
    )
}

@Serializable
internal data class CloudMemberDoc(
    val uid: String = "",
    val name: String = "",
)

@Serializable
internal data class ExpenseDoc(
    val id: String = "",
    val title: String = "",
    val amountInput: String = "",
    val payerUid: String = "",
    val splitMode: String = "Equal",
    val participants: List<ExpenseParticipantDoc> = emptyList(),
    val createdByUid: String = "",
)

@Serializable
internal data class ExpenseParticipantDoc(
    val memberUid: String = "",
    val included: Boolean = true,
    val percentInput: String = "",
)

// Members and participants helpers. The GroupDoc exposes CloudMemberDoc, but the ExpenseDoc
// exposes ExpenseParticipantDoc directly to avoid a conversion on every read — the Cloud*
// equivalents are structurally identical.

private fun List<ExpenseParticipantDoc>.toCloudParticipants(): List<CloudExpenseParticipant> =
    map {
        CloudExpenseParticipant(
            memberUid = it.memberUid,
            included = it.included,
            percentInput = it.percentInput,
        )
    }

private fun List<CloudExpenseParticipant>.toDocs(): List<ExpenseParticipantDoc> =
    map {
        ExpenseParticipantDoc(
            memberUid = it.memberUid,
            included = it.included,
            percentInput = it.percentInput,
        )
    }

private fun ExpenseDoc.toCloudExpense(): CloudExpense = CloudExpense(
    id = id,
    title = title,
    amountInput = amountInput,
    payerUid = payerUid,
    splitMode = splitMode,
    participants = participants.toCloudParticipants(),
    createdByUid = createdByUid,
)
