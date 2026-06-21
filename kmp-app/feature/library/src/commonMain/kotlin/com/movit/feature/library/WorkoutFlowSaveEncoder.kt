package com.movit.feature.library

import com.movit.core.network.dto.EffectivePlanItemDto
import com.movit.core.network.dto.EffectivePlanPayloadDto
import com.movit.core.network.dto.ProgramCustomizationKeys
import com.movit.core.network.dto.UserProgramUpdateRequest

/** Persists customize-step sets/rest overrides onto the effective day plan. */
object WorkoutFlowSaveEncoder {

    fun encodeDayUpdate(
        config: WorkoutFlowConfigUi,
        context: WorkoutSessionContextUi,
        plan: EffectivePlanPayloadDto,
    ): UserProgramUpdateRequest {
        val dayKey = ProgramCustomizationKeys.dayKey(context.weekNumber, context.dayNumber)
        val originalsById = plan.plannedWorkouts
            .firstOrNull { it.id == context.plannedWorkoutId }
            ?.items
            ?.associateBy { it.id }
            .orEmpty()
        val restItems = plan.plannedWorkouts
            .firstOrNull { it.id == context.plannedWorkoutId }
            ?.items
            .orEmpty()
            .filter { !it.type.equals("exercise", ignoreCase = true) }
        val updatedExercises = config.exercises.mapNotNull { override ->
            val item = originalsById[override.id] ?: return@mapNotNull null
            if (!item.type.equals("exercise", ignoreCase = true)) return@mapNotNull null
            item.copy(
                sets = override.sets,
                targetReps = override.reps ?: item.targetReps,
                targetDuration = override.durationSeconds ?: item.targetDuration,
                restBetweenSetsMs = config.restBetweenSetsSeconds * 1000,
            )
        }
        val updatedWorkouts = plan.plannedWorkouts.map { workout ->
            if (workout.id != context.plannedWorkoutId) {
                workout
            } else {
                workout.copy(items = updatedExercises + restItems)
            }
        }
        return UserProgramUpdateRequest(customizations = mapOf(dayKey to updatedWorkouts))
    }
}
