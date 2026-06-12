package com.movit.feature.train

sealed interface MovitTrainEffect {
    data object OpenSessionPreview : MovitTrainEffect
    data class OpenProgramWorkout(val target: TrainWorkoutLaunchUi) : MovitTrainEffect
    data object OpenProgramList : MovitTrainEffect
    data object OpenAssessment : MovitTrainEffect
    data class OpenProgramDetail(val programId: String) : MovitTrainEffect
    data class OpenProgramWeekPlan(
        val programId: String,
        val weekNumber: Int,
    ) : MovitTrainEffect
    data class OpenWeeklyReport(
        val programId: String,
        val weekNumber: Int,
    ) : MovitTrainEffect
    data object OpenExplore : MovitTrainEffect
    data object OpenReports : MovitTrainEffect
    data class ShowMessage(val message: String) : MovitTrainEffect
}
