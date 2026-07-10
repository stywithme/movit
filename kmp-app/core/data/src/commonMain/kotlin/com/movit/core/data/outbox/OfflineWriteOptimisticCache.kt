package com.movit.core.data.outbox

import com.movit.core.data.cache.MovitCachePolicy
import com.movit.core.data.local.MovitLocalStore
import com.movit.core.data.repository.DayCustomizationLocalStore
import com.movit.core.data.repository.ExercisePreferenceLocalStore
import com.movit.core.data.repository.MovitCacheKeys
import com.movit.core.data.repository.dayCustomizationKeyFromUpdate
import com.movit.core.data.sync.MovitCacheInvalidation
import com.movit.core.network.MovitJson
import com.movit.core.network.dto.HomeDataDto
import com.movit.core.network.dto.TrainModeDto

/**
 * Unified optimistic cache map (P2.8):
 * applyOptimistic / onSuccess / onServerWins / onPermanentFailure per outbox type.
 */
internal object OfflineWriteOptimisticCache {
    fun apply(localStore: MovitLocalStore, type: OutboxOperationType, payload: String) {
        applyOptimistic(localStore, type, payload)
    }

    fun applyOptimistic(localStore: MovitLocalStore, type: OutboxOperationType, payload: String) {
        var mutated = false
        when (type) {
            OutboxOperationType.SAVE_DAY_CUSTOMIZATIONS -> {
                applyDayCustomizations(localStore, payload)
                mutated = true
            }
            OutboxOperationType.EXERCISE_PREFERENCE_UPSERT -> {
                applyExercisePreferenceUpsert(localStore, payload)
                mutated = true
            }
            OutboxOperationType.EXERCISE_PREFERENCE_DELETE -> {
                applyExercisePreferenceDelete(localStore, payload)
                mutated = true
            }
            OutboxOperationType.PLANNED_WORKOUT_COMPLETE,
            OutboxOperationType.PLAN_COMPLETE,
            -> {
                applyHomeCompletionOptimistic(localStore, type)
                mutated = true
            }
            // P2.8 / E-N7: legacy report must NOT mark todayWorkout completed.
            OutboxOperationType.PLANNED_WORKOUT_REPORT -> Unit
            else -> Unit
        }
        if (mutated) MovitCacheInvalidation.signal()
    }

    fun onSuccess(localStore: MovitLocalStore, type: OutboxOperationType, payload: String) {
        OutboxSuccessHooks.apply(localStore, type, payload)
        when (type) {
            OutboxOperationType.PLANNED_WORKOUT_COMPLETE,
            OutboxOperationType.PLAN_COMPLETE,
            -> clearHomeUploadFailureFlag(localStore)
            else -> Unit
        }
    }

    fun onServerWins(localStore: MovitLocalStore, type: OutboxOperationType, payload: String) {
        onSuccess(localStore, type, payload)
    }

    fun onPermanentFailure(localStore: MovitLocalStore, type: OutboxOperationType, payload: String) {
        when (type) {
            OutboxOperationType.PLANNED_WORKOUT_COMPLETE -> rollbackHomeComplete(localStore)
            OutboxOperationType.PLAN_COMPLETE -> rollbackHomePlanComplete(localStore)
            else -> Unit
        }
        markHomeUploadFailed(localStore)
        MovitCacheInvalidation.signal()
    }

    private fun applyDayCustomizations(localStore: MovitLocalStore, payload: String) {
        val parsed = MovitJson.decodeFromString<SaveDayCustomizationsOutboxPayload>(payload)
        val workouts = dayCustomizationKeyFromUpdate(
            weekNumber = parsed.weekNumber,
            dayNumber = parsed.dayNumber,
            customizations = parsed.request.customizations,
        ) ?: return

        val dayStore = DayCustomizationLocalStore(localStore)
        dayStore.saveUserCustomizations(
            userProgramId = parsed.userProgramId,
            weekNumber = parsed.weekNumber,
            dayNumber = parsed.dayNumber,
            plannedWorkouts = workouts,
        )
        DayCustomizationLocalStore.applyToEffectivePlanCache(
            localStore = localStore,
            userProgramId = parsed.userProgramId,
            weekNumber = parsed.weekNumber,
            dayNumber = parsed.dayNumber,
            plannedWorkouts = workouts,
        )
    }

    private fun applyExercisePreferenceUpsert(localStore: MovitLocalStore, payload: String) {
        val parsed = MovitJson.decodeFromString<ExercisePreferenceUpsertOutboxPayload>(payload)
        ExercisePreferenceLocalStore(localStore).upsert(parsed.exerciseId, parsed.request)
    }

    private fun applyExercisePreferenceDelete(localStore: MovitLocalStore, payload: String) {
        val parsed = MovitJson.decodeFromString<ExercisePreferenceDeleteOutboxPayload>(payload)
        ExercisePreferenceLocalStore(localStore).remove(parsed.exerciseId)
    }

    private fun applyHomeCompletionOptimistic(localStore: MovitLocalStore, type: OutboxOperationType) {
        patchHomeTrainMode(localStore) { mode ->
            when (type) {
                OutboxOperationType.PLANNED_WORKOUT_COMPLETE -> mode.copy(
                    todayWorkout = mode.todayWorkout?.copy(isCompleted = true),
                )
                OutboxOperationType.PLAN_COMPLETE -> mode.copy(status = "completed", isPaused = false)
                else -> mode
            }
        }
    }

    private fun rollbackHomeComplete(localStore: MovitLocalStore) {
        patchHomeTrainMode(localStore) { mode ->
            mode.copy(todayWorkout = mode.todayWorkout?.copy(isCompleted = false))
        }
    }

    private fun rollbackHomePlanComplete(localStore: MovitLocalStore) {
        patchHomeTrainMode(localStore) { mode ->
            if (mode.status == "completed") mode.copy(status = "active") else mode
        }
    }

    private fun markHomeUploadFailed(localStore: MovitLocalStore) {
        localStore.writeString(MovitCacheKeys.HOME_STORE, MovitCacheKeys.HOME_UPLOAD_FAILED, "1")
    }

    private fun clearHomeUploadFailureFlag(localStore: MovitLocalStore) {
        localStore.remove(MovitCacheKeys.HOME_STORE, MovitCacheKeys.HOME_UPLOAD_FAILED)
    }

    private fun patchHomeTrainMode(localStore: MovitLocalStore, transform: (TrainModeDto) -> TrainModeDto) {
        val cached = MovitCachePolicy.readJson(
            localStore,
            MovitCacheKeys.HOME_STORE,
            MovitCacheKeys.HOME_DATA,
            HomeDataDto.serializer(),
        ) ?: return
        val trainMode = cached.trainMode ?: return
        MovitCachePolicy.writeJson(
            localStore,
            MovitCacheKeys.HOME_STORE,
            MovitCacheKeys.HOME_DATA,
            cached.copy(trainMode = transform(trainMode)),
            HomeDataDto.serializer(),
        )
    }
}
