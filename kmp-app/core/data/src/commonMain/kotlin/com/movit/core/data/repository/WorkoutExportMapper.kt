package com.movit.core.data.repository

import com.movit.core.network.dto.WorkoutExportDto
import com.movit.core.network.dto.WorkoutExportExerciseDto
import com.movit.core.network.dto.WorkoutExportPhaseDto
import com.movit.core.network.dto.WorkoutTemplateEmbeddedExerciseDto
import com.movit.core.network.dto.WorkoutTemplateExerciseDto
import com.movit.core.network.dto.WorkoutTemplatePhaseDto
import com.movit.core.network.dto.WorkoutTemplateTrainingConfigDto

/**
 * Maps authoritative sync [WorkoutExportDto] into [WorkoutTemplateTrainingConfigDto]
 * for offline workout session preview and training-config cache reads.
 */
internal object WorkoutExportMapper {

    fun toTrainingConfig(export: WorkoutExportDto): WorkoutTemplateTrainingConfigDto {
        val phases = export.phases.mapIndexed { index, phase -> mapPhase(phase, index) }
        val flatExercises = if (phases.isNotEmpty()) {
            phases.flatMap { it.exercises }
        } else {
            export.exercises.mapIndexed { index, exercise -> mapExercise(exercise, index) }
        }
        return WorkoutTemplateTrainingConfigDto(
            id = export.id,
            slug = export.slug,
            name = export.name.toNameMap(),
            description = export.description?.toNameMap(),
            coverImageUrl = export.coverImageUrl,
            estimatedDurationMin = export.estimatedDurationMin,
            exercises = flatExercises,
            phases = phases,
        )
    }

    fun exerciseSlugs(export: WorkoutExportDto): List<String> {
        val fromPhases = export.phases.flatMap { phase ->
            phase.exercises.mapNotNull { it.exercise.takeIf(String::isNotBlank) }
        }
        if (fromPhases.isNotEmpty()) return fromPhases.distinct()
        return export.exercises.mapNotNull { it.exercise.takeIf(String::isNotBlank) }.distinct()
    }

    private fun mapPhase(phase: WorkoutExportPhaseDto, phaseIndex: Int): WorkoutTemplatePhaseDto =
        WorkoutTemplatePhaseDto(
            id = phase.id.ifBlank { "phase-$phaseIndex" },
            role = phase.role.ifBlank { "MAIN" },
            name = phase.name.toNameMap(),
            sortOrder = phase.sortOrder,
            exercises = phase.exercises.mapIndexed { index, exercise -> mapExercise(exercise, index) },
        )

    private fun mapExercise(exercise: WorkoutExportExerciseDto, index: Int): WorkoutTemplateExerciseDto {
        val slug = exercise.exercise.trim()
        val displayName = exercise.name?.toNameMap()?.takeIf { it.isNotEmpty() }
            ?: mapOf("en" to slug)
        return WorkoutTemplateExerciseDto(
            workoutExerciseId = "we-$index",
            sortOrder = index,
            variantIndex = exercise.variantIndex,
            targetReps = exercise.targetReps,
            targetDuration = exercise.targetDuration,
            sets = exercise.sets,
            restBetweenSetsMs = exercise.restBetweenSetsMs,
            restAfterExerciseMs = exercise.restAfterExerciseMs,
            weightPerSet = exercise.weightPerSet,
            exercise = WorkoutTemplateEmbeddedExerciseDto(
                slug = slug,
                name = displayName,
            ),
        )
    }

    private fun com.movit.core.network.dto.LocalizedNameDto.toNameMap(): Map<String, String> =
        buildMap {
            if (en.isNotBlank()) put("en", en)
            if (ar.isNotBlank()) put("ar", ar)
        }
}
