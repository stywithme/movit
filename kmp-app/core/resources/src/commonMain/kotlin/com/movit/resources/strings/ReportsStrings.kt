package com.movit.resources.strings

import com.movit.resources.localizedString

data class ReportsStrings(
    val language: String,
    val kpiDays: String,
    val kpiReps: String,
    val kpiVolume: String,
    val kpiTime: String,
    val formImproving: String,
    val fatigueLabel: String,
    val fatigueElevated: String,
    val fatigueModerate: String,
    val periodDefault: String,
) {
    suspend fun formAvgMessage(score: Int): String =
        localizedString(language, "reports_form_avg_message", score)

    suspend fun sessions(count: Int): String =
        localizedString(language, "reports_sessions", count)

    suspend fun weekShort(week: Int): String =
        localizedString(language, "reports_week_short", week)

    companion object {
        suspend fun load(language: String): ReportsStrings = ReportsStrings(
            language = language,
            kpiDays = localizedString(language, "reports_kpi_days"),
            kpiReps = localizedString(language, "reports_kpi_reps"),
            kpiVolume = localizedString(language, "reports_kpi_volume"),
            kpiTime = localizedString(language, "reports_kpi_time"),
            formImproving = localizedString(language, "reports_form_improving"),
            fatigueLabel = localizedString(language, "reports_fatigue_label"),
            fatigueElevated = localizedString(language, "reports_fatigue_elevated"),
            fatigueModerate = localizedString(language, "reports_fatigue_moderate"),
            periodDefault = localizedString(language, "reports_period_default"),
        )
    }
}
