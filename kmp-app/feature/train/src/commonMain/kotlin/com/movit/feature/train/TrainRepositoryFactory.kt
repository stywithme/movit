package com.movit.feature.train

/**
 * Platform-provided default [TrainRepository].
 * Android can install a debug bridge later; iOS uses fake data until Ktor lands.
 */
expect fun defaultTrainRepository(): TrainRepository
