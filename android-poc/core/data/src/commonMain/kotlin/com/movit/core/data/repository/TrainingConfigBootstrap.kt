package com.movit.core.data.repository

import com.movit.core.network.MovitJson
import com.movit.core.training.config.ExerciseConfigParser
import com.movit.core.training.config.ExerciseConfigRecord
import com.movit.resources.readBundledExerciseSeedSquatJson

/** Seeds bundled squat config for offline/dev when sync has not run yet. */
suspend fun TrainingConfigRepository.seedBundledDefaultsIfEmpty() {
    if (allCachedSlugs().isNotEmpty()) return
    val json = runCatching { readBundledExerciseSeedSquatJson() }.getOrNull() ?: return
    val element = runCatching { MovitJson.parseToJsonElement(json) }.getOrNull() ?: return
    val config = runCatching { ExerciseConfigParser.parseConfig(element) }.getOrNull() ?: return
    listOf("squat", "bodyweight-squat", "barbell-squat").forEach { slug ->
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
