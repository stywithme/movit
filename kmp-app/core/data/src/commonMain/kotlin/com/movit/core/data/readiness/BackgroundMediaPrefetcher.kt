package com.movit.core.data.readiness

import com.movit.core.data.audio.AudioPrefetchRunner
import com.movit.core.data.repository.ExploreSyncRepository
import com.movit.core.data.repository.HomeSyncRepository
import com.movit.core.data.repository.PlanSyncRepository
import com.movit.core.data.sync.SyncStatusBus

/**
 * Fire-and-forget background media warmup after bootstrap (R7).
 * Audio via [AudioPrefetchRunner]; images via platform Coil when available.
 */
class BackgroundMediaPrefetcher(
    private val audioPrefetch: AudioPrefetchRunner,
    private val exploreSync: ExploreSyncRepository,
    private val homeSync: HomeSyncRepository,
    private val planSync: PlanSyncRepository,
    private val syncStatusBus: SyncStatusBus,
    private val imageWarmup: (List<String>) -> Unit = { urls ->
        MovitDataImageWarmup.warmup(urls)
    },
) {
    suspend fun runAfterBootstrap() {
        syncStatusBus.onPrefetchStarted()
        try {
            val exerciseSlugs = exploreSync.readCached()
                ?.exercises
                ?.mapNotNull { it.slug.takeIf { slug -> slug.isNotBlank() } }
                .orEmpty()
            audioPrefetch.afterManifestApplied(isFullSync = false)
            if (exerciseSlugs.isNotEmpty()) {
                audioPrefetch.prefetchForTargets(
                    com.movit.core.data.audio.EntityAudioManifestFetcher.Targets(
                        exerciseSlugs = exerciseSlugs.take(12),
                    ),
                    isFullSync = false,
                )
            }
            imageWarmup(collectCatalogImageUrls())
        } finally {
            syncStatusBus.onPrefetchFinished()
        }
    }

    private fun collectCatalogImageUrls(): List<String> {
        val urls = linkedSetOf<String>()
        exploreSync.readCached()?.exercises?.forEach { ex ->
            ex.imageUrl?.takeIf { it.isNotBlank() }?.let(urls::add)
        }
        exploreSync.readCached()?.workoutTemplates?.forEach { wt ->
            wt.coverImageUrl?.takeIf { it.isNotBlank() }?.let(urls::add)
        }
        exploreSync.readCached()?.programs?.forEach { prog ->
            prog.coverImageUrl?.takeIf { it.isNotBlank() }?.let(urls::add)
        }
        val activeProgramId = homeSync.readCached()?.trainMode?.activeProgram?.id
            ?: planSync.readCachedActiveUserProgramId()
        if (!activeProgramId.isNullOrBlank()) {
            exploreSync.readCached()?.programs
                ?.firstOrNull { it.id == activeProgramId }
                ?.coverImageUrl
                ?.takeIf { it.isNotBlank() }
                ?.let(urls::add)
        }
        return urls.take(24).toList()
    }
}

/** Shell/platform sets this once; default no-op keeps commonMain tests simple. */
object MovitDataImageWarmup {
    var warmup: (List<String>) -> Unit = {}
}
