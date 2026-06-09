package com.movit.feature.home

data class MovitHomeUiState(
    val isLoading: Boolean = false,
    val userName: String = "Athlete",
    val greetingEyebrow: String = "Good morning",
    val greetingTitle: String = "",
    val greetingSubtitle: String = "",
    val metricTiles: List<HomeMetricTileUi> = emptyList(),
    val levelCard: HomeLevelCardUi? = null,
    val alert: HomeAlertUi? = null,
    val activeProgram: HomeActiveProgramUi? = null,
    val todayPlan: HomeTrainingPlanUi? = null,
    val showBodyScanCta: Boolean = false,
    val showNoProgramEmpty: Boolean = false,
    val journeyRows: List<HomeJourneyRowUi> = emptyList(),
    val recentActivities: List<HomeActivityUi> = emptyList(),
    val progress: HomeProgressUi? = null,
    val reportPreview: HomeReportPreviewUi? = null,
    val quickActions: List<HomeQuickActionUi> = emptyList(),
    val insightMessage: String? = null,
    val errorMessage: String? = null,
)
