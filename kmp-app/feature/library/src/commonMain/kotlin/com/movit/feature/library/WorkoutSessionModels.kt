package com.movit.feature.library

data class PlannedWorkoutCardUi(
    val id: String,
    val title: String,
    val exerciseCount: Int,
    val durationLabel: String,
    val isSelected: Boolean,
)

data class SessionCatchUpPromptUi(
    val message: String,
    val missedWeekNumber: Int,
    val missedDayNumber: Int,
)

data class WorkoutSessionUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val exerciseCount: Int,
    val durationLabel: String,
    val setCount: Int,
    val sections: List<WorkoutSessionSectionUi>,
    val context: WorkoutSessionContextUi? = null,
    val warmupSkipped: Boolean = false,
)

fun WorkoutSessionUi.firstExerciseSlug(): String? =
    sections
        .asSequence()
        .flatMap { it.items.asSequence() }
        .filterIsInstance<WorkoutSessionBlockUi.Exercise>()
        .firstOrNull()
        ?.exerciseSlug

fun WorkoutSessionUi.firstExerciseId(): String? =
    sectionsForTraining()
        .asSequence()
        .flatMap { it.items.asSequence() }
        .filterIsInstance<WorkoutSessionBlockUi.Exercise>()
        .firstOrNull()
        ?.id

fun WorkoutSessionUi.hasWarmupSection(): Boolean =
    sections.any { it.phaseRole == "WARMUP" && it.items.any { block -> block is WorkoutSessionBlockUi.Exercise } }

fun WorkoutSessionUi.sectionsForTraining(): List<WorkoutSessionSectionUi> =
    if (warmupSkipped) sections.filter { it.phaseRole != "WARMUP" } else sections

fun WorkoutSessionUi.withoutWarmup(): WorkoutSessionUi {
    if (!hasWarmupSection() || warmupSkipped) return this
    return copy(warmupSkipped = true).recalculated()
}

data class WorkoutSessionSectionUi(
    val title: String,
    val phaseRole: String,
    val items: List<WorkoutSessionBlockUi>,
)

sealed interface WorkoutSessionBlockUi {
    val id: String

    data class Exercise(
        override val id: String,
        val exerciseSlug: String,
        val index: Int,
        val name: String,
        val category: String,
        val imageUrl: String? = null,
        val setsLabel: String,
        val weightLabel: String? = null,
        val restLabel: String,
        val sets: Int = 3,
        val reps: Int? = null,
        val durationSeconds: Int? = null,
        /** Rest between sets (seconds) — UI display. */
        val restSeconds: Int = 60,
        val weightKg: Float? = null,
        val phaseRole: String = "MAIN",
        val variantIndex: Int = 0,
        /** Rest after this exercise (seconds); may also appear as a following Rest block. */
        val restAfterExerciseSeconds: Int = 0,
        val restBetweenSetsMs: Long = 0L,
        val restAfterExerciseMs: Long = 0L,
        val weightPerSetKg: List<Float>? = null,
    ) : WorkoutSessionBlockUi

    data class Rest(
        override val id: String,
        val durationLabel: String,
        val durationSeconds: Int = 90,
    ) : WorkoutSessionBlockUi
}

data class ExerciseEditDraft(
    val sets: Int,
    val reps: Int?,
    val durationSeconds: Int?,
    val weightKg: Float?,
    val restSeconds: Int,
)

sealed interface SessionSheet {
    data class Swap(
        val exerciseId: String,
        val exerciseName: String,
        val exerciseCategory: String,
        val replacingSlug: String,
        val query: String = "",
        val candidates: List<SessionSwapCandidateUi> = emptyList(),
        val isLoadingCandidates: Boolean = false,
    ) : SessionSheet

    data class EditDetails(
        val exerciseId: String,
        val draft: ExerciseEditDraft,
    ) : SessionSheet

    data class AddExercise(
        val sectionPhaseRole: String,
        val query: String = "",
        val candidates: List<SessionSwapCandidateUi> = emptyList(),
        val isLoadingCandidates: Boolean = false,
    ) : SessionSheet

    data class EditRest(
        val restId: String,
        val durationSeconds: Int,
    ) : SessionSheet
}

object WorkoutSessionFormatting {
    fun setsLabel(sets: Int, reps: Int?, durationSeconds: Int?): String = when {
        durationSeconds != null && reps == null -> "$sets × ${durationSeconds}s"
        reps != null -> "$sets × $reps"
        else -> "$sets sets"
    }

    fun restLabel(restSeconds: Int): String =
        if (restSeconds <= 0) "" else "${restSeconds}s rest"

    fun weightLabel(weightKg: Float?): String? {
        val value = weightKg?.takeIf { it > 0f } ?: return null
        val text = if (value == value.toInt().toFloat()) {
            value.toInt().toString()
        } else {
            value.toString()
        }
        return "$text kg"
    }

    fun restDurationLabel(restSeconds: Int): String = "${restSeconds}s"

    fun estimateDurationMinutes(sections: List<WorkoutSessionSectionUi>): Int {
        var totalSeconds = 0
        sections.forEach { section ->
            section.items.forEach { block ->
                when (block) {
                    is WorkoutSessionBlockUi.Exercise -> {
                        val sets = block.sets.coerceAtLeast(1)
                        val perSet = (block.durationSeconds ?: 30) + block.restSeconds
                        totalSeconds += sets * perSet
                    }
                    is WorkoutSessionBlockUi.Rest -> totalSeconds += block.durationSeconds
                }
            }
        }
        return (totalSeconds / 60).coerceAtLeast(1)
    }
}

fun WorkoutSessionBlockUi.Exercise.withUpdatedMetrics(
    sets: Int = this.sets,
    reps: Int? = this.reps,
    durationSeconds: Int? = this.durationSeconds,
    restSeconds: Int = this.restSeconds,
    weightKg: Float? = this.weightKg,
): WorkoutSessionBlockUi.Exercise = copy(
    sets = sets,
    reps = reps,
    durationSeconds = durationSeconds,
    restSeconds = restSeconds,
    weightKg = weightKg,
    setsLabel = WorkoutSessionFormatting.setsLabel(sets, reps, durationSeconds),
    restLabel = WorkoutSessionFormatting.restLabel(restSeconds),
    weightLabel = WorkoutSessionFormatting.weightLabel(weightKg),
)

fun WorkoutSessionUi.recalculated(): WorkoutSessionUi {
    val exercises = sections.flatMap { it.items }.filterIsInstance<WorkoutSessionBlockUi.Exercise>()
    return copy(
        exerciseCount = exercises.size,
        setCount = exercises.sumOf { it.sets },
        durationLabel = "~${WorkoutSessionFormatting.estimateDurationMinutes(sections)}m",
    )
}
