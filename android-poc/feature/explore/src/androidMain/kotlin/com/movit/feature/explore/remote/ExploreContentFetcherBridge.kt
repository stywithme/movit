package com.movit.feature.explore.remote

import com.movit.feature.explore.ExploreContent
import com.movit.shared.AppResult

fun interface ExploreContentFetcher {
    suspend fun fetchExploreContent(): AppResult<ExploreContent>
}

/**
 * App module installs a fetcher that maps legacy Retrofit/cache to [ExploreContent].
 * When unset, [com.movit.feature.explore.remote.RemoteExploreRepository] falls back to fake data.
 */
object ExploreContentFetcherBridge {
    var fetcher: ExploreContentFetcher? = null
}
