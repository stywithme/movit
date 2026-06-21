package com.movit.core.data.repository

import com.movit.core.network.dto.ExploreDataDto

internal fun mergeExploreData(
    old: ExploreDataDto?,
    incoming: ExploreDataDto,
    isFullSync: Boolean,
): ExploreDataDto {
    if (old == null || isFullSync) return incoming

    val mergedPrograms = old.programs
        .associateBy { it.id }
        .toMutableMap()
        .apply {
            incoming.deletedProgramIds.forEach { remove(it) }
            incoming.programs.forEach { put(it.id, it) }
        }
        .values
        .sortedByDescending { it.updatedAt }

    val mergedWorkouts = old.workoutTemplates
        .associateBy { it.id }
        .toMutableMap()
        .apply {
            incoming.deletedWorkoutTemplateIds.forEach { remove(it) }
            incoming.workoutTemplates.forEach { put(it.id, it) }
        }
        .values
        .sortedByDescending { it.updatedAt }

    val mergedExercises = old.exercises
        .associateBy { it.id }
        .toMutableMap()
        .apply {
            incoming.deletedExerciseIds.forEach { remove(it) }
            incoming.exercises.forEach { put(it.id, it) }
        }
        .values
        .sortedByDescending { it.updatedAt }

    return ExploreDataDto(
        levels = if (incoming.levels.isNotEmpty()) incoming.levels else old.levels,
        programs = mergedPrograms,
        workoutTemplates = mergedWorkouts,
        exercises = mergedExercises,
        deletedProgramIds = emptyList(),
        deletedWorkoutTemplateIds = emptyList(),
        deletedExerciseIds = emptyList(),
    )
}
