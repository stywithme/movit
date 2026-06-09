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
    val repCompare: List<ReportRepCompareUi>,
    val fatigueLabel: String,
    val fatigueTitle: String,
    val fatigueMessage: String,
    val fatigueProgressPercent: Int,
    val formBySetValues: List<Float>,
    val formBySetLabels: List<String>,
    val tips: List<ReportCoachingTipUi>,
)

object ReportDetailPreviewData {
    val squat = ReportDetailUi(
        id = "barbell-squat",
        exerciseName = "Barbell Squat",
        formScore = 92,
        badgeLabel = "Personal best",
        sets = "4",
        reps = "40",
        durationLabel = "12m",
        overviewInsightTitle = "Excellent depth consistency",
        overviewInsightMessage = "Hip crease stayed below knee line on 95% of reps.",
        joints = listOf(
            ReportJointScoreUi("Knees", 94, ReportScoreTone.Success),
            ReportJointScoreUi("Hips", 88, ReportScoreTone.Primary),
            ReportJointScoreUi("Spine", 76, ReportScoreTone.Warning),
        ),
        repCompare = listOf(
            ReportRepCompareUi("Best · Set 2", 97, isBest = true),
            ReportRepCompareUi("Worst · Set 4", 81, isBest = false),
        ),
        fatigueLabel = "FATIGUE INDEX",
        fatigueTitle = "Low",
        fatigueMessage = "Form dropped 6% from set 1 to set 4 — within normal range.",
        fatigueProgressPercent = 28,
        formBySetValues = listOf(95f, 97f, 90f, 81f),
        formBySetLabels = listOf("S1", "S2", "S3", "S4"),
        tips = listOf(
            ReportCoachingTipUi(
                title = "Brace your core earlier",
                message = "On the descent, engage core 0.5s before hip hinge to protect lumbar spine.",
            ),
            ReportCoachingTipUi(
                title = "Keep knees tracking toes",
                message = "Slight inward drift on set 4 — focus on pushing knees out.",
            ),
        ),
    )

    fun forId(reportId: String): ReportDetailUi? = when (reportId) {
        squat.id, "preview" -> squat.copy(id = reportId)
        "romanian-deadlift" -> squat.copy(
            id = reportId,
            exerciseName = "Romanian Deadlift",
            formScore = 85,
            badgeLabel = null,
        )
        "overhead-press" -> squat.copy(
            id = reportId,
            exerciseName = "Overhead Press",
            formScore = 71,
            badgeLabel = null,
        )
        else -> null
    }
}
