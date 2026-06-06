package com.trainingvalidator.poc.training.models

import com.google.gson.annotations.SerializedName

/**
 * ProgramConfig - Program structure from backend
 */
data class ProgramConfig(
    val id: String,
    val slug: String,
    val name: LocalizedText,
    val description: LocalizedText? = null,
    val coverImageUrl: String? = null,
    val durationWeeks: Int,
    val levelRangeMin: Int = 0,
    val levelRangeMax: Int = 0,
    val tags: List<String> = emptyList(),
    @SerializedName("weeks") private val weeksField: List<ProgramWeek>? = null,
    val weeklyWorkoutTarget: Int? = null,
    val estimatedWorkoutMinutes: Int? = null,
    val isFeatured: Boolean = false
) {
    val weeks: List<ProgramWeek> get() = weeksField.orEmpty()
}

data class ProgramWeek(
    val weekNumber: Int,
    val name: LocalizedText? = null,
    val description: LocalizedText? = null,
    @SerializedName("days") private val daysField: List<ProgramDay>? = null
) {
    val days: List<ProgramDay> get() = daysField.orEmpty()
}

data class ProgramDay(
    val dayNumber: Int,
    val isRestDay: Boolean = false,
    val name: LocalizedText? = null,
    @SerializedName("plannedWorkouts") private val plannedWorkoutsField: List<ProgramWorkout>? = null,
    @SerializedName("sessions") private val legacySessionsField: List<ProgramWorkout>? = null
) {
    val workouts: List<ProgramWorkout>
        get() = plannedWorkoutsField ?: legacySessionsField ?: emptyList()
}

data class ProgramWorkout(
    val id: String,
    val name: LocalizedText,
    val sortOrder: Int = 0,
    val role: String = "MAIN",
    val estimatedDurationMin: Int? = null,
    @SerializedName("items") private val itemsField: List<WorkoutLineItem>? = null
) {
    val items: List<WorkoutLineItem> get() = itemsField.orEmpty()
}

data class WorkoutLineItem(
    val type: String,
    val serverItemId: String? = null,
    val exerciseSlug: String? = null,
    val deletedExercise: Boolean? = null,
    val sets: Int? = null,
    val targetReps: Int? = null,
    val targetDuration: Int? = null,
    val restBetweenSetsMs: Long? = null,
    val weightKg: Float? = null,
    val weightPerSet: List<Float>? = null,
    val notes: LocalizedText? = null,
    val restDurationMs: Long? = null,
    val suggestionSource: String? = null,
    val variantIndex: Int? = null,
    val sortOrder: Int = 0
)
