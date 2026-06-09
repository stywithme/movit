package com.movit.feature.account

object FakeAssessmentPreviewData {
    val results = AssessmentResultsUi(
        bodyScore = 72,
        levelLabel = "Level 2 · Building",
        regions = listOf(
            AssessmentRegionUi("hips", 84, AssessmentRegionTone.Good),
            AssessmentRegionUi("shoulders", 61, AssessmentRegionTone.Warning),
            AssessmentRegionUi("spine", 74, AssessmentRegionTone.Neutral),
            AssessmentRegionUi("knees", 79, AssessmentRegionTone.Good),
        ),
        insights = listOf(
            AssessmentInsightUi(
                title = "Limited shoulder flexion",
                message = "May affect overhead pressing. Consider mobility work before heavy loads.",
            ),
            AssessmentInsightUi(
                title = "Good hip hinge pattern",
                message = "Deadlift and squat patterns look solid. Ready for progressive loading.",
            ),
        ),
    )

    val parqQuestions = listOf(
        "assessment_parq_heart",
        "assessment_parq_chest_pain",
        "assessment_parq_dizziness",
    )
}

data class AssessmentResultsUi(
    val bodyScore: Int,
    val levelLabel: String,
    val regions: List<AssessmentRegionUi>,
    val insights: List<AssessmentInsightUi>,
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
    val title: String,
    val message: String,
)
