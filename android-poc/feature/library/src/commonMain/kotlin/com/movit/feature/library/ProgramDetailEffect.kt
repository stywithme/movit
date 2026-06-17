package com.movit.feature.library

sealed interface ProgramDetailEffect {
    data class StartSession(val sessionKey: String) : ProgramDetailEffect
    data class ViewWeeklyReport(val weekNumber: Int) : ProgramDetailEffect
}
