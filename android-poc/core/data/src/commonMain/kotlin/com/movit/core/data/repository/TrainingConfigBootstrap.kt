package com.movit.core.data.repository

import com.movit.core.network.MovitJson
import com.movit.core.training.config.ExerciseConfigParser
import com.movit.core.training.config.ExerciseConfigRecord
import com.movit.resources.readBundledExerciseSeedSquatJson

private val BUNDLED_TRAINING_SLUGS = listOf("squat", "bodyweight-squat", "barbell-squat")

/** Seeds bundled squat config when any bundled alias is still missing (sync may leave a non-empty index). */
suspend fun TrainingConfigRepository.seedBundledDefaultsIfEmpty() {
    if (BUNDLED_TRAINING_SLUGS.all { resolveBySlug(it) != null }) return
    val json = runCatching { readBundledExerciseSeedSquatJson() }.getOrNull() ?: return
    val element = runCatching { MovitJson.parseToJsonElement(json) }.getOrNull() ?: return
    val config = runCatching { ExerciseConfigParser.parseConfig(element) }.getOrNull() ?: return
    BUNDLED_TRAINING_SLUGS.forEach { slug ->
        if (resolveBySlug(slug) != null) return@forEach
        seedRecord(
            ExerciseConfigRecord.fromConfig(
                id = "bundled-seed-$slug",
                slug = slug,
                updatedAt = "",
                config = config,
            ),
        )
    }
}
