package com.movit.core.data.outbox

import com.movit.core.data.local.MovitLocalStore
import com.movit.core.data.platform.MovitPlatformBindings
import com.movit.core.data.sync.MovitSyncTelemetry
import com.movit.core.network.MovitClock
import com.movit.core.network.MovitJson
import com.movit.core.network.MovitMobileApi
import com.movit.core.network.dto.PlannedWorkoutCompleteRequestDto
import com.movit.core.network.dto.PlannedWorkoutStartRequestDto
import com.movit.core.network.dto.ProgressionMarkSeenRequest
import com.movit.core.network.dto.UserExercisePreferenceUpsertRequest
import com.movit.core.network.dto.UserProgramOverrideCreateRequest
import com.movit.core.network.dto.UserProgramUpdateRequest
import com.movit.core.network.dto.WorkoutExecutionUploadRequestDto
import kotlin.random.Random
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** How [OfflineWriteQueue.replayPending] acquires the shared enqueue/replay mutex. */
enum class OutboxReplayAcquisition {
    /** Enqueue-triggered flush — wait so the new write is not skipped. */
    Wait,

    /** Connectivity / periodic sync — skip if a replay or enqueue already holds the lock. */
    TrySkipIfBusy,
}

/**
 * Durable offline write queue with optimistic local cache updates and replay on connectivity.
 *
 * A single [Mutex] serializes enqueue (existence + insert) with full [replayPending] so a parallel
 * enqueue cannot resurrect a SUCCEEDED row mid-replay (P1.2 / H02).
 */
class OfflineWriteQueue(
    private val localStore: MovitLocalStore,
    private val api: MovitMobileApi,
    private val platform: () -> MovitPlatformBindings,
    private val guestGate: GuestOutboxAttributionGate = GuestOutboxAttributionGate(localStore),
) {
    private val dispatcher = OutboxDispatcher(api)
    private val telemetry = MovitSyncTelemetry(localStore)
    private val mutex = Mutex()

    suspend fun pendingCount(): Long =
        localStore.countOutboxByStatus(OutboxStatus.PENDING)

    /** UX.6 / UX.1 — permanent failures that need user attention. */
    suspend fun failedCount(): Long =
        localStore.countOutboxByStatus(OutboxStatus.FAILED_PERMANENT)

    /** UX.2a — pending + in-flight + permanent failures for the sync settings list. */
    suspend fun listVisibleOutbox(): List<OutboxEntry> =
        localStore.listAllOutbox().filter {
            it.status == OutboxStatus.PENDING ||
                it.status == OutboxStatus.IN_FLIGHT ||
                it.status == OutboxStatus.FAILED_PERMANENT
        }

    /** UX.2a — reset permanent failures back to PENDING and replay. */
    suspend fun retryFailedPermanent(): Int {
        var reset = 0
        for (entry in localStore.listAllOutbox()) {
            if (entry.status != OutboxStatus.FAILED_PERMANENT) continue
            localStore.updateOutboxStatus(
                id = entry.id,
                status = OutboxStatus.PENDING,
                attempts = 0,
                nextAttemptAtEpochMs = null,
            )
            reset++
        }
        if (reset > 0 && platform().isNetworkAvailable() && platform().authHeader() != null) {
            replayPending(OutboxReplayAcquisition.Wait)
        }
        return reset
    }

    suspend fun enqueuePlannedWorkoutStart(
        workoutId: String,
        request: PlannedWorkoutStartRequestDto,
        operationId: String = newOperationId(),
    ): String = enqueue(
        operationId = operationId,
        type = OutboxOperationType.PLANNED_WORKOUT_START,
        payload = MovitJson.encodeToString(PlannedWorkoutStartOutboxPayload(workoutId, request)),
    )

    suspend fun enqueuePlannedWorkoutComplete(
        workoutId: String,
        request: PlannedWorkoutCompleteRequestDto,
        operationId: String = newOperationId(),
        workoutGroupId: String? = null,
    ): String = enqueue(
        operationId = operationId,
        type = OutboxOperationType.PLANNED_WORKOUT_COMPLETE,
        payload = MovitJson.encodeToString(
            PlannedWorkoutCompleteOutboxPayload(workoutId, request, workoutGroupId),
        ),
    )

    suspend fun enqueuePlannedWorkoutReport(
        workoutId: String,
        request: PlannedWorkoutCompleteRequestDto,
        operationId: String = newOperationId(),
        workoutGroupId: String? = null,
    ): String = enqueue(
        operationId = operationId,
        type = OutboxOperationType.PLANNED_WORKOUT_REPORT,
        payload = MovitJson.encodeToString(
            PlannedWorkoutCompleteOutboxPayload(workoutId, request, workoutGroupId),
        ),
    )

    suspend fun enqueuePlanComplete(operationId: String = newOperationId()): String =
        enqueue(operationId, OutboxOperationType.PLAN_COMPLETE, payload = "{}")

    suspend fun enqueueExercisePreferenceUpsert(
        exerciseId: String,
        request: UserExercisePreferenceUpsertRequest,
        operationId: String = newOperationId(),
    ): String = enqueue(
        operationId = operationId,
        type = OutboxOperationType.EXERCISE_PREFERENCE_UPSERT,
        payload = MovitJson.encodeToString(ExercisePreferenceUpsertOutboxPayload(exerciseId, request)),
    )

    suspend fun enqueueExercisePreferenceDelete(
        exerciseId: String,
        operationId: String = newOperationId(),
    ): String = enqueue(
        operationId = operationId,
        type = OutboxOperationType.EXERCISE_PREFERENCE_DELETE,
        payload = MovitJson.encodeToString(ExercisePreferenceDeleteOutboxPayload(exerciseId)),
    )

    suspend fun enqueueUserProgramOverrideCreate(
        userProgramId: String,
        request: UserProgramOverrideCreateRequest,
        operationId: String = newOperationId(),
    ): String = enqueue(
        operationId = operationId,
        type = OutboxOperationType.USER_PROGRAM_OVERRIDE_CREATE,
        payload = MovitJson.encodeToString(UserProgramOverrideCreateOutboxPayload(userProgramId, request)),
    )

    suspend fun enqueueUserProgramOverrideDelete(
        userProgramId: String,
        overrideId: String,
        operationId: String = newOperationId(),
    ): String = enqueue(
        operationId = operationId,
        type = OutboxOperationType.USER_PROGRAM_OVERRIDE_DELETE,
        payload = MovitJson.encodeToString(
            UserProgramOverrideDeleteOutboxPayload(userProgramId, overrideId),
        ),
    )

    suspend fun enqueueSaveDayCustomizations(
        userProgramId: String,
        weekNumber: Int,
        dayNumber: Int,
        request: UserProgramUpdateRequest,
        operationId: String = newOperationId(),
    ): String = enqueue(
        operationId = operationId,
        type = OutboxOperationType.SAVE_DAY_CUSTOMIZATIONS,
        payload = MovitJson.encodeToString(
            SaveDayCustomizationsOutboxPayload(userProgramId, weekNumber, dayNumber, request),
        ),
    )

    suspend fun enqueueWorkoutExecutionUpload(
        request: WorkoutExecutionUploadRequestDto,
        operationId: String = request.id,
    ): String = enqueue(
        operationId = operationId,
        type = OutboxOperationType.WORKOUT_EXECUTION_UPLOAD,
        payload = MovitJson.encodeToString(WorkoutExecutionUploadOutboxPayload(request)),
    )

    suspend fun enqueueProgressionMarkSeen(
        request: ProgressionMarkSeenRequest,
        operationId: String = newOperationId(),
    ): String = enqueue(
        operationId = operationId,
        type = OutboxOperationType.PROGRESSION_MARK_SEEN,
        payload = MovitJson.encodeToString(ProgressionMarkSeenOutboxPayload(request)),
    )

    /**
     * Replays pending entries; succeeded rows are skipped (idempotency).
     *
     * @param acquisition [OutboxReplayAcquisition.Wait] for enqueue-triggered flush;
     *   [OutboxReplayAcquisition.TrySkipIfBusy] for connectivity / periodic sync.
     */
    suspend fun replayPending(
        acquisition: OutboxReplayAcquisition = OutboxReplayAcquisition.TrySkipIfBusy,
    ): OutboxReplayResult {
        return when (acquisition) {
            OutboxReplayAcquisition.Wait -> mutex.withLock { replayPendingLocked() }
            OutboxReplayAcquisition.TrySkipIfBusy -> {
                if (!mutex.tryLock()) {
                    return OutboxReplayResult(0, 0, 0, 0)
                }
                try {
                    replayPendingLocked()
                } finally {
                    mutex.unlock()
                }
            }
        }
    }

    private suspend fun replayPendingLocked(): OutboxReplayResult {
        val auth = platform().authHeader()
            ?: return OutboxReplayResult(0, 0, 0, 0)

        if (!platform().isNetworkAvailable()) {
            return OutboxReplayResult(0, 0, 0, 0)
        }

        localStore.recoverInFlightOutbox()

        val currentUserId = platform().userId()?.takeIf { it.isNotBlank() }
        if (currentUserId == null) {
            // Auth token present (checked above) but no persisted userId — common on legacy iOS sessions.
            println(
                "outbox: replay skipped — auth present but userId is null; " +
                    "call ensureUserIdFromProfileIfNeeded / fetchProfile to re-persist",
            )
            return OutboxReplayResult(0, 0, 0, 0)
        }
        val guestAccepted = guestGate.isGuestReplayAccepted(currentUserId)
        val now = MovitClock.nowEpochMs()

        var attempted = 0
        var succeeded = 0
        var failed = 0
        var skipped = 0

        for (entry in OutboxReplayOrdering.sortForReplay(localStore.listPendingOutbox())) {
            val current = localStore.getOutboxById(entry.id) ?: continue
            if (current.status != OutboxStatus.PENDING) {
                skipped++
                continue
            }

            val nextAt = current.nextAttemptAtEpochMs
            if (nextAt != null && nextAt > now) {
                skipped++
                continue
            }

            val owner = current.ownerUserId
            val eligible = when {
                owner == currentUserId -> true
                owner == null && guestAccepted -> true
                else -> false
            }
            if (!eligible) {
                skipped++
                continue
            }

            // Fresh list so same-cycle execution SUCCEEDED unblocks complete (D-N3).
            when (OutboxDependencyGate.shouldDeferComplete(current, localStore.listAllOutbox())) {
                DeferDecision.WaitForExecutions -> {
                    skipped++
                    continue
                }
                DeferDecision.ProceedWithWarning -> {
                    println(
                        "outbox: complete ${current.id} proceeding despite FAILED_PERMANENT " +
                            "execution dependency",
                    )
                }
                DeferDecision.Proceed -> Unit
            }

            attempted++
            localStore.updateOutboxStatus(
                id = current.id,
                status = OutboxStatus.IN_FLIGHT,
                attempts = current.attempts,
                nextAttemptAtEpochMs = null,
            )

            val outcome = dispatcher.dispatch(current, auth)
            println(telemetry.outboxReplayLogLine(entry.id, current.type, outcome.name))

            when (outcome) {
                OutboxDispatchOutcome.SUCCESS,
                OutboxDispatchOutcome.SERVER_WINS,
                -> {
                    localStore.updateOutboxStatus(
                        id = entry.id,
                        status = OutboxStatus.SUCCEEDED,
                        attempts = current.attempts,
                        nextAttemptAtEpochMs = null,
                    )
                    if (outcome == OutboxDispatchOutcome.SERVER_WINS) {
                        OfflineWriteOptimisticCache.onServerWins(localStore, current.type, current.payload)
                    } else {
                        OfflineWriteOptimisticCache.onSuccess(localStore, current.type, current.payload)
                    }
                    succeeded++
                }
                OutboxDispatchOutcome.PERMANENT_FAILURE -> {
                    localStore.updateOutboxStatus(
                        id = entry.id,
                        status = OutboxStatus.FAILED_PERMANENT,
                        attempts = current.attempts + 1,
                        nextAttemptAtEpochMs = null,
                    )
                    OfflineWriteOptimisticCache.onPermanentFailure(
                        localStore,
                        current.type,
                        current.payload,
                    )
                    telemetry.incrementOutboxFailedPermanent()
                    failed++
                }
                OutboxDispatchOutcome.RETRYABLE_NETWORK,
                OutboxDispatchOutcome.RETRYABLE_UNEXPECTED,
                -> {
                    val nextAttempts = current.attempts + 1
                    val permanent = OutboxConflictPolicy.shouldMarkPermanent(nextAttempts, outcome)
                    val status = if (permanent) {
                        OutboxStatus.FAILED_PERMANENT
                    } else {
                        OutboxStatus.PENDING
                    }
                    val nextAttemptAt = if (permanent) {
                        null
                    } else {
                        OutboxConflictPolicy.nextAttemptAtEpochMs(now, nextAttempts)
                    }
                    localStore.updateOutboxStatus(
                        id = entry.id,
                        status = status,
                        attempts = nextAttempts,
                        nextAttemptAtEpochMs = nextAttemptAt,
                    )
                    if (status == OutboxStatus.FAILED_PERMANENT) {
                        OfflineWriteOptimisticCache.onPermanentFailure(
                            localStore,
                            current.type,
                            current.payload,
                        )
                        telemetry.incrementOutboxRetryExhausted()
                    }
                    failed++
                }
            }
        }

        OutboxMaintenance.purgeCompletedOlderThanRetention(localStore)
        return OutboxReplayResult(attempted, succeeded, failed, skipped)
    }

    private suspend fun enqueue(
        operationId: String,
        type: OutboxOperationType,
        payload: String,
    ): String {
        LegacyWorkoutSyncGate.awaitBeforeEnqueue()

        mutex.withLock {
            val existing = localStore.getOutboxById(operationId)
            if (existing != null && existing.status != OutboxStatus.FAILED_PERMANENT) {
                return operationId
            }

            localStore.insertOutbox(
                OutboxEntry(
                    id = operationId,
                    type = type,
                    payload = payload,
                    createdAt = MovitClock.nowEpochMs(),
                    attempts = 0,
                    status = OutboxStatus.PENDING,
                    ownerUserId = platform().userId(),
                    nextAttemptAtEpochMs = null,
                ),
            )
            OfflineWriteOptimisticCache.apply(localStore, type, payload)
        }

        if (platform().isNetworkAvailable() && platform().authHeader() != null) {
            replayPending(OutboxReplayAcquisition.Wait)
        }

        return operationId
    }

    private fun newOperationId(): String =
        "op-${MovitClock.nowEpochMs()}-${Random.nextInt(1_000_000)}"
}
