package com.movit.feature.train

data class MovitTrainUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val dashboard: TrainDashboardUi? = null,
    val errorMessage: String? = null,
    val selectedWeekIndex: Int = 0,
    val selectedDayIndex: Int? = null,
)
