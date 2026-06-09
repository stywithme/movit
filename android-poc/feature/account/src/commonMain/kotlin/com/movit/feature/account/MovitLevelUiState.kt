package com.movit.feature.account

enum class LevelTab {
    LevelProfile,
    PlanOverview,
}

data class MovitLevelUiState(
    val selectedTab: LevelTab = LevelTab.LevelProfile,
    val isLoading: Boolean = true,
    val profile: LevelProfileUi? = null,
    val errorMessage: String? = null,
)
