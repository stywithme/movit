package com.trainingvalidator.poc.network

import com.google.gson.annotations.SerializedName

data class AssessmentTemplateResponse(
    val success: Boolean,
    val data: AssessmentTemplateData?
)

data class AssessmentTemplateData(
    val templateId: String,
    val name: LocalizedName,
    val type: String,
    val domainWeights: DomainWeightsData,
    val exercises: List<TemplateExerciseData>
)

data class DomainWeightsData(
    val mobility: Float,
    val control: Float,
    val symmetry: Float,
    val safety: Float
)

data class TemplateExerciseData(
    val exerciseId: String,
    val exerciseSlug: String,
    val sortOrder: Int,
    val targetRegion: String,
    val side: String,
    val entryType: String,
    val activationCondition: Map<String, Any>?,
    val referenceNormDegrees: Float?,
    val thresholds: ExerciseThresholds?
)

data class ExerciseThresholds(
    val excellent: Float?,
    val good: Float?,
    val average: Float?,
    val limited: Float?
)
