package com.movit.core.data

import com.movit.core.data.audio.AudioPrefetchRunner
import com.movit.core.data.cache.AudioManifestCache
import com.movit.core.data.cache.ColdOfflineBundleSeeder
import com.movit.core.data.cache.SystemMessageCache
import com.movit.core.data.di.movitDataModule
import com.movit.core.data.local.DefaultMovitLocalStoreFactory
import com.movit.core.data.local.MovitLocalStore
import com.movit.core.data.local.MovitLocalStoreFactory
import com.movit.core.data.outbox.OfflineWriteQueue
import com.movit.core.data.platform.MovitPlatformBindings
import com.movit.core.data.repository.AccountSyncRepository
import com.movit.core.data.repository.ExploreSyncRepository
import com.movit.core.data.repository.HomeSyncRepository
import com.movit.core.data.repository.MobileWriteSyncRepository
import com.movit.core.data.repository.PlanSyncRepository
import com.movit.core.data.repository.ProgramFlowSyncRepository
import com.movit.core.data.repository.ReportsSyncRepository
import com.movit.core.data.preferences.MovitTrainingPreferences
import com.movit.core.data.repository.TrainingConfigRepository
import com.movit.core.data.repository.TrainingSessionWriteCoordinator
import com.movit.core.data.repository.WorkoutSessionSyncRepository
import com.movit.core.data.sync.MovitSyncOrchestrator
import com.movit.core.data.sync.WeekOfflinePackPrefetcher
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import kotlinx.coroutines.runBlocking
import org.koin.core.module.Module

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

    fun koin(): Koin = koinApp?.koin ?: error("MovitData.install() was not called.")

    fun install(
        platform: MovitPlatformBindings,
        localStoreFactory: MovitLocalStoreFactory = DefaultMovitLocalStoreFactory,
        additionalModules: List<Module> = emptyList(),
    ) {
        if (koinApp != null) {
            stopKoin()
        }
        onSessionExpired = null
        koinApp = startKoin {
            modules(movitDataModule(platform, localStoreFactory) + additionalModules)
        }
    }

    /**
     * Seeds bundled cold-start data and hydrates [SystemMessageRegistry] on first launch.
     * Call once after [install] from the shell entry point.
     */
    suspend fun bootstrapLocalCaches() {
        if (!isInstalled) return
        val koin = koin()
        koin.get<ColdOfflineBundleSeeder>().seedIfNeeded()
        koin.get<SystemMessageCache>().loadIntoRegistry()
    }

    internal fun notifySessionExpired() {
        if (isInstalled) {
            runBlocking { clearAllUserData() }
        }
        onSessionExpired?.invoke()
    }

    /**
     * Clears all user-owned durable caches (SQLDelight JSON + sync metadata + outbox + audio manifest)
     * and legacy platform migration stores. Call before [MovitPlatformBindings.clearAuthSession] on logout.
     */
    suspend fun clearAllUserData() {
        if (!isInstalled) return
        val koin = koin()
        koin.get<MovitLocalStore>().clearAllUserData()
        koin.get<AudioManifestCache>().clear()
        koin.get<MovitPlatformBindings>().clearLegacyUserCaches()
    }

    fun requirePlatform(): MovitPlatformBindings = koin().get()

    val localStore: MovitLocalStore get() = koin().get()

    val explore: ExploreSyncRepository get() = koin().get()
    val home: HomeSyncRepository get() = koin().get()
    val plan: PlanSyncRepository get() = koin().get()
    val programFlow: ProgramFlowSyncRepository get() = koin().get()
    val reports: ReportsSyncRepository get() = koin().get()
    val workoutSession: WorkoutSessionSyncRepository get() = koin().get()
    val trainingWrites: TrainingSessionWriteCoordinator get() = koin().get()
    val mobileWrites: MobileWriteSyncRepository get() = koin().get()
    val account: AccountSyncRepository get() = koin().get()
    val billing: com.movit.core.data.repository.BillingSyncRepository get() = koin().get()
    val trainingConfig: TrainingConfigRepository get() = koin().get()
    val trainingPreferences: MovitTrainingPreferences get() = koin().get()
    val audioManifest: AudioManifestCache get() = koin().get()
    val audioPrefetch: AudioPrefetchRunner get() = koin().get()
    val offlineWrites: OfflineWriteQueue get() = koin().get()
    val sync: MovitSyncOrchestrator get() = koin().get()
    val weekOfflinePrefetch: WeekOfflinePackPrefetcher get() = koin().get()

}
