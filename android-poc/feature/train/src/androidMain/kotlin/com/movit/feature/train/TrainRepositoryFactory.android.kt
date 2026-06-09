package com.movit.feature.train

actual fun defaultTrainRepository(): TrainRepository = SharedTrainRepository()
