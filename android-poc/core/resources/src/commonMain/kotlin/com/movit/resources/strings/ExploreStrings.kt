package com.movit.resources.strings

import com.movit.resources.localizedString

data class ExploreStrings(
    val language: String,
    val itemWorkout: String,
    val itemExercise: String,
    val itemProgram: String,
) {
    suspend fun exercisesCount(count: Int): String =
        localizedString(language, "home_exercises_count", count)

    suspend fun minEst(min: Int): String =
        localizedString(language, "home_min_est", min)

    suspend fun musclesCount(count: Int): String =
        localizedString(language, "explore_muscles_count", count)

    suspend fun weeksCount(weeks: Int): String =
        localizedString(language, "train_weeks", weeks)

    companion object {
        suspend fun load(language: String): ExploreStrings = ExploreStrings(
            language = language,
            itemWorkout = localizedString(language, "explore_item_workout"),
            itemExercise = localizedString(language, "explore_item_exercise"),
            itemProgram = localizedString(language, "explore_item_program"),
        )
    }
}
