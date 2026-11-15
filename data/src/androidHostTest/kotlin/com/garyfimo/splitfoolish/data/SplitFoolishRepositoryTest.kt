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
 */
class SplitFoolishRepositoryTest {

    private fun newRepo(): SplitFoolishRepository {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        SplitFoolishDb.Schema.create(driver)
        return SplitFoolishRepository(SplitFoolishDb(driver))
    }

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
        val repo = newRepo()
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
        val loaded = repo.loadSnapshot()
        assertEquals(emptyList(), loaded.groups)
    }
}
