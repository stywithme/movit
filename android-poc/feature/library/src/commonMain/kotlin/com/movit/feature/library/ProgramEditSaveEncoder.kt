package com.movit.feature.library

import com.movit.core.network.dto.EffectivePlanPayloadDto
import com.movit.core.network.dto.ProgramCustomizationKeys
import com.movit.core.network.dto.UserProgramUpdateRequest

/** Persists program-detail edit-day session reorder and exercise param overrides. */
object ProgramEditSaveEncoder {

    fun encodeDayUpdate(
        weekNumber: Int,
        dayNumber: Int,
        sessions: List<ProgramEditSessionUi>,
        baselinePlan: EffectivePlanPayloadDto,
        removedSessionIds: Set<String> = emptySet(),
        removedExerciseIds: Set<String> = emptySet(),
    ): UserProgramUpdateRequest {
        val sessionOrder = sessions.mapIndexed { index, session -> session.id to index }.toMap()
        val exerciseOverrides = sessions
            .flatMap { it.exercises }
            .associateBy { it.id }

        val updatedWorkouts = baselinePlan.plannedWorkouts
            .filter { it.id !in removedSessionIds }
            .sortedBy { sessionOrder[it.id] ?: it.sortOrder }
            .mapIndexed { index, workout ->
                val sessionUi = sessions.firstOrNull { it.id == workout.id }
                workout.copy(
                    sortOrder = index,
                    items = workout.items.map { item ->
                        if (item.id in removedExerciseIds) {
                            item.copy(skipped = true)
                        } else {
                            val override = exerciseOverrides[item.id] ?: return@map item
                            if (!item.type.equals("exercise", ignoreCase = true)) return@map item
                            item.copy(
                                sets = override.sets,
                                targetReps = override.reps ?: item.targetReps,
                                restBetweenSetsMs = override.restSeconds * 1000,
                                weightPerSet = override.weightKg?.let { kg ->
                                    List(override.sets.coerceAtLeast(1)) { kg }
                                } ?: item.weightPerSet,
                            )
                        }
                    },
                ).let { updated ->
                    if (sessionUi == null) updated else updated.copy(
                        name = updated.name ?: mapOf("en" to sessionUi.title),
                    )
                }
            }

        return UserProgramUpdateRequest(
            customizations = mapOf(
                ProgramCustomizationKeys.dayKey(weekNumber, dayNumber) to updatedWorkouts,
            ),
        )
    }
}
