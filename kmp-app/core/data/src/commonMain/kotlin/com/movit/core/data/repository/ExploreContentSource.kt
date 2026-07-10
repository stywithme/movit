package com.movit.core.data.repository

import com.movit.core.data.cache.CacheState
import com.movit.core.model.ExploreContent
import com.movit.core.model.ExploreRepository
import kotlinx.coroutines.flow.Flow

interface ExploreContentSource : ExploreRepository {
    fun observeExploreContent(): Flow<CacheState<ExploreContent>>
}

fun defaultExploreContentSource(): ExploreContentSource = SharedExploreRepository()
