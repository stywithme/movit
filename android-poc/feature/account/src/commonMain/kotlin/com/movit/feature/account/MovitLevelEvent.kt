package com.movit.feature.account

sealed interface MovitLevelEvent {
    data object RetryClicked : MovitLevelEvent
    data class TabSelected(val tab: LevelTab) : MovitLevelEvent
    data object StartScanClicked : MovitLevelEvent
    data object BrowseProgramsClicked : MovitLevelEvent
    data object DismissLevelUpCelebration : MovitLevelEvent
}
