package com.movit.core.data.local

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.movit.core.data.db.MovitDatabase
import com.movit.core.data.platform.MovitPlatformBindings

object MovitAndroidRuntime {
    lateinit var applicationContext: Context
}

internal actual fun createDefaultMovitLocalStore(platform: MovitPlatformBindings): MovitLocalStore {
    val driver = AndroidSqliteDriver(
        schema = MovitDatabase.Schema,
        context = MovitAndroidRuntime.applicationContext,
        name = DATABASE_NAME,
    )
    return buildMigratingStore(driver, platform)
}

internal fun createMovitLocalStoreWithDriver(
    driver: SqlDriver,
    platform: MovitPlatformBindings,
): MovitLocalStore = buildMigratingStore(driver, platform)

private fun buildMigratingStore(
    driver: SqlDriver,
    platform: MovitPlatformBindings,
): MovitLocalStore {
    val sqlStore = SqlDelightMovitLocalStore(MovitDatabase(driver))
    return MigratingMovitLocalStore(sqlStore, platform = { platform }).also {
        it.migrateKnownCachesFromPlatform()
    }
}

private const val DATABASE_NAME = "movit_local.db"
