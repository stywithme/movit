package com.movit.feature.library

import com.movit.core.network.dto.EffectivePlanItemDto
import com.movit.core.network.dto.EffectivePlanPayloadDto

internal object ProgramDetailEditMapper {

    fun mapSessions(
        plan: EffectivePlanPayloadDto,
        language: String,
    ): List<ProgramEditSessionUi> =
        plan.plannedWorkouts
            .sortedBy { it.sortOrder }
            .mapIndexed { index, workout ->
                ProgramEditSessionUi(
                    id = workout.id,
                    title = workout.name.localized(language).ifBlank { "Session ${index + 1}" },
                    sortOrder = index,
                    exercises = workout.items
                        .filter { it.skipped != true && it.type.equals("exercise", ignoreCase = true) }
                        .sortedBy { it.sortOrder }
                        .map(::mapExercise),
                )
            }

    private fun mapExercise(item: EffectivePlanItemDto): ProgramEditExerciseUi {
        val restSeconds = (item.restBetweenSetsMs ?: 60_000) / 1000
        val weight = item.weightPerSet?.firstOrNull()
        return ProgramEditExerciseUi(
            id = item.id,
            name = item.exerciseId?.replace('-', ' ')?.replaceFirstChar { it.uppercase() }
                ?: "Exercise",
            sets = item.sets ?: 3,
            reps = item.targetReps,
            weightKg = weight,
            restSeconds = restSeconds,
        )
    }

    private fun Map<String, String>?.localized(language: String): String {
        if (this == null) return ""
        val primary = if (language == "ar") this["ar"] else this["en"]
        val fallback = if (language == "ar") this["en"] else this["ar"]
        return primary?.takeIf { it.isNotBlank() }
            ?: fallback?.takeIf { it.isNotBlank() }
            ?: ""
    }
}
