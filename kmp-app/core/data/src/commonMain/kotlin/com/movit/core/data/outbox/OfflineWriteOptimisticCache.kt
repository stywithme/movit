package com.movit.core.data.outbox

import com.movit.core.data.local.MovitLocalStore
import com.movit.core.data.repository.DayCustomizationLocalStore
import com.movit.core.data.repository.ExercisePreferenceLocalStore
import com.movit.core.data.repository.dayCustomizationKeyFromUpdate
import com.movit.core.network.MovitJson

/** Applies optimistic local cache updates immediately after outbox enqueue. */
internal object OfflineWriteOptimisticCache {
    fun apply(localStore: MovitLocalStore, type: OutboxOperationType, payload: String) {
        when (type) {
            OutboxOperationType.SAVE_DAY_CUSTOMIZATIONS -> applyDayCustomizations(localStore, payload)
            OutboxOperationType.EXERCISE_PREFERENCE_UPSERT -> applyExercisePreferenceUpsert(localStore, payload)
            OutboxOperationType.EXERCISE_PREFERENCE_DELETE -> applyExercisePreferenceDelete(localStore, payload)
            else -> Unit
        }
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
}
