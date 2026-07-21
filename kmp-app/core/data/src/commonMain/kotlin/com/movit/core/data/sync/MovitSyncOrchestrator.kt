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
import com.movit.core.network.dto.SyncMessageTemplateDto
import com.movit.core.network.dto.SyncMetaDto
import com.movit.shared.AppResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
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
    private val syncStatusBus: SyncStatusBus? = null,
  /** P2 / Option 2: deferred message-library writes (delta + warm cache). Tests may pass [runBlocking]. */
    private val deferredApplyScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
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
    /** B3: after HTTP 5xx, suppress forceCheck sync attempts until this epoch ms. */
    private var httpErrorCooldownUntilMs = 0L
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
        /** Manual "Sync now" / fullRefresh passes true to bypass the 5xx cooldown. */
        bypassHttpCooldown: Boolean = false,
    ): SyncOutcome {
        if (!forceCheck && shouldSkipSync(minIntervalMs)) return SyncOutcome.Skipped
        // B3: cooldown after 5xx — ensure forceCheck still respects it; profile Sync now uses fullRefresh.
        if (!bypassHttpCooldown && isInHttpErrorCooldown()) return SyncOutcome.Skipped
        if (!beginSync()) return SyncOutcome.Skipped

        lastSyncAttemptMs = currentTimeMs()
        syncStatusBus?.onSyncStarted()
        return try {
            val outcome = runSyncCycle(
                forceFullRefresh = false,
                reason = "syncIfNeeded",
                escalatedToFull = false,
            )
            syncStatusBus?.onSyncFinished(outcome)
            outcome
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val outcome = outcomeFromThrowable(
                error = e,
                reason = "syncIfNeeded",
                forceFullRefresh = false,
                updatedAfter = metadataStore.readLastSyncTimestamp(),
                escalatedToFull = false,
            )
            syncStatusBus?.onSyncFinished(outcome)
            outcome
        } finally {
            endSync()
        }
    }

    suspend fun fullRefresh(): SyncOutcome {
        if (TrainingSessionSyncGate.trainingSessionActive) {
            return SyncOutcome.Skipped
        }
        // Manual sync clears HTTP cooldown (B3 exception for profile "Sync all now").
        httpErrorCooldownUntilMs = 0L
        if (!beginSync()) return SyncOutcome.Skipped

        lastSyncAttemptMs = currentTimeMs()
        syncStatusBus?.onSyncStarted()
        return try {
            val outcome = runSyncCycle(
                forceFullRefresh = true,
                reason = "fullRefresh",
                escalatedToFull = false,
            )
            syncStatusBus?.onSyncFinished(outcome)
            outcome
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val outcome = outcomeFromThrowable(
                error = e,
                reason = "fullRefresh",
                forceFullRefresh = true,
                updatedAfter = null,
                escalatedToFull = false,
            )
            syncStatusBus?.onSyncFinished(outcome)
            outcome
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

            // P2 / Option 2: critical path only inside one DB transaction — explore, configs,
            // catalog exports, system messages, user slices. Message library (~2.6k JSON) is
            // applied after the transaction closes (full sync) or deferred after Success (delta).
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

            val libraryPayload = payload.messageLibrary
            val deferLibrary = shouldDeferMessageLibraryApply(isFullSync, libraryPayload)
            if (libraryPayload.isNotEmpty() && !deferLibrary) {
                applyMessageLibraryFromSync(libraryPayload, isFullSync)
                writeMessageLibraryStatsFromSync(syncResponse, libraryPayload)
            }
        }

        metadataStore.writeFromSyncMeta(syncResponse.meta)
        if (syncResponse.timestamp.isNotBlank()) {
            metadataStore.writeLastSyncTimestamp(syncResponse.timestamp)
        }
        val deferredLibrary = syncResponse.data?.messageLibrary
            ?.takeIf { it.isNotEmpty() && shouldDeferMessageLibraryApply(isFullSync, it) }
        if (deferredLibrary != null) {
            scheduleDeferredMessageLibraryApply(syncResponse, deferredLibrary, isFullSync)
        } else if (syncResponse.data?.messageLibrary.isNullOrEmpty()) {
            syncResponse.meta?.messageLibraryStats?.let(metadataStore::writeMessageStats)
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
        if (outcome is SyncOutcome.Error && outcome.kind == SyncOutcome.ErrorKind.Http) {
            // B3: 30–60s cooldown after 5xx/HTTP errors from sync (45s mid-point).
            httpErrorCooldownUntilMs = currentTimeMs() + HTTP_ERROR_COOLDOWN_MS
        }
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

    /**
     * Full sync / empty local library must block until persisted (bootstrap CoreReady).
     * Delta with warm cache can defer — merge-on-read keeps exercise configs correct.
     */
    private fun shouldDeferMessageLibraryApply(
        isFullSync: Boolean,
        incoming: List<SyncMessageTemplateDto>,
    ): Boolean {
        if (incoming.isEmpty()) return false
        if (isFullSync || messageLibraryCache.read().isEmpty()) return false
        return true
    }

    private fun applyMessageLibraryFromSync(
        templates: List<SyncMessageTemplateDto>,
        isFullSync: Boolean,
    ) {
        if (templates.isEmpty()) return
        if (isFullSync) {
            messageLibraryCache.replaceFull(templates)
        } else {
            messageLibraryCache.mergePartial(templates)
        }
        // F5 / Splash hang: do NOT eagerly rewrite every exercise config here.
        // Merge-on-read covers correctness without N× full-library rewrites.
    }

    private fun writeMessageLibraryStatsFromSync(
        syncResponse: MobileSyncApiResponse,
        library: List<SyncMessageTemplateDto>,
    ) {
        syncResponse.meta?.messageLibraryStats?.let(metadataStore::writeMessageStats)
            ?: library.takeIf { it.isNotEmpty() }?.let { templates ->
                metadataStore.writeMessageStats(
                    trainingConfig.computeMessageLibraryStats(
                        messageLibrary = templates,
                        assignmentsInCachedExercises = trainingConfig.countMessageAssignmentsInCache(),
                    ),
                )
            }
    }

    private fun scheduleDeferredMessageLibraryApply(
        syncResponse: MobileSyncApiResponse,
        templates: List<SyncMessageTemplateDto>,
        isFullSync: Boolean,
    ) {
        deferredApplyScope.launch {
            applyMessageLibraryFromSync(templates, isFullSync)
            writeMessageLibraryStatsFromSync(syncResponse, templates)
            notifyCacheInvalidated()
        }
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

    private fun isInHttpErrorCooldown(): Boolean =
        httpErrorCooldownUntilMs > 0L && currentTimeMs() < httpErrorCooldownUntilMs

    /** Atomic try-lock — prevents concurrent sync cycles (H14). */
    private fun beginSync(): Boolean = syncMutex.tryLock()

    private fun endSync() {
        if (syncMutex.isLocked) syncMutex.unlock()
    }

    companion object {
        const val DEFAULT_MIN_SYNC_INTERVAL_MS = 5 * 60 * 1000L
        /** B3: mid-point of 30–60s after HTTP sync failures. */
        const val HTTP_ERROR_COOLDOWN_MS = 45_000L
    }
}

internal expect fun currentTimeMs(): Long
