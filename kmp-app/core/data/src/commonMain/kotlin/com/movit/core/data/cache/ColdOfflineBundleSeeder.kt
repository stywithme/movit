package com.movit.core.data.cache

import com.movit.core.data.local.MovitLocalStore
import com.movit.core.data.repository.ExploreSyncRepository
import com.movit.core.data.repository.HomeSyncRepository
import com.movit.core.data.repository.MovitCacheKeys
import com.movit.core.data.repository.TrainingConfigRepository
import com.movit.core.network.MovitJson
import com.movit.core.network.dto.BundledColdOfflineDto
import com.movit.core.network.dto.ExploreDataDto
import com.movit.core.network.dto.HomeDataDto
import com.movit.resources.readBundledColdOfflineJson

/**
 * Seeds local caches from the bundled cold-start JSON when no network cache exists yet.
 * Feeds [com.movit.core.data.sync.MovitSyncOrchestrator.readColdOfflineBundle] on first install offline.
 *
 * The bundled JSON ships real catalog/training config + system messages only; [BundledColdOfflineDto.home]
 * is intentionally null because home dashboard data is user-specific and must come from sync.
 */
class ColdOfflineBundleSeeder(
    private val localStore: MovitLocalStore,
    private val homeSync: HomeSyncRepository,
    private val exploreSync: ExploreSyncRepository,
    private val systemMessageCache: SystemMessageCache,
    private val trainingConfig: TrainingConfigRepository,
    private val messageLibraryCache: MessageLibraryCache,
    private val bundleJsonProvider: suspend () -> String = { readBundledColdOfflineJson() },
) {
    suspend fun seedIfNeeded(): Boolean {
        val homeMissing = homeSync.readCached() == null
        val exploreMissing = exploreSync.readCached() == null
        val messagesMissing = systemMessageCache.read().isEmpty()
        val trainingConfigMissing = trainingConfig.allCachedSlugs().isEmpty()
        val messageLibraryMissing = messageLibraryCache.read().isEmpty()
        if (
            !homeMissing &&
            !exploreMissing &&
            !messagesMissing &&
            !trainingConfigMissing &&
            !messageLibraryMissing
        ) {
            return false
        }

        val bundle = runCatching {
            MovitJson.decodeFromString(
                BundledColdOfflineDto.serializer(),
                bundleJsonProvider(),
            )
        }.getOrNull() ?: return false

        var seeded = false
        if (homeMissing) {
            bundle.home?.let {
                MovitCachePolicy.writeJson(
                    localStore,
                    MovitCacheKeys.HOME_STORE,
                    MovitCacheKeys.HOME_DATA,
                    it,
                    HomeDataDto.serializer(),
                )
                seeded = true
            }
        }
        if (exploreMissing) {
            bundle.explore?.let {
                MovitCachePolicy.writeJson(
                    localStore,
                    MovitCacheKeys.EXPLORE_STORE,
                    MovitCacheKeys.EXPLORE_DATA,
                    it,
                    ExploreDataDto.serializer(),
                )
                seeded = true
            }
        }
        if (trainingConfigMissing && bundle.exercises.isNotEmpty()) {
            trainingConfig.applySyncExercises(
                exercises = bundle.exercises,
                isFullSync = true,
            )
            seeded = true
        }
        if (messageLibraryMissing && bundle.messageLibrary.isNotEmpty()) {
            messageLibraryCache.replaceFull(bundle.messageLibrary)
            seeded = true
        }
        if (bundle.messageLibrary.isNotEmpty() && trainingConfig.allCachedSlugs().isNotEmpty()) {
            trainingConfig.applySyncMessageLibrary(bundle.messageLibrary)
        }
        if (messagesMissing && bundle.systemMessages.isNotEmpty()) {
            systemMessageCache.save(bundle.systemMessages)
            seeded = true
        }
        return seeded
    }
}
