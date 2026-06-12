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
        val preview = ExercisePreparePreviewData.byId(exerciseId)
        if (preview != null) return preview
        val item = repository.findItem(exerciseId) ?: return null
        return mapExploreItem(item, exerciseId, language)
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

    private fun resolveLanguage(): String =
        if (MovitData.isInstalled) MovitData.requirePlatform().preferredLanguage() else "en"
}
