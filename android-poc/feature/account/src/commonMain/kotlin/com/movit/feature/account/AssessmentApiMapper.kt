package com.movit.feature.account

import com.movit.core.network.dto.LevelProfileDetailDto
import com.movit.core.network.dto.LimitingFactorDto
import com.movit.core.network.dto.RegionLevelDto

object AssessmentApiMapper {
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
        return FakeAssessmentPreviewData.results.insights
    }
}
