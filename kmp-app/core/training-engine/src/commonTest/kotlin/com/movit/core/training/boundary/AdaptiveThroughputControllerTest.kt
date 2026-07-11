package com.movit.core.training.boundary

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AdaptiveThroughputControllerTest {

    @Test
    fun warmup_noDowngradeWhenUnderBudget() {
        val ctrl = AdaptiveThroughputController(warmupFrames = 10, budgetSlackMs = 5f)
        val high = TrainingThroughputProfiles.HIGH
        repeat(9) {
            assertNull(ctrl.onInferenceMs(10f, high))
        }
        assertNull(ctrl.onInferenceMs(10f, high))
        assertEquals(0, ctrl.adaptiveDowngradeCount)
    }

    @Test
    fun warmup_downgradesWhenP95OverBudget() {
        val ctrl = AdaptiveThroughputController(warmupFrames = 10, budgetSlackMs = 5f)
        val high = TrainingThroughputProfiles.HIGH // budget ~28ms
        repeat(9) {
            assertNull(ctrl.onInferenceMs(40f, high))
        }
        val next = ctrl.onInferenceMs(40f, high)
        assertEquals(TrainingThroughputProfiles.MEDIUM, next)
        assertEquals(1, ctrl.adaptiveDowngradeCount)
    }

    @Test
    fun stepDown_ladderEndsAtStable() {
        assertEquals(TrainingThroughputProfiles.MEDIUM, TrainingThroughputProfiles.stepDown(TrainingThroughputProfiles.HIGH))
        assertEquals(TrainingThroughputProfiles.STABLE, TrainingThroughputProfiles.stepDown(TrainingThroughputProfiles.MEDIUM))
        assertNull(TrainingThroughputProfiles.stepDown(TrainingThroughputProfiles.STABLE))
    }
}
