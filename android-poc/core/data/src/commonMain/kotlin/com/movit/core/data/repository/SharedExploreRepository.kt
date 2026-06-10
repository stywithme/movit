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
        return when (val result = MovitData.explore.sync()) {
            is AppResult.Success -> AppResult.Success(
                ExploreApiMapper.map(result.value, language, strings),
            )
            is AppResult.Failure -> {
                val cached = MovitData.explore.readCached()
                if (cached != null) {
                    AppResult.Success(ExploreApiMapper.map(cached, language, strings))
                } else {
                    fallback.getExploreContent()
                }
            }
        }
    }
}

fun defaultExploreRepository(): ExploreRepository = SharedExploreRepository()
