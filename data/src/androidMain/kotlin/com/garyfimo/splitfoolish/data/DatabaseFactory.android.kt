package com.garyfimo.splitfoolish.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.garyfimo.splitfoolish.data.db.SplitFoolishDb

/**
 * Open (or create, on first launch) the local database using Android's bundled SQLite. The
 * [AndroidSqliteDriver] wires schema migrations automatically — bumping [SplitFoolishDb.Schema]
 * is what triggers them, so this call never has to know about versions.
 *
 * The custom [AndroidSqliteDriver.Callback] enables SQLite foreign-key enforcement on every
 * connection open: `PRAGMA foreign_keys = ON` is scoped per connection, defaults OFF on Android,
 * and without it the `ON DELETE CASCADE` declarations in `SplitFoolish.sq` are silently inert,
 * which would leak orphaned member/expense/participant rows every time a group is deleted.
 */
fun createSplitFoolishDb(context: Context): SplitFoolishDb =
    SplitFoolishDb(
        AndroidSqliteDriver(
            schema = SplitFoolishDb.Schema,
            context = context.applicationContext,
            name = "splitfoolish.db",
            callback = object : AndroidSqliteDriver.Callback(SplitFoolishDb.Schema) {
                override fun onConfigure(db: SupportSQLiteDatabase) {
                    super.onConfigure(db)
                    db.setForeignKeyConstraintsEnabled(true)
                }
            },
        )
    )
