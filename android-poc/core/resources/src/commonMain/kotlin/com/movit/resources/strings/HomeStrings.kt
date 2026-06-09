package com.movit.resources.strings

import com.movit.resources.localizedString

data class HomeStrings(
    val language: String,
    val metricThisWeek: String,
    val metricFormAvg: String,
    val metricStreak: String,
    val yourLevel: String,
    val currentPlan: String,
    val activeProgramFallback: String,
    val viewProgram: String,
    val todaysWorkout: String,
    val dash: String,
    val completedToday: String,
    val readyToStart: String,
    val startWorkout: String,
    val activeRecovery: String,
    val restDay: String,
    val recoverySubtitle: String,
    val rest: String,
    val noWorkout: String,
    val takeItEasy: String,
    val viewTrain: String,
    val yourPlanLabel: String,
    val programComplete: String,
    val programCompleteSubtitle: String,
    val complete: String,
    val greatWork: String,
    val startReassessment: String,
    val startBodyScan: String,
    val startScan: String,
    val todayCaps: String,
    val unlockLevel: String,
    val assessment: String,
    val requiredBeforePlan: String,
    val planTimeline: String,
    val reassessmentDue: String,
    val soon: String,
    val lastSession: String,
    val greetingMorning: String,
    val greetingAfternoon: String,
    val greetingEvening: String,
    val greetingActive: String,
    val greetingRest: String,
    val greetingNoPlan: String,
    val greetingNoAssessment: String,
    val greetingProgramComplete: String,
    val greetingReassessment: String,
    val greetingDefault: String,
    val quickExplore: String,
    val quickExploreSub: String,
    val quickReports: String,
    val quickReportsSub: String,
    val actionUnavailable: String,
) {
    suspend fun levelTitle(level: Int, codeSuffix: String): String =
        localizedString(language, "home_level_title", level, codeSuffix)

    suspend fun levelCodeSuffix(code: String): String =
        localizedString(language, "home_level_code_suffix", code)

    suspend fun bodyScorePts(score: Int, pts: Int): String =
        localizedString(language, "home_body_score_pts", score, pts)

    suspend fun bodyScoreOnly(score: Int): String =
        localizedString(language, "home_body_score_only", score)

    suspend fun weekProgress(week: Int, total: Int, done: Int, totalW: Int): String =
        localizedString(language, "home_week_progress", week, total, done, totalW)

    suspend fun exercisesCount(count: Int): String =
        localizedString(language, "home_exercises_count", count)

    suspend fun minEst(min: Int): String =
        localizedString(language, "home_min_est", min)

    suspend fun workoutProgress(current: Int, total: Int): String =
        localizedString(language, "home_workout_progress", current, total)

    suspend fun weekDayLabel(week: Int, day: Int): String =
        localizedString(language, "home_week_day_label", week, day)

    suspend fun weekInProgress(week: Int, total: Int): String =
        localizedString(language, "home_week_in_progress", week, total)

    suspend fun activityForm(name: String, score: Int): String =
        localizedString(language, "home_activity_form", name, score)

    suspend fun activityReps(reps: Int, date: String): String =
        localizedString(language, "home_activity_reps", reps, date)

    suspend fun programWeekSubtitle(week: Int, totalWeeks: Int, completed: Int, total: Int): String =
        localizedString(language, "home_program_week_subtitle", week, totalWeeks, completed, total)

    suspend fun levelNamed(level: Int, name: String): String =
        localizedString(language, "home_level_named", level, name)

    suspend fun bodyScorePtsNext(score: Int, pts: Int): String =
        localizedString(language, "home_body_score_pts_next", score, pts)

    suspend fun minTotal(min: Int): String =
        localizedString(language, "home_min_total", min)

    suspend fun reportReps(reps: Int): String =
        localizedString(language, "home_report_reps", reps)

    companion object {
        suspend fun load(language: String): HomeStrings = HomeStrings(
            language = language,
            metricThisWeek = localizedString(language, "home_metric_this_week"),
            metricFormAvg = localizedString(language, "home_metric_form_avg"),
            metricStreak = localizedString(language, "home_metric_streak"),
            yourLevel = localizedString(language, "home_your_level"),
            currentPlan = localizedString(language, "home_current_plan"),
            activeProgramFallback = localizedString(language, "home_active_program_fallback"),
            viewProgram = localizedString(language, "home_view_program"),
            todaysWorkout = localizedString(language, "home_todays_workout"),
            dash = localizedString(language, "home_dash"),
            completedToday = localizedString(language, "home_completed_today"),
            readyToStart = localizedString(language, "home_ready_to_start"),
            startWorkout = localizedString(language, "home_start_workout"),
            activeRecovery = localizedString(language, "home_active_recovery"),
            restDay = localizedString(language, "home_rest_day"),
            recoverySubtitle = localizedString(language, "home_recovery_subtitle"),
            rest = localizedString(language, "home_rest"),
            noWorkout = localizedString(language, "home_no_workout"),
            takeItEasy = localizedString(language, "home_take_it_easy"),
            viewTrain = localizedString(language, "home_view_train"),
            yourPlanLabel = localizedString(language, "home_your_plan_label"),
            programComplete = localizedString(language, "home_program_complete"),
            programCompleteSubtitle = localizedString(language, "home_program_complete_subtitle"),
            complete = localizedString(language, "home_complete"),
            greatWork = localizedString(language, "home_great_work"),
            startReassessment = localizedString(language, "home_start_reassessment"),
            startBodyScan = localizedString(language, "home_start_body_scan"),
            startScan = localizedString(language, "home_start_scan"),
            todayCaps = localizedString(language, "home_today_caps"),
            unlockLevel = localizedString(language, "home_unlock_level"),
            assessment = localizedString(language, "home_assessment"),
            requiredBeforePlan = localizedString(language, "home_required_before_plan"),
            planTimeline = localizedString(language, "home_plan_timeline"),
            reassessmentDue = localizedString(language, "home_reassessment_due"),
            soon = localizedString(language, "home_soon"),
            lastSession = localizedString(language, "home_last_session"),
            greetingMorning = localizedString(language, "home_greeting_morning"),
            greetingAfternoon = localizedString(language, "home_greeting_afternoon"),
            greetingEvening = localizedString(language, "home_greeting_evening"),
            greetingActive = localizedString(language, "home_greeting_active"),
            greetingRest = localizedString(language, "home_greeting_rest"),
            greetingNoPlan = localizedString(language, "home_greeting_no_plan"),
            greetingNoAssessment = localizedString(language, "home_greeting_no_assessment"),
            greetingProgramComplete = localizedString(language, "home_greeting_program_complete"),
            greetingReassessment = localizedString(language, "home_greeting_reassessment"),
            greetingDefault = localizedString(language, "home_greeting_default"),
            quickExplore = localizedString(language, "home_quick_explore"),
            quickExploreSub = localizedString(language, "home_quick_explore_sub"),
            quickReports = localizedString(language, "home_quick_reports"),
            quickReportsSub = localizedString(language, "home_quick_reports_sub"),
            actionUnavailable = localizedString(language, "home_action_unavailable"),
        )
    }
}
