package com.movit.feature.library

import com.movit.core.data.MovitData
import com.movit.core.model.ExploreItemUi

/**
 * Shared exercise content loader for Exercise Detail and Exercise Prepare screens.
 */
object ExerciseContentMapper {

    suspend fun loadExercise(
        exerciseId: String,
        repository: LibraryRepository = defaultLibraryRepository(),
        language: String = resolveLanguage(),
    ): ExercisePrepareUi? {
        val item = repository.findItem(exerciseId) ?: return null
        return mapExploreItem(item, exerciseId, language)
    }

    suspend fun loadWorkoutExercise(
        exerciseId: String,
        workoutId: String?,
        language: String = resolveLanguage(),
    ): ExercisePrepareUi? {
        val config = workoutId?.let { WorkoutFlowCache.get(it) } ?: return null
        val exercise = config.exercises.firstOrNull { item ->
            item.id == exerciseId ||
                item.exerciseSlug == exerciseId ||
                normalizeTrainingSlug(item.exerciseSlug) == normalizeTrainingSlug(exerciseId)
        } ?: return null
        val index = config.exercises.indexOf(exercise).coerceAtLeast(0)
        return mapWorkoutExercise(
            exercise = exercise,
            session = config,
            index = index,
            language = language,
        )
    }

    suspend fun mapExploreItem(
        item: ExploreItemUi,
        exerciseId: String = item.id,
        language: String = resolveLanguage(),
    ): ExercisePrepareUi {
        val slug = legacySlug(exerciseId)
        val media = ExercisePrepareMediaResolver.resolve(
            exerciseSlug = slug,
            language = language,
            fallbackImageUrl = item.imageUrl,
        )
        return ExercisePrepareUi(
            id = item.id,
            exerciseSlug = slug,
            name = item.title,
            category = item.subtitle,
            sets = "3",
            reps = "12",
            rest = "60s",
            equipment = item.metadata.firstOrNull() ?: "None",
            axesLabel = media.axesLabel,
            distanceTip = "Stand ~2 m from the camera, full body in frame.",
            instructions = media.instructions.ifEmpty { listOf(item.subtitle) },
            targetMuscles = media.targetMuscles.ifEmpty { item.metadata.take(3) },
            sessionProgressPercent = 20,
            sessionSummary = "1 exercise · ~10 min",
            legacyFileName = slug,
            heroImageUrl = media.heroImageUrl,
            poseVariants = media.poseVariants,
            selectedPoseVariantIndex = media.selectedPoseVariantIndex,
        )
    }

    private suspend fun mapWorkoutExercise(
        exercise: WorkoutFlowExerciseUi,
        session: WorkoutFlowConfigUi,
        index: Int,
        language: String,
    ): ExercisePrepareUi {
        val rawSlug = exercise.exerciseSlug.ifBlank { exercise.id }
        val slug = if (MovitData.isInstalled) {
            MovitData.trainingConfig.resolveAvailableSlug(
                rawSlug,
                exercise.id,
                normalizeTrainingSlug(rawSlug),
                normalizeTrainingSlug(exercise.id),
            )
        } else {
            null
        } ?: rawSlug
        val media = ExercisePrepareMediaResolver.resolve(
            exerciseSlug = slug,
            language = language,
            fallbackImageUrl = null,
        )
        val repsText = when {
            exercise.reps != null -> exercise.reps.toString()
            exercise.durationSeconds != null -> "${exercise.durationSeconds}s"
            else -> "12"
        }
        return ExercisePrepareUi(
            id = exercise.id,
            exerciseSlug = slug,
            name = exercise.name,
            category = session.subtitle,
            sets = exercise.sets.coerceAtLeast(1).toString(),
            reps = repsText,
            repsLabelKey = if (exercise.reps == null && exercise.durationSeconds != null) {
                "prepare_stat_duration"
            } else {
                "prepare_stat_reps"
            },
            rest = "${exercise.restSeconds.coerceAtLeast(0)}s",
            equipment = "From workout",
            axesLabel = media.axesLabel,
            distanceTip = "Stand ~2 m from the camera, full body in frame.",
            instructions = media.instructions.ifEmpty { listOf(exercise.name) },
            targetMuscles = media.targetMuscles,
            sessionProgressPercent = (((index + 1).toFloat() / session.exerciseCount.coerceAtLeast(1)) * 100)
                .toInt()
                .coerceIn(1, 100),
            sessionSummary = session.summaryLabel(exercise.restSeconds),
            legacyFileName = slug,
            heroImageUrl = media.heroImageUrl,
            poseVariants = media.poseVariants,
            selectedPoseVariantIndex = media.selectedPoseVariantIndex,
        )
    }

    private fun resolveLanguage(): String =
        if (MovitData.isInstalled) MovitData.requirePlatform().preferredLanguage() else "en"
}
