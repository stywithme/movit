package com.movit.feature.home

object HomeSummaryCalculator {

    fun clampPercent(value: Int): Int = value.coerceIn(0, 100)

    fun weeklyCompletionLabel(percent: Int): String {
        return "${clampPercent(percent)}%"
    }
}
