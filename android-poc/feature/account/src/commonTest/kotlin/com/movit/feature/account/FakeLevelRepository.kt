package com.movit.feature.account

import com.movit.shared.AppResult

class FakeLevelRepository(
    private val profile: LevelProfileUi = FakeLevelPreviewData.profile,
    private val shouldFail: Boolean = false,
) : LevelRepository {
    override suspend fun fetchLevelProfile(): AppResult<LevelProfileUi> {
        return if (shouldFail) {
            AppResult.Failure("Unable to load level profile.")
        } else {
            AppResult.Success(profile)
        }
    }
}

object FakeLevelPreviewData {
    val profile = LevelProfileUi(
        levelNumber = 2,
        levelName = "Building",
        bodyScore = 65,
        progressToNext = 70,
        pointsToNext = 8,
        reassessmentLabel = "Reassessment in 2 weeks",
        domains = listOf(
            LevelDomainUi("Mobility", 78),
            LevelDomainUi("Stability", 65),
            LevelDomainUi("Strength", 58),
        ),
        planPhases = listOf(
            PlanPhaseUi(
                title = "Foundation · Completed",
                subtitle = "Mobility Starter · 4 weeks",
                status = PlanPhaseStatus.Done,
                highlight = "62 → 68",
            ),
            PlanPhaseUi(
                title = "Building · In progress",
                subtitle = "Full Body 4-Week · Week 2 of 4",
                status = PlanPhaseStatus.Active,
                progressPercent = 48,
                highlight = "Next reassessment after Week 4",
            ),
            PlanPhaseUi(
                title = "Strength · Upcoming",
                subtitle = "Unlocks after progression check",
                status = PlanPhaseStatus.Upcoming,
            ),
        ),
    )
}
