package com.movit.core.data.repository

import com.movit.core.data.MovitData
import com.movit.core.data.cache.CacheState
import com.movit.core.data.cache.staleWhileRevalidate
import com.movit.core.model.ExploreContent
import com.movit.core.model.ExploreRepository
import com.movit.resources.strings.ExploreStrings
import com.movit.shared.AppResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

class SharedExploreRepository(
    private val fallback: ExploreRepository = InMemoryExploreRepository(),
) : ExploreContentSource {

    override fun observeExploreContent(): Flow<CacheState<ExploreContent>> {
        if (!MovitData.isInstalled) {
            return staleWhileRevalidate(
                screenId = "explore",
                readCached = { null },
                syncFresh = { fallback.getExploreContent() },
            )
        }
        val platform = MovitData.requirePlatform()
        val language = platform.preferredLanguage()
        return flow {
            val strings = ExploreStrings.load(language)
            emitAll(
                staleWhileRevalidate(
                    screenId = "explore",
                    readCached = {
                        MovitData.explore.readCached()?.let { cached ->
                            ExploreApiMapper.map(cached, language, strings)
                        }
                    },
                    syncFresh = { syncExplore(language, strings) },
                ),
            )
        }
    }

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

    private suspend fun syncExplore(
        language: String,
        strings: ExploreStrings,
    ): AppResult<ExploreContent> = when (val result = MovitData.explore.sync()) {
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

    private suspend fun refreshExploreContent(
        language: String,
        strings: ExploreStrings,
    ): AppResult<ExploreContent> = syncExplore(language, strings)
}

fun defaultExploreRepository(): ExploreRepository = SharedExploreRepository()
