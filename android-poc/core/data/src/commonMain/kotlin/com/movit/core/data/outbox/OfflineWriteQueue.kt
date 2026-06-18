package com.movit.core.data.outbox

import com.movit.core.data.local.MovitLocalStore
import com.movit.core.data.platform.MovitPlatformBindings
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

/**
 * Durable offline write queue with optimistic local cache updates and replay on connectivity.
 */
class OfflineWriteQueue(
    private val localStore: MovitLocalStore,
    private val api: MovitMobileApi,
    private val platform: () -> MovitPlatformBindings,
) {
    private val dispatcher = OutboxDispatcher(api)

    suspend fun pendingCount(): Long =
        localStore.countOutboxByStatus(OutboxStatus.PENDING)

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
    ): String = enqueue(
        operationId = operationId,
        type = OutboxOperationType.PLANNED_WORKOUT_COMPLETE,
        payload = MovitJson.encodeToString(PlannedWorkoutCompleteOutboxPayload(workoutId, request)),
    )

    suspend fun enqueuePlannedWorkoutReport(
        workoutId: String,
        request: PlannedWorkoutCompleteRequestDto,
        operationId: String = newOperationId(),
    ): String = enqueue(
        operationId = operationId,
        type = OutboxOperationType.PLANNED_WORKOUT_REPORT,
        payload = MovitJson.encodeToString(PlannedWorkoutCompleteOutboxPayload(workoutId, request)),
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

    /** Replays pending entries; succeeded rows are skipped (idempotency). */
    suspend fun replayPending(): OutboxReplayResult {
        val auth = platform().authHeader()
            ?: return OutboxReplayResult(0, 0, 0, 0)

        if (!platform().isNetworkAvailable()) {
            return OutboxReplayResult(0, 0, 0, 0)
        }

        localStore.recoverInFlightOutbox()

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

            attempted++

            when (dispatcher.dispatch(current, auth)) {
                OutboxDispatchOutcome.SUCCESS,
                OutboxDispatchOutcome.SERVER_WINS,
                -> {
                    localStore.updateOutboxStatus(entry.id, OutboxStatus.SUCCEEDED, current.attempts)
                    succeeded++
                }
                OutboxDispatchOutcome.PERMANENT_FAILURE -> {
                    localStore.updateOutboxStatus(
                        entry.id,
                        OutboxStatus.FAILED_PERMANENT,
                        current.attempts + 1,
                    )
                    failed++
                }
                OutboxDispatchOutcome.RETRYABLE -> {
                    val nextAttempts = current.attempts + 1
                    val status = if (OutboxConflictPolicy.shouldMarkPermanent(nextAttempts, null)) {
                        OutboxStatus.FAILED_PERMANENT
                    } else {
                        OutboxStatus.PENDING
                    }
                    localStore.updateOutboxStatus(entry.id, status, nextAttempts)
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
            ),
        )
        OfflineWriteOptimisticCache.apply(localStore, type, payload)

        if (platform().isNetworkAvailable() && platform().authHeader() != null) {
            replayPending()
        }

        return operationId
    }

    private fun newOperationId(): String =
        "op-${MovitClock.nowEpochMs()}-${Random.nextInt(1_000_000)}"
}
