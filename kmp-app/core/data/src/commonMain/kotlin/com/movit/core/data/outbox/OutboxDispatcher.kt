package com.movit.core.data.outbox

import com.movit.core.data.sync.OutboxFailureKind
import com.movit.core.data.sync.classifyOutboxFailure
import com.movit.core.network.MovitApiException
import com.movit.core.network.MovitJson
import com.movit.core.network.MovitMobileApi
import com.movit.core.network.dto.PlanCompleteRequestDto

internal class OutboxDispatcher(
    private val api: MovitMobileApi,
) {
    suspend fun dispatch(entry: OutboxEntry, authorization: String): OutboxDispatchOutcome {
        val result = runCatching {
            when (entry.type) {
                OutboxOperationType.PLANNED_WORKOUT_START -> {
                    val payload = MovitJson.decodeFromString<PlannedWorkoutStartOutboxPayload>(entry.payload)
                    // P1.3: outbox operationId is the server idempotency key.
                    val request = payload.request.copy(idempotencyKey = entry.id)
                    api.startPlannedWorkout(payload.workoutId, request, authorization).getOrThrow()
                }
                OutboxOperationType.PLANNED_WORKOUT_COMPLETE -> {
                    val payload = MovitJson.decodeFromString<PlannedWorkoutCompleteOutboxPayload>(entry.payload)
                    val request = payload.request.copy(idempotencyKey = entry.id)
                    api.completePlannedWorkout(payload.workoutId, request, authorization).getOrThrow()
                }
                OutboxOperationType.PLANNED_WORKOUT_REPORT -> {
                    val payload = MovitJson.decodeFromString<PlannedWorkoutCompleteOutboxPayload>(entry.payload)
                    val request = payload.request.copy(idempotencyKey = entry.id)
                    api.reportPlannedWorkout(payload.workoutId, request, authorization).getOrThrow()
                }
                OutboxOperationType.PLAN_COMPLETE -> {
                    api.completePlan(
                        authorization = authorization,
                        request = PlanCompleteRequestDto(idempotencyKey = entry.id),
                    ).getOrThrow()
                }
                OutboxOperationType.EXERCISE_PREFERENCE_UPSERT -> {
                    val payload = MovitJson.decodeFromString<ExercisePreferenceUpsertOutboxPayload>(entry.payload)
                    api.upsertExercisePreference(payload.exerciseId, payload.request, authorization).getOrThrow()
                }
                OutboxOperationType.EXERCISE_PREFERENCE_DELETE -> {
                    val payload = MovitJson.decodeFromString<ExercisePreferenceDeleteOutboxPayload>(entry.payload)
                    api.deleteExercisePreference(payload.exerciseId, authorization).getOrThrow()
                }
                OutboxOperationType.USER_PROGRAM_OVERRIDE_CREATE -> {
                    val payload = MovitJson.decodeFromString<UserProgramOverrideCreateOutboxPayload>(entry.payload)
                    val request = payload.request.copy(idempotencyKey = entry.id)
                    api.createUserProgramOverride(payload.userProgramId, request, authorization).getOrThrow()
                }
                OutboxOperationType.USER_PROGRAM_OVERRIDE_DELETE -> {
                    val payload = MovitJson.decodeFromString<UserProgramOverrideDeleteOutboxPayload>(entry.payload)
                    api.deleteUserProgramOverride(
                        payload.userProgramId,
                        payload.overrideId,
                        authorization,
                    ).getOrThrow()
                }
                OutboxOperationType.SAVE_DAY_CUSTOMIZATIONS -> {
                    val payload = MovitJson.decodeFromString<SaveDayCustomizationsOutboxPayload>(entry.payload)
                    api.updateUserProgramCustomizations(
                        userProgramId = payload.userProgramId,
                        request = payload.request,
                        authorization = authorization,
                    ).getOrThrow()
                }
                OutboxOperationType.PROGRESSION_MARK_SEEN -> {
                    val payload = MovitJson.decodeFromString<ProgressionMarkSeenOutboxPayload>(entry.payload)
                    api.markProgressionSeen(payload.request, authorization).getOrThrow()
                }
                OutboxOperationType.WORKOUT_EXECUTION_UPLOAD -> {
                    val payload = MovitJson.decodeFromString<WorkoutExecutionUploadOutboxPayload>(entry.payload)
                    api.uploadWorkoutExecution(payload.request, authorization).getOrThrow()
                }
            }
        }

        return result.fold(
            onSuccess = { OutboxDispatchOutcome.SUCCESS },
            onFailure = { error ->
                val status = (error as? MovitApiException)?.status
                    ?: parseHttpStatusFromError(error.message)
                if (status != null && OutboxConflictPolicy.isServerWins(status)) {
                    OutboxDispatchOutcome.SERVER_WINS
                } else if (status != null && OutboxConflictPolicy.isPermanentClientError(status)) {
                    OutboxDispatchOutcome.PERMANENT_FAILURE
                } else {
                    when (classifyOutboxFailure(error)) {
                        OutboxFailureKind.Network -> OutboxDispatchOutcome.RETRYABLE_NETWORK
                        OutboxFailureKind.Unexpected -> OutboxDispatchOutcome.RETRYABLE_UNEXPECTED
                    }
                }
            },
        )
    }
}
