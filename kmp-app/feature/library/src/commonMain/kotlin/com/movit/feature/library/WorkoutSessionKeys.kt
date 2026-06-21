package com.movit.feature.library

data class WorkoutSessionContextUi(
    val programId: String,
    val programSlug: String,
    val weekNumber: Int,
    val dayNumber: Int,
    val plannedWorkoutId: String,
)

data class ParsedSessionKey(
    val programId: String,
    val weekNumber: Int,
    val dayNumber: Int,
    val plannedWorkoutId: String,
)

object WorkoutSessionKeys {
    private const val PREFIX = "session:"

    /** Resolves to the first planned workout when loading a program day (catch-up / home). */
    const val AUTO_PLANNED_WORKOUT = "_auto"

    fun encode(
        programId: String,
        weekNumber: Int,
        dayNumber: Int,
        plannedWorkoutId: String,
    ): String = "$PREFIX$programId:$weekNumber:$dayNumber:$plannedWorkoutId"

    fun encode(context: WorkoutSessionContextUi): String = encode(
        programId = context.programId,
        weekNumber = context.weekNumber,
        dayNumber = context.dayNumber,
        plannedWorkoutId = context.plannedWorkoutId,
    )

    fun parse(workoutId: String): ParsedSessionKey? {
        if (!workoutId.startsWith(PREFIX)) return null
        val parts = workoutId.removePrefix(PREFIX).split(":")
        if (parts.size < 4) return null
        val plannedWorkoutId = parts.last()
        val dayNumber = parts[parts.size - 2].toIntOrNull() ?: return null
        val weekNumber = parts[parts.size - 3].toIntOrNull() ?: return null
        val programId = parts.dropLast(3).joinToString(":")
        if (programId.isBlank() || plannedWorkoutId.isBlank()) return null
        return ParsedSessionKey(
            programId = programId,
            weekNumber = weekNumber,
            dayNumber = dayNumber,
            plannedWorkoutId = plannedWorkoutId,
        )
    }
}
