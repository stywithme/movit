package com.movit.feature.training

import com.movit.feature.training.BuildConfig

internal actual fun readTrainingThroughputProfileFlag(): String? =
    BuildConfig.TRAINING_THROUGHPUT_PROFILE.takeIf { it.isNotBlank() }
