package com.movit.core.data.sync

import com.movit.core.data.cache.AudioManifestCache
import com.movit.core.data.cache.MovitCacheDriftDetector
import com.movit.core.data.cache.MovitSyncMetadataStore
import com.movit.core.data.local.MovitLocalStore
import com.movit.core.data.platform.MovitPlatformBindings
import com.movit.core.data.repository.ExploreSyncRepository
import com.movit.core.data.repository.HomeSyncRepository
import com.movit.core.data.repository.PlanSyncRepository
import com.movit.core.data.repository.ReportsSyncRepository
import com.movit.core.network.MovitMobileApi
import com.movit.core.network.dto.ExploreDataDto
import com.movit.core.network.dto.HomeDataDto
import com.movit.core.network.dto.MobileSyncApiResponse
import com.movit.core.network.dto.ReportsDashboardApiResponse
import com.movit.shared.AppResult

class MovitSyncOrchestrator(
    private val api: MovitMobileApi,
    private val platform: () -> MovitPlatformBindings,
    private val localStore: MovitLocalStore,
    private val homeSync: HomeSyncRepository,
    private val exploreSync: ExploreSyncRepository,
    private val reportsSync: ReportsSyncRepository,
    private val planSync: PlanSyncRepository,
    private val metadataStore: MovitSyncMetadataStore,
    private val audioManifestCache: AudioManifestCache,
) {
    sealed class SyncOutcome {
        data class Success(
            val home: HomeDataDto?,
            val explore: ExploreDataDto?,
            val reports: ReportsDashboardApiResponse?,
            val isFullSync: Boolean,
        ) : SyncOutcome()

        data class Offline(
            val bundle: ColdOfflineBundle,
        ) : SyncOutcome()

        data object Skipped : SyncOutcome()

        data class Error(val message: String) : SyncOutcome()
    }

    data class ColdOfflineBundle(
        val home: HomeDataDto?,
        val explore: ExploreDataDto?,
        val reports: ReportsDashboardApiResponse?,
        val audioManifestFileCount: Int,
    )

    private var syncBusy = false
    private var lastSyncAttemptMs = 0L

    fun readColdOfflineBundle(): ColdOfflineBundle =
        ColdOfflineBundle(
            home = homeSync.readCached(),
            explore = exploreSync.readCached(),
            reports = reportsSync.readCachedDashboard(),
            audioManifestFileCount = audioManifestCache.read()?.manifest?.files?.size ?: 0,
        )

    suspend fun syncIfNeeded(
        forceCheck: Boolean = false,
        minIntervalMs: Long = DEFAULT_MIN_SYNC_INTERVAL_MS,
    ): SyncOutcome {
        if (!forceCheck && shouldSkipSync(minIntervalMs)) return SyncOutcome.Skipped
        if (!beginSync()) return SyncOutcome.Skipped

        lastSyncAttemptMs = currentTimeMs()
        return try {
            runSyncCycle(forceFullRefresh = false)
        } catch (_: Throwable) {
            SyncOutcome.Offline(readColdOfflineBundle())
        } finally {
            endSync()
        }
    }

    suspend fun fullRefresh(): SyncOutcome {
        if (!beginSync()) return SyncOutcome.Skipped

        lastSyncAttemptMs = currentTimeMs()
        return try {
            runSyncCycle(forceFullRefresh = true)
        } catch (_: Throwable) {
            SyncOutcome.Offline(readColdOfflineBundle())
        } finally {
            endSync()
        }
    }

    private suspend fun runSyncCycle(forceFullRefresh: Boolean): SyncOutcome {
        val bindings = platform()
        val auth = bindings.authHeader()
            ?: return readColdOfflineBundle().let { bundle ->
                if (bundle.home != null || bundle.explore != null) {
                    SyncOutcome.Offline(bundle)
                } else {
                    SyncOutcome.Error("Sign in to sync.")
                }
            }

        val syncResponse = api.fetchSync(
            updatedAfter = if (forceFullRefresh) null else metadataStore.readLastSyncTimestamp(),
            forceRefresh = forceFullRefresh,
            authorization = auth,
        ).getOrElse {
            return SyncOutcome.Offline(readColdOfflineBundle())
        }

        if (!syncResponse.success) {
            return SyncOutcome.Offline(readColdOfflineBundle())
        }

        val drift = detectDrift(syncResponse, forceFullRefresh)
        if (!forceFullRefresh && drift != MovitCacheDriftDetector.DriftVerdict.Ok) {
            return runSyncCycle(forceFullRefresh = true)
        }

        applyAudioManifest(syncResponse, forceFullRefresh)
        metadataStore.writeFromSyncMeta(syncResponse.meta)
        if (syncResponse.timestamp.isNotBlank()) {
            metadataStore.writeLastSyncTimestamp(syncResponse.timestamp)
        }

        planSync.refreshActiveUserProgramId()

        val exploreResult = if (forceFullRefresh) exploreSync.syncFull() else exploreSync.sync()
        val homeResult = homeSync.sync()
        val reportsResult = if (bindings.isProUser()) reportsSync.syncDashboard() else null

        updateLocalEntityCounts(exploreSync.readCached())

        return SyncOutcome.Success(
            home = (homeResult as? AppResult.Success)?.value ?: homeSync.readCached(),
            explore = (exploreResult as? AppResult.Success)?.value ?: exploreSync.readCached(),
            reports = (reportsResult as? AppResult.Success)?.value ?: reportsSync.readCachedDashboard(),
            isFullSync = forceFullRefresh || syncResponse.meta?.isFullSync == true,
        )
    }

    private fun detectDrift(
        response: MobileSyncApiResponse,
        isFullSync: Boolean,
    ): MovitCacheDriftDetector.DriftVerdict {
        val meta = response.meta
        val hasNoEntityDelta = meta != null &&
            meta.exercisesInResponse == 0 &&
            meta.workoutTemplatesInResponse == 0 &&
            meta.programsInResponse == 0

        val entityDrift = MovitCacheDriftDetector.detectEntityDrift(
            local = metadataStore.readEntityCounts(),
            meta = meta,
            hasNoEntityDelta = hasNoEntityDelta,
        )
        if (entityDrift == MovitCacheDriftDetector.DriftVerdict.NeedsFullRefresh && !isFullSync) {
            return entityDrift
        }

        val messageDrift = MovitCacheDriftDetector.detectMessageStatsDrift(
            cached = metadataStore.readMessageStats(),
            server = meta?.messageLibraryStats,
        )
        return if (messageDrift == MovitCacheDriftDetector.DriftVerdict.MessageStatsMismatch && !isFullSync) {
            messageDrift
        } else {
            MovitCacheDriftDetector.DriftVerdict.Ok
        }
    }

    private fun applyAudioManifest(response: MobileSyncApiResponse, isFullSync: Boolean) {
        val manifest = response.data?.audioManifest ?: return
        if (!isFullSync && manifest.files.isEmpty()) return

        val base = AudioManifestCache.resolveEffectiveAudioBase(
            apiBaseUrl = platform().apiBaseUrl(),
            manifest = manifest,
        )
        if (isFullSync) {
            audioManifestCache.replaceFull(base, manifest)
        } else {
            audioManifestCache.mergePartial(base, manifest)
        }
    }

    private fun updateLocalEntityCounts(explore: ExploreDataDto?) {
        if (explore == null) return
        metadataStore.writeEntityCounts(
            MovitCacheDriftDetector.EntityCounts(
                exercises = explore.exercises.size,
                workouts = explore.workoutTemplates.size,
                programs = explore.programs.size,
            ),
        )
    }

    private fun shouldSkipSync(minIntervalMs: Long): Boolean {
        if (lastSyncAttemptMs == 0L) return false
        return currentTimeMs() - lastSyncAttemptMs < minIntervalMs
    }

    private fun beginSync(): Boolean {
        if (syncBusy) return false
        syncBusy = true
        return true
    }

    private fun endSync() {
        syncBusy = false
    }

    companion object {
        const val DEFAULT_MIN_SYNC_INTERVAL_MS = 5 * 60 * 1000L
    }
}

internal expect fun currentTimeMs(): Long
