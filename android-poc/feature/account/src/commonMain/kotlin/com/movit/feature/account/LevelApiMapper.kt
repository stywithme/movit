package com.movit.feature.account

import com.movit.core.network.dto.LevelProfileDetailDto

object LevelApiMapper {
    fun map(dto: LevelProfileDetailDto, language: String = "en"): LevelProfileUi {
        val levelName = when (language.lowercase()) {
            "ar" -> dto.levelInfo.name.ar.ifBlank { dto.levelInfo.name.en }
            else -> dto.levelInfo.name.en.ifBlank { dto.levelInfo.code }
        }
        val bodyScore = dto.bodyScore.toInt()
        val nextTarget = ((dto.overallLevel + 1) * 10).coerceAtMost(100)
        val pointsToNext = (nextTarget - bodyScore).coerceAtLeast(0)
        return LevelProfileUi(
            levelNumber = dto.overallLevel,
            levelName = levelName,
            bodyScore = bodyScore,
            progressToNext = bodyScore,
            pointsToNext = pointsToNext,
            reassessmentLabel = "Reassessment in 2 weeks",
            domains = dto.domainLevels.map { domain ->
                LevelDomainUi(
                    name = domain.domain.replaceFirstChar { it.uppercase() },
                    score = domain.score.toInt(),
                )
            },
            planPhases = FakeLevelPreviewData.profile.planPhases,
        )
    }
}
