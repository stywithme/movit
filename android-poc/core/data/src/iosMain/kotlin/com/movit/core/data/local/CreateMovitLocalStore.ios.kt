package com.movit.core.data.local

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.movit.core.data.db.MovitDatabase
import com.movit.core.data.platform.MovitPlatformBindings

internal actual fun createDefaultMovitLocalStore(platform: MovitPlatformBindings): MovitLocalStore {
    val driver = NativeSqliteDriver(MovitDatabase.Schema, DATABASE_NAME)
    val sqlStore = SqlDelightMovitLocalStore(MovitDatabase(driver))
    return MigratingMovitLocalStore(sqlStore, platform = { platform }).also {
        it.migrateKnownCachesFromPlatform()
    }
}

private const val DATABASE_NAME = "movit_local.db"
