package com.movit.core.data.repository

import com.movit.core.data.cache.MovitCachePolicy
import com.movit.core.data.local.MovitLocalStore
import com.movit.core.data.platform.MovitPlatformBindings
import com.movit.core.network.MovitMobileApi
import com.movit.core.network.dto.ExploreDataDto
import com.movit.core.network.dto.MobileSyncDataDto
import com.movit.shared.AppResult
import kotlin.concurrent.Volatile

class ExploreSyncRepository(
    private val api: MovitMobileApi,
    private val platform: () -> MovitPlatformBindings,
    private val localStore: () -> MovitLocalStore,
) {
    @Volatile
    private var memoizedExplore: ExploreDataDto? = null

    fun readCached(): ExploreDataDto? {
        memoizedExplore?.let { return it }
        return MovitCachePolicy.readJson(
            localStore(),
            MovitCacheKeys.EXPLORE_STORE,
            MovitCacheKeys.EXPLORE_DATA,
            ExploreDataDto.serializer(),
        ).also { memoizedExplore = it }
    }

    fun invalidateMemoized() {
        memoizedExplore = null
    }

    /** Thumbnail URL from the explore catalog cache (replaces legacy ExerciseRepository lookup). */
    fun exerciseImageUrl(slug: String): String? =
        readCached()
            ?.exercises
            ?.firstOrNull { it.slug == slug }
            ?.imageUrl
            ?.takeIf { it.isNotBlank() }

    /** Resolves a cached workout template id to its backend slug for entity audio manifest APIs. */
    fun slugForWorkoutTemplateId(templateId: String): String? =
        readCached()
            ?.workoutTemplates
            ?.firstOrNull { it.id == templateId }
            ?.slug
            ?.takeIf { it.isNotBlank() }

    suspend fun sync(limit: Int? = null): AppResult<ExploreDataDto> =
        syncInternal(clearLastSync = false, limit = limit)

    suspend fun syncFull(limit: Int? = null): AppResult<ExploreDataDto> =
        syncInternal(clearLastSync = true, limit = limit)

    /**
     * Hydrates the explore catalog cache from `/api/mobile/sync` (authoritative catalog source).
     * Returns merged cache; skips write when partial sync has no catalog delta.
     */
    fun applyFromSync(
        payload: MobileSyncDataDto,
        isFullSync: Boolean,
    ): ExploreDataDto {
        val incoming = SyncCatalogMapper.mapSyncPayloadToExploreSlice(payload)
        val hasCatalogDelta = incoming.workoutTemplates.isNotEmpty() ||
            incoming.programs.isNotEmpty() ||
            incoming.exercises.isNotEmpty() ||
            incoming.levels.isNotEmpty() ||
            incoming.deletedProgramIds.isNotEmpty() ||
            incoming.deletedWorkoutTemplateIds.isNotEmpty() ||
            incoming.deletedExerciseIds.isNotEmpty()

        if (!hasCatalogDelta && !isFullSync) {
            return readCached() ?: incoming
        }

        val merged = mergeExploreData(readCached(), incoming, isFullSync)
        MovitCachePolicy.writeJson(
            localStore(),
            MovitCacheKeys.EXPLORE_STORE,
            MovitCacheKeys.EXPLORE_DATA,
            merged,
            ExploreDataDto.serializer(),
        )
        memoizedExplore = merged
        return merged
    }

    /** P2.3: unify explore watermark with general sync timestamp. */
    fun writeExploreLastSync(timestamp: String) {
        if (timestamp.isBlank()) return
        localStore().writeJsonCache(
            MovitCacheKeys.EXPLORE_STORE,
            MovitCacheKeys.EXPLORE_LAST_SYNC,
            timestamp,
        )
    }

    private suspend fun syncInternal(clearLastSync: Boolean, limit: Int?): AppResult<ExploreDataDto> {
        val bindings = platform()
        val store = localStore()
        val cached = readCached()
        if (clearLastSync) {
            store.removeJsonCache(MovitCacheKeys.EXPLORE_STORE, MovitCacheKeys.EXPLORE_LAST_SYNC)
        }
        val updatedAfter = store.readJsonCache(MovitCacheKeys.EXPLORE_STORE, MovitCacheKeys.EXPLORE_LAST_SYNC)

        val response = api.fetchExplore(
            authorization = bindings.authHeader(),
            updatedAfter = updatedAfter,
            limit = limit,
        ).getOrElse { error ->
            return cached?.let { AppResult.Success(it) }
                ?: AppResult.Failure(error.message ?: "Explore sync failed.")
        }

        if (!response.success || response.data == null) {
            return cached?.let { AppResult.Success(it) }
                ?: AppResult.Failure(response.error ?: "Explore sync failed.")
        }

        val isFullSync = clearLastSync || response.meta?.isFullSync == true || updatedAfter == null
        val merged = mergeExploreData(cached, response.data!!, isFullSync)
        MovitCachePolicy.writeJson(
            store,
            MovitCacheKeys.EXPLORE_STORE,
            MovitCacheKeys.EXPLORE_DATA,
            merged,
            ExploreDataDto.serializer(),
        )
        if (response.timestamp.isNotBlank()) {
            store.writeJsonCache(
                MovitCacheKeys.EXPLORE_STORE,
                MovitCacheKeys.EXPLORE_LAST_SYNC,
                response.timestamp,
            )
        }

        return AppResult.Success(merged)
    }
}
