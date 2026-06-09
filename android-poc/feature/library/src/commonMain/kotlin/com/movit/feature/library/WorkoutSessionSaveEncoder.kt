package com.movit.feature.library

import com.movit.core.network.dto.EffectivePlanItemDto
import com.movit.core.network.dto.EffectivePlanPayloadDto
import com.movit.core.network.dto.EffectivePlannedWorkoutDto
import com.movit.core.network.dto.UserProgramUpdateRequest

object WorkoutSessionSaveEncoder {

    fun encodeDayUpdate(
        session: WorkoutSessionUi,
        plan: EffectivePlanPayloadDto,
        exerciseBySlug: Map<String, ExerciseCatalogEntry> = emptyMap(),
    ): UserProgramUpdateRequest {
        val context = session.context
            ?: error("Session context is required to save customizations.")
        val dayKey = "day_${context.weekNumber}_${context.dayNumber}"
        val updatedWorkouts = plan.plannedWorkouts.map { workout ->
            if (workout.id == context.plannedWorkoutId) {
                val originalItems = workout.items.associateBy { it.id }
                workout.copy(
                    items = encodeItems(session, originalItems, exerciseBySlug),
                )
            } else {
                workout
            }
        }
        return UserProgramUpdateRequest(
            customizations = mapOf(dayKey to updatedWorkouts),
        )
    }

    private fun encodeItems(
        session: WorkoutSessionUi,
        originals: Map<String, EffectivePlanItemDto>,
        exerciseBySlug: Map<String, ExerciseCatalogEntry>,
    ): List<EffectivePlanItemDto> {
        val flattened = session.sections.flatMap { section ->
            section.items.map { block ->
                when (block) {
                    is WorkoutSessionBlockUi.Rest -> {
                        val original = originals[block.id]
                        (original ?: EffectivePlanItemDto(type = "rest", id = block.id)).copy(
                            type = "rest",
                            id = block.id.ifBlank { original?.id.orEmpty() },
                            restDurationMs = block.durationSeconds * 1000,
                            sortOrder = original?.sortOrder ?: 0,
                            phaseRole = original?.phaseRole ?: section.phaseRole,
                        )
                    }
                    is WorkoutSessionBlockUi.Exercise -> {
                        val original = originals[block.id]
                        val weightList = block.weightKg?.let { weight ->
                            List(block.sets.coerceAtLeast(1)) { weight.toDouble() }
                        }
                        val resolvedExerciseId = exerciseBySlug[block.exerciseSlug]?.serverId
                            ?: original?.exerciseId
                        (original ?: EffectivePlanItemDto(type = "exercise", id = block.id)).copy(
                            type = "exercise",
                            id = block.id.ifBlank { original?.id.orEmpty() },
                            exerciseId = resolvedExerciseId,
                            sets = block.sets,
                            targetReps = block.reps,
                            targetDuration = block.durationSeconds,
                            restBetweenSetsMs = block.restSeconds * 1000,
                            weightPerSet = weightList ?: original?.weightPerSet,
                            phaseRole = block.phaseRole,
                            sortOrder = original?.sortOrder ?: 0,
                        )
                    }
                }
            }
        }
        return flattened.mapIndexed { index, item -> item.copy(sortOrder = index) }
    }
}
