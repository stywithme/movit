package com.movit.core.data.di

import com.movit.core.data.platform.MovitPlatformBindings
import com.movit.core.data.repository.AccountSyncRepository
import com.movit.core.data.repository.ExploreSyncRepository
import com.movit.core.data.repository.HomeSyncRepository
import com.movit.core.data.repository.ReportsSyncRepository
import com.movit.core.data.repository.WorkoutSessionSyncRepository
import com.movit.core.network.MovitMobileApi
import com.movit.core.network.createMovitHttpClient
import org.koin.core.module.Module
import org.koin.dsl.module

fun movitDataModule(platform: MovitPlatformBindings): Module = module {
    single<MovitPlatformBindings> { platform }
    single {
        MovitMobileApi(createMovitHttpClient(enableLogging = false)) {
            get<MovitPlatformBindings>().apiBaseUrl()
        }
    }
    single { ExploreSyncRepository(api = get(), platform = { get() }) }
    single { HomeSyncRepository(api = get(), platform = { get() }) }
    single { ReportsSyncRepository(api = get(), platform = { get() }) }
    single { WorkoutSessionSyncRepository(api = get(), platform = { get() }) }
    single { AccountSyncRepository(api = get(), platform = { get() }) }
}
