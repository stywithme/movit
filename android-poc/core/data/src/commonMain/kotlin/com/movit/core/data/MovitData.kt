package com.movit.core.data

import com.movit.core.data.di.movitDataModule
import com.movit.core.data.platform.MovitPlatformBindings
import com.movit.core.data.repository.ExploreSyncRepository
import com.movit.core.data.repository.HomeSyncRepository
import com.movit.core.data.repository.ReportsSyncRepository
import com.movit.core.data.repository.WorkoutSessionSyncRepository
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin

/**
 * Typed accessors for Movit sync repositories resolved through Koin.
 * Call [install] once per process from the platform entry point.
 */
object MovitData {
    val isInstalled: Boolean
        get() = GlobalContext.getOrNull() != null

    fun install(platform: MovitPlatformBindings) {
        if (isInstalled) {
            stopKoin()
        }
        startKoin {
            modules(movitDataModule(platform))
        }
    }

    fun requirePlatform(): MovitPlatformBindings = koin().get()

    val explore: ExploreSyncRepository get() = koin().get()
    val home: HomeSyncRepository get() = koin().get()
    val reports: ReportsSyncRepository get() = koin().get()
    val workoutSession: WorkoutSessionSyncRepository get() = koin().get()

    private fun koin() = GlobalContext.get()
        ?: error("MovitData.install() was not called.")
}
