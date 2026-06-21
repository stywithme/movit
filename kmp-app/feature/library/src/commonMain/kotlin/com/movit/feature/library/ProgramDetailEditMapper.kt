package com.movit.feature.library

import com.movit.core.network.dto.EffectivePlanItemDto
import com.movit.core.network.dto.EffectivePlanPayloadDto
import com.movit.core.network.dto.EffectivePlannedWorkoutDto
import com.movit.core.network.dto.LocalizedNameDto
import com.movit.core.network.dto.ProgramExportDto

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
                        .map { item -> mapExercise(item, language) },
                )
            }

    fun fallbackSessions(
        export: ProgramExportDto?,
        weekNumber: Int,
        dayNumber: Int,
        language: String,
    ): List<ProgramEditSessionUi> {
        val day = export?.weeks
            ?.firstOrNull { it.weekNumber == weekNumber }
            ?.days
            ?.firstOrNull { it.dayNumber == dayNumber }
        val workouts = day?.plannedWorkouts.orEmpty()
        if (workouts.isNotEmpty()) {
            return workouts.mapIndexed { index, workout ->
                ProgramEditSessionUi(
                    id = workout.id.ifBlank { "session-$index" },
                    title = workout.name.localized(language).ifBlank { "Session ${index + 1}" },
                    sortOrder = index,
                    exercises = previewExercises(index),
                )
            }
        }
        return listOf(
            ProgramEditSessionUi(
                id = "session-am",
                title = "Morning session",
                sortOrder = 0,
                exercises = previewExercises(0),
            ),
            ProgramEditSessionUi(
                id = "session-pm",
                title = "Evening session",
                sortOrder = 1,
                exercises = previewExercises(1),
            ),
        )
    }

    private fun previewExercises(sessionIndex: Int): List<ProgramEditExerciseUi> = listOf(
        ProgramEditExerciseUi(
            id = "ex-${sessionIndex}-squat",
            name = "Goblet squat",
            sets = 3,
            reps = 12,
            weightKg = 16.0,
            restSeconds = 60,
        ),
        ProgramEditExerciseUi(
            id = "ex-${sessionIndex}-hinge",
            name = "Romanian deadlift",
            sets = 3,
            reps = 10,
            weightKg = 20.0,
            restSeconds = 75,
        ),
    )

    private fun mapExercise(item: EffectivePlanItemDto, language: String): ProgramEditExerciseUi {
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

    fun toBaselinePlan(
        sessions: List<ProgramEditSessionUi>,
        userProgramId: String,
        weekNumber: Int,
        dayNumber: Int,
        programId: String?,
    ): EffectivePlanPayloadDto = EffectivePlanPayloadDto(
        userProgramId = userProgramId,
        programId = programId,
        weekNumber = weekNumber,
        dayNumber = dayNumber,
        plannedWorkouts = sessions.map { session ->
            EffectivePlannedWorkoutDto(
                id = session.id,
                name = mapOf("en" to session.title),
                sortOrder = session.sortOrder,
                items = session.exercises.mapIndexed { index, exercise ->
                    EffectivePlanItemDto(
                        id = exercise.id,
                        type = "exercise",
                        exerciseId = exercise.id,
                        sets = exercise.sets,
                        targetReps = exercise.reps,
                        restBetweenSetsMs = exercise.restSeconds * 1000,
                        weightPerSet = exercise.weightKg?.let { kg -> List(exercise.sets) { kg } },
                        sortOrder = index,
                    )
                },
            )
        },
    )

    private fun LocalizedNameDto.localized(language: String): String {
        val primary = if (language == "ar") ar else en
        val fallback = if (language == "ar") en else ar
        return primary.takeIf { it.isNotBlank() }
            ?: fallback.takeIf { it.isNotBlank() }
            ?: ""
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
