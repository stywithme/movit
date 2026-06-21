package com.movit.resources.strings

import com.movit.resources.localizedString

data class TrainStrings(
    val language: String,
    val title: String,
    val activeProgram: String,
    val activePlan: String,
    val noActiveProgram: String,
    val zeroExercises: String,
    val explorePrograms: String,
    val programComplete: String,
    val programCompleteSub: String,
    val viewReport: String,
    val activeRecovery: String,
    val recoveryDay: String,
    val recoverToday: String,
    val noWorkout: String,
    val recovery: String,
    val exploreRecovery: String,
    val training: String,
    val startSession: String,
    val completed: String,
    val ready: String,
    val todaysWorkout: String,
    val viewSummary: String,
    val thisWeek: String,
    val readiness: String,
    val latestReport: String,
    val lastWorkout: String,
    val metricForm: String,
    val metricReps: String,
    val metricStreak: String,
    val metricMinutes: String,
    val programFallback: String,
    val featured: String,
    val workComplete: String,
    val currentDay: String,
    val statusRecovery: String,
    val findPlan: String,
    val assessment: String,
    val reassessmentDue: String,
    val startBodyScan: String,
    val startReassessment: String,
    val subtitleActive: String,
    val subtitleCompleteToday: String,
    val subtitleRest: String,
    val subtitleProgramComplete: String,
    val subtitleNoAssessment: String,
    val subtitleReassessment: String,
    val subtitleNoPlan: String,
    val readyMsgActive: String,
    val readyMsgRest: String,
    val readyMsgComplete: String,
    val readyMsgProgramDone: String,
    val readyMsgNoPlan: String,
    val guideActive: String,
    val guideRest: String,
    val guideComplete: String,
    val guideProgramDone: String,
    val guideNoPlan: String,
    val quickExplore: String,
    val quickExploreSub: String,
    val quickReports: String,
    val quickReportsSub: String,
    val quickTune: String,
    val quickTuneSub: String,
    val prefsLater: String,
    val focusProgram: String,
    val focusProgress: String,
    val complete: String,
    val dash: String,
    val statusToday: String,
    val statusUpcoming: String,
    val statusInProgress: String,
    val statusCompleted: String,
    val statusMissed: String,
    val statusRest: String,
) {
    suspend fun weekDayPosition(week: Int, total: Int, day: Int): String =
        localizedString(language, "train_week_day_position", week, total, day)

    suspend fun weekProgressLabel(completed: Int, total: Int): String =
        localizedString(language, "train_week_progress", completed, total)

    suspend fun weekdayShort(index: Int): String =
        localizedString(language, "train_weekday_${index.coerceIn(0, 6)}")

    suspend fun workoutsThisWeek(count: Int): String =
        localizedString(language, "train_workouts_this_week", count)

    suspend fun dayStreak(days: Int): String =
        localizedString(language, "train_day_streak", days)

    suspend fun exercisesCount(count: Int): String =
        localizedString(language, "train_exercises_count", count)

    suspend fun minEst(min: Int): String =
        localizedString(language, "home_min_est", min)

    suspend fun workoutNOfM(current: Int, total: Int): String =
        localizedString(language, "train_workout_n_of_m", current, total)

    suspend fun weekDaySub(week: Int, day: Int): String =
        localizedString(language, "train_week_day_sub", week, day)

    suspend fun weekTitle(week: Int): String =
        localizedString(language, "train_week_title", week)

    suspend fun dayShort(day: Int): String =
        localizedString(language, "train_day_short", day)

    suspend fun restInProgram(name: String): String =
        localizedString(language, "train_rest_in_program", name)

    suspend fun guidedPlan(weeks: Int): String =
        localizedString(language, "train_guided_plan", weeks)

    suspend fun weeksCount(weeks: Int): String =
        localizedString(language, "train_weeks", weeks)

    suspend fun streakShort(days: Int): String =
        localizedString(language, "train_streak_short", days)

    companion object {
        suspend fun load(language: String): TrainStrings = TrainStrings(
            language = language,
            title = localizedString(language, "train_title"),
            activeProgram = localizedString(language, "train_active_program"),
            activePlan = localizedString(language, "train_active_plan"),
            noActiveProgram = localizedString(language, "train_no_active_program"),
            zeroExercises = localizedString(language, "train_zero_exercises"),
            explorePrograms = localizedString(language, "train_explore_programs"),
            programComplete = localizedString(language, "train_program_complete"),
            programCompleteSub = localizedString(language, "train_program_complete_sub"),
            viewReport = localizedString(language, "train_view_report"),
            activeRecovery = localizedString(language, "train_active_recovery"),
            recoveryDay = localizedString(language, "train_recovery_day"),
            recoverToday = localizedString(language, "train_recover_today"),
            noWorkout = localizedString(language, "train_no_workout"),
            recovery = localizedString(language, "train_recovery"),
            exploreRecovery = localizedString(language, "train_explore_recovery"),
            training = localizedString(language, "train_training"),
            startSession = localizedString(language, "train_start_session"),
            completed = localizedString(language, "train_completed"),
            ready = localizedString(language, "train_ready"),
            todaysWorkout = localizedString(language, "train_todays_workout"),
            viewSummary = localizedString(language, "train_view_summary"),
            thisWeek = localizedString(language, "train_this_week"),
            readiness = localizedString(language, "train_readiness"),
            latestReport = localizedString(language, "train_latest_report"),
            lastWorkout = localizedString(language, "train_last_workout"),
            metricForm = localizedString(language, "train_metric_form"),
            metricReps = localizedString(language, "train_metric_reps"),
            metricStreak = localizedString(language, "train_metric_streak"),
            metricMinutes = localizedString(language, "train_metric_minutes"),
            programFallback = localizedString(language, "train_program_fallback"),
            featured = localizedString(language, "train_featured"),
            workComplete = localizedString(language, "train_work_complete"),
            currentDay = localizedString(language, "train_current_day"),
            statusRecovery = localizedString(language, "train_status_recovery"),
            findPlan = localizedString(language, "train_find_plan"),
            assessment = localizedString(language, "home_assessment"),
            reassessmentDue = localizedString(language, "home_reassessment_due"),
            startBodyScan = localizedString(language, "home_start_body_scan"),
            startReassessment = localizedString(language, "home_start_reassessment"),
            subtitleActive = localizedString(language, "train_subtitle_active"),
            subtitleCompleteToday = localizedString(language, "train_subtitle_complete_today"),
            subtitleRest = localizedString(language, "train_subtitle_rest"),
            subtitleProgramComplete = localizedString(language, "train_subtitle_program_complete"),
            subtitleNoAssessment = localizedString(language, "train_subtitle_no_assessment"),
            subtitleReassessment = localizedString(language, "train_subtitle_reassessment"),
            subtitleNoPlan = localizedString(language, "train_subtitle_no_plan"),
            readyMsgActive = localizedString(language, "train_ready_msg_active"),
            readyMsgRest = localizedString(language, "train_ready_msg_rest"),
            readyMsgComplete = localizedString(language, "train_ready_msg_complete"),
            readyMsgProgramDone = localizedString(language, "train_ready_msg_program_done"),
            readyMsgNoPlan = localizedString(language, "train_ready_msg_no_plan"),
            guideActive = localizedString(language, "train_guide_active"),
            guideRest = localizedString(language, "train_guide_rest"),
            guideComplete = localizedString(language, "train_guide_complete"),
            guideProgramDone = localizedString(language, "train_guide_program_done"),
            guideNoPlan = localizedString(language, "train_guide_no_plan"),
            quickExplore = localizedString(language, "train_quick_explore"),
            quickExploreSub = localizedString(language, "train_quick_explore_sub"),
            quickReports = localizedString(language, "train_quick_reports"),
            quickReportsSub = localizedString(language, "train_quick_reports_sub"),
            quickTune = localizedString(language, "train_quick_tune"),
            quickTuneSub = localizedString(language, "train_quick_tune_sub"),
            prefsLater = localizedString(language, "train_prefs_later"),
            focusProgram = localizedString(language, "train_focus_program"),
            focusProgress = localizedString(language, "train_focus_progress"),
            complete = localizedString(language, "home_complete"),
            dash = localizedString(language, "home_dash"),
            statusToday = localizedString(language, "train_status_today"),
            statusUpcoming = localizedString(language, "train_status_upcoming"),
            statusInProgress = localizedString(language, "train_status_in_progress"),
            statusCompleted = localizedString(language, "train_status_completed"),
            statusMissed = localizedString(language, "ds_week_legend_missed"),
            statusRest = localizedString(language, "train_status_rest"),
        )
    }
}
