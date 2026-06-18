package com.movit.core.data.sync

import com.movit.core.data.audio.AudioPrefetchRunner
import com.movit.core.data.cache.AudioManifestCache
import com.movit.core.data.cache.MessageLibraryCache
import com.movit.core.data.cache.MovitCacheDriftDetector
import com.movit.core.data.cache.MovitSyncMetadataStore
import com.movit.core.data.cache.SystemMessageCache
import com.movit.core.data.local.MovitLocalStore
import com.movit.core.data.outbox.LegacyWorkoutSyncGate
import com.movit.core.data.outbox.OfflineWriteQueue
import com.movit.core.data.platform.MovitPlatformBindings
import com.movit.core.data.repository.DayCustomizationLocalStore
import com.movit.core.data.repository.ExercisePreferenceLocalStore
import com.movit.core.data.repository.ExploreSyncRepository
import com.movit.core.data.repository.HomeSyncRepository
import com.movit.core.data.repository.PlanSyncRepository
import com.movit.core.data.repository.ReportsSyncRepository
import com.movit.core.data.repository.SyncCatalogOfflineRepository
import com.movit.core.data.repository.TrainingConfigRepository
import com.movit.core.data.repository.UserProgramEnrollmentLocalStore
import com.movit.core.network.MovitMobileApi
import com.movit.core.network.dto.ExploreDataDto
import com.movit.core.network.dto.HomeDataDto
import com.movit.core.network.dto.MobileSyncApiResponse
import com.movit.core.network.dto.ReportsDashboardApiResponse
import com.movit.core.network.dto.SyncMetaDto
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
    private val audioPrefetchRunner: AudioPrefetchRunner,
    private val offlineWrites: OfflineWriteQueue,
    private val trainingConfig: TrainingConfigRepository,
    private val catalogOffline: SyncCatalogOfflineRepository,
    private val systemMessageCache: SystemMessageCache,
    private val exercisePreferenceLocalStore: ExercisePreferenceLocalStore,
    private val dayCustomizationLocalStore: DayCustomizationLocalStore,
    private val messageLibraryCache: MessageLibraryCache,
    private val userProgramEnrollmentLocalStore: UserProgramEnrollmentLocalStore,
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

        if (!forceFullRefresh && needsTrainingConfigBackfill(syncResponse.meta)) {
            return runSyncCycle(forceFullRefresh = true)
        }

        val drift = detectDrift(syncResponse, forceFullRefresh)
        if (!forceFullRefresh && drift != MovitCacheDriftDetector.DriftVerdict.Ok) {
            return runSyncCycle(forceFullRefresh = true)
        }

        val audioFullSync = applyAudioManifest(syncResponse, forceFullRefresh)
        audioPrefetchRunner.afterManifestApplied(audioFullSync)

        val isFullSync = forceFullRefresh || syncResponse.meta?.isFullSync == true
        var exploreData = exploreSync.readCached()
        syncResponse.data?.let { payload ->
            trainingConfig.applySyncExercises(
                exercises = payload.exercises,
                deletedExerciseIds = payload.deletedExerciseIds,
                isFullSync = isFullSync,
            )

            val systemMessages = payload.systemMessages
            if (systemMessages.isNotEmpty()) {
                systemMessageCache.save(systemMessages)
                systemMessageCache.loadIntoRegistry()
            }

            payload.userExercisePreferences?.let { preferences ->
                val pendingIds = ExercisePreferenceLocalStore.pendingExerciseIdsFromOutbox(localStore)
                exercisePreferenceLocalStore.hydrateFromSync(preferences, pendingIds)
            }

            if (payload.plannedWorkoutReports.isNotEmpty()) {
                reportsSync.hydrateFromSync(payload.plannedWorkoutReports)
            }

            if (payload.userPrograms.isNotEmpty()) {
                userProgramEnrollmentLocalStore.hydrateFromSync(
                    rows = payload.userPrograms,
                    isFullSync = isFullSync,
                )
            }

            payload.userPrograms.forEach { userProgram ->
                val userProgramId = userProgram.id.takeIf { it.isNotBlank() } ?: return@forEach
                if (userProgram.customizations != null) {
                    dayCustomizationLocalStore.hydrateFromBackend(
                        userProgramId = userProgramId,
                        customizations = userProgram.customizations,
                        serverCustomizationsUpdatedAt = userProgram.customizationsUpdatedAt,
                    )
                }
            }

            if (payload.messageLibrary.isNotEmpty()) {
                if (isFullSync) {
                    messageLibraryCache.replaceFull(payload.messageLibrary)
                } else {
                    messageLibraryCache.mergePartial(payload.messageLibrary)
                }
                trainingConfig.applySyncMessageLibrary(
                    messageLibrary = payload.messageLibrary,
                )
            }

            exploreData = exploreSync.applyFromSync(payload, isFullSync)
            catalogOffline.applyFromSync(payload, isFullSync)
        }

        metadataStore.writeFromSyncMeta(syncResponse.meta)
        if (syncResponse.timestamp.isNotBlank()) {
            metadataStore.writeLastSyncTimestamp(syncResponse.timestamp)
        }
        syncResponse.meta?.messageLibraryStats?.let(metadataStore::writeMessageStats)
            ?: syncResponse.data?.messageLibrary?.takeIf { it.isNotEmpty() }?.let { library ->
                metadataStore.writeMessageStats(
                    trainingConfig.computeMessageLibraryStats(
                        messageLibrary = library,
                        assignmentsInCachedExercises = trainingConfig.countMessageAssignmentsInCache(),
                    ),
                )
            }

        planSync.refreshActiveUserProgramId()

        val homeResult = homeSync.sync()
        val reportsResult = if (bindings.isProUser()) reportsSync.syncDashboard() else null

        updateLocalEntityCounts(exploreData)
        LegacyWorkoutSyncGate.drainLegacyExecutionsIfRegistered()
        offlineWrites.replayPending()

        return SyncOutcome.Success(
            home = (homeResult as? AppResult.Success)?.value ?: homeSync.readCached(),
            explore = exploreData,
            reports = (reportsResult as? AppResult.Success)?.value ?: reportsSync.readCachedDashboard(),
            isFullSync = isFullSync,
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

    private suspend fun applyAudioManifest(response: MobileSyncApiResponse, isFullSync: Boolean): Boolean {
        val manifest = response.data?.audioManifest ?: return false
        if (!isFullSync && manifest.files.isEmpty()) return false

        val base = AudioManifestCache.resolveEffectiveAudioBase(
            apiBaseUrl = platform().apiBaseUrl(),
            manifest = manifest,
        )
        if (isFullSync) {
            audioManifestCache.replaceFull(base, manifest)
        } else {
            audioManifestCache.mergePartial(base, manifest)
        }
        return isFullSync
    }

    private fun needsTrainingConfigBackfill(meta: SyncMetaDto?): Boolean {
        if (meta == null) return false
        val serverExercises = meta.totalExercises
        if (serverExercises <= 0) return false
        return trainingConfig.allCachedSlugs().isEmpty()
    }

    private fun updateLocalEntityCounts(explore: ExploreDataDto?) {
        metadataStore.writeEntityCounts(
            MovitCacheDriftDetector.EntityCounts(
                exercises = trainingConfig.allCachedSlugs().size,
                workouts = explore?.workoutTemplates?.size ?: 0,
                programs = explore?.programs?.size ?: 0,
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
