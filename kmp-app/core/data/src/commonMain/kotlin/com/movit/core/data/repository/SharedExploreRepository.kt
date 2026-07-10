package com.movit.core.data.repository

import com.movit.core.data.MovitData
import com.movit.core.model.ExploreContent
import com.movit.core.model.ExploreRepository
import com.movit.resources.strings.ExploreStrings
import com.movit.shared.AppResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

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
        MovitData.explore.readCached()?.let { cached ->
            return withContext(Dispatchers.IO) {
                AppResult.Success(ExploreApiMapper.map(cached, language, strings))
            }
        }
        return refreshExploreContent(language, strings)
    }

    override suspend fun refreshExploreContent(): AppResult<ExploreContent> {
        if (!MovitData.isInstalled) {
            return fallback.refreshExploreContent()
        }
        val platform = MovitData.requirePlatform()
        val language = platform.preferredLanguage()
        val strings = ExploreStrings.load(language)
        return refreshExploreContent(language, strings)
    }

    private suspend fun refreshExploreContent(
        language: String,
        strings: ExploreStrings,
    ): AppResult<ExploreContent> {
        return when (val result = MovitData.explore.sync()) {
            is AppResult.Success -> AppResult.Success(
                ExploreApiMapper.map(result.value, language, strings),
            )
            is AppResult.Failure -> {
                val cached = MovitData.explore.readCached()
                if (cached != null) {
                    AppResult.Success(ExploreApiMapper.map(cached, language, strings))
                } else {
                    AppResult.Failure(result.message)
                }
            }
        }
    }
}

fun defaultExploreRepository(): ExploreRepository = SharedExploreRepository()
