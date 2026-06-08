package com.movit.feature.home

import kotlin.test.Test
import kotlin.test.assertEquals

class HomeSummaryCalculatorTest {

    @Test
    fun clampPercent_belowZero_returnsZero() {
        assertEquals(0, HomeSummaryCalculator.clampPercent(-12))
    }

    @Test
    fun clampPercent_aboveHundred_returnsHundred() {
        assertEquals(100, HomeSummaryCalculator.clampPercent(140))
    }

    @Test
    fun clampPercent_inRange_returnsValue() {
        assertEquals(71, HomeSummaryCalculator.clampPercent(71))
    }

    @Test
    fun weeklyCompletionLabel_formatsPercent() {
        assertEquals("71%", HomeSummaryCalculator.weeklyCompletionLabel(71))
        assertEquals("100%", HomeSummaryCalculator.weeklyCompletionLabel(500))
    }
}
