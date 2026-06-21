package com.movit.feature.explore

import com.movit.core.model.ExploreContent
import com.movit.core.model.ExploreRepository
import com.movit.shared.AppResult

class FakeExploreRepository(
    private val content: ExploreContent = MovitExplorePreviewData.content,
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
