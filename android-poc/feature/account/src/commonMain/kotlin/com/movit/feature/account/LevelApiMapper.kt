package com.movit.feature.account

import com.movit.core.network.dto.ActivePlanDto
import com.movit.core.network.dto.ActivePlanProgramDto
import com.movit.core.network.dto.LevelProfileDetailDto
import com.movit.core.network.dto.ReassessmentDto
import com.movit.resources.strings.LevelStrings

object LevelApiMapper {
    suspend fun map(
        dto: LevelProfileDetailDto,
        plan: ActivePlanDto?,
        reassessments: List<ReassessmentDto>,
        strings: LevelStrings,
    ): LevelProfileUi {
        val levelName = dto.levelInfo.name.display(strings.language).ifBlank { dto.levelInfo.code }
        val bodyScore = dto.bodyScore.toInt()
        val nextTarget = ((dto.overallLevel + 1) * 10).coerceAtMost(100)
        val pointsToNext = (nextTarget - bodyScore).coerceAtLeast(0)
        val reassessmentLabel = reassessments.firstOrNull()?.scheduledDate?.takeIf { it.isNotBlank() }
            ?.let { strings.reassessmentScheduled(it) }
            ?: strings.reassessmentSoon

        return LevelProfileUi(
            levelNumber = dto.overallLevel,
            levelName = levelName,
            bodyScore = bodyScore,
            progressToNext = bodyScore,
            pointsToNext = pointsToNext,
            reassessmentLabel = reassessmentLabel,
            domains = dto.domainLevels.map { domain ->
                LevelDomainUi(
                    name = strings.domainName(domain.domain),
                    score = domain.score.toInt(),
                )
            },
            regions = dto.regionLevels.map { region ->
                LevelRegionUi(
                    name = strings.regionName(region.region),
                    level = region.level,
                    score = region.score.toInt(),
                    isLimiting = region.isLimiting,
                )
            },
            limitingFactors = dto.limitingFactors.map { factor ->
                LevelLimitingFactorUi(
                    type = factor.type,
                    name = strings.limitingFactorName(factor.type, factor.code),
                    currentLevel = factor.currentLevel,
                    targetLevel = factor.targetLevel,
                    gap = factor.gap,
                )
            },
            planPhases = mapPlanPhases(plan, strings, reassessments),
        )
    }

    private suspend fun mapPlanPhases(
        plan: ActivePlanDto?,
        strings: LevelStrings,
        reassessments: List<ReassessmentDto>,
    ): List<PlanPhaseUi> {
        val programs = plan?.programs.orEmpty().sortedBy { it.sortOrder }
        if (programs.isEmpty()) return emptyList()

        return programs.map { slot ->
            val program = slot.program
            val programName = program?.name?.localized(strings.language)
                ?.ifBlank { strings.programFallback }
                ?: strings.programFallback
            val levelMin = program?.levelMin
            val levelLabel = levelMin?.name?.localized(strings.language)
                ?.ifBlank { levelMin.code }
                ?: program?.type.orEmpty().ifBlank { programName }
            val durationWeeks = program?.durationWeeks ?: 0

            when (slot.status.lowercase()) {
                "completed" -> PlanPhaseUi(
                    title = "$levelLabel · ${strings.phaseCompleted}",
                    subtitle = strings.phaseWeeks(programName, durationWeeks),
                    status = PlanPhaseStatus.Done,
                    highlight = null,
                )
                "active" -> {
                    val progress = slot.progress
                    val percent = if (progress.totalDays > 0) {
                        (progress.completedDays.toFloat() / progress.totalDays * 100).toInt()
                    } else {
                        0
                    }
                    val highlight = reassessments.firstOrNull()?.let {
                        strings.reassessmentNext(progress.currentWeek.coerceAtLeast(1))
                    } ?: strings.reassessmentNext(durationWeeks.coerceAtLeast(progress.currentWeek))
                    PlanPhaseUi(
                        title = "$levelLabel · ${strings.phaseInProgress}",
                        subtitle = "${programName} · ${strings.phaseWeekProgress(
                            progress.currentWeek,
                            durationWeeks.coerceAtLeast(1),
                        )}",
                        status = PlanPhaseStatus.Active,
                        progressPercent = percent,
                        highlight = highlight,
                    )
                }
                else -> PlanPhaseUi(
                    title = "$levelLabel · ${strings.phaseUpcoming}",
                    subtitle = strings.unlocksAfterProgression,
                    status = PlanPhaseStatus.Upcoming,
                )
            }
        }
    }

    private fun Map<String, String>.localized(language: String): String {
        if (isEmpty()) return ""
        return when (language.lowercase()) {
            "ar" -> this["ar"].orEmpty().ifBlank { this["en"].orEmpty() }
            else -> this["en"].orEmpty().ifBlank { this["ar"].orEmpty() }
        }
    }
}
