package com.movit.feature.shell

/**
 * Stack entries for inner pages (no bottom nav — prototype sess-top + back).
 * Maps to library / training / account flows under the main tabs.
 */
sealed interface MovitInnerRoute {
    data object ExercisesLibrary : MovitInnerRoute
    data object WorkoutsLibrary : MovitInnerRoute
    data class ProgramDetail(val programId: String) : MovitInnerRoute
    data class WorkoutSession(val workoutId: String) : MovitInnerRoute
    data class ExercisePrepare(val exerciseId: String) : MovitInnerRoute
    data class ReportDetail(val reportId: String) : MovitInnerRoute
}
