package com.movit.feature.training

import com.movit.core.data.MovitData
import com.movit.core.data.repository.TrainingConfigRepository

object KmpTrainingSlugs {
    fun supports(
        slug: String,
        repository: TrainingConfigRepository = MovitData.trainingConfig,
    ): Boolean = repository.supports(slug)
}
