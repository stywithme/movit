package com.movit.resources

import org.jetbrains.compose.resources.ExperimentalResourceApi

private const val EXERCISE_SEED_SQUAT_PATH = "files/exercise_seed_squat.json"

@OptIn(ExperimentalResourceApi::class)
suspend fun readBundledExerciseSeedSquatJson(): String =
    Res.readBytes(EXERCISE_SEED_SQUAT_PATH).decodeToString()
