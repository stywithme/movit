package com.movit.core.data.di

import com.movit.core.data.MovitData
import com.movit.core.data.audio.AudioFileDownloadPort
import com.movit.core.data.audio.AudioFileDownloader
import com.movit.core.data.audio.AudioPrefetchRunner
import com.movit.core.data.audio.EntityAudioManifestFetcher
import com.movit.core.data.audio.MovitMobileEntityAudioClient
import com.movit.core.data.cache.AudioManifestCache
import com.movit.core.data.cache.ColdOfflineBundleSeeder
import com.movit.core.data.cache.MessageLibraryCache
import com.movit.core.data.cache.MovitCacheFreshnessDiagnostics
import com.movit.core.data.cache.MovitSyncMetadataStore
import com.movit.core.data.cache.SystemMessageCache
import com.movit.core.data.local.DefaultMovitLocalStoreFactory
import com.movit.core.data.local.MovitLocalStore
import com.movit.core.data.local.MovitLocalStoreFactory
import com.movit.core.data.outbox.OfflineWriteQueue
import com.movit.core.data.platform.MovitPlatformBindings
import com.movit.core.data.platform.PlatformMovitAuthTokenStore
import com.movit.core.data.repository.AccountSyncRepository
import com.movit.core.data.repository.BillingSyncRepository
import com.movit.core.data.repository.DayCustomizationLocalStore
import com.movit.core.data.repository.ExerciseIdResolver
import com.movit.core.data.repository.ExercisePreferenceLocalStore
import com.movit.core.data.repository.ExploreSyncRepository
import com.movit.core.data.repository.HomeSyncRepository
import com.movit.core.data.repository.MobileWriteSyncRepository
import com.movit.core.data.repository.PlanSyncRepository
import com.movit.core.data.repository.UserProgramEnrollmentLocalStore
import com.movit.core.data.repository.ProgramFlowSyncRepository
import com.movit.core.data.preferences.MovitTrainingPreferences
import com.movit.core.data.repository.ReportsSyncRepository
import com.movit.core.data.repository.SyncCatalogOfflineRepository
import com.movit.core.data.repository.TrainingConfigRepository
import com.movit.core.data.journal.SessionJournalStore
import com.movit.core.data.repository.TrainingSessionWriteCoordinator
import com.movit.core.data.repository.WorkoutSessionSyncRepository
import com.movit.core.data.sync.MovitSyncOrchestrator
import com.movit.core.data.sync.SyncStatusBus
import com.movit.core.data.sync.WeekOfflinePackPrefetcher
import com.movit.core.data.readiness.BackgroundMediaPrefetcher
import com.movit.core.data.readiness.DataReadinessGate
import com.movit.core.network.MovitAuthTokenStore
import com.movit.core.network.MovitBillingApi
import com.movit.core.network.MovitHttpClientConfig
import com.movit.core.network.MovitMobileApi
import com.movit.core.network.createMovitHttpClient
import io.ktor.client.HttpClient
import org.koin.core.module.Module
import org.koin.dsl.module

fun movitDataModule(
    platform: MovitPlatformBindings,
    localStoreFactory: MovitLocalStoreFactory = DefaultMovitLocalStoreFactory,
): Module = module {
    single<MovitPlatformBindings> { platform }
    single<MovitLocalStore> { localStoreFactory.create(platform) }
    single<MovitAuthTokenStore> { PlatformMovitAuthTokenStore { get() } }
    single<HttpClient> {
        val bindings = get<MovitPlatformBindings>()
        val tokenStore = get<MovitAuthTokenStore>()
        val refreshClient = createMovitHttpClient(enableLogging = false)
        createMovitHttpClient(
            enableLogging = false,
            auth = MovitHttpClientConfig(
                tokenStore = tokenStore,
                baseUrlProvider = { bindings.apiBaseUrl() },
                refreshHttpClient = refreshClient,
                onSessionExpired = { MovitData.notifySessionExpired() },
            ),
        )
    }
    single { MovitMobileApi(get()) { get<MovitPlatformBindings>().apiBaseUrl() } }
    single { MovitBillingApi(get()) { get<MovitPlatformBindings>().apiBaseUrl() } }
    single { MovitSyncMetadataStore(get()) }
    single { MovitCacheFreshnessDiagnostics(get(), get()) }
    single { AudioManifestCache(get()) }
    single<AudioFileDownloadPort> { AudioFileDownloader() }
    single<EntityAudioManifestFetcher> {
        EntityAudioManifestFetcher(
            client = MovitMobileEntityAudioClient(get()),
            manifestCache = get(),
            platform = { get() },
            exploreSync = get(),
        )
    }
    single { AudioPrefetchRunner(get(), get(), get()) }
    single { SystemMessageCache(get()) }
    single {
        ColdOfflineBundleSeeder(
            localStore = get(),
            homeSync = get(),
            exploreSync = get(),
            systemMessageCache = get(),
            trainingConfig = get(),
            messageLibraryCache = get(),
        )
    }
    single { com.movit.core.data.outbox.GuestOutboxAttributionGate(get()) }
    single { SyncStatusBus(platform = { get() }, localStore = get()) }
    single {
        OfflineWriteQueue(
            localStore = get(),
            api = get(),
            platform = { get() },
            guestGate = get(),
            syncStatusBus = get(),
        )
    }
    single { ExploreSyncRepository(api = get(), platform = { get() }, localStore = { get() }) }
    single { HomeSyncRepository(api = get(), platform = { get() }, localStore = { get() }) }
    single { UserProgramEnrollmentLocalStore(get()) }
    single {
        PlanSyncRepository(
            api = get(),
            platform = { get() },
            homeSync = get(),
            userProgramEnrollments = get(),
        )
    }
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
            trainingConfig = get(),
            catalogOffline = get(),
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
    single { AccountSyncRepository(api = get(), platform = { get() }, offlineWrites = { get() }) }
    single { BillingSyncRepository(api = get(), platform = { get() }) }
    single { ExercisePreferenceLocalStore(get(), ExerciseIdResolver(get())) }
    single { DayCustomizationLocalStore(get()) }
    single { MessageLibraryCache(get()) }
    single { TrainingConfigRepository(localStore = get(), messageLibraryCache = get()) }
    single { SyncCatalogOfflineRepository(localStore = get(), trainingConfig = get()) }
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
            catalogOffline = get(),
            systemMessageCache = get(),
            exercisePreferenceLocalStore = get(),
            dayCustomizationLocalStore = get(),
            messageLibraryCache = get(),
            userProgramEnrollmentLocalStore = get(),
            syncStatusBus = get(),
        )
    }
    single {
        DataReadinessGate(
            exploreSync = get(),
            trainingConfig = get(),
            catalogOffline = get(),
            messageLibraryCache = get(),
            systemMessageCache = get(),
            homeSync = get(),
        )
    }
    single {
        BackgroundMediaPrefetcher(
            audioPrefetch = get(),
            exploreSync = get(),
            homeSync = get(),
            planSync = get(),
            syncStatusBus = get(),
        )
    }
    single {
        WeekOfflinePackPrefetcher(
            sync = get(),
            audioPrefetch = get(),
            workoutSession = get(),
            trainingConfig = get(),
            platform = { get() },
        )
    }
}
