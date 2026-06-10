package com.movit.core.data.outbox

import com.movit.core.network.MovitJson
import com.movit.core.network.MovitMobileApi

internal class OutboxDispatcher(
    private val api: MovitMobileApi,
) {
    suspend fun dispatch(entry: OutboxEntry, authorization: String): OutboxDispatchOutcome {
        val result = runCatching {
            when (entry.type) {
                OutboxOperationType.PLANNED_WORKOUT_START -> {
                    val payload = MovitJson.decodeFromString<PlannedWorkoutStartOutboxPayload>(entry.payload)
                    api.startPlannedWorkout(payload.workoutId, payload.request, authorization).getOrThrow()
                }
                OutboxOperationType.PLANNED_WORKOUT_COMPLETE -> {
                    val payload = MovitJson.decodeFromString<PlannedWorkoutCompleteOutboxPayload>(entry.payload)
                    api.completePlannedWorkout(payload.workoutId, payload.request, authorization).getOrThrow()
                }
                OutboxOperationType.PLANNED_WORKOUT_REPORT -> {
                    val payload = MovitJson.decodeFromString<PlannedWorkoutCompleteOutboxPayload>(entry.payload)
                    api.reportPlannedWorkout(payload.workoutId, payload.request, authorization).getOrThrow()
                }
                OutboxOperationType.PLAN_COMPLETE -> api.completePlan(authorization).getOrThrow()
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
                    api.createUserProgramOverride(payload.userProgramId, payload.request, authorization).getOrThrow()
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
            }
        }

        return result.fold(
            onSuccess = { OutboxDispatchOutcome.SUCCESS },
            onFailure = { error ->
                val status = parseHttpStatusFromError(error.message)
                when {
                    status != null && OutboxConflictPolicy.isServerWins(status) ->
                        OutboxDispatchOutcome.SERVER_WINS
                    status != null && OutboxConflictPolicy.isPermanentClientError(status) ->
                        OutboxDispatchOutcome.PERMANENT_FAILURE
                    else -> OutboxDispatchOutcome.RETRYABLE
                }
            },
        )
    }
}
