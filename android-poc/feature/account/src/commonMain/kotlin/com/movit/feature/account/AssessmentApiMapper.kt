package com.movit.feature.account

import com.movit.core.network.dto.AssessmentRegionDto
import com.movit.core.network.dto.AssessmentTemplateDto
import com.movit.core.network.dto.BodyScanResultDto
import com.movit.core.network.dto.LevelProfileDetailDto
import com.movit.core.network.dto.LimitingFactorDto
import com.movit.core.network.dto.RegionLevelDto

object AssessmentApiMapper {
    fun map(dto: BodyScanResultDto, language: String = "en"): AssessmentResultsUi {
        val levelNumber = dto.levelNumber ?: bodyScoreToLevel(dto.bodyScore)
        val fitnessLabel = dto.fitnessLevel
            ?.replace("_", " ")
            ?.replaceFirstChar { it.uppercase() }
            ?: bodyScoreLabel(dto.bodyScore)
        val domains = listOf(
            AssessmentDomainUi("mobility", dto.mobilityScore.toInt().coerceIn(0, 100)),
            AssessmentDomainUi("control", dto.controlScore.toInt().coerceIn(0, 100)),
            AssessmentDomainUi("symmetry", (dto.symmetryScore ?: average(dto.mobilityScore, dto.controlScore)).toInt().coerceIn(0, 100)),
            AssessmentDomainUi("safety", dto.safetyScore.toInt().coerceIn(0, 100)),
        )
        val regions = dto.regions.map(::mapRegion)
        return AssessmentResultsUi(
            bodyScore = dto.bodyScore.toInt().coerceIn(0, 100),
            levelLabel = "Level $levelNumber · $fitnessLabel",
            domains = domains,
            regions = regions,
            insights = mapRegionInsights(regions),
        )
    }

    fun mapTemplate(dto: AssessmentTemplateDto, language: String = "en"): AssessmentTemplateUi {
        val weights = dto.domainWeights
        return AssessmentTemplateUi(
            templateId = dto.templateId.ifBlank { null },
            type = dto.type.ifBlank { "initial" },
            domainWeights = AssessmentDomainWeights(
                mobility = weights["mobility"] ?: AssessmentDomainWeights().mobility,
                control = weights["control"] ?: AssessmentDomainWeights().control,
                symmetry = weights["symmetry"] ?: AssessmentDomainWeights().symmetry,
                safety = weights["safety"] ?: AssessmentDomainWeights().safety,
            ),
            movements = dto.exercises
                .sortedBy { it.sortOrder }
                .map { exercise ->
                    val localizedName = when (language.lowercase()) {
                        "ar" -> exercise.exerciseName.ar.ifBlank { exercise.exerciseName.en }
                        else -> exercise.exerciseName.en.ifBlank { exercise.exerciseSlug }
                    }
                    AssessmentMovementUi(
                        exerciseId = exercise.exerciseId,
                        exerciseSlug = exercise.exerciseSlug,
                        titleKey = movementKey(exercise.exerciseSlug, localizedName),
                        targetRegion = exercise.targetRegion,
                        side = exercise.side,
                        entryType = exercise.entryType,
                        referenceNormDegrees = exercise.referenceNormDegrees,
                        thresholds = exercise.thresholds.orEmpty(),
                    )
                }
                .ifEmpty { AssessmentDefaults.initialTemplate.movements },
        )
    }

    fun map(dto: LevelProfileDetailDto, language: String = "en"): AssessmentResultsUi {
        val levelName = when (language.lowercase()) {
            "ar" -> dto.levelInfo.name.ar.ifBlank { dto.levelInfo.name.en }
            else -> dto.levelInfo.name.en.ifBlank { dto.levelInfo.code }
        }
        val levelNumber = dto.overallLevel.coerceAtLeast(dto.levelInfo.number)
        val levelLabel = if (levelNumber > 0) {
            "Level $levelNumber · $levelName"
        } else {
            levelName
        }
        return AssessmentResultsUi(
            bodyScore = dto.bodyScore.toInt().coerceIn(0, 100),
            levelLabel = levelLabel,
            domains = dto.domainLevels.map { domain ->
                AssessmentDomainUi(
                    domainKey = domain.domain.lowercase(),
                    score = domain.score.toInt().coerceIn(0, 100),
                )
            },
            regions = dto.regionLevels.map(::mapRegion),
            insights = mapInsights(dto.limitingFactors, dto.regionLevels),
        )
    }

    private fun mapRegion(region: RegionLevelDto): AssessmentRegionUi {
        val score = region.score.toInt().coerceIn(0, 100)
        val tone = when {
            region.isLimiting || score < 65 -> AssessmentRegionTone.Warning
            score >= 80 -> AssessmentRegionTone.Good
            else -> AssessmentRegionTone.Neutral
        }
        return AssessmentRegionUi(
            regionKey = region.region.lowercase(),
            score = score,
            tone = tone,
        )
    }

    private fun mapInsights(
        limitingFactors: List<LimitingFactorDto>,
        regions: List<RegionLevelDto>,
    ): List<AssessmentInsightUi> {
        if (limitingFactors.isNotEmpty()) {
            return limitingFactors.take(3).map { factor ->
                AssessmentInsightUi(
                    titleKey = "assessment_insight_limiting_title",
                    messageKey = "assessment_insight_limiting_message",
                    titleArgs = listOf(factor.code.ifBlank { factor.type }),
                    messageArgs = listOf(factor.gap.coerceAtLeast(0)),
                )
            }
        }
        val limitingRegions = regions.filter { it.isLimiting }.take(2)
        if (limitingRegions.isNotEmpty()) {
            return limitingRegions.map { region ->
                AssessmentInsightUi(
                    titleKey = "assessment_insight_region_limit_title",
                    messageKey = "assessment_insight_region_limit_message",
                    titleArgs = listOf(region.region.replaceFirstChar { it.uppercase() }),
                    messageArgs = listOf(region.score.toInt()),
                )
            }
        }
        return listOf(
            AssessmentInsightUi(
                titleKey = "assessment_insight_hip_title",
                messageKey = "assessment_insight_hip_message",
            ),
        )
    }

    private fun mapRegion(region: AssessmentRegionDto): AssessmentRegionUi {
        val score = region.regionalScore.toInt().coerceIn(0, 100)
        return AssessmentRegionUi(
            regionKey = regionKey(region.region),
            score = score,
            tone = when {
                region.status == "limited" || region.status == "weak" || score < 65 -> AssessmentRegionTone.Warning
                score >= 80 -> AssessmentRegionTone.Good
                else -> AssessmentRegionTone.Neutral
            },
        )
    }

    private fun mapRegionInsights(regions: List<AssessmentRegionUi>): List<AssessmentInsightUi> {
        val limiting = regions.filter { it.tone == AssessmentRegionTone.Warning }
            .sortedBy { it.score }
            .take(2)
        if (limiting.isEmpty()) {
            return listOf(
                AssessmentInsightUi(
                    titleKey = "assessment_insight_hip_title",
                    messageKey = "assessment_insight_hip_message",
                ),
            )
        }
        return limiting.map { region ->
            AssessmentInsightUi(
                titleKey = "assessment_insight_region_limit_title",
                messageKey = "assessment_insight_region_limit_message",
                titleArgs = listOf(region.regionKey.replaceFirstChar { it.uppercase() }),
                messageArgs = listOf(region.score),
            )
        }
    }

    private fun movementKey(slug: String, localizedName: String): String {
        return when {
            "forward" in slug || "fold" in slug -> "assessment_movement_forward_fold"
            "squat" in slug -> "assessment_movement_overhead_squat"
            "balance" in slug -> "assessment_movement_single_leg_balance"
            else -> localizedName.ifBlank { slug }
        }
    }

    private fun regionKey(region: String): String = when (region.lowercase()) {
        "hip", "hips" -> "hips"
        "shoulder", "shoulders" -> "shoulders"
        "knee", "knees" -> "knees"
        "spine", "lower_back", "back" -> "spine"
        "balance" -> "balance"
        "core" -> "core"
        else -> region.lowercase().ifBlank { "core" }
    }

    private fun bodyScoreToLevel(score: Double): Int = when {
        score >= 85.0 -> 5
        score >= 65.0 -> 4
        score >= 45.0 -> 3
        score >= 25.0 -> 2
        else -> 1
    }

    private fun bodyScoreLabel(score: Double): String = when {
        score >= 85.0 -> "Excellent"
        score >= 65.0 -> "Good"
        score >= 45.0 -> "Average"
        score >= 25.0 -> "Limited"
        else -> "Needs rehab"
    }

    private fun average(a: Double, b: Double): Double = (a + b) / 2.0
}
