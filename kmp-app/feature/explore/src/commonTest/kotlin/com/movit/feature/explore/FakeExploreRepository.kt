package com.movit.feature.explore

import com.movit.core.data.cache.CacheState
import com.movit.core.model.ExploreContent
import com.movit.core.model.ExploreRepository
import com.movit.shared.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeExploreRepository(
    private val content: ExploreContent = MovitExplorePreviewData.content,
    private val shouldFail: Boolean = false,
    private val freshContent: ExploreContent? = null,
) : ExploreRepository {

    override suspend fun getExploreContent(): AppResult<ExploreContent> {
        return if (shouldFail) {
            AppResult.Failure("Unable to load explore content.")
        } else {
            AppResult.Success(content)
        }
    }

    fun observeExploreContent(): Flow<CacheState<ExploreContent>> = flow {
        if (shouldFail) {
            emit(CacheState.Error("Unable to load explore content."))
            return@flow
        }
        emit(CacheState.Cached(content))
        emit(CacheState.Fresh(freshContent ?: content))
    }
}

fun FakeExploreRepository.asContentSource(): com.movit.core.data.repository.ExploreContentSource =
    object : com.movit.core.data.repository.ExploreContentSource {
        override suspend fun getExploreContent() = this@asContentSource.getExploreContent()
        override suspend fun refreshExploreContent() = this@asContentSource.getExploreContent()
        override fun observeExploreContent() = this@asContentSource.observeExploreContent()
    }
