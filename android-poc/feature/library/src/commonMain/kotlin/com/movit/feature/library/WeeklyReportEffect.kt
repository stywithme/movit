package com.movit.feature.library

sealed interface WeeklyReportEffect {
    data class ShareRequested(
        val subject: String,
        val text: String,
    ) : WeeklyReportEffect
}
