package com.movit.feature.home.remote

import com.movit.feature.home.HomeDashboardUi
import com.movit.shared.AppResult

fun interface HomeContentFetcher {
    suspend fun fetchHomeDashboard(): AppResult<HomeDashboardUi>
}

/**
 * App module installs a fetcher that maps legacy Retrofit/cache to [HomeDashboardUi].
 * When unset, [com.movit.feature.home.remote.RemoteHomeRepository] falls back to fake data.
 */
object HomeContentFetcherBridge {
    var fetcher: HomeContentFetcher? = null
}
