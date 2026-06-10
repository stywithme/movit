package com.movit.core.data.repository

import com.movit.core.model.ExploreContent
import com.movit.core.model.ExploreItemType
import com.movit.core.model.ExploreItemUi
import com.movit.core.network.dto.ExploreDataDto
import com.movit.core.network.dto.ExploreExerciseDto
import com.movit.core.network.dto.ExploreProgramDto
import com.movit.core.network.dto.ExploreWorkoutDto
import com.movit.core.network.dto.LocalizedNameDto
import com.movit.core.model.MovitTagVariant
import com.movit.resources.strings.ExploreStrings

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
    val duration = estimatedDurationMin
    val levelLabel = level?.name?.display(language)
    val metadata = buildList {
        add(strings.exercisesCount(exerciseCount))
        duration?.let { add(strings.minEst(it)) }
        levelLabel?.let { add(it) }
    }
    val focus = metadata.lastOrNull { it != strings.exercisesCount(exerciseCount) }
    return ExploreItemUi(
        id = slug,
        title = name.display(language),
        subtitle = strings.itemWorkout,
        type = ExploreItemType.Workout,
        imageUrl = coverImageUrl,
        badge = levelLabel,
        badgeVariant = level?.number.toBadgeVariant(),
        focusLabel = focus?.takeIf { it != levelLabel } ?: levelLabel,
        metadata = metadata,
        levelNumber = level?.number,
        durationMinutes = duration,
    )
}

private suspend fun ExploreExerciseDto.toExploreItemUi(
    language: String,
    strings: ExploreStrings,
): ExploreItemUi {
    val categoryLabel = categoryName?.display(language) ?: categoryCode
    val metadata = buildList {
        categoryLabel?.let { add(it) }
        if (musclesCount > 0) add(strings.musclesCount(musclesCount))
    }
    return ExploreItemUi(
        id = slug,
        title = name.display(language),
        subtitle = categoryLabel ?: strings.itemExercise,
        type = ExploreItemType.Exercise,
        imageUrl = imageUrl,
        badge = categoryLabel,
        badgeVariant = categoryCode.toExerciseBadgeVariant(),
        metadata = metadata,
        tags = metadata,
        categoryCode = categoryCode,
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
        id = id.ifBlank { slug },
        title = name.display(language),
        subtitle = strings.itemProgram,
        type = ExploreItemType.Program,
        imageUrl = coverImageUrl,
        metadata = metadata,
    )
}

private fun Int?.toBadgeVariant(): MovitTagVariant? = when (this) {
    1 -> MovitTagVariant.Lime
    2 -> MovitTagVariant.Blue
    null -> null
    else -> MovitTagVariant.Coral
}

private fun String?.toExerciseBadgeVariant(): MovitTagVariant = when (this?.lowercase()) {
    "mobility", "stretch", "flexibility" -> MovitTagVariant.Blue
    "strength", "weights", "equipment" -> MovitTagVariant.Coral
    else -> MovitTagVariant.Blue
}
