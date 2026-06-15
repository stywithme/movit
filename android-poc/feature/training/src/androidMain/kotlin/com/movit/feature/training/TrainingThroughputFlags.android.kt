package com.movit.feature.training

import com.movit.feature.training.buildconfig.MovitGeneratedBuildConfig

internal actual fun readTrainingThroughputProfileFlag(): String? =
    MovitGeneratedBuildConfig.TRAINING_THROUGHPUT_PROFILE.takeIf { it.isNotBlank() }
