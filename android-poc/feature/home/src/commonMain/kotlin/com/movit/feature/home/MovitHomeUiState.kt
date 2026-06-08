package com.movit.feature.home

data class MovitHomeUiState(
    val isLoading: Boolean = false,
    val greetingTitle: String = "Home",
    val greetingSubtitle: String = "",
    val todayPlan: HomeTrainingPlanUi? = null,
    val progress: HomeProgressUi? = null,
    val reportPreview: HomeReportPreviewUi? = null,
    val quickActions: List<HomeQuickActionUi> = emptyList(),
    val insightMessage: String? = null,
    val errorMessage: String? = null,
) {
    val isEmpty: Boolean
        get() = !isLoading &&
            errorMessage == null &&
            todayPlan == null &&
            progress == null &&
            reportPreview == null
}
