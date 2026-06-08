package com.movit.feature.explore.remote

import com.movit.feature.explore.ExploreRepository
import com.movit.feature.explore.FakeExploreRepository
import com.movit.shared.AppResult

class RemoteExploreRepository(
    private val fallback: ExploreRepository = FakeExploreRepository(),
) : ExploreRepository {

    override suspend fun getExploreContent(): AppResult<com.movit.feature.explore.ExploreContent> {
        val fetcher = ExploreContentFetcherBridge.fetcher
        return if (fetcher != null) {
            fetcher.fetchExploreContent()
        } else {
            fallback.getExploreContent()
        }
    }
}
