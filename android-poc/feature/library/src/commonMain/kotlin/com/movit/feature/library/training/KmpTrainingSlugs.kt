package com.movit.feature.library.training

import com.movit.core.training.blueprint.ExerciseBlueprintRegistry

object KmpTrainingSlugs {
    fun supports(slug: String): Boolean = ExerciseBlueprintRegistry.resolve(slug) != null
}
