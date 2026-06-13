package com.movit.feature.reports

enum class ReportDetailPage {
    Overview,
    Form,
    Fatigue,
    Tips,
}

data class ReportJointScoreUi(
    val label: String,
    val scorePercent: Int,
    val tone: ReportScoreTone,
)

enum class ReportScoreTone {
    Success,
    Primary,
    Warning,
}

enum class ReportJointsEmptyReason {
    Generic,
    ApiPending,
    SessionUntracked,
}

data class ReportRepCompareUi(
    val label: String,
    val score: Int,
    val isBest: Boolean,
)

data class ReportCoachingTipUi(
    val title: String,
    val message: String,
)

data class ReportDetailUi(
    val id: String,
    val exerciseName: String,
    val formScore: Int,
    val badgeLabel: String?,
    val sets: String,
    val reps: String,
    val durationLabel: String,
    val overviewInsightTitle: String,
    val overviewInsightMessage: String,
    val joints: List<ReportJointScoreUi>,
    val jointsEmptyReason: ReportJointsEmptyReason = ReportJointsEmptyReason.Generic,
    val repCompare: List<ReportRepCompareUi>,
    val fatigueLabel: String,
    val fatigueTitle: String,
    val fatigueMessage: String,
    val fatigueProgressPercent: Int,
    val formBySetValues: List<Float>,
    val formBySetLabels: List<String>,
    val tips: List<ReportCoachingTipUi>,
)

