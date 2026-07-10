package com.movit.core.data.sync

import com.movit.core.data.audio.AudioPrefetchRunner
import com.movit.core.data.cache.AudioManifestCache
import com.movit.core.data.cache.JsonCacheMaintenance
import com.movit.core.data.cache.MessageLibraryCache
import com.movit.core.data.cache.MovitCacheDriftDetector
import com.movit.core.data.cache.MovitSyncMetadataStore
import com.movit.core.data.cache.SystemMessageCache
import com.movit.core.data.journal.SessionJournalStore
import com.movit.core.data.local.MovitLocalStore
import com.movit.core.data.outbox.LegacyWorkoutSyncGate
import com.movit.core.data.outbox.OfflineWriteQueue
import com.movit.core.data.outbox.OutboxReplayAcquisition
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex

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

        enum class ErrorKind {
            Network,
            Decode,
            Http,
            Unknown,
        }

        data class Error(
            val message: String,
            val kind: ErrorKind = ErrorKind.Unknown,
        ) : SyncOutcome()
    }

    data class ColdOfflineBundle(
        val home: HomeDataDto?,
        val explore: ExploreDataDto?,
        val reports: ReportsDashboardApiResponse?,
        val audioManifestFileCount: Int,
    )

    private val syncMutex = Mutex()
    private var lastSyncAttemptMs = 0L
    private val telemetry = MovitSyncTelemetry(localStore)

    private val _cacheInvalidated = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Emitted after a successful sync cycle (and by [notifyCacheInvalidated] for optimistic writes). */
    val cacheInvalidated: SharedFlow<Unit> = _cacheInvalidated.asSharedFlow()

    /** True while a sync cycle holds [syncMutex] (for ensure-wait / diagnostics). */
    val isSyncInProgress: Boolean
        get() = syncMutex.isLocked

    fun notifyCacheInvalidated() {
        _cacheInvalidated.tryEmit(Unit)
    }

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
            runSyncCycle(
                forceFullRefresh = false,
                reason = "syncIfNeeded",
                escalatedToFull = false,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            outcomeFromThrowable(
                error = e,
                reason = "syncIfNeeded",
                forceFullRefresh = false,
                updatedAfter = metadataStore.readLastSyncTimestamp(),
                escalatedToFull = false,
            )
        } finally {
            endSync()
        }
    }

    suspend fun fullRefresh(): SyncOutcome {
        if (TrainingSessionSyncGate.trainingSessionActive) {
            return SyncOutcome.Skipped
        }
        if (!beginSync()) return SyncOutcome.Skipped

        lastSyncAttemptMs = currentTimeMs()
        return try {
            runSyncCycle(
                forceFullRefresh = true,
                reason = "fullRefresh",
                escalatedToFull = false,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            outcomeFromThrowable(
                error = e,
                reason = "fullRefresh",
                forceFullRefresh = true,
                updatedAfter = null,
                escalatedToFull = false,
            )
        } finally {
            endSync()
        }
    }

    /**
     * Waits until any in-flight sync finishes (or [timeoutMs] elapses).
     * Used by TrainingConfigEnsure when [syncIfNeeded] returns Skipped because another cycle holds the lock.
     */
    suspend fun awaitSyncIdle(timeoutMs: Long = 15_000L) {
        val deadline = currentTimeMs() + timeoutMs
        while (syncMutex.isLocked && currentTimeMs() < deadline) {
            delay(50)
        }
    }

    private suspend fun runSyncCycle(
        forceFullRefresh: Boolean,
        reason: String,
        escalatedToFull: Boolean,
    ): SyncOutcome {
        val bindings = platform()
        val updatedAfter = if (forceFullRefresh) null else metadataStore.readLastSyncTimestamp()
        val auth = bindings.authHeader()
            ?: return readColdOfflineBundle().let { bundle ->
                val outcome = if (bundle.home != null || bundle.explore != null) {
                    SyncOutcome.Offline(bundle)
                } else {
                    SyncOutcome.Error("Sign in to sync.", SyncOutcome.ErrorKind.Unknown)
                }
                recordSyncCycle(
                    reason = reason,
                    isFull = forceFullRefresh,
                    updatedAfter = updatedAfter,
                    escalatedToFull = escalatedToFull,
                    outcome = outcome,
                )
                outcome
            }

        LegacyWorkoutSyncGate.drainLegacyExecutionsIfRegistered()
        offlineWrites.replayPending(OutboxReplayAcquisition.TrySkipIfBusy)

        val syncResponse = api.fetchSync(
            updatedAfter = updatedAfter,
            forceRefresh = forceFullRefresh,
            authorization = auth,
        ).getOrElse { error ->
            return outcomeFromFetchFailure(
                error = error,
                reason = reason,
                forceFullRefresh = forceFullRefresh,
                updatedAfter = updatedAfter,
                escalatedToFull = escalatedToFull,
            )
        }

        if (!syncResponse.success) {
            return outcomeFromFetchFailure(
                error = IllegalStateException(syncResponse.error ?: "Sync request failed."),
                reason = reason,
                forceFullRefresh = forceFullRefresh,
                updatedAfter = updatedAfter,
                escalatedToFull = escalatedToFull,
            )
        }

        if (!forceFullRefresh && needsTrainingConfigBackfill(syncResponse.meta)) {
            if (TrainingSessionSyncGate.trainingSessionActive) {
                // P2.11: defer full escalation during an active training session.
            } else {
                return runSyncCycle(
                    forceFullRefresh = true,
                    reason = reason,
                    escalatedToFull = true,
                )
            }
        }

        val drift = detectDrift(syncResponse, forceFullRefresh)
        if (!forceFullRefresh && drift != MovitCacheDriftDetector.DriftVerdict.Ok) {
            if (TrainingSessionSyncGate.trainingSessionActive) {
                // P2.11: defer full escalation during an active training session.
            } else {
                return runSyncCycle(
                    forceFullRefresh = true,
                    reason = reason,
                    escalatedToFull = true,
                )
            }
        }

        val audioFullSync = applyAudioManifest(syncResponse, forceFullRefresh)
        audioPrefetchRunner.afterManifestApplied(audioFullSync)

        val isFullSync = forceFullRefresh || syncResponse.meta?.isFullSync == true
        var exploreData = exploreSync.readCached()
        syncResponse.data?.let { payload ->
            val pendingPreferenceIds = if (payload.userExercisePreferences != null) {
                ExercisePreferenceLocalStore.pendingExerciseIdsFromOutbox(localStore)
            } else {
                emptySet()
            }
            val pendingReportIds = if (payload.plannedWorkoutReports.isNotEmpty()) {
                ReportsSyncRepository.pendingPlannedWorkoutIdsFromOutbox(localStore)
            } else {
                emptySet()
            }
            val pendingDayKeys = if (payload.userPrograms.any { it.customizations != null }) {
                DayCustomizationLocalStore.pendingDayKeysFromOutbox(localStore)
            } else {
                emptySet()
            }

            // Suspend hydrates cannot run inside non-suspend transaction {}.
            payload.userExercisePreferences?.let { preferences ->
                exercisePreferenceLocalStore.hydrateFromSync(preferences, pendingPreferenceIds)
            }
            if (payload.plannedWorkoutReports.isNotEmpty()) {
                reportsSync.hydrateFromSync(payload.plannedWorkoutReports, pendingReportIds)
            }
            payload.userPrograms.forEach { userProgram ->
                val userProgramId = userProgram.id.takeIf { it.isNotBlank() } ?: return@forEach
                if (userProgram.customizations != null) {
                    dayCustomizationLocalStore.hydrateFromBackend(
                        userProgramId = userProgramId,
                        customizations = userProgram.customizations,
                        serverCustomizationsUpdatedAt = userProgram.customizationsUpdatedAt,
                        pendingDayKeys = pendingDayKeys,
                    )
                }
            }

            // P2.7: atomic catalog/config/explore apply (SQLDelight transaction; InMemory passthrough).
            localStore.transaction {
                trainingConfig.applySyncExercises(
                    exercises = payload.exercises,
                    deletedExerciseIds = payload.deletedExerciseIds,
                    isFullSync = isFullSync,
                )
                if (isFullSync || payload.systemMessages.isNotEmpty()) {
                    systemMessageCache.save(payload.systemMessages)
                    systemMessageCache.loadIntoRegistry()
                }
                if (payload.userPrograms.isNotEmpty()) {
                    userProgramEnrollmentLocalStore.hydrateFromSync(
                        rows = payload.userPrograms,
                        isFullSync = isFullSync,
                    )
                }
                if (payload.messageLibrary.isNotEmpty()) {
                    if (isFullSync) {
                        messageLibraryCache.replaceFull(payload.messageLibrary)
                    } else {
                        messageLibraryCache.mergePartial(payload.messageLibrary)
                    }
                    trainingConfig.applySyncMessageLibrary(
                        messageLibrary = messageLibraryCache.read(),
                    )
                }
                exploreData = exploreSync.applyFromSync(payload, isFullSync)
                val catalogReport = catalogOffline.applyFromSync(payload, isFullSync)
                if (isFullSync && !catalogReport.isComplete) {
                    println(
                        "sync: catalog graph incomplete after full" +
                            " | missingWorkouts=${catalogReport.missingWorkoutTemplateIds.size}" +
                            " | missingExercises=${catalogReport.missingExerciseSlugs.size}",
                    )
                    telemetry.incrementCatalogGraphIncomplete()
                }
                if (syncResponse.timestamp.isNotBlank()) {
                    exploreSync.writeExploreLastSync(syncResponse.timestamp)
                }
            }
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

        updateLocalEntityCounts()
        LegacyWorkoutSyncGate.drainLegacyExecutionsIfRegistered()
        offlineWrites.replayPending(OutboxReplayAcquisition.TrySkipIfBusy)
        val activeWeek = (homeResult as? AppResult.Success)?.value?.trainMode?.activeProgram?.weekNumber
            ?: homeSync.readCached()?.trainMode?.activeProgram?.weekNumber
            ?: homeSync.readCached()?.trainMode?.weekCalendars?.firstOrNull { it.isCurrentWeek }?.weekNumber
        runCatching {
            val maintenance = JsonCacheMaintenance(localStore, platform = { bindings })
            maintenance.runAfterSuccessfulSync(activeWeekNumber = activeWeek)
            val protectedFrames = maintenance.protectedFrameCaptureSessionIds() +
                SessionJournalStore(localStore).listActiveCheckpoints().map { it.sessionId }
            bindings.cleanupOrphanFrameCaptures(protectedFrames)
        }

        val outcome = SyncOutcome.Success(
            home = (homeResult as? AppResult.Success)?.value ?: homeSync.readCached(),
            explore = exploreData,
            reports = (reportsResult as? AppResult.Success)?.value ?: reportsSync.readCachedDashboard(),
            isFullSync = isFullSync,
        )
        recordSyncCycle(
            reason = reason,
            isFull = isFullSync,
            updatedAfter = updatedAfter,
            escalatedToFull = escalatedToFull,
            outcome = outcome,
        )
        notifyCacheInvalidated()
        return outcome
    }

    private fun outcomeFromFetchFailure(
        error: Throwable,
        reason: String,
        forceFullRefresh: Boolean,
        updatedAfter: String?,
        escalatedToFull: Boolean,
    ): SyncOutcome = outcomeFromThrowable(
        error = error,
        reason = reason,
        forceFullRefresh = forceFullRefresh,
        updatedAfter = updatedAfter,
        escalatedToFull = escalatedToFull,
    )

    private fun outcomeFromThrowable(
        error: Throwable,
        reason: String,
        forceFullRefresh: Boolean,
        updatedAfter: String?,
        escalatedToFull: Boolean,
    ): SyncOutcome {
        val kind = classifySyncFailure(error)
        val outcome = when (kind) {
            SyncFailureKind.Network -> offlineOutcomeOrError(error)
            SyncFailureKind.Decode -> {
                telemetry.incrementSyncErrorDecode()
                SyncOutcome.Error(
                    message = error.message ?: "Sync response could not be decoded.",
                    kind = SyncOutcome.ErrorKind.Decode,
                )
            }
            SyncFailureKind.Http -> SyncOutcome.Error(
                message = error.message ?: "Sync request failed.",
                kind = SyncOutcome.ErrorKind.Http,
            )
            SyncFailureKind.Unknown -> SyncOutcome.Error(
                message = error.message ?: "Sync failed.",
                kind = SyncOutcome.ErrorKind.Unknown,
            )
        }
        recordSyncCycle(
            reason = reason,
            isFull = forceFullRefresh,
            updatedAfter = updatedAfter,
            escalatedToFull = escalatedToFull,
            outcome = outcome,
        )
        return outcome
    }

    private fun offlineOutcomeOrError(error: Throwable): SyncOutcome {
        val bundle = readColdOfflineBundle()
        return if (bundle.home != null || bundle.explore != null) {
            SyncOutcome.Offline(bundle)
        } else {
            SyncOutcome.Error(
                message = error.message ?: "Network unavailable.",
                kind = SyncOutcome.ErrorKind.Network,
            )
        }
    }

    private fun recordSyncCycle(
        reason: String,
        isFull: Boolean?,
        updatedAfter: String?,
        escalatedToFull: Boolean,
        outcome: SyncOutcome,
    ) {
        telemetry.recordSyncCycle(
            LastSyncCycleRecord(
                reason = reason,
                isFull = isFull,
                updatedAfter = updatedAfter,
                escalatedToFull = escalatedToFull,
                outcome = outcome.telemetryLabel(),
            ),
        )
    }

    private fun SyncOutcome.telemetryLabel(): String = when (this) {
        is SyncOutcome.Success -> "success"
        is SyncOutcome.Offline -> "offline_network"
        SyncOutcome.Skipped -> "skipped"
        is SyncOutcome.Error -> when (kind) {
            SyncOutcome.ErrorKind.Network -> "error_network"
            SyncOutcome.ErrorKind.Decode -> "error_decode"
            SyncOutcome.ErrorKind.Http -> "error_http"
            SyncOutcome.ErrorKind.Unknown -> "error_unknown"
        }
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

    private fun updateLocalEntityCounts() {
        // P2.7: count from catalogOffline indexes (source of truth), not explore blob (H12).
        metadataStore.writeEntityCounts(
            MovitCacheDriftDetector.EntityCounts(
                exercises = trainingConfig.allCachedSlugs().size,
                workouts = catalogOffline.allWorkoutTemplateIds().size,
                programs = catalogOffline.allProgramIds().size,
            ),
        )
    }

    private fun shouldSkipSync(minIntervalMs: Long): Boolean {
        if (lastSyncAttemptMs == 0L) return false
        return currentTimeMs() - lastSyncAttemptMs < minIntervalMs
    }

    /** Atomic try-lock — prevents concurrent sync cycles (H14). */
    private fun beginSync(): Boolean = syncMutex.tryLock()

    private fun endSync() {
        if (syncMutex.isLocked) syncMutex.unlock()
    }

    companion object {
        const val DEFAULT_MIN_SYNC_INTERVAL_MS = 5 * 60 * 1000L
    }
}

internal expect fun currentTimeMs(): Long
