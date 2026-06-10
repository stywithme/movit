package com.movit.core.data.repository

import com.movit.core.model.ExploreContent
import com.movit.core.model.ExploreRepository
import com.movit.shared.AppResult

class InMemoryExploreRepository(
    private val content: ExploreContent = ExploreContent(
        featured = emptyList(),
        workouts = emptyList(),
        exercises = emptyList(),
        programs = emptyList(),
    ),
    private val shouldFail: Boolean = false,
) : ExploreRepository {

    override suspend fun getExploreContent(): AppResult<ExploreContent> {
        return if (shouldFail) {
            AppResult.Failure("Unable to load explore content.")
        } else {
            AppResult.Success(content)
        }
    }
}
