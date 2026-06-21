package com.movit.feature.library

actual fun defaultWorkoutSessionRepository(): WorkoutSessionRepository = SharedWorkoutSessionRepository()
