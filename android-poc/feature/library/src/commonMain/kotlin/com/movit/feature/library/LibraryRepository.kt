package com.movit.feature.library

import com.movit.feature.explore.ExploreContent
import com.movit.feature.explore.ExploreItemType
import com.movit.feature.explore.ExploreItemUi
import com.movit.feature.explore.ExploreRepository
import com.movit.feature.explore.defaultExploreRepository
import com.movit.shared.AppResult

interface LibraryRepository {
    suspend fun loadContent(): AppResult<ExploreContent>
    suspend fun findItem(id: String): ExploreItemUi?
}

class DefaultLibraryRepository(
    private val exploreRepository: ExploreRepository = defaultExploreRepository(),
) : LibraryRepository {

    private var cached: ExploreContent? = null

    override suspend fun loadContent(): AppResult<ExploreContent> {
        return when (val result = exploreRepository.getExploreContent()) {
            is AppResult.Success -> {
                cached = result.value
                result
            }
            is AppResult.Failure -> result
        }
    }

    override suspend fun findItem(id: String): ExploreItemUi? {
        val content = cached ?: when (val result = loadContent()) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return null
        }
        return (content.workouts + content.exercises + content.programs + content.featured)
            .distinctBy { it.id }
            .firstOrNull { it.id == id }
    }
}

fun defaultLibraryRepository(): LibraryRepository = DefaultLibraryRepository()
