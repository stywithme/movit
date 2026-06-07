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
    val levelMinId: String? = null,
    val levelMaxId: String? = null,
    val levelMin: ProgramLevelConfig? = null,
    val levelMax: ProgramLevelConfig? = null,
    val tags: List<String> = emptyList(),
    @SerializedName("weeks") private val weeksField: List<ProgramWeek>? = null,
    val weeklyWorkoutTarget: Int? = null,
    val estimatedWorkoutMinutes: Int? = null,
    val isFeatured: Boolean = false
) {
    val weeks: List<ProgramWeek> get() = weeksField.orEmpty()
}

data class ProgramLevelConfig(
    val id: String? = null,
    val number: Int = 0,
    val code: String = "",
    val name: LocalizedText = LocalizedText()
)

data class ProgramWeek(
    val weekNumber: Int,
    val target: LocalizedText? = null,
    val description: LocalizedText? = null,
    @SerializedName("days") private val daysField: List<ProgramDay>? = null
) {
    val days: List<ProgramDay> get() = daysField.orEmpty()
}

data class MuscleRef(
    val code: String,
    val name: LocalizedText = LocalizedText()
)

data class ProgramDay(
    val dayNumber: Int,
    val dayType: String = "training",
    val isRestDay: Boolean = false,
    val targetMuscles: List<MuscleRef> = emptyList(),
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
    val workoutTemplateId: String? = null,
    val workoutTemplateSlug: String? = null,
    val estimatedDurationMin: Int? = null,
    @SerializedName("phases") private val phasesField: List<WorkoutPhaseConfig>? = null,
    @SerializedName("items") private val itemsField: List<WorkoutLineItem>? = null
) {
    val phases: List<WorkoutPhaseConfig> get() = phasesField.orEmpty()
    val items: List<WorkoutLineItem> get() = itemsField.orEmpty()
}

data class WorkoutLineItem(
    val type: PlannedWorkoutItemType,
    val serverItemId: String? = null,
    val exerciseSlug: String? = null,
    val deletedExercise: Boolean? = null,
    val sets: Int? = null,
    val targetReps: Int? = null,
    val targetRepsPerSet: List<Int>? = null,
    val targetDuration: Int? = null,
    val restBetweenSetsMs: Long? = null,
    val restBetweenSetsPerSetMs: List<Long>? = null,
    val weightPerSet: List<Float>? = null,
    val notes: LocalizedText? = null,
    val restDurationMs: Long? = null,
    val suggestionSource: String? = null,
    val variantIndex: Int? = null,
    val phaseIndex: Int? = null,
    val phaseRole: String? = null,
    val phaseCanSkip: Boolean? = null,
    val phaseCanContinue: Boolean? = null,
    val phaseMaxContinueTimeMs: Long? = null,
    val sortOrder: Int = 0
)
