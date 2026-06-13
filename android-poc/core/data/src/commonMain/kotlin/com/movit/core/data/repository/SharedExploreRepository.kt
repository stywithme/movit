package com.movit.core.data.repository

import com.movit.core.data.MovitData
import com.movit.core.model.ExploreContent
import com.movit.core.model.ExploreRepository
import com.movit.resources.strings.ExploreStrings
import com.movit.shared.AppResult

class SharedExploreRepository(
    private val fallback: ExploreRepository = InMemoryExploreRepository(),
) : ExploreRepository {

    override suspend fun getExploreContent(): AppResult<ExploreContent> {
        if (!MovitData.isInstalled) {
            return fallback.getExploreContent()
        }
        val platform = MovitData.requirePlatform()
        val language = platform.preferredLanguage()
        val strings = ExploreStrings.load(language)
        val cached = MovitData.explore.readCached()
        return if (cached != null) {
            AppResult.Success(ExploreApiMapper.map(cached, language, strings))
        } else {
            fallback.getExploreContent()
        }
    }
}

fun defaultExploreRepository(): ExploreRepository = SharedExploreRepository()
