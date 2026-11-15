package com.garyfimo.splitfoolish.data

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.garyfimo.splitfoolish.data.db.SplitFoolishDb

/**
 * Open (or create) the local database using Native's SQLiter-backed driver. Unlike the Android
 * driver this one doesn't need a context — the file is placed in the default documents dir
 * under the given name.
 *
 * The [onConfiguration] lambda flips [DatabaseConfiguration.Extended.foreignKeyConstraints] on,
 * which tells SQLiter to emit `PRAGMA foreign_keys = ON` for every connection it opens. SQLiter
 * defaults this to `false`; without it, the `ON DELETE CASCADE` rules in `SplitFoolish.sq`
 * don't fire and deleting a group would orphan its members, expenses, and participants in the
 * database file forever.
 */
fun createSplitFoolishDb(): SplitFoolishDb =
    SplitFoolishDb(
        NativeSqliteDriver(
            schema = SplitFoolishDb.Schema,
            name = "splitfoolish.db",
            onConfiguration = { configuration ->
                configuration.copy(
                    extendedConfig = configuration.extendedConfig.copy(
                        foreignKeyConstraints = true,
                    ),
                )
            },
        )
    )
