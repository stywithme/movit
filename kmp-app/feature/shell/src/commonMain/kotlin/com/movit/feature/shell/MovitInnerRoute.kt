package com.movit.feature.shell

import com.movit.core.training.session.TrainingFlowItem
import com.movit.feature.library.PlannedWorkoutLaunch
import com.movit.feature.library.ReturnTarget

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
        /** Encoded [com.movit.feature.library.ExercisePrepareMode] / rest phase. */
        val prepareMode: String = "solo",
        val runId: String? = null,
        val restSeconds: Int? = null,
        val upNextExerciseId: String? = null,
    ) : MovitInnerRoute
    data class TrainingSession(
        val exerciseSlug: String,
        val exerciseName: String,
        val targetReps: Int,
        val workoutId: String? = null,
        val flowItems: List<TrainingFlowItem>? = null,
        val plannedWorkout: PlannedWorkoutLaunch? = null,
        val startExerciseIndex: Int = 0,
        val poseVariantIndex: Int = 0,
        val runId: String? = null,
    ) : MovitInnerRoute
    data class ReportDetail(
        val reportId: String,
        val returnTarget: ReturnTarget? = null,
        val doneTarget: ReturnTarget? = null,
    ) : MovitInnerRoute
    data object Auth : MovitInnerRoute
    /** Account / settings — opened from header avatar, not the bottom nav. */
    data object Profile : MovitInnerRoute
    data object ProfileOnboarding : MovitInnerRoute
    data class Assessment(val mode: String = "initial") : MovitInnerRoute
    data object LevelProfile : MovitInnerRoute
    /** Hidden debug/internal route — Training Debug Lab (D10). */
    data class TrainingDebugLab(val exerciseSlug: String? = null) : MovitInnerRoute
}
