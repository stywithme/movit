package com.movit.resources.strings

import com.movit.resources.localizedString

data class ProgramDetailStrings(
    val language: String,
    val durationLabel: String,
    val weeklyTargetLabel: String,
    val sessionTimeLabel: String,
    val planLoadLabel: String,
    val weeklyHint: String,
    val sessionHint: String,
) {
    suspend fun durationValue(weeks: Int): String =
        localizedString(language, "program_stat_duration_value", weeks)

    suspend fun weeklyValue(target: Int): String =
        localizedString(language, "program_stat_weekly_value", target)

    suspend fun sessionValue(minutes: Int): String =
        localizedString(language, "program_stat_session_value", minutes)

    suspend fun planValue(sessions: Int): String =
        localizedString(language, "program_stat_plan_value", sessions)

    suspend fun durationHint(trainingDays: Int): String =
        localizedString(language, "program_stat_duration_hint", trainingDays)

    suspend fun planHint(restDays: Int): String =
        localizedString(language, "program_stat_plan_hint", restDays)

    companion object {
        suspend fun load(language: String): ProgramDetailStrings = ProgramDetailStrings(
            language = language,
            durationLabel = localizedString(language, "program_stat_duration"),
            weeklyTargetLabel = localizedString(language, "program_stat_weekly_target"),
            sessionTimeLabel = localizedString(language, "program_stat_session_time"),
            planLoadLabel = localizedString(language, "program_stat_plan_load"),
            weeklyHint = localizedString(language, "program_stat_weekly_hint"),
            sessionHint = localizedString(language, "program_stat_session_hint"),
        )
    }
}
