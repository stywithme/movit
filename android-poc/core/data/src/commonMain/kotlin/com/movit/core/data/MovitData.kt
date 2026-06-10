package com.movit.core.data

import com.movit.core.data.di.movitDataModule
import com.movit.core.data.platform.MovitPlatformBindings
import com.movit.core.data.repository.AccountSyncRepository
import com.movit.core.data.repository.ExploreSyncRepository
import com.movit.core.data.repository.HomeSyncRepository
import com.movit.core.data.repository.PlanSyncRepository
import com.movit.core.data.repository.ProgramFlowSyncRepository
import com.movit.core.data.repository.ReportsSyncRepository
import com.movit.core.data.repository.WorkoutSessionSyncRepository
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin

/**
 * Typed accessors for Movit sync repositories resolved through Koin.
 * Call [install] once per process from the platform entry point.
 *
 * Holds the [KoinApplication] reference directly rather than reading
 * `GlobalContext`, which is JVM-only and does not resolve on Kotlin/Native (iOS).
 * `startKoin`/`stopKoin` + the returned [KoinApplication.koin] are the
 * multiplatform-safe surface.
 */
object MovitData {
    private var koinApp: KoinApplication? = null

    /** Invoked when refresh fails after 401; wire to shell auth gate. */
    var onSessionExpired: (() -> Unit)? = null

    val isInstalled: Boolean
        get() = koinApp != null

    fun install(platform: MovitPlatformBindings) {
        if (koinApp != null) {
            stopKoin()
        }
        onSessionExpired = null
        koinApp = startKoin {
            modules(movitDataModule(platform))
        }
    }

    internal fun notifySessionExpired() {
        onSessionExpired?.invoke()
    }

    fun requirePlatform(): MovitPlatformBindings = koin().get()

    val explore: ExploreSyncRepository get() = koin().get()
    val home: HomeSyncRepository get() = koin().get()
    val plan: PlanSyncRepository get() = koin().get()
    val programFlow: ProgramFlowSyncRepository get() = koin().get()
    val reports: ReportsSyncRepository get() = koin().get()
    val workoutSession: WorkoutSessionSyncRepository get() = koin().get()
    val account: AccountSyncRepository get() = koin().get()

    private fun koin(): Koin = koinApp?.koin
        ?: error("MovitData.install() was not called.")
}
