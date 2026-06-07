package com.trainingvalidator.poc.training.models

import com.google.gson.annotations.SerializedName

enum class PlannedWorkoutItemType {
    @SerializedName("exercise")
    EXERCISE,

    @SerializedName("rest")
    REST,
}

enum class WorkoutExecutionContext {
    @SerializedName("free")
    FREE,

    @SerializedName("program")
    PROGRAM,

    @SerializedName("assessment")
    ASSESSMENT,

    @SerializedName("explore_workout")
    EXPLORE_WORKOUT,

    @SerializedName("quick_start")
    QUICK_START,
}

fun WorkoutExecutionContext.wireValue(): String = when (this) {
    WorkoutExecutionContext.FREE -> "free"
    WorkoutExecutionContext.PROGRAM -> "program"
    WorkoutExecutionContext.ASSESSMENT -> "assessment"
    WorkoutExecutionContext.EXPLORE_WORKOUT -> "explore_workout"
    WorkoutExecutionContext.QUICK_START -> "quick_start"
}

fun PlannedWorkoutItemType.wireValue(): String = when (this) {
    PlannedWorkoutItemType.EXERCISE -> "exercise"
    PlannedWorkoutItemType.REST -> "rest"
}

fun parseWorkoutExecutionContext(value: String?): WorkoutExecutionContext {
    return WorkoutExecutionContext.entries.firstOrNull { it.wireValue() == value }
        ?: WorkoutExecutionContext.FREE
}
