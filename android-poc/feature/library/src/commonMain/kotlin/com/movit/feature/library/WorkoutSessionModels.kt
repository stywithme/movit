package com.movit.feature.library

data class WorkoutSessionUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val exerciseCount: Int,
    val durationLabel: String,
    val setCount: Int,
    val sections: List<WorkoutSessionSectionUi>,
    val context: WorkoutSessionContextUi? = null,
)

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
        val reps: Int? = 12,
        val durationSeconds: Int? = null,
        val restSeconds: Int = 60,
        val weightKg: Float? = null,
        val phaseRole: String = "MAIN",
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

object WorkoutSessionPreviewData {
    val preview: WorkoutSessionUi = WorkoutSessionUi(
        id = "preview",
        title = "Day 3 · Lower Body",
        subtitle = "Week 2 · Strength focus",
        exerciseCount = 6,
        durationLabel = "~45m",
        setCount = 18,
        sections = listOf(
            WorkoutSessionSectionUi(
                title = "Warm-up",
                phaseRole = "WARMUP",
                items = listOf(
                    WorkoutSessionBlockUi.Exercise(
                        id = "ex-squat-warm",
                        exerciseSlug = "bodyweight-squat",
                        index = 1,
                        name = "Bodyweight Squat",
                        category = "Quads · Mobility",
                        imageUrl = "https://images.unsplash.com/photo-1518611012118-696072aa579a?auto=format&fit=crop&w=200&q=70",
                        sets = 2,
                        reps = 15,
                        restSeconds = 30,
                        setsLabel = "2 × 15",
                        restLabel = "30s rest",
                        phaseRole = "WARMUP",
                    ),
                    WorkoutSessionBlockUi.Exercise(
                        id = "ex-leg-swings",
                        exerciseSlug = "leg-swings",
                        index = 2,
                        name = "Leg Swings",
                        category = "Hips · Mobility",
                        imageUrl = "https://images.unsplash.com/photo-1544367567-0f2fcb009e0b?auto=format&fit=crop&w=200&q=70",
                        sets = 2,
                        durationSeconds = 20,
                        restSeconds = 20,
                        setsLabel = "2 × 20s",
                        restLabel = "20s rest",
                        phaseRole = "WARMUP",
                    ),
                ),
            ),
            WorkoutSessionSectionUi(
                title = "Main workout",
                phaseRole = "MAIN",
                items = listOf(
                    WorkoutSessionBlockUi.Exercise(
                        id = "ex-barbell-squat",
                        exerciseSlug = "barbell-squat",
                        index = 1,
                        name = "Barbell Squat",
                        category = "Quads · Glutes",
                        imageUrl = "https://images.unsplash.com/photo-1574680096145-d05b474e2155?auto=format&fit=crop&w=200&q=70",
                        sets = 3,
                        reps = 12,
                        weightKg = 40f,
                        restSeconds = 60,
                        setsLabel = "3 × 12",
                        weightLabel = "40 kg",
                        restLabel = "60s rest",
                        phaseRole = "MAIN",
                    ),
                    WorkoutSessionBlockUi.Exercise(
                        id = "ex-rdl",
                        exerciseSlug = "romanian-deadlift",
                        index = 2,
                        name = "Romanian Deadlift",
                        category = "Hamstrings · Back",
                        imageUrl = "https://images.unsplash.com/photo-1534438327276-14e5300c3a48?auto=format&fit=crop&w=200&q=70",
                        sets = 3,
                        reps = 10,
                        weightKg = 30f,
                        restSeconds = 60,
                        setsLabel = "3 × 10",
                        weightLabel = "30 kg",
                        restLabel = "60s rest",
                        phaseRole = "MAIN",
                    ),
                    WorkoutSessionBlockUi.Rest(
                        id = "rest-90",
                        durationLabel = "90s",
                        durationSeconds = 90,
                    ),
                    WorkoutSessionBlockUi.Exercise(
                        id = "ex-lunge",
                        exerciseSlug = "walking-lunge",
                        index = 3,
                        name = "Walking Lunge",
                        category = "Quads · Glutes",
                        imageUrl = "https://images.unsplash.com/photo-1571019613454-1cb2f99b2d8b?auto=format&fit=crop&w=200&q=70",
                        sets = 3,
                        reps = 12,
                        restSeconds = 45,
                        setsLabel = "3 × 12",
                        restLabel = "45s rest",
                        phaseRole = "MAIN",
                    ),
                ),
            ),
            WorkoutSessionSectionUi(
                title = "Cool-down",
                phaseRole = "COOLDOWN",
                items = listOf(
                    WorkoutSessionBlockUi.Exercise(
                        id = "ex-quad-stretch",
                        exerciseSlug = "quad-stretch",
                        index = 1,
                        name = "Quad Stretch",
                        category = "Quads · Recovery",
                        imageUrl = "https://images.unsplash.com/photo-1571019613454-1cb2f99b2d8b?auto=format&fit=crop&w=200&q=70",
                        sets = 2,
                        durationSeconds = 30,
                        restSeconds = 0,
                        setsLabel = "2 × 30s",
                        restLabel = "",
                        phaseRole = "COOLDOWN",
                    ),
                ),
            ),
        ),
    )
}
