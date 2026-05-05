package com.trainingvalidator.poc.training.models

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
    val weeks: List<ProgramWeek> = emptyList(),
    val weeklySessionTarget: Int? = null,
    val estimatedSessionMinutes: Int? = null,
    val isFeatured: Boolean = false
)

data class ProgramWeek(
    val weekNumber: Int,
    val name: LocalizedText? = null,
    val description: LocalizedText? = null,
    val days: List<ProgramDay> = emptyList()
)

data class ProgramDay(
    val dayNumber: Int,
    val isRestDay: Boolean = false,
    val name: LocalizedText? = null,
    val sessions: List<ProgramSession> = emptyList()
)

data class ProgramSession(
    val id: String,
    val name: LocalizedText,
    val sortOrder: Int = 0,
    val role: String = "MAIN",
    val estimatedDurationMin: Int? = null,
    val items: List<ProgramSessionItem> = emptyList()
)

data class ProgramSessionItem(
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
