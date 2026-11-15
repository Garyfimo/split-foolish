package com.garyfimo.splitfoolish.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.garyfimo.splitfoolish.data.db.SplitFoolishDb
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Host-side tests for the repo. We use SQLDelight's JDBC driver with its in-memory URL so each
 * test gets a fresh database, and we manually call [SplitFoolishDb.Schema.create] because the
 * JDBC driver (unlike Android/Native) doesn't automatically run the schema on open.
 *
 * The `PRAGMA foreign_keys = ON` step is essential: sqlite-jdbc (like every other SQLite driver)
 * defaults foreign-key enforcement OFF per connection, and without it the `ON DELETE CASCADE`
 * declarations in `SplitFoolish.sq` are silently inert. The in-memory driver uses a single
 * connection for its whole lifetime, so one PRAGMA before `Schema.create` covers everything —
 * no per-connection callback needed here the way there is on Android and iOS.
 */
class SplitFoolishRepositoryTest {

    /**
     * Build a repo and keep a handle to the underlying db so tests that need to assert on raw
     * child-table state (cascades, etc.) can bypass the loader and read the tables directly.
     */
    private fun newRepoWithDb(): Pair<SplitFoolishRepository, SplitFoolishDb> {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, "PRAGMA foreign_keys = ON", 0, null)
        SplitFoolishDb.Schema.create(driver)
        val db = SplitFoolishDb(driver)
        return SplitFoolishRepository(db) to db
    }

    private fun newRepo(): SplitFoolishRepository = newRepoWithDb().first

    @Test
    fun empty_database_loads_as_empty_snapshot() = runTest {
        val snap = newRepo().loadSnapshot()
        assertEquals(emptyList(), snap.friends)
        assertEquals(emptyList(), snap.groups)
    }

    @Test
    fun save_then_load_round_trips_the_full_tree() = runTest {
        val repo = newRepo()
        val input = AppSnapshot(
            friends = listOf(
                PersistedFriend(id = "f1", name = "Alice"),
                PersistedFriend(id = "f2", name = "Bob"),
            ),
            groups = listOf(
                PersistedGroup(
                    id = "g1",
                    name = "Trip",
                    members = listOf(
                        PersistedMember(id = "m1", name = "Alice"),
                        PersistedMember(id = "m2", name = "Bob"),
                    ),
                    expenses = listOf(
                        PersistedExpense(
                            id = "e1",
                            title = "Dinner",
                            amountInput = "40.00",
                            payerId = "m1",
                            splitMode = "Equal",
                            participants = listOf(
                                PersistedExpenseParticipant("m1", included = true, percentInput = ""),
                                PersistedExpenseParticipant("m2", included = true, percentInput = ""),
                            ),
                        ),
                    ),
                ),
            ),
        )

        repo.saveSnapshot(input)
        val loaded = repo.loadSnapshot()

        assertEquals(input.friends, loaded.friends)
        assertEquals(input.groups, loaded.groups)
    }

    @Test
    fun save_replaces_prior_content() = runTest {
        val repo = newRepo()
        repo.saveSnapshot(
            AppSnapshot(
                friends = listOf(PersistedFriend("old", "Ghost")),
                groups = emptyList(),
            )
        )
        repo.saveSnapshot(
            AppSnapshot(
                friends = listOf(PersistedFriend("new", "Alive")),
                groups = emptyList(),
            )
        )
        val loaded = repo.loadSnapshot()
        assertEquals(1, loaded.friends.size)
        assertEquals("new", loaded.friends[0].id)
        assertEquals("Alive", loaded.friends[0].name)
    }

    @Test
    fun deleting_a_group_cascades_to_members_expenses_and_participants() = runTest {
        val (repo, db) = newRepoWithDb()
        repo.saveSnapshot(
            AppSnapshot(
                friends = emptyList(),
                groups = listOf(
                    PersistedGroup(
                        id = "g1",
                        name = "Trip",
                        members = listOf(PersistedMember("m1", "Alice")),
                        expenses = listOf(
                            PersistedExpense(
                                id = "e1",
                                title = "Dinner",
                                amountInput = "10",
                                payerId = "m1",
                                splitMode = "Equal",
                                participants = listOf(
                                    PersistedExpenseParticipant("m1", included = true, percentInput = ""),
                                ),
                            ),
                        ),
                    ),
                ),
            )
        )
        // Save with no groups at all; the wipe + cascade should remove every descendant.
        repo.saveSnapshot(AppSnapshot(friends = emptyList(), groups = emptyList()))

        // Load-level assertion catches "nothing visible" — but loadSnapshot groupBy's child rows
        // by group_id and only emits groups that still exist in `selectAllGroups`, so orphans
        // would be silently filtered out. The raw-table assertions below are what actually prove
        // the cascade fired instead of leaving orphans in the database file.
        val loaded = repo.loadSnapshot()
        assertEquals(emptyList(), loaded.groups)

        val queries = db.splitFoolishQueries
        assertEquals(
            0, queries.selectAllMembers().executeAsList().size,
            "members rows must cascade-delete when their parent group is removed",
        )
        assertEquals(
            0, queries.selectAllExpenses().executeAsList().size,
            "expenses rows must cascade-delete when their parent group is removed",
        )
        assertEquals(
            0, queries.selectAllExpenseParticipants().executeAsList().size,
            "expense_participants rows must cascade-delete when their parent expense is removed",
        )
    }
}
