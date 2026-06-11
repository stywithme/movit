package com.movit.core.data.di

import com.movit.core.data.MovitData
import com.movit.core.data.audio.AudioFileDownloader
import com.movit.core.data.audio.AudioPrefetchRunner
import com.movit.core.data.cache.AudioManifestCache
import com.movit.core.data.cache.ColdOfflineBundleSeeder
import com.movit.core.data.cache.MovitSyncMetadataStore
import com.movit.core.data.cache.SystemMessageCache
import com.movit.core.data.local.DefaultMovitLocalStoreFactory
import com.movit.core.data.local.MovitLocalStore
import com.movit.core.data.local.MovitLocalStoreFactory
import com.movit.core.data.outbox.OfflineWriteQueue
import com.movit.core.data.platform.MovitPlatformBindings
import com.movit.core.data.platform.PlatformMovitAuthTokenStore
import com.movit.core.data.repository.AccountSyncRepository
import com.movit.core.data.repository.ExploreSyncRepository
import com.movit.core.data.repository.HomeSyncRepository
import com.movit.core.data.repository.MobileWriteSyncRepository
import com.movit.core.data.repository.PlanSyncRepository
import com.movit.core.data.repository.ProgramFlowSyncRepository
import com.movit.core.data.preferences.MovitTrainingPreferences
import com.movit.core.data.repository.ReportsSyncRepository
import com.movit.core.data.repository.TrainingConfigRepository
import com.movit.core.data.journal.SessionJournalStore
import com.movit.core.data.repository.TrainingSessionWriteCoordinator
import com.movit.core.data.repository.WorkoutSessionSyncRepository
import com.movit.core.data.sync.MovitSyncOrchestrator
import com.movit.core.network.MovitAuthTokenStore
import com.movit.core.network.MovitHttpClientConfig
import com.movit.core.network.MovitMobileApi
import com.movit.core.network.createMovitHttpClient
import org.koin.core.module.Module
import org.koin.dsl.module

fun movitDataModule(
    platform: MovitPlatformBindings,
    localStoreFactory: MovitLocalStoreFactory = DefaultMovitLocalStoreFactory,
): Module = module {
    single<MovitPlatformBindings> { platform }
    single<MovitLocalStore> { localStoreFactory.create(platform) }
    single<MovitAuthTokenStore> { PlatformMovitAuthTokenStore { get() } }
    single {
        val bindings = get<MovitPlatformBindings>()
        val tokenStore = get<MovitAuthTokenStore>()
        val refreshClient = createMovitHttpClient(enableLogging = false)
        MovitMobileApi(
            createMovitHttpClient(
                enableLogging = false,
                auth = MovitHttpClientConfig(
                    tokenStore = tokenStore,
                    baseUrlProvider = { bindings.apiBaseUrl() },
                    refreshHttpClient = refreshClient,
                    onSessionExpired = { MovitData.notifySessionExpired() },
                ),
            ),
        ) { bindings.apiBaseUrl() }
    }
    single { MovitSyncMetadataStore(get()) }
    single { AudioManifestCache(get()) }
    single { AudioFileDownloader() }
    single { AudioPrefetchRunner(get(), get()) }
    single { SystemMessageCache(get()) }
    single {
        ColdOfflineBundleSeeder(
            localStore = get(),
            homeSync = get(),
            exploreSync = get(),
            systemMessageCache = get(),
        )
    }
    single {
        OfflineWriteQueue(
            localStore = get(),
            api = get(),
            platform = { get() },
        )
    }
    single { ExploreSyncRepository(api = get(), platform = { get() }, localStore = { get() }) }
    single { HomeSyncRepository(api = get(), platform = { get() }, localStore = { get() }) }
    single { PlanSyncRepository(api = get(), platform = { get() }, homeSync = get()) }
    single {
        ProgramFlowSyncRepository(
            api = get(),
            platform = { get() },
            localStore = { get() },
            exploreSync = get(),
            homeSync = get(),
            planSync = get(),
        )
    }
    single { ReportsSyncRepository(api = get(), platform = { get() }, localStore = { get() }) }
    single {
        MobileWriteSyncRepository(
            platform = { get() },
            localStore = { get() },
            offlineWrites = get(),
        )
    }
    single {
        WorkoutSessionSyncRepository(
            api = get(),
            platform = { get() },
            localStore = { get() },
            mobileWrites = get(),
        )
    }
    single { SessionJournalStore(localStore = get()) }
    single {
        TrainingSessionWriteCoordinator(
            mobileWrites = get(),
            reportsSync = get(),
            journalStore = get(),
        )
    }
    single { AccountSyncRepository(api = get(), platform = { get() }) }
    single { TrainingConfigRepository(localStore = get()) }
    single { MovitTrainingPreferences(localStore = get()) }
    single {
        MovitSyncOrchestrator(
            api = get(),
            platform = { get() },
            localStore = get(),
            homeSync = get(),
            exploreSync = get(),
            reportsSync = get(),
            planSync = get(),
            metadataStore = get(),
            audioManifestCache = get(),
            audioPrefetchRunner = get(),
            offlineWrites = get(),
            trainingConfig = get(),
        )
    }
}
