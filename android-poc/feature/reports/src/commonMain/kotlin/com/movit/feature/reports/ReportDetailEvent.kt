package com.movit.feature.reports

sealed interface ReportDetailEvent {
    data class PageSelected(val page: ReportDetailPage) : ReportDetailEvent
    data object ShareClicked : ReportDetailEvent
    data object ExportClicked : ReportDetailEvent
    data object RetryClicked : ReportDetailEvent
}
