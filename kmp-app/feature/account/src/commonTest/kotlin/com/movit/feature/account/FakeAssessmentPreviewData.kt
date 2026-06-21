package com.movit.feature.account

object FakeAssessmentPreviewData {
    val results = AssessmentResultsUi(
        bodyScore = 72,
        levelLabel = "Level 2 · Building",
        domains = listOf(
            AssessmentDomainUi("mobility", 78),
            AssessmentDomainUi("control", 65),
            AssessmentDomainUi("symmetry", 71),
            AssessmentDomainUi("safety", 74),
        ),
        regions = listOf(
            AssessmentRegionUi("hips", 84, AssessmentRegionTone.Good),
            AssessmentRegionUi("shoulders", 61, AssessmentRegionTone.Warning),
            AssessmentRegionUi("spine", 74, AssessmentRegionTone.Neutral),
            AssessmentRegionUi("knees", 79, AssessmentRegionTone.Good),
        ),
        insights = listOf(
            AssessmentInsightUi(
                titleKey = "assessment_insight_shoulder_title",
                messageKey = "assessment_insight_shoulder_message",
            ),
            AssessmentInsightUi(
                titleKey = "assessment_insight_hip_title",
                messageKey = "assessment_insight_hip_message",
            ),
        ),
    )

    val parqQuestions = AssessmentDefaults.parqQuestions

    val bodyScanMovements = listOf(
        "assessment_movement_forward_fold",
        "assessment_movement_overhead_squat",
        "assessment_movement_single_leg_balance",
    )
}
