package com.movit.feature.reports

sealed interface MovitReportsEffect {
    data object OpenTrain : MovitReportsEffect
    data object OpenUpgrade : MovitReportsEffect
    data class OpenReportDetail(val reportId: String) : MovitReportsEffect
    data class ShowMessage(val message: String) : MovitReportsEffect
}
