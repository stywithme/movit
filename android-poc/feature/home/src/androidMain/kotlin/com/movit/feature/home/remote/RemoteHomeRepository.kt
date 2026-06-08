package com.movit.feature.home.remote

import com.movit.feature.home.FakeHomeRepository
import com.movit.feature.home.HomeDashboardUi
import com.movit.feature.home.HomeRepository
import com.movit.shared.AppResult

class RemoteHomeRepository(
    private val fallback: HomeRepository = FakeHomeRepository(),
) : HomeRepository {

    override suspend fun getHomeDashboard(): AppResult<HomeDashboardUi> {
        val fetcher = HomeContentFetcherBridge.fetcher
        return if (fetcher != null) {
            fetcher.fetchHomeDashboard()
        } else {
            fallback.getHomeDashboard()
        }
    }
}
