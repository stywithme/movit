package com.movit.core.data.readiness

import com.movit.core.data.audio.AudioPrefetchRunner
import com.movit.core.data.image.ImageCacheCoverage
import com.movit.core.data.image.ImagePrefetchRunner
import com.movit.core.data.repository.ExploreSyncRepository
import com.movit.core.data.repository.HomeSyncRepository
import com.movit.core.data.repository.PlanSyncRepository
import com.movit.core.data.sync.SyncStatusBus

/**
 * Fire-and-forget background media warmup after bootstrap (R7).
 *
 * Audio via [AudioPrefetchRunner]; images via [ImagePrefetchRunner], which downloads the **whole**
 * catalog image set to durable storage. The previous implementation only enqueued the first 24
 * explore cover URLs into the Coil disk cache, leaving most exercise thumbnails — and every
 * pose-position frame — unavailable offline.
 */
class BackgroundMediaPrefetcher(
    private val audioPrefetch: AudioPrefetchRunner,
    private val imagePrefetch: ImagePrefetchRunner,
    private val exploreSync: ExploreSyncRepository,
    private val homeSync: HomeSyncRepository,
    private val planSync: PlanSyncRepository,
    private val syncStatusBus: SyncStatusBus,
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
            imagePrefetch.prefetchCatalog()
        } finally {
            syncStatusBus.onPrefetchFinished()
        }
    }

    /** Offline-readiness readout for the media warmup: how much of the catalog is on disk. */
    fun imageCoverage(): ImageCacheCoverage = imagePrefetch.coverage()

    /** Cover of the active enrollment — cheap check for callers that only care about it. */
    fun activeProgramCoverImageUrl(): String? {
        val activeProgramId = homeSync.readCached()?.trainMode?.activeProgram?.id
            ?: planSync.readCachedActiveUserProgramId()
        if (activeProgramId.isNullOrBlank()) return null
        return exploreSync.readCached()
            ?.programs
            ?.firstOrNull { it.id == activeProgramId }
            ?.coverImageUrl
            ?.takeIf { it.isNotBlank() }
    }
}

/** Shell/platform sets this once; default no-op keeps commonMain tests simple. */
object MovitDataImageWarmup {
    var warmup: (List<String>) -> Unit = {}
}
