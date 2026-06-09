package com.movit.feature.reports

data class MovitReportsUiState(
    val isLoading: Boolean = false,
    val selectedTab: ReportsTab = ReportsTab.Overview,
    val dashboard: ReportsDashboardUi? = null,
    val errorMessage: String? = null,
)
