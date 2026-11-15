package com.garyfimo.splitfoolish.data

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.garyfimo.splitfoolish.data.db.SplitFoolishDb

/**
 * Open (or create, on first launch) the local database using Android's bundled SQLite. The
 * [AndroidSqliteDriver] wires schema migrations automatically — bumping [SplitFoolishDb.Schema]
 * is what triggers them, so this call never has to know about versions.
 */
fun createSplitFoolishDb(context: Context): SplitFoolishDb =
    SplitFoolishDb(
        AndroidSqliteDriver(
            schema = SplitFoolishDb.Schema,
            context = context.applicationContext,
            name = "splitfoolish.db",
        )
    )
