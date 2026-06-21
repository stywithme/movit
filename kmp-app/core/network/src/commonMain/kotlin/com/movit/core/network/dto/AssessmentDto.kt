package com.movit.core.network.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class AssessmentApiResponse(
    val success: Boolean = false,
    val data: BodyScanResultDto? = null,
    val error: String? = null,
    val message: String? = null,
)

@Serializable
data class AssessmentProgressApiResponse(
    val success: Boolean = false,
    val data: AssessmentProgressDto? = null,
    val error: String? = null,
)

@Serializable
data class AssessmentTemplateApiResponse(
    val success: Boolean = false,
    val data: AssessmentTemplateDto? = null,
    val error: String? = null,
)

@Serializable
data class BodyScanUploadRequestDto(
    val type: String = "initial",
    val bodyScore: Double,
    val mobilityScore: Double,
    val controlScore: Double,
    val symmetryScore: Double? = null,
    val safetyScore: Double,
    val levelId: String? = null,
    val levelNumber: Int? = null,
    val fitnessLevel: String? = null,
    val regions: List<AssessmentRegionDto>,
    val symmetryData: Map<String, Double>? = null,
    val hypotheses: List<JsonElement>? = null,
    val recommendations: List<AssessmentRecommendationDto>? = null,
    val rawReportIds: List<String>? = null,
    val previousId: String? = null,
    val durationMs: Long? = null,
    val movementCount: Int,
    val templateId: String? = null,
)

@Serializable
data class BodyScanResultDto(
    val id: String = "",
    val userId: String = "",
    val type: String = "",
    val bodyScore: Double = 0.0,
    val mobilityScore: Double = 0.0,
    val controlScore: Double = 0.0,
    val symmetryScore: Double? = null,
    val safetyScore: Double = 0.0,
    val fitnessLevel: String? = null,
    val levelNumber: Int? = null,
    val regions: List<AssessmentRegionDto> = emptyList(),
    val symmetryData: Map<String, Double>? = null,
    val rawReportIds: List<String>? = null,
    val previousId: String? = null,
    val durationMs: Long? = null,
    val movementCount: Int = 0,
    val templateId: String? = null,
    val completedAt: String? = null,
)

@Serializable
data class AssessmentRegionDto(
    val region: String,
    val side: String = "center",
    val absoluteRom: Double = 0.0,
    val errorBand: Double = 0.0,
    val referenceNorm: Double = 0.0,
    val romPercentage: Double = 0.0,
    val romPercentageMin: Double = 0.0,
    val romPercentageMax: Double = 0.0,
    val movementQualityGrade: Int = 2,
    val stabilityScore: Double = 0.0,
    val regionalScore: Double = 0.0,
    val confidence: String = "medium",
    val status: String = "average",
)

@Serializable
data class AssessmentRecommendationDto(
    val priority: Int,
    val type: String,
    val exercises: List<String> = emptyList(),
)

@Serializable
data class AssessmentTemplateDto(
    val templateId: String = "",
    val name: LocalizedNameDto = LocalizedNameDto(),
    val type: String = "initial",
    val domainWeights: Map<String, Double> = emptyMap(),
    val exercises: List<AssessmentTemplateExerciseDto> = emptyList(),
)

@Serializable
data class AssessmentTemplateExerciseDto(
    val exerciseId: String = "",
    val exerciseSlug: String = "",
    val exerciseName: LocalizedNameDto = LocalizedNameDto(),
    val sortOrder: Int = 0,
    val targetRegion: String = "",
    val side: String = "center",
    val entryType: String = "core",
    val activationCondition: JsonElement? = null,
    val referenceNormDegrees: Double? = null,
    val thresholds: Map<String, Double>? = null,
)

@Serializable
data class AssessmentProgressDto(
    val current: AssessmentProgressPointDto? = null,
    val previous: AssessmentProgressPointDto? = null,
    val changes: AssessmentProgressChangesDto? = null,
)

@Serializable
data class AssessmentProgressPointDto(
    val bodyScore: Double = 0.0,
    val domainScores: AssessmentDomainScoresDto = AssessmentDomainScoresDto(),
    val completedAt: String = "",
)

@Serializable
data class AssessmentDomainScoresDto(
    val mobility: Double = 0.0,
    val control: Double = 0.0,
    val symmetry: Double? = null,
    val safety: Double = 0.0,
)

@Serializable
data class AssessmentProgressChangesDto(
    val bodyScoreDelta: Double = 0.0,
    val mobilityDelta: Double = 0.0,
    val controlDelta: Double = 0.0,
    val symmetryDelta: Double? = null,
    val safetyDelta: Double = 0.0,
    val isRealImprovement: Boolean = false,
)
