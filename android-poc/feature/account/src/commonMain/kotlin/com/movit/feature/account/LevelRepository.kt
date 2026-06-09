package com.movit.feature.account

import com.movit.shared.AppResult

data class LevelProfileUi(
    val levelNumber: Int,
    val levelName: String,
    val bodyScore: Int,
    val progressToNext: Int,
    val pointsToNext: Int,
    val reassessmentLabel: String,
    val domains: List<LevelDomainUi>,
    val planPhases: List<PlanPhaseUi>,
)

data class LevelDomainUi(
    val name: String,
    val score: Int,
)

data class PlanPhaseUi(
    val title: String,
    val subtitle: String,
    val status: PlanPhaseStatus,
    val progressPercent: Int? = null,
    val highlight: String? = null,
)

enum class PlanPhaseStatus {
    Done,
    Active,
    Upcoming,
}

interface LevelRepository {
    suspend fun fetchLevelProfile(): AppResult<LevelProfileUi>
}

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
