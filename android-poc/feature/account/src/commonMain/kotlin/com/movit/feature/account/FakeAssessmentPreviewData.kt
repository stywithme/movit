package com.movit.feature.account

object AssessmentDefaults {
    val parqQuestions = listOf(
        "assessment_parq_heart",
        "assessment_parq_chest_pain",
        "assessment_parq_dizziness",
        "assessment_parq_bone_joint",
        "assessment_parq_medication",
        "assessment_parq_other_reason",
        "assessment_parq_pregnancy",
    )

    val initialTemplate = AssessmentTemplateUi(
        templateId = null,
        type = "initial",
        domainWeights = AssessmentDomainWeights(),
        movements = listOf(
            AssessmentMovementUi(
                exerciseId = "local-forward-fold",
                exerciseSlug = "forward_fold",
                titleKey = "assessment_movement_forward_fold",
                targetRegion = "spine",
                side = "center",
                referenceNormDegrees = 110.0,
            ),
            AssessmentMovementUi(
                exerciseId = "local-overhead-squat",
                exerciseSlug = "overhead_squat",
                titleKey = "assessment_movement_overhead_squat",
                targetRegion = "hips",
                side = "center",
                referenceNormDegrees = 120.0,
            ),
            AssessmentMovementUi(
                exerciseId = "local-single-leg-balance",
                exerciseSlug = "single_leg_balance",
                titleKey = "assessment_movement_single_leg_balance",
                targetRegion = "balance",
                side = "center",
                referenceNormDegrees = 100.0,
            ),
        ),
    )
}

data class AssessmentResultsUi(
    val bodyScore: Int,
    val levelLabel: String,
    val domains: List<AssessmentDomainUi> = emptyList(),
    val regions: List<AssessmentRegionUi>,
    val insights: List<AssessmentInsightUi>,
)

data class AssessmentTemplateUi(
    val templateId: String?,
    val type: String = "initial",
    val domainWeights: AssessmentDomainWeights = AssessmentDomainWeights(),
    val movements: List<AssessmentMovementUi>,
) {
    val safeMovements: List<AssessmentMovementUi>
        get() = movements.ifEmpty { AssessmentDefaults.initialTemplate.movements }
}

data class AssessmentDomainWeights(
    val mobility: Double = 0.35,
    val control: Double = 0.25,
    val symmetry: Double = 0.20,
    val safety: Double = 0.20,
)

data class AssessmentMovementUi(
    val exerciseId: String,
    val exerciseSlug: String,
    val titleKey: String,
    val targetRegion: String,
    val side: String = "center",
    val entryType: String = "core",
    val referenceNormDegrees: Double? = null,
    val thresholds: Map<String, Double> = emptyMap(),
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
