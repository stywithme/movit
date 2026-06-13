package com.movit.feature.shell

import com.movit.core.training.session.TrainingFlowItem
import com.movit.feature.library.PlannedWorkoutLaunch

/**
 * Stack entries for inner pages (no bottom nav — prototype sess-top + back).
 * Maps to library / training / account flows under the main tabs.
 */
sealed interface MovitInnerRoute {
    data object ExercisesLibrary : MovitInnerRoute
    data object WorkoutsLibrary : MovitInnerRoute
    data object ProgramList : MovitInnerRoute
    data class WeeklyReport(val programId: String, val weekNumber: Int = 1) : MovitInnerRoute
    data class ProgramDetail(
        val programId: String,
        val initialWeekNumber: Int? = null,
    ) : MovitInnerRoute
    data class WorkoutSession(val workoutId: String) : MovitInnerRoute
    data class ExercisePrepare(
        val exerciseId: String,
        val workoutId: String? = null,
        val prepareMode: String = "prepare",
        val restSeconds: Int? = null,
        val upNextExerciseId: String? = null,
    ) : MovitInnerRoute
    data class ExerciseLive(
        val exerciseSlug: String,
        val exerciseName: String,
        val targetReps: Int,
        val workoutId: String? = null,
    ) : MovitInnerRoute
    data class TrainingSession(
        val exerciseSlug: String,
        val exerciseName: String,
        val targetReps: Int,
        val workoutId: String? = null,
        val flowItems: List<TrainingFlowItem>? = null,
        val plannedWorkout: PlannedWorkoutLaunch? = null,
        val startExerciseIndex: Int = 0,
    ) : MovitInnerRoute
    data class ReportDetail(val reportId: String) : MovitInnerRoute
    data object Auth : MovitInnerRoute
    data object ProfileOnboarding : MovitInnerRoute
    data class Assessment(val mode: String = "initial") : MovitInnerRoute
    data object LevelProfile : MovitInnerRoute
}
