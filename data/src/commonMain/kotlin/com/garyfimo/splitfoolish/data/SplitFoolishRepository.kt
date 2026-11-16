package com.garyfimo.splitfoolish.data

import com.garyfimo.splitfoolish.data.db.SplitFoolishDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Persistence contract for the app's whole-world snapshot. Reads and writes are suspending so
 * callers can safely hit them from main-thread coroutines without blocking the UI.
 *
 * [saveSnapshot] replaces the entire persisted state in a single transaction. That's wasteful in
 * theory, but at the scale this app runs at (dozens of groups, hundreds of expenses) it's a sub-
 * millisecond operation and sidesteps an entire class of partial-write bugs. When Firebase sync
 * lands we'll move to granular writes; the schema is already keyed with stable UUIDs for that.
 */
interface SplitFoolishRepository {
    suspend fun loadSnapshot(): AppSnapshot
    suspend fun saveSnapshot(snapshot: AppSnapshot)
}

fun SplitFoolishRepository(db: SplitFoolishDb): SplitFoolishRepository = SqlSplitFoolishRepository(db)

internal class SqlSplitFoolishRepository(
    private val db: SplitFoolishDb,
) : SplitFoolishRepository {

    override suspend fun loadSnapshot(): AppSnapshot = withContext(Dispatchers.Default) {
        val queries = db.splitFoolishQueries

        val friends = queries.selectAllFriends().executeAsList().map { row ->
            PersistedFriend(id = row.id, name = row.name)
        }

        // Pre-group members and expenses by group_id once so the per-group assembly below is linear
        // in the total record count rather than N × M.
        val membersByGroup = queries.selectAllMembers().executeAsList()
            .groupBy { it.group_id }
            .mapValues { (_, rows) -> rows.map { PersistedMember(id = it.id, name = it.name) } }

        val participantsByExpense = queries.selectAllExpenseParticipants().executeAsList()
            .groupBy { it.expense_id }
            .mapValues { (_, rows) ->
                rows.map {
                    PersistedExpenseParticipant(
                        memberId = it.member_id,
                        included = it.included != 0L,
                        percentInput = it.percent_input,
                    )
                }
            }

        val expensesByGroup = queries.selectAllExpenses().executeAsList()
            .groupBy { it.group_id }
            .mapValues { (_, rows) ->
                rows.map { expenseRow ->
                    PersistedExpense(
                        id = expenseRow.id,
                        title = expenseRow.title,
                        amountInput = expenseRow.amount_input,
                        payerId = expenseRow.payer_id,
                        splitMode = expenseRow.split_mode,
                        participants = participantsByExpense[expenseRow.id].orEmpty(),
                        // SQLite path is the local-only / pre-Firestore path. The legacy schema
                        // has no creator column, so hydrate as "unknown"; AppState.toEditable()
                        // falls back to the signed-in user for empty values, and Firestore writes
                        // thereafter carry the real creator forward.
                        createdByUid = "",
                    )
                }
            }

        val groups = queries.selectAllGroups().executeAsList().map { groupRow ->
            PersistedGroup(
                id = groupRow.id,
                name = groupRow.name,
                createdByUid = "",
                members = membersByGroup[groupRow.id].orEmpty(),
                expenses = expensesByGroup[groupRow.id].orEmpty(),
            )
        }

        AppSnapshot(friends = friends, groups = groups)
    }

    override suspend fun saveSnapshot(snapshot: AppSnapshot) = withContext(Dispatchers.Default) {
        val queries = db.splitFoolishQueries
        // One transaction for the whole world. wipeGroups cascades through members/expenses/
        // participants via the ON DELETE CASCADE foreign keys, so we only hand-wipe friends
        // (which has no parent) before reinserting everything.
        queries.transaction {
            queries.wipeGroups()
            queries.wipeFriends()

            snapshot.friends.forEach { friend ->
                queries.upsertFriend(id = friend.id, name = friend.name)
            }

            snapshot.groups.forEachIndexed { groupIndex, group ->
                queries.upsertGroup(
                    id = group.id,
                    name = group.name,
                    position_in_app = groupIndex.toLong(),
                )
                group.members.forEachIndexed { memberIndex, member ->
                    queries.upsertMember(
                        id = member.id,
                        group_id = group.id,
                        name = member.name,
                        position_in_app = memberIndex.toLong(),
                    )
                }
                group.expenses.forEachIndexed { expenseIndex, expense ->
                    queries.upsertExpense(
                        id = expense.id,
                        group_id = group.id,
                        title = expense.title,
                        amount_input = expense.amountInput,
                        payer_id = expense.payerId,
                        split_mode = expense.splitMode,
                        position_in_app = expenseIndex.toLong(),
                    )
                    expense.participants.forEach { participant ->
                        queries.upsertExpenseParticipant(
                            expense_id = expense.id,
                            member_id = participant.memberId,
                            included = if (participant.included) 1L else 0L,
                            percent_input = participant.percentInput,
                        )
                    }
                }
            }
        }
    }
}
