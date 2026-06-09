package com.movit.feature.explore

import com.movit.resources.strings.ExploreStrings
import com.movit.core.network.dto.ExploreDataDto
import com.movit.core.network.dto.ExploreExerciseDto
import com.movit.core.network.dto.ExploreProgramDto
import com.movit.core.network.dto.ExploreWorkoutDto
import com.movit.core.network.dto.LocalizedNameDto

object ExploreApiMapper {
    suspend fun map(
        data: ExploreDataDto,
        language: String,
        strings: ExploreStrings,
    ): ExploreContent {
        val workouts = data.workoutTemplates.map { it.toExploreItemUi(language, strings) }
        val exercises = data.exercises.map { it.toExploreItemUi(language, strings) }
        val programs = data.programs.map { it.toExploreItemUi(language, strings) }
        val allItems = workouts + exercises + programs
        val featured = workouts.take(2).ifEmpty { allItems.take(1) }
        return ExploreContent(
            featured = featured,
            workouts = workouts,
            exercises = exercises,
            programs = programs,
        )
    }
}

private suspend fun ExploreWorkoutDto.toExploreItemUi(
    language: String,
    strings: ExploreStrings,
): ExploreItemUi {
    val duration = estimatedDurationMin?.let { strings.minEst(it) }
    val metadata = buildList {
        add(strings.exercisesCount(exerciseCount))
        duration?.let { add(it) }
        level?.name?.display(language)?.let { add(it) }
    }
    return ExploreItemUi(
        id = slug,
        title = name.display(language),
        subtitle = strings.itemWorkout,
        type = ExploreItemType.Workout,
        imageUrl = coverImageUrl,
        metadata = metadata,
    )
}

private suspend fun ExploreExerciseDto.toExploreItemUi(
    language: String,
    strings: ExploreStrings,
): ExploreItemUi {
    val metadata = buildList {
        categoryName?.display(language)?.let { add(it) }
        if (musclesCount > 0) add(strings.musclesCount(musclesCount))
    }
    return ExploreItemUi(
        id = slug,
        title = name.display(language),
        subtitle = categoryCode ?: strings.itemExercise,
        type = ExploreItemType.Exercise,
        metadata = metadata,
    )
}

private suspend fun ExploreProgramDto.toExploreItemUi(
    language: String,
    strings: ExploreStrings,
): ExploreItemUi {
    val metadata = buildList {
        add(strings.weeksCount(durationWeeks))
        levelMin?.name?.display(language)?.let { add(it) }
    }
    return ExploreItemUi(
        id = slug,
        title = name.display(language),
        subtitle = strings.itemProgram,
        type = ExploreItemType.Program,
        imageUrl = coverImageUrl,
        metadata = metadata,
    )
}
