package com.movit.resources.strings

import com.movit.resources.localizedString

data class ProgramFlowStrings(
    val language: String,
    val loadFailed: String,
    val programNotFound: String,
    val activeBadge: String,
    val allChip: String,
    val daySubtitleCompleted: String,
    val daySubtitleRest: String,
    val daySubtitlePlanned: String,
    val daySubtitleToday: String,
    val reportHeroTitle: String,
) {
    suspend fun dayShort(day: Int): String =
        localizedString(language, "program_flow_day_short", day)

    suspend fun weekTitle(week: Int): String =
        localizedString(language, "program_flow_week_title", week)

    suspend fun weekSubtitle(workouts: Int, rest: Int): String =
        localizedString(language, "program_flow_week_subtitle", workouts, rest)

    suspend fun daySubtitleTodayDetail(exercises: Int, minutes: Int?): String =
        if (minutes != null && minutes > 0) {
            localizedString(language, "program_flow_day_today_detail", exercises, minutes)
        } else {
            daySubtitleToday
        }

    suspend fun reportHeroEyebrow(week: Int): String =
        localizedString(language, "program_flow_report_hero_eyebrow", week)

    suspend fun reportHeroSubtitle(completed: Int, planned: Int): String =
        localizedString(language, "program_flow_report_hero_subtitle", completed, planned)

    suspend fun weekProgress(completed: Int, planned: Int): String =
        localizedString(language, "program_flow_week_progress", completed, planned)

    suspend fun weekReps(reps: String): String =
        localizedString(language, "program_flow_week_reps", reps)

    suspend fun weekForm(percent: Int): String =
        localizedString(language, "program_flow_week_form", percent)

    suspend fun weekMessage(completed: Int, planned: Int, avgForm: Int): String {
        if (planned == 0 || completed == 0) {
            return localizedString(language, "program_flow_week_message_start")
        }
        val progress = completed.toFloat() / planned
        return when {
            progress >= 0.9f && avgForm >= 85 ->
                localizedString(language, "program_flow_week_message_excellent")
            progress >= 0.6f ->
                localizedString(language, "program_flow_week_message_good")
            else -> localizedString(language, "program_flow_week_message_start")
        }
    }

    suspend fun shareBody(
        programName: String,
        weekNumber: Int,
        subtitle: String,
        completed: Int,
        planned: Int,
        formPercent: Int,
        reps: String,
    ): String = localizedString(
        language,
        "program_flow_share_body",
        programName,
        weekNumber,
        subtitle,
        completed,
        planned,
        formPercent,
        reps,
    )

    companion object {
        suspend fun load(language: String): ProgramFlowStrings = ProgramFlowStrings(
            language = language,
            loadFailed = localizedString(language, "program_flow_load_failed"),
            programNotFound = localizedString(language, "program_flow_program_not_found"),
            activeBadge = localizedString(language, "program_flow_active_badge"),
            allChip = localizedString(language, "program_flow_chip_all"),
            daySubtitleCompleted = localizedString(language, "program_flow_day_completed"),
            daySubtitleRest = localizedString(language, "program_flow_status_rest"),
            daySubtitlePlanned = localizedString(language, "program_flow_status_planned"),
            daySubtitleToday = localizedString(language, "program_flow_status_today"),
            reportHeroTitle = localizedString(language, "program_flow_report_hero_title"),
        )
    }
}
