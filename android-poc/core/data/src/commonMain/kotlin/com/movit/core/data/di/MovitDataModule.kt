package com.movit.core.data.di

import com.movit.core.data.MovitData
import com.movit.core.data.platform.MovitPlatformBindings
import com.movit.core.data.platform.PlatformMovitAuthTokenStore
import com.movit.core.data.repository.AccountSyncRepository
import com.movit.core.data.repository.ExploreSyncRepository
import com.movit.core.data.repository.HomeSyncRepository
import com.movit.core.data.repository.PlanSyncRepository
import com.movit.core.data.repository.ProgramFlowSyncRepository
import com.movit.core.data.repository.ReportsSyncRepository
import com.movit.core.data.repository.WorkoutSessionSyncRepository
import com.movit.core.network.MovitAuthTokenStore
import com.movit.core.network.MovitHttpClientConfig
import com.movit.core.network.MovitMobileApi
import com.movit.core.network.createMovitHttpClient
import org.koin.core.module.Module
import org.koin.dsl.module

fun movitDataModule(platform: MovitPlatformBindings): Module = module {
    single<MovitPlatformBindings> { platform }
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
    single { ExploreSyncRepository(api = get(), platform = { get() }) }
    single { HomeSyncRepository(api = get(), platform = { get() }) }
    single { PlanSyncRepository(api = get(), platform = { get() }, homeSync = get()) }
    single {
        ProgramFlowSyncRepository(
            api = get(),
            platform = { get() },
            exploreSync = get(),
            homeSync = get(),
            planSync = get(),
        )
    }
    single { ReportsSyncRepository(api = get(), platform = { get() }) }
    single { WorkoutSessionSyncRepository(api = get(), platform = { get() }) }
    single { AccountSyncRepository(api = get(), platform = { get() }) }
}
