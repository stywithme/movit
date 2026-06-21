package com.movit.feature.library

import com.movit.core.data.MovitData
import com.movit.core.training.config.ExerciseConfig
import com.movit.core.training.config.LocalizedText
import com.movit.core.training.config.PoseVariant

data class PreparePoseVariantUi(
    val index: Int,
    val label: String,
    val imageUrl: String?,
)

internal object ExercisePrepareMediaResolver {

    fun resolve(
        exerciseSlug: String,
        language: String,
        fallbackImageUrl: String? = null,
    ): ExercisePrepareMediaUi {
        val config = if (MovitData.isInstalled) {
            MovitData.trainingConfig.getExercise(exerciseSlug)
        } else {
            null
        }
        val variants = config?.poseVariants.orEmpty().mapIndexed { index, variant ->
            variant.toUi(index, language)
        }
        val selected = variants.firstOrNull()
        return ExercisePrepareMediaUi(
            heroImageUrl = selected?.imageUrl
                ?: config?.imageUrl
                ?: fallbackImageUrl,
            poseVariants = variants,
            selectedPoseVariantIndex = 0,
            axesLabel = selected?.label ?: defaultAxesLabel(config, language),
            instructions = config?.instructions?.localized(language)?.lines()
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                .orEmpty(),
            targetMuscles = config?.muscles?.map { muscle ->
                muscle.replace('_', ' ').replaceFirstChar { it.uppercase() }
            }.orEmpty(),
        )
    }

    fun withSelectedVariant(
        media: ExercisePrepareMediaUi,
        index: Int,
        language: String,
        exerciseSlug: String,
    ): ExercisePrepareMediaUi {
        val variant = media.poseVariants.getOrNull(index) ?: return media
        val config = if (MovitData.isInstalled) {
            MovitData.trainingConfig.getExercise(exerciseSlug)
        } else {
            null
        }
        return media.copy(
            selectedPoseVariantIndex = index,
            heroImageUrl = variant.imageUrl ?: config?.imageUrl ?: media.heroImageUrl,
            axesLabel = variant.label,
        )
    }

    private fun PoseVariant.toUi(index: Int, language: String): PreparePoseVariantUi {
        val label = name.localized(language).ifBlank {
            listOfNotNull(cameraPosition, posePosition)
                .joinToString(" · ")
                .replace('_', ' ')
                .replaceFirstChar { it.uppercase() }
        }
        return PreparePoseVariantUi(
            index = index,
            label = label.ifBlank { "View ${index + 1}" },
            imageUrl = positionImageUrl,
        )
    }

    private fun defaultAxesLabel(config: ExerciseConfig?, language: String): String {
        val variant = config?.poseVariants?.firstOrNull() ?: return "Front · Side · 45°"
        return variant.name.localized(language).ifBlank { "Front · Side · 45°" }
    }

    private fun LocalizedText.localized(language: String): String {
        val primary = if (language == "ar") ar else en
        val fallback = if (language == "ar") en else ar
        return primary.takeIf { it.isNotBlank() }
            ?: fallback.takeIf { it.isNotBlank() }
            ?: ""
    }
}

data class ExercisePrepareMediaUi(
    val heroImageUrl: String?,
    val poseVariants: List<PreparePoseVariantUi>,
    val selectedPoseVariantIndex: Int,
    val axesLabel: String,
    val instructions: List<String>,
    val targetMuscles: List<String>,
)
