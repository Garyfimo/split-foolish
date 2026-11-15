package com.garyfimo.splitfoolish.data

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.garyfimo.splitfoolish.data.db.SplitFoolishDb

/**
 * Open (or create) the local database using Native's SQLiter-backed driver. Unlike the Android
 * driver this one doesn't need a context — the file is placed in the default documents dir
 * under the given name.
 */
fun createSplitFoolishDb(): SplitFoolishDb =
    SplitFoolishDb(
        NativeSqliteDriver(
            schema = SplitFoolishDb.Schema,
            name = "splitfoolish.db",
        )
    )
