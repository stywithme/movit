package com.movit.feature.library

sealed interface ProgramDetailEvent {
    data class TabSelected(val tab: ProgramDetailTab) : ProgramDetailEvent
    data class WeekSelected(val weekNumber: Int) : ProgramDetailEvent
    data class DaySelected(val dayNumber: Int) : ProgramDetailEvent
    data object StartProgramClicked : ProgramDetailEvent
    data class SessionMove(val sessionId: String, val direction: Int) : ProgramDetailEvent
    data class ExerciseParamChange(
        val sessionId: String,
        val exerciseId: String,
        val sets: Int? = null,
        val reps: Int? = null,
        val weightKg: Double? = null,
        val restSeconds: Int? = null,
    ) : ProgramDetailEvent
    data class RemoveSession(val sessionId: String) : ProgramDetailEvent
    data class RemoveExercise(val sessionId: String, val exerciseId: String) : ProgramDetailEvent
    data object ResetEditDay : ProgramDetailEvent
    data object SaveEdit : ProgramDetailEvent
    data object ViewWeeklyReportClicked : ProgramDetailEvent
    data object DownloadWeekOffline : ProgramDetailEvent
    data object RetryClicked : ProgramDetailEvent
}
