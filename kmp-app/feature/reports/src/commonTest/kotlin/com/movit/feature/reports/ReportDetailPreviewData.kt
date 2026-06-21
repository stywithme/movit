package com.movit.feature.reports

import com.movit.resources.localizedString
import com.movit.resources.strings.ReportDetailStrings

object ReportDetailPreviewData {
    suspend fun squat(language: String = "en"): ReportDetailUi {
        val strings = ReportDetailStrings.load(language)
        return ReportDetailUi(
            id = "barbell-squat",
            exerciseName = "Barbell Squat",
            formScore = 92,
            badgeLabel = localizedString(language, "report_detail_preview_personal_best"),
            sets = "4",
            reps = "40",
            durationLabel = "12m",
            overviewInsightTitle = localizedString(language, "report_detail_preview_insight_title"),
            overviewInsightMessage = localizedString(language, "report_detail_preview_insight_message"),
            joints = listOf(
                ReportJointScoreUi(localizedString(language, "report_detail_preview_joint_knees"), 94, ReportScoreTone.Success),
                ReportJointScoreUi(localizedString(language, "report_detail_preview_joint_hips"), 88, ReportScoreTone.Primary),
                ReportJointScoreUi(localizedString(language, "report_detail_preview_joint_spine"), 76, ReportScoreTone.Warning),
            ),
            repCompare = listOf(
                ReportRepCompareUi(strings.bestSet(2), 97, isBest = true),
                ReportRepCompareUi(strings.worstSet(4), 81, isBest = false),
            ),
            fatigueLabel = strings.fatigueLabel,
            fatigueTitle = strings.fatigueLow,
            fatigueMessage = localizedString(language, "report_detail_preview_fatigue_message"),
            fatigueProgressPercent = 28,
            formBySetValues = listOf(95f, 97f, 90f, 81f),
            formBySetLabels = listOf(strings.setShort(1), strings.setShort(2), strings.setShort(3), strings.setShort(4)),
            tips = listOf(
                ReportCoachingTipUi(
                    title = localizedString(language, "report_detail_preview_tip1_title"),
                    message = localizedString(language, "report_detail_preview_tip1_message"),
                ),
                ReportCoachingTipUi(
                    title = localizedString(language, "report_detail_preview_tip2_title"),
                    message = localizedString(language, "report_detail_preview_tip2_message"),
                ),
            ),
        )
    }

    suspend fun forId(reportId: String, language: String = "en"): ReportDetailUi? {
        val base = squat(language)
        return when (reportId) {
            base.id, "preview" -> base.copy(id = reportId)
            "romanian-deadlift" -> base.copy(
                id = reportId,
                exerciseName = "Romanian Deadlift",
                formScore = 85,
                badgeLabel = null,
            )
            "overhead-press" -> base.copy(
                id = reportId,
                exerciseName = "Overhead Press",
                formScore = 71,
                badgeLabel = null,
            )
            else -> null
        }
    }
}
