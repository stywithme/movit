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

    val parqQuestions = listOf(
        "assessment_parq_heart",
        "assessment_parq_chest_pain",
        "assessment_parq_dizziness",
        "assessment_parq_bone_joint",
        "assessment_parq_medication",
        "assessment_parq_other_reason",
        "assessment_parq_pregnancy",
    )

    val bodyScanMovements = listOf(
        "assessment_movement_forward_fold",
        "assessment_movement_overhead_squat",
        "assessment_movement_single_leg_balance",
    )
}

data class AssessmentResultsUi(
    val bodyScore: Int,
    val levelLabel: String,
    val domains: List<AssessmentDomainUi> = emptyList(),
    val regions: List<AssessmentRegionUi>,
    val insights: List<AssessmentInsightUi>,
)

data class AssessmentDomainUi(
    val domainKey: String,
    val score: Int,
)

data class AssessmentRegionUi(
    val regionKey: String,
    val score: Int,
    val tone: AssessmentRegionTone,
)

enum class AssessmentRegionTone {
    Good,
    Warning,
    Neutral,
}

data class AssessmentInsightUi(
    val titleKey: String,
    val messageKey: String,
    val titleArgs: List<Any> = emptyList(),
    val messageArgs: List<Any> = emptyList(),
)
